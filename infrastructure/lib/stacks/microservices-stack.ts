import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as path from 'path';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface MicroservicesStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  vpc: ec2.IVpc;
  databaseEndpoint?: string;
  cacheEndpoint?: string;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
}

/**
 * Microservices Stack - Deploys all BATbern microservices on ECS Fargate
 *
 * Architecture:
 * - Spring Boot API Gateway (public ALB) - handles all external requests
 * - 5 Domain Microservices (internal ALBs) - handle domain-specific logic
 *
 * Services:
 * 1. api-gateway              - Routes /api/v1/* to domain services
 * 2. event-management         - Handles /api/v1/events
 * 3. speaker-coordination     - Handles /api/v1/speakers
 * 4. partner-coordination     - Handles /api/v1/partners
 * 5. attendee-experience      - Handles /api/v1/content
 * 6. company-management       - Handles /api/v1/companies
 *
 * Path-based routing (NOT subdomain routing):
 * Client → api.staging.batbern.ch/api/v1/events → API Gateway → event-management:8080
 */
export class MicroservicesStack extends cdk.Stack {
  public readonly cluster: ecs.Cluster;
  public readonly apiGatewayService: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly apiGatewayUrl: string;

  constructor(scope: Construct, id: string, props: MicroservicesStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const isProd = envName === 'production';

    // Create ECS cluster
    this.cluster = new ecs.Cluster(this, 'MicroservicesCluster', {
      vpc: props.vpc,
      clusterName: `batbern-${envName}`,
      containerInsights: isProd, // Enable Container Insights for production
    });

    // Common environment variables for all services
    const commonEnv = {
      SPRING_PROFILES_ACTIVE: envName,
      AWS_REGION: props.config.region,
      LOG_LEVEL: isProd ? 'INFO' : 'DEBUG',
      ...(props.databaseEndpoint && { DATABASE_ENDPOINT: props.databaseEndpoint }),
      ...(props.cacheEndpoint && { REDIS_ENDPOINT: props.cacheEndpoint }),
    };

    // Service configurations
    const services = [
      {
        name: 'event-management',
        port: 8080,
        cpu: 512,
        memory: 1024,
        desiredCount: isProd ? 2 : 1,
        publicLoadBalancer: false,
        healthCheck: '/actuator/health',
      },
      {
        name: 'speaker-coordination',
        port: 8080,
        cpu: 256,
        memory: 512,
        desiredCount: isProd ? 2 : 1,
        publicLoadBalancer: false,
        healthCheck: '/actuator/health',
      },
      {
        name: 'partner-coordination',
        port: 8080,
        cpu: 256,
        memory: 512,
        desiredCount: isProd ? 2 : 1,
        publicLoadBalancer: false,
        healthCheck: '/actuator/health',
      },
      {
        name: 'attendee-experience',
        port: 8080,
        cpu: 512,
        memory: 1024,
        desiredCount: isProd ? 2 : 1,
        publicLoadBalancer: false,
        healthCheck: '/actuator/health',
      },
      {
        name: 'company-management',
        port: 8080,
        cpu: 256,
        memory: 512,
        desiredCount: isProd ? 2 : 1,
        publicLoadBalancer: false,
        healthCheck: '/actuator/health',
      },
    ];

    // Create stable log groups for all domain microservices
    const serviceLogGroups: { [key: string]: logs.LogGroup } = {};
    services.forEach(serviceConfig => {
      serviceLogGroups[serviceConfig.name] = new logs.LogGroup(this, `${serviceConfig.name}-log-group`, {
        logGroupName: `/aws/ecs/BATbern-${envName}/${serviceConfig.name}`,
        retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
        removalPolicy: cdk.RemovalPolicy.RETAIN, // Preserve logs on stack deletion
      });
    });

    // Create domain microservices with internal ALBs
    const serviceUrls: { [key: string]: string } = {};

    services.forEach(serviceConfig => {
      // Create task definition
      const taskDefinition = new ecs.FargateTaskDefinition(this, `${serviceConfig.name}-task`, {
        cpu: serviceConfig.cpu,
        memoryLimitMiB: serviceConfig.memory,
        runtimePlatform: {
          operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
          cpuArchitecture: ecs.CpuArchitecture.ARM64,
        },
      });

      // Add container with automatic Docker build from source
      const container = taskDefinition.addContainer(`${serviceConfig.name}-container`, {
        image: ecs.ContainerImage.fromAsset(path.join(__dirname, '../../..'), {
          file: `services/${serviceConfig.name}-service/Dockerfile`,
        }),
        logging: ecs.LogDrivers.awsLogs({
          logGroup: serviceLogGroups[serviceConfig.name],
          streamPrefix: serviceConfig.name,
        }),
        environment: {
          ...commonEnv,
          SERVICE_NAME: serviceConfig.name,
          // Cognito configuration
          COGNITO_USER_POOL_ID: props.userPool.userPoolId,
          COGNITO_CLIENT_ID: props.userPoolClient.userPoolClientId,
        },
        healthCheck: {
          command: ['CMD-SHELL', `curl -f http://localhost:${serviceConfig.port}${serviceConfig.healthCheck} || exit 1`],
          interval: cdk.Duration.seconds(30),
          timeout: cdk.Duration.seconds(5),
          retries: 3,
          startPeriod: cdk.Duration.seconds(60),
        },
      });

      container.addPortMappings({
        containerPort: serviceConfig.port,
        protocol: ecs.Protocol.TCP,
      });

      // Create Fargate service with internal ALB
      const service = new ecsPatterns.ApplicationLoadBalancedFargateService(this, `${serviceConfig.name}-service`, {
        cluster: this.cluster,
        taskDefinition,
        publicLoadBalancer: false, // Internal ALB only
        desiredCount: serviceConfig.desiredCount,
        healthCheckGracePeriod: cdk.Duration.seconds(60),
        assignPublicIp: false,
        taskSubnets: {
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        },
        listenerPort: 80,
      });

      // Configure health checks
      service.targetGroup.configureHealthCheck({
        path: serviceConfig.healthCheck,
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      });

      // Configure auto-scaling
      const scaling = service.service.autoScaleTaskCount({
        minCapacity: serviceConfig.desiredCount,
        maxCapacity: serviceConfig.desiredCount * 4,
      });

      scaling.scaleOnCpuUtilization(`${serviceConfig.name}-cpu-scaling`, {
        targetUtilizationPercent: 70,
        scaleInCooldown: cdk.Duration.seconds(60),
        scaleOutCooldown: cdk.Duration.seconds(60),
      });

      // Store internal ALB DNS for API Gateway routing
      serviceUrls[serviceConfig.name] = `http://${service.loadBalancer.loadBalancerDnsName}`;

      // Output service URL
      new cdk.CfnOutput(this, `${serviceConfig.name}-url`, {
        value: serviceUrls[serviceConfig.name],
        description: `${serviceConfig.name} service internal URL`,
        exportName: `${envName}-${serviceConfig.name}-url`,
      });
    });

    // Create API Gateway service (public ALB)
    // Create stable log group for API Gateway
    const apiGatewayLogGroup = new logs.LogGroup(this, 'api-gateway-log-group', {
      logGroupName: `/aws/ecs/BATbern-${envName}/api-gateway`,
      retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.RETAIN, // Preserve logs on stack deletion
    });

    const apiGatewayTaskDef = new ecs.FargateTaskDefinition(this, 'api-gateway-task', {
      cpu: 512,
      memoryLimitMiB: 1024,
      runtimePlatform: {
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        cpuArchitecture: ecs.CpuArchitecture.ARM64,
      },
    });

    const apiGatewayContainer = apiGatewayTaskDef.addContainer('api-gateway-container', {
      image: ecs.ContainerImage.fromAsset(path.join(__dirname, '../../..'), {
        file: 'api-gateway/Dockerfile',
      }),
      logging: ecs.LogDrivers.awsLogs({
        logGroup: apiGatewayLogGroup,
        streamPrefix: 'api-gateway',
      }),
      environment: {
        ...commonEnv,
        SERVICE_NAME: 'api-gateway',
        // Service discovery URLs for internal routing
        EVENT_MANAGEMENT_SERVICE_URL: serviceUrls['event-management'],
        SPEAKER_COORDINATION_SERVICE_URL: serviceUrls['speaker-coordination'],
        PARTNER_COORDINATION_SERVICE_URL: serviceUrls['partner-coordination'],
        ATTENDEE_EXPERIENCE_SERVICE_URL: serviceUrls['attendee-experience'],
        COMPANY_MANAGEMENT_SERVICE_URL: serviceUrls['company-management'],
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

    apiGatewayContainer.addPortMappings({
      containerPort: 8080,
      protocol: ecs.Protocol.TCP,
    });

    // Create API Gateway service with PUBLIC ALB
    this.apiGatewayService = new ecsPatterns.ApplicationLoadBalancedFargateService(this, 'api-gateway-service', {
      cluster: this.cluster,
      taskDefinition: apiGatewayTaskDef,
      publicLoadBalancer: true, // Public ALB
      desiredCount: isProd ? 2 : 1,
      healthCheckGracePeriod: cdk.Duration.seconds(60),
      assignPublicIp: false,
      taskSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
      listenerPort: 80,
    });

    // Configure API Gateway health checks
    this.apiGatewayService.targetGroup.configureHealthCheck({
      path: '/actuator/health',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      healthyThresholdCount: 2,
      unhealthyThresholdCount: 3,
    });

    // Configure API Gateway auto-scaling
    const apiGatewayScaling = this.apiGatewayService.service.autoScaleTaskCount({
      minCapacity: isProd ? 2 : 1,
      maxCapacity: isProd ? 8 : 4,
    });

    apiGatewayScaling.scaleOnCpuUtilization('api-gateway-cpu-scaling', {
      targetUtilizationPercent: 70,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    this.apiGatewayUrl = `http://${this.apiGatewayService.loadBalancer.loadBalancerDnsName}`;

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'Microservices');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'ClusterName', {
      value: this.cluster.clusterName,
      description: 'ECS Cluster Name',
      exportName: `${envName}-ClusterName`,
    });

    new cdk.CfnOutput(this, 'ApiGatewayUrl', {
      value: this.apiGatewayUrl,
      description: 'API Gateway Service URL (internal ALB)',
      exportName: `${envName}-ApiGatewayServiceUrl`,
    });

    new cdk.CfnOutput(this, 'ApiGatewayLoadBalancerArn', {
      value: this.apiGatewayService.loadBalancer.loadBalancerArn,
      description: 'API Gateway Load Balancer ARN',
      exportName: `${envName}-ApiGatewayLoadBalancerArn`,
    });
  }
}
