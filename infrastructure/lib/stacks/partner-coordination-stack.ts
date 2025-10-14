import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as path from 'path';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface PartnerCoordinationStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  cacheEndpoint?: string;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
}

/**
 * Partner Coordination Service Stack
 *
 * Domain microservice for managing partners, sponsors, and exhibitors.
 * Handles /api/v1/partners routes.
 */
export class PartnerCoordinationStack extends cdk.Stack {
  public readonly service: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly serviceUrl: string;

  constructor(scope: Construct, id: string, props: PartnerCoordinationStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const isProd = envName === 'production';
    const serviceName = 'partner-coordination';

    // Common environment variables (non-sensitive)
    const commonEnv = {
      SPRING_PROFILES_ACTIVE: envName,
      AWS_REGION: props.config.region,
      LOG_LEVEL: isProd ? 'INFO' : 'DEBUG',
      ...(props.databaseEndpoint && {
        DATABASE_URL: `jdbc:postgresql://${props.databaseEndpoint}:5432/batbern`,
      }),
      ...(props.cacheEndpoint && { REDIS_ENDPOINT: props.cacheEndpoint }),
    };

    // Secrets from AWS Secrets Manager
    const secrets: Record<string, ecs.Secret> = {};
    if (props.databaseSecret) {
      secrets.DATABASE_USERNAME = ecs.Secret.fromSecretsManager(props.databaseSecret, 'username');
      secrets.DATABASE_PASSWORD = ecs.Secret.fromSecretsManager(props.databaseSecret, 'password');
    }

    // Create stable log group
    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: `/aws/ecs/BATbern-${envName}/${serviceName}`,
      retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Create task definition
    const taskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      cpu: 256,
      memoryLimitMiB: 512,
      runtimePlatform: {
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        cpuArchitecture: ecs.CpuArchitecture.ARM64,
      },
    });

    // Add container
    const container = taskDefinition.addContainer('Container', {
      image: ecs.ContainerImage.fromAsset(path.join(__dirname, '../../..'), {
        file: `services/${serviceName}-service/Dockerfile`,
      }),
      logging: ecs.LogDrivers.awsLogs({
        logGroup,
        streamPrefix: serviceName,
      }),
      environment: {
        ...commonEnv,
        SERVICE_NAME: serviceName,
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
    this.service = new ecsPatterns.ApplicationLoadBalancedFargateService(this, 'Service', {
      cluster: props.cluster,
      taskDefinition,
      publicLoadBalancer: false, // Internal ALB only
      desiredCount: isProd ? 2 : 1,
      healthCheckGracePeriod: cdk.Duration.seconds(60),
      assignPublicIp: false,
      taskSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
      listenerPort: 80,
      minHealthyPercent: 100, // Ensure zero-downtime deployments
      maxHealthyPercent: 200, // Allow temporary extra tasks during deployments
    });

    // Configure health checks
    this.service.targetGroup.configureHealthCheck({
      path: '/actuator/health',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      healthyThresholdCount: 2,
      unhealthyThresholdCount: 3,
    });

    // Configure auto-scaling
    const scaling = this.service.service.autoScaleTaskCount({
      minCapacity: isProd ? 2 : 1,
      maxCapacity: (isProd ? 2 : 1) * 4,
    });

    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 70,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    this.serviceUrl = `http://${this.service.loadBalancer.loadBalancerDnsName}`;

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'PartnerCoordination-Service');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: this.serviceUrl,
      description: 'Partner Coordination Service internal URL',
      exportName: `${envName}-partner-coordination-url`,
    });
  }
}
