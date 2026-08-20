import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface ClusterStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  vpc: ec2.IVpc;
}

/**
 * Cluster Stack - Shared ECS Cluster for all microservices
 *
 * Creates a shared ECS Fargate cluster that all microservices use.
 * This allows services to be deployed independently while sharing
 * the same cluster infrastructure.
 *
 * Features:
 * - Single ECS cluster per environment
 * - Container Insights for monitoring (production only)
 * - Shared across all microservices for cost optimization
 */
export class ClusterStack extends cdk.Stack {
  public readonly cluster: ecs.Cluster;

  constructor(scope: Construct, id: string, props: ClusterStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    // Create ECS cluster with Service Connect enabled
    this.cluster = new ecs.Cluster(this, 'MicroservicesCluster', {
      vpc: props.vpc,
      clusterName: `batbern-${envName}`,
      // DISABLED for cost: $46.07/month, 15% of the entire AWS bill, for 163 custom
      // metrics at $0.30 each (docs/plans/aws-cost-reduction.md).
      //
      // It was previously enabled with the comment "provides RunningTaskCount,
      // DesiredTaskCount metrics needed by deployment alarms ... essential for diagnosing
      // stuck deployments". That intent was never wired up and was verified as such before
      // switching it off:
      //   - no alarm in this repo references RunningTaskCount or DesiredTaskCount
      //   - no deployed alarm uses the ECS/ContainerInsights namespace
      //   - the one consumer, EcsServiceAlarms' OOM-kill alarm, is gated on a
      //     `containerInsightsEnabled` prop that NO stack passes, so it is never created
      //
      // What is genuinely lost: the CloudWatch console's Container Insights dashboards,
      // which a human may reach for while a deploy is stuck. Per-service CPU and memory
      // alarms are unaffected — those read the free AWS/ECS namespace.
      //
      // To restore for an incident: flip to ENABLED and deploy this stack alone
      // (`npm run deploy:staging:layer3-application`). Metrics resume within minutes.
      containerInsightsV2: ecs.ContainerInsights.DISABLED,
      // Enable Service Connect for automatic service-to-service networking
      defaultCloudMapNamespace: {
        name: `batbern.local`,
        useForServiceConnect: true,
      },
      // Enable Fargate capacity providers (Spot + On-Demand)
      // Spot provides 70% cost savings but can be interrupted
      // Using 70/30 split for cost optimization while maintaining stability
      enableFargateCapacityProviders: true,
    });

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'ECS-Cluster');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'ClusterName', {
      value: this.cluster.clusterName,
      description: 'ECS Cluster Name',
      exportName: `${envName}-ClusterName`,
    });

    new cdk.CfnOutput(this, 'ClusterArn', {
      value: this.cluster.clusterArn,
      description: 'ECS Cluster ARN',
      exportName: `${envName}-ClusterArn`,
    });
  }
}
