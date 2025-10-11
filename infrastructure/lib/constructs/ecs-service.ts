import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { AutoScalingConfig } from '../config/environment-config';

export interface EcsServiceProps {
  vpc: ec2.IVpc;
  cluster: ecs.ICluster;
  securityGroup: ec2.ISecurityGroup;
  serviceName: string;
  containerPort: number;
  cpu: number;
  memory: number;
  desiredCount: number;
  autoScaling: AutoScalingConfig;
  environment?: { [key: string]: string };
  secrets?: { [key: string]: ecs.Secret };
  repository?: ecr.IRepository;
  envName: string;
}

/**
 * Reusable ECS Fargate service construct with ALB and auto-scaling
 *
 * Features:
 * - Fargate service with configurable CPU/memory
 * - Application Load Balancer with health checks
 * - Auto-scaling based on CPU utilization
 * - CloudWatch logging with configurable retention
 * - ARM64 architecture for cost optimization
 */
export class EcsService extends Construct {
  public readonly service: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly taskDefinition: ecs.FargateTaskDefinition;
  public readonly container: ecs.ContainerDefinition;

  constructor(scope: Construct, id: string, props: EcsServiceProps) {
    super(scope, id);

    const isProd = props.envName === 'production';

    // Create task definition
    this.taskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      cpu: props.cpu,
      memoryLimitMiB: props.memory,
      runtimePlatform: {
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        cpuArchitecture: ecs.CpuArchitecture.ARM64, // Cost optimization
      },
    });

    // Create log group for the service
    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: `/aws/ecs/${props.envName}/${props.serviceName}`,
      retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Add container to task definition
    this.container = this.taskDefinition.addContainer('Container', {
      image: props.repository
        ? ecs.ContainerImage.fromEcrRepository(props.repository)
        : ecs.ContainerImage.fromRegistry('public.ecr.aws/docker/library/nginx:alpine'),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: props.serviceName,
        logGroup: logGroup,
      }),
      environment: props.environment,
      secrets: props.secrets,
      healthCheck: {
        command: ['CMD-SHELL', 'curl -f http://localhost/health || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        startPeriod: cdk.Duration.seconds(60),
      },
    });

    // Add port mapping
    this.container.addPortMappings({
      containerPort: props.containerPort,
      protocol: ecs.Protocol.TCP,
    });

    // Create ALB Fargate Service
    this.service = new ecsPatterns.ApplicationLoadBalancedFargateService(this, 'Service', {
      cluster: props.cluster,
      taskDefinition: this.taskDefinition,
      publicLoadBalancer: true,
      desiredCount: props.desiredCount,
      healthCheckGracePeriod: cdk.Duration.seconds(60),
      assignPublicIp: false,
      securityGroups: [props.securityGroup],
      taskSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
    });

    // Configure target group health checks
    this.service.targetGroup.configureHealthCheck({
      path: '/health',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      healthyThresholdCount: 2,
      unhealthyThresholdCount: 3,
    });

    // Configure auto-scaling
    const scaling = this.service.service.autoScaleTaskCount({
      minCapacity: props.autoScaling.minCapacity,
      maxCapacity: props.autoScaling.maxCapacity,
    });

    // Scale based on CPU utilization
    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: props.autoScaling.targetCpuUtilization,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    // Scale based on memory utilization
    scaling.scaleOnMemoryUtilization('MemoryScaling', {
      targetUtilizationPercent: props.autoScaling.targetCpuUtilization + 10, // Slightly higher for memory
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    // Apply tags
    cdk.Tags.of(this.service).add('Environment', props.envName);
    cdk.Tags.of(this.service).add('Service', props.serviceName);
  }
}
