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
    const isProd = envName === 'production';

    // Create ECS cluster
    this.cluster = new ecs.Cluster(this, 'MicroservicesCluster', {
      vpc: props.vpc,
      clusterName: `batbern-${envName}`,
      containerInsightsV2: isProd ? ecs.ContainerInsights.ENABLED : ecs.ContainerInsights.DISABLED, // Enable Container Insights V2 for production
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
