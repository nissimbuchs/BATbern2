import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as logs from 'aws-cdk-lib/aws-logs';
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
  public readonly lambdaTriggersSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: VpcConstructProps) {
    super(scope, id);

    // Create log group for VPC custom resource Lambda
    const customResourceLogGroup = new logs.LogGroup(this, 'CustomResourceLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${props.envName}/vpc-restrict-default-sg`,
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

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

    // Allow PostgreSQL access from private subnets (for ECS services)
    // This avoids cyclic dependencies when service stacks create their own security groups
    this.vpc.privateSubnets.forEach((subnet, index) => {
      this.databaseSecurityGroup.addIngressRule(
        ec2.Peer.ipv4(subnet.ipv4CidrBlock),
        ec2.Port.tcp(5432),
        `Allow PostgreSQL access from private subnet ${index + 1}`
      );
    });

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

    // Lambda triggers security group (for Cognito triggers that access database)
    this.lambdaTriggersSecurityGroup = new ec2.SecurityGroup(this, 'LambdaTriggersSG', {
      vpc: this.vpc,
      description: 'Security group for Cognito Lambda triggers',
      allowAllOutbound: true, // Needs outbound for CloudWatch, Secrets Manager, etc.
    });

    // Allow database access from Lambda triggers
    this.databaseSecurityGroup.addIngressRule(
      this.lambdaTriggersSecurityGroup,
      ec2.Port.tcp(5432),
      'Allow Cognito trigger Lambda functions to access PostgreSQL database'
    );

    // Apply tags
    cdk.Tags.of(this.vpc).add('Environment', props.envName);
    cdk.Tags.of(this.vpc).add('Component', 'Network');
  }
}
