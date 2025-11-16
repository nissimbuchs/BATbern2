import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createContainerImage } from '../utils/container-image-helper';

export interface DomainServiceConfig {
  serviceName: string;
  componentTag: string;
  routePattern: string;
  cpu: number;
  memoryLimitMiB: number;
  additionalEnvironment?: Record<string, string>;
}

export interface DomainServiceConstructProps {
  config: EnvironmentConfig;
  serviceConfig: DomainServiceConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
}

/**
 * Reusable Domain Service Helper for BATbern Microservices
 *
 * This helper function creates common infrastructure for all domain microservices:
 * - Fargate task definition with ARM64 architecture
 * - Container with Docker build from source
 * - Spring Boot configuration (profiles, database, Cognito)
 * - Service Connect for direct service-to-service communication (no ALB needed)
 * - Health checks via Spring Boot Actuator
 * - CPU-based auto-scaling
 * - CloudWatch logging with environment-based retention
 * - IAM permissions for logs and secrets
 *
 * Used by: Event Management, Speaker Coordination, Partner Coordination,
 *          Company Management, and Attendee Experience services
 *
 * @returns Object with service property
 */
export function createDomainService(
  scope: Construct,
  props: DomainServiceConstructProps
): { service: ecs.FargateService } {

    const envName = props.config.envName;
    const isProd = envName === 'production';
    const { serviceName, componentTag, additionalEnvironment } = props.serviceConfig;

    // Common environment variables (non-sensitive)
    const commonEnv = {
      SPRING_PROFILES_ACTIVE: envName,
      AWS_REGION: props.config.region,
      LOG_LEVEL: isProd ? 'INFO' : 'DEBUG',
      SERVICE_NAME: serviceName,
      ...(props.databaseEndpoint && {
        DATABASE_URL: `jdbc:postgresql://${props.databaseEndpoint}:5432/batbern`,
      }),
      // Cognito configuration
      COGNITO_USER_POOL_ID: props.userPool.userPoolId,
      COGNITO_CLIENT_ID: props.userPoolClient.userPoolClientId,
      // Merge any additional service-specific environment variables
      ...(additionalEnvironment || {}),
    };

    // Secrets from AWS Secrets Manager
    const secrets: Record<string, ecs.Secret> = {};
    if (props.databaseSecret) {
      secrets.DATABASE_USERNAME = ecs.Secret.fromSecretsManager(props.databaseSecret, 'username');
      secrets.DATABASE_PASSWORD = ecs.Secret.fromSecretsManager(props.databaseSecret, 'password');
    }

    // Create stable log group
    const logGroup = new logs.LogGroup(scope, 'LogGroup', {
      logGroupName: `/aws/ecs/BATbern-${envName}/${serviceName}`,
      retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Create task definition
    const taskDefinition = new ecs.FargateTaskDefinition(scope, 'TaskDef', {
      cpu: props.serviceConfig.cpu,
      memoryLimitMiB: props.serviceConfig.memoryLimitMiB,
      runtimePlatform: {
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        cpuArchitecture: ecs.CpuArchitecture.ARM64,
      },
    });

    // Add container
    const container = taskDefinition.addContainer('Container', {
      image: createContainerImage(
        scope,
        'ServiceRepository',
        `${serviceName}-service`,
        envName,
        `services/${serviceName}-service/Dockerfile`
      ),
      logging: ecs.LogDrivers.awsLogs({
        logGroup,
        streamPrefix: serviceName,
      }),
      environment: commonEnv,
      secrets,
      healthCheck: {
        command: ['CMD-SHELL', 'curl -f http://localhost:8080/actuator/health || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        startPeriod: cdk.Duration.seconds(60),
      },
    });

    // Add named port mapping (required for Service Connect)
    container.addPortMappings({
      name: `${serviceName}-port`,
      containerPort: 8080,
      protocol: ecs.Protocol.TCP,
    });

    // Grant CloudWatch Logs permissions
    taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'logs:CreateLogGroup',
        'logs:CreateLogStream',
        'logs:PutLogEvents',
        'logs:DescribeLogStreams',
      ],
      resources: ['*'],
    }));

    // Grant CloudWatch Metrics permissions
    taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cloudwatch:PutMetricData',
      ],
      resources: ['*'],
    }));

    // Grant Secrets Manager permissions to task execution role
    if (props.databaseSecret) {
      props.databaseSecret.grantRead(taskDefinition.executionRole!);
    }

    // Create explicit security group with restricted egress
    const serviceSecurityGroup = new ec2.SecurityGroup(scope, 'ServiceSecurityGroup', {
      vpc: props.vpc,
      description: `Security group for ${serviceName} ECS service`,
      allowAllOutbound: false, // Explicitly disable to avoid CDK warning
    });

    // Add only necessary egress rules
    serviceSecurityGroup.addEgressRule(
      props.databaseSecurityGroup,
      ec2.Port.tcp(5432),
      'Allow outbound to PostgreSQL database'
    );

    // IMPORTANT: Database security group ingress is configured in VPC construct
    // to allow connections from private subnets, avoiding cyclic dependencies
    // between Network stack and service stacks

    // Allow HTTPS outbound for AWS API calls (Secrets Manager, CloudWatch, etc.)
    serviceSecurityGroup.addEgressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(443),
      'Allow HTTPS outbound for AWS API calls'
    );

    // Allow HTTP outbound for Service Connect inter-service communication
    // Service Connect uses port 8080 for microservice-to-microservice calls
    serviceSecurityGroup.addEgressRule(
      ec2.Peer.ipv4(props.vpc.vpcCidrBlock),
      ec2.Port.tcp(8080),
      'Allow HTTP outbound for Service Connect inter-service communication'
    );

    // Allow HTTP inbound for Service Connect inter-service communication
    // This allows other services in the VPC to connect to this service via Service Connect
    serviceSecurityGroup.addIngressRule(
      ec2.Peer.ipv4(props.vpc.vpcCidrBlock),
      ec2.Port.tcp(8080),
      'Allow HTTP inbound for Service Connect inter-service communication'
    );

    // Create service with Service Connect (no ALB needed)
    const service = new ecs.FargateService(scope, 'Service', {
      cluster: props.cluster,
      taskDefinition,
      desiredCount: isProd ? 2 : 1,
      assignPublicIp: false,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
      minHealthyPercent: 100, // Ensure zero-downtime deployments
      maxHealthyPercent: 200, // Allow temporary extra tasks during deployments
      securityGroups: [serviceSecurityGroup], // Use explicit security group
      enableExecuteCommand: true, // Allow ECS Exec for debugging
    });

    // Enable Service Connect for service-to-service communication
    // This provides automatic DNS-based discovery without requiring ALBs
    // Using addPropertyOverride because L2 FargateService doesn't fully support Service Connect yet
    const cfnService = service.node.defaultChild as ecs.CfnService;
    cfnService.addPropertyOverride('ServiceConnectConfiguration', {
      Enabled: true,
      Namespace: 'batbern.local',
      Services: [{
        PortName: `${serviceName}-port`, // Must match container port mapping name
        DiscoveryName: serviceName, // Service discovery name in CloudMap
        ClientAliases: [{
          Port: 8080, // Port where service is accessible
          DnsName: serviceName, // DNS name for other services to use
        }],
      }],
    });

    // Configure auto-scaling
    const scaling = service.autoScaleTaskCount({
      minCapacity: isProd ? 2 : 1,
      maxCapacity: (isProd ? 2 : 1) * 4,
    });

    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 70,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    // Output Service Connect DNS name for debugging
    new cdk.CfnOutput(scope, 'ServiceConnectDNS', {
      value: `http://${serviceName}:8080`,
      description: `${serviceName} Service Connect DNS endpoint (accessible within VPC)`,
      exportName: `${envName}-${serviceName}-service-connect-dns`,
    });

    new cdk.CfnOutput(scope, 'ServiceArn', {
      value: service.serviceArn,
      description: `${serviceName} ECS Service ARN`,
      exportName: `${envName}-${serviceName}-service-arn`,
    });

    // Apply tags
    cdk.Tags.of(scope).add('Environment', envName);
    cdk.Tags.of(scope).add('Component', componentTag);
    cdk.Tags.of(scope).add('Project', 'BATbern');

    return { service };
}
