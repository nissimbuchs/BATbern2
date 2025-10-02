import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { VpcConstruct } from '../constructs/vpc-construct';

export interface NetworkStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
}

/**
 * Network Stack - Provides VPC and network infrastructure for BATbern platform
 *
 * Implements:
 * - AC3: Network Isolation with VPC per environment
 * - AC5: Resource Tagging for cost allocation
 *
 * Creates:
 * - VPC with public, private, and isolated subnets
 * - NAT Gateways for private subnet internet access
 * - Security groups for application, database, and cache tiers
 */
export class NetworkStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly applicationSecurityGroup: ec2.SecurityGroup;
  public readonly databaseSecurityGroup: ec2.SecurityGroup;
  public readonly cacheSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: NetworkStackProps) {
    super(scope, id, props);

    // Use VPC construct for standardized VPC creation
    const vpcConstruct = new VpcConstruct(this, 'VpcConstruct', {
      cidr: props.config.vpc.cidr,
      maxAzs: props.config.vpc.maxAzs,
      natGateways: props.config.vpc.natGateways,
      envName: props.config.envName,
    });

    // Expose VPC and security groups
    this.vpc = vpcConstruct.vpc;
    this.applicationSecurityGroup = vpcConstruct.applicationSecurityGroup;
    this.databaseSecurityGroup = vpcConstruct.databaseSecurityGroup;
    this.cacheSecurityGroup = vpcConstruct.cacheSecurityGroup;

    // Apply tags to all resources
    cdk.Tags.of(this.vpc).add('Environment', props.config.envName);
    cdk.Tags.of(this.vpc).add('Project', 'BATbern');
    cdk.Tags.of(this.vpc).add('ManagedBy', 'CDK');
    cdk.Tags.of(this.vpc).add('Component', 'Network');

    // Export VPC ID for cross-stack references
    new cdk.CfnOutput(this, 'VpcId', {
      value: this.vpc.vpcId,
      description: 'VPC ID for environment',
      exportName: `${props.config.envName}-VpcId`,
    });

    new cdk.CfnOutput(this, 'VpcCidr', {
      value: this.vpc.vpcCidrBlock,
      description: 'VPC CIDR block',
      exportName: `${props.config.envName}-VpcCidr`,
    });
  }
}
