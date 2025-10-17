import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as events from 'aws-cdk-lib/aws-events';
import * as path from 'path';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface CompanyManagementStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: any;
  cacheEndpoint?: string;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
  contentBucket?: s3.IBucket;
  eventBus?: events.IEventBus;
}

/**
 * Company & User Management Service Stack
 *
 * Consolidated master data microservice for managing:
 * - Company profiles, employees, and corporate data (/api/v1/companies)
 * - User profiles, preferences, settings, and roles (/api/v1/users)
 * - User-company relationships
 * - AWS Cognito integration
 * - File storage (logos, profile pictures) with S3 presigned URLs
 *
 * This service consolidates Company Management and User Management into a single
 * service because both are tightly coupled master data concerns used by all domain services.
 */
export class CompanyManagementStack extends cdk.Stack {
  public readonly service: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly serviceUrl: string;

  constructor(scope: Construct, id: string, props: CompanyManagementStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const isProd = envName === 'production';
    const serviceName = 'company-user-management';

    // Common environment variables
    const commonEnv = {
      SPRING_PROFILES_ACTIVE: envName,
      AWS_REGION: props.config.region,
      LOG_LEVEL: isProd ? 'INFO' : 'DEBUG',
      ...(props.databaseEndpoint && { DATABASE_ENDPOINT: props.databaseEndpoint }),
      ...(props.cacheEndpoint && { REDIS_ENDPOINT: props.cacheEndpoint }),
    };

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
        // Cognito configuration for user management
        COGNITO_USER_POOL_ID: props.userPool.userPoolId,
        COGNITO_CLIENT_ID: props.userPoolClient.userPoolClientId,
        // Service scope - consolidated master data
        SERVICE_SCOPE: 'companies-and-users',
        HANDLES_COMPANIES: 'true',
        HANDLES_USERS: 'true',
        // S3 bucket for company logos and user profile pictures
        ...(props.contentBucket && {
          S3_CONTENT_BUCKET_NAME: props.contentBucket.bucketName,
        }),
        // EventBridge for domain events
        ...(props.eventBus && {
          EVENT_BUS_NAME: props.eventBus.eventBusName,
        }),
      },
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

    // Grant S3 permissions for company logos and user profile pictures
    if (props.contentBucket) {
      props.contentBucket.grantReadWrite(taskDefinition.taskRole);
      // Grant permissions for presigned URL generation
      taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          's3:PutObject',
          's3:GetObject',
          's3:DeleteObject',
        ],
        resources: [`${props.contentBucket.bucketArn}/*`],
      }));
    }

    // Grant EventBridge permissions for domain events
    if (props.eventBus) {
      taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'events:PutEvents',
        ],
        resources: [props.eventBus.eventBusArn],
      }));
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
    cdk.Tags.of(this).add('Component', 'CompanyUserManagement-Service');
    cdk.Tags.of(this).add('Project', 'BATbern');
    cdk.Tags.of(this).add('Consolidation', 'Companies+Users');

    // Outputs
    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: this.serviceUrl,
      description: 'Company & User Management Service internal URL (consolidated)',
      exportName: `${envName}-company-user-management-url`,
    });
  }
}
