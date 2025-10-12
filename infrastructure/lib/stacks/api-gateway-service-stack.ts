import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as path from 'path';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface ApiGatewayServiceStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseEndpoint?: string;
  cacheEndpoint?: string;
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
 * It runs on ECS with a public ALB and handles all API routing logic.
 *
 * Architecture:
 * Client → AWS API Gateway (Cognito auth) → **This Service** → Domain Microservices
 */
export class ApiGatewayServiceStack extends cdk.Stack {
  public readonly service: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly apiGatewayUrl: string;

  constructor(scope: Construct, id: string, props: ApiGatewayServiceStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const isProd = envName === 'production';

    // Common environment variables
    const commonEnv = {
      SPRING_PROFILES_ACTIVE: envName,
      APP_ENVIRONMENT: envName,
      AWS_REGION: props.config.region,
      LOG_LEVEL: isProd ? 'INFO' : 'DEBUG',
      ...(props.databaseEndpoint && { DATABASE_ENDPOINT: props.databaseEndpoint }),
      ...(props.cacheEndpoint && { REDIS_ENDPOINT: props.cacheEndpoint }),
    };

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
      image: ecs.ContainerImage.fromAsset(path.join(__dirname, '../../..'), {
        file: 'api-gateway/Dockerfile',
      }),
      logging: ecs.LogDrivers.awsLogs({
        logGroup,
        streamPrefix: 'api-gateway',
      }),
      environment: {
        ...commonEnv,
        SERVICE_NAME: 'api-gateway',
        // Service discovery URLs for internal routing
        EVENT_MANAGEMENT_SERVICE_URL: props.eventManagementServiceUrl || 'http://event-management.internal',
        SPEAKER_COORDINATION_SERVICE_URL: props.speakerCoordinationServiceUrl || 'http://speaker-coordination.internal',
        PARTNER_COORDINATION_SERVICE_URL: props.partnerCoordinationServiceUrl || 'http://partner-coordination.internal',
        ATTENDEE_EXPERIENCE_SERVICE_URL: props.attendeeExperienceServiceUrl || 'http://attendee-experience.internal',
        COMPANY_USER_MANAGEMENT_SERVICE_URL: props.companyUserManagementServiceUrl || 'http://company-user-management.internal',
        // Cognito configuration
        COGNITO_USER_POOL_ID: props.userPool.userPoolId,
        COGNITO_CLIENT_ID: props.userPoolClient.userPoolClientId,
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
