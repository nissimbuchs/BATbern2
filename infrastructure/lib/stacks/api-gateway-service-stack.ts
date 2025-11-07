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

export interface ApiGatewayServiceStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
  eventManagementServiceUrl?: string;
  speakerCoordinationServiceUrl?: string;
  partnerCoordinationServiceUrl?: string;
  attendeeExperienceServiceUrl?: string;
  companyUserManagementServiceUrl?: string;
}

/**
 * API Gateway Service Stack - Spring Boot API Gateway on ECS Fargate
 *
 * This is the Spring Boot application that routes requests to domain microservices.
 * It runs on ECS with a public ALB and uses Service Connect for direct communication with microservices.
 *
 * Architecture:
 * Client → AWS HTTP API (Cognito auth) → **This Service** (Public ALB) → Domain Microservices (Service Connect)
 *
 * Service Connect eliminates the need for internal ALBs on microservices, reducing costs by ~$64/month
 * and improving latency through direct container-to-container communication via Envoy proxy.
 */
export class ApiGatewayServiceStack extends cdk.Stack {
  public readonly service: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly apiGatewayUrl: string;

  constructor(scope: Construct, id: string, props: ApiGatewayServiceStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const isProd = envName === 'production';

    // Common environment variables (non-sensitive)
    const commonEnv = {
      SPRING_PROFILES_ACTIVE: envName,
      APP_ENVIRONMENT: envName,
      AWS_REGION: props.config.region,
      LOG_LEVEL: isProd ? 'INFO' : 'DEBUG',
      ...(props.databaseEndpoint && {
        DATABASE_URL: `jdbc:postgresql://${props.databaseEndpoint}:5432/batbern`,
      }),
    };

    // Secrets from AWS Secrets Manager
    const secrets: Record<string, ecs.Secret> = {};
    if (props.databaseSecret) {
      secrets.DATABASE_USERNAME = ecs.Secret.fromSecretsManager(props.databaseSecret, 'username');
      secrets.DATABASE_PASSWORD = ecs.Secret.fromSecretsManager(props.databaseSecret, 'password');
    }

    // Create stable log group for API Gateway
    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: `/aws/ecs/BATbern-${envName}/api-gateway`,
      retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Create task definition
    const taskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      cpu: 512,
      memoryLimitMiB: 1024,
      runtimePlatform: {
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        cpuArchitecture: ecs.CpuArchitecture.ARM64,
      },
    });

    // Add container
    const container = taskDefinition.addContainer('Container', {
      image: createContainerImage(
        this,
        'ApiGatewayRepository',
        'api-gateway',
        envName,
        'api-gateway/Dockerfile'
      ),
      logging: ecs.LogDrivers.awsLogs({
        logGroup,
        streamPrefix: 'api-gateway',
      }),
      environment: {
        ...commonEnv,
        SERVICE_NAME: 'api-gateway',
        // Service Connect DNS names for direct service-to-service communication
        // Format: http://<service-name>:8080 (resolves via CloudMap namespace: batbern.local)
        EVENT_MANAGEMENT_SERVICE_URL: 'http://event-management:8080',
        SPEAKER_COORDINATION_SERVICE_URL: 'http://speaker-coordination:8080',
        PARTNER_COORDINATION_SERVICE_URL: 'http://partner-coordination:8080',
        ATTENDEE_EXPERIENCE_SERVICE_URL: 'http://attendee-experience:8080',
        COMPANY_USER_MANAGEMENT_SERVICE_URL: 'http://company-user-management:8080',
        // Cognito configuration
        COGNITO_USER_POOL_ID: props.userPool.userPoolId,
        COGNITO_CLIENT_ID: props.userPoolClient.userPoolClientId,
      },
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
      name: 'api-gateway-port',
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
    const serviceSecurityGroup = new ec2.SecurityGroup(this, 'ServiceSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for API Gateway ECS service',
      allowAllOutbound: false, // Explicitly disable to avoid CDK warning
    });

    // Add only necessary egress rules
    serviceSecurityGroup.addEgressRule(
      props.databaseSecurityGroup,
      ec2.Port.tcp(5432),
      'Allow outbound to PostgreSQL database'
    );

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

    // Create service with PUBLIC ALB
    this.service = new ecsPatterns.ApplicationLoadBalancedFargateService(this, 'Service', {
      cluster: props.cluster,
      taskDefinition,
      publicLoadBalancer: true,
      desiredCount: isProd ? 2 : 1,
      healthCheckGracePeriod: cdk.Duration.seconds(60),
      assignPublicIp: false,
      taskSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
      listenerPort: 80,
      minHealthyPercent: 100, // Ensure zero-downtime deployments
      maxHealthyPercent: 200, // Allow temporary extra tasks during deployments
      securityGroups: [serviceSecurityGroup], // Use explicit security group
    });

    // Configure health checks
    this.service.targetGroup.configureHealthCheck({
      path: '/actuator/health',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      healthyThresholdCount: 2,
      unhealthyThresholdCount: 3,
    });

    // Enable Service Connect for service-to-service communication
    // This allows API Gateway to communicate directly with microservices using DNS names
    // Must use addPropertyOverride because ApplicationLoadBalancedFargateService doesn't expose Service Connect
    const cfnService = this.service.service.node.defaultChild as ecs.CfnService;
    cfnService.addPropertyOverride('ServiceConnectConfiguration', {
      Enabled: true,
      Namespace: 'batbern.local',
      Services: [{
        PortName: 'api-gateway-port',
        DiscoveryName: 'api-gateway',
        ClientAliases: [{
          Port: 8080,
          DnsName: 'api-gateway',
        }],
      }],
    });

    // Output Service Connect DNS name for debugging
    new cdk.CfnOutput(this, 'ServiceConnectDNS', {
      value: 'http://api-gateway:8080',
      description: 'API Gateway Service Connect DNS endpoint',
      exportName: `${envName}-api-gateway-service-connect-dns`,
    });

    // Configure auto-scaling
    const scaling = this.service.service.autoScaleTaskCount({
      minCapacity: isProd ? 2 : 1,
      maxCapacity: isProd ? 8 : 4,
    });

    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 70,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    this.apiGatewayUrl = `http://${this.service.loadBalancer.loadBalancerDnsName}`;

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'ApiGateway-Service');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: this.apiGatewayUrl,
      description: 'API Gateway Service URL (internal ALB)',
      exportName: `${envName}-ApiGatewayServiceUrl`,
    });

    new cdk.CfnOutput(this, 'LoadBalancerArn', {
      value: this.service.loadBalancer.loadBalancerArn,
      description: 'API Gateway Load Balancer ARN',
      exportName: `${envName}-ApiGatewayLoadBalancerArn`,
    });
  }
}
