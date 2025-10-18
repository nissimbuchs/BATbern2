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
 * - Internal ALB for service-to-service communication
 * - Health checks via Spring Boot Actuator
 * - CPU-based auto-scaling
 * - CloudWatch logging with environment-based retention
 * - IAM permissions for logs and secrets
 *
 * Used by: Event Management, Speaker Coordination, Partner Coordination,
 *          Company Management, and Attendee Experience services
 *
 * @returns Object with service and serviceUrl properties
 */
export function createDomainService(
  scope: Construct,
  props: DomainServiceConstructProps
): { service: ecsPatterns.ApplicationLoadBalancedFargateService; serviceUrl: string } {

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

    container.addPortMappings({
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

    // Grant Secrets Manager permissions to task execution role
    if (props.databaseSecret) {
      props.databaseSecret.grantRead(taskDefinition.executionRole!);
    }

    // Create service with INTERNAL ALB
    const service = new ecsPatterns.ApplicationLoadBalancedFargateService(scope, 'Service', {
      cluster: props.cluster,
      taskDefinition,
      publicLoadBalancer: false, // Internal ALB only
      desiredCount: isProd ? 2 : 1,
      healthCheckGracePeriod: cdk.Duration.seconds(180), // Services take ~105s to start
      assignPublicIp: false,
      taskSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
      listenerPort: 80,
      minHealthyPercent: 100, // Ensure zero-downtime deployments
      maxHealthyPercent: 200, // Allow temporary extra tasks during deployments
    });

    // Configure health checks
    service.targetGroup.configureHealthCheck({
      path: '/actuator/health',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      healthyThresholdCount: 2,
      unhealthyThresholdCount: 3,
    });

    // Configure auto-scaling
    const scaling = service.service.autoScaleTaskCount({
      minCapacity: isProd ? 2 : 1,
      maxCapacity: (isProd ? 2 : 1) * 4,
    });

    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 70,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    const serviceUrl = `http://${service.loadBalancer.loadBalancerDnsName}`;

    // Allow service to connect to database
    service.service.connections.allowTo(
      props.databaseSecurityGroup,
      ec2.Port.tcp(5432),
      'Allow ECS tasks to connect to PostgreSQL database'
    );

    // Apply tags
    cdk.Tags.of(scope).add('Environment', envName);
    cdk.Tags.of(scope).add('Component', componentTag);
    cdk.Tags.of(scope).add('Project', 'BATbern');

    return { service, serviceUrl };
}
