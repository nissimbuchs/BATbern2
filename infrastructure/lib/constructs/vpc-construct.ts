import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';
import * as cdk from 'aws-cdk-lib';

export interface VpcConstructProps {
  cidr: string;
  maxAzs: number;
  natGateways: number;
  envName: string;
}

/**
 * Reusable VPC construct with standardized subnet configuration
 *
 * Creates:
 * - Public subnets for load balancers and NAT gateways
 * - Private subnets with egress for application tier
 * - Isolated subnets for database tier
 * - Security groups for each tier
 */
export class VpcConstruct extends Construct {
  public readonly vpc: ec2.Vpc;
  public readonly applicationSecurityGroup: ec2.SecurityGroup;
  public readonly databaseSecurityGroup: ec2.SecurityGroup;
  public readonly cacheSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: VpcConstructProps) {
    super(scope, id);

    // Create VPC with standardized subnet configuration
    this.vpc = new ec2.Vpc(this, 'VPC', {
      ipAddresses: ec2.IpAddresses.cidr(props.cidr),
      maxAzs: props.maxAzs,
      natGateways: props.natGateways,
      subnetConfiguration: [
        {
          name: 'Public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24,
        },
        {
          name: 'Private',
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
          cidrMask: 24,
        },
        {
          name: 'Isolated',
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
          cidrMask: 24,
        },
      ],
      enableDnsHostnames: true,
      enableDnsSupport: true,
    });

    // Application tier security group
    this.applicationSecurityGroup = new ec2.SecurityGroup(this, 'ApplicationSG', {
      vpc: this.vpc,
      description: 'Security group for application tier (ECS tasks)',
      allowAllOutbound: true,
    });

    // Database tier security group
    this.databaseSecurityGroup = new ec2.SecurityGroup(this, 'DatabaseSG', {
      vpc: this.vpc,
      description: 'Security group for database tier (RDS PostgreSQL)',
      allowAllOutbound: false,
    });

    // Allow PostgreSQL access from application tier
    this.databaseSecurityGroup.addIngressRule(
      this.applicationSecurityGroup,
      ec2.Port.tcp(5432),
      'Allow PostgreSQL access from application tier'
    );

    // Cache tier security group
    this.cacheSecurityGroup = new ec2.SecurityGroup(this, 'CacheSG', {
      vpc: this.vpc,
      description: 'Security group for cache tier (ElastiCache Redis)',
      allowAllOutbound: false,
    });

    // Allow Redis access from application tier
    this.cacheSecurityGroup.addIngressRule(
      this.applicationSecurityGroup,
      ec2.Port.tcp(6379),
      'Allow Redis access from application tier'
    );

    // Apply tags
    cdk.Tags.of(this.vpc).add('Environment', props.envName);
    cdk.Tags.of(this.vpc).add('Component', 'Network');
  }
}
