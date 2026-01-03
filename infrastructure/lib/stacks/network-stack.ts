import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
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
 * - ACM certificate for API Gateway (eu-central-1, automatic DNS validation)
 */
export class NetworkStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly applicationSecurityGroup: ec2.SecurityGroup;
  public readonly databaseSecurityGroup: ec2.SecurityGroup;
  public readonly cacheSecurityGroup: ec2.SecurityGroup;
  public readonly lambdaTriggersSecurityGroup: ec2.SecurityGroup;
  public readonly apiCertificate?: certificatemanager.ICertificate;

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
    this.lambdaTriggersSecurityGroup = vpcConstruct.lambdaTriggersSecurityGroup;

    // VPC Endpoints - Cost Optimization Decision
    //
    // Analysis: Interface endpoints cost significantly more than they save
    // - Interface endpoints: $58.40/month (4 endpoints × 2 AZs × $0.01/hr × 730hr)
    // - NAT Gateway data saved: ~$0.45/month (5 GB × $0.09/GB)
    // - Net cost: -$57.95/month (endpoints cost 130× more than they save)
    //
    // Decision: Use only FREE gateway endpoints, rely on NAT Gateway for AWS API calls
    // - Very low data transfer volumes (~5-10 GB/month) don't justify interface endpoint costs
    // - NAT Gateway already required for general internet access
    // - Marginal additional cost for AWS API traffic via NAT Gateway
    //
    // If traffic increases significantly (>100 GB/month to AWS services), reconsider interface endpoints

    // S3 Gateway Endpoint (FREE) - reduces NAT data transfer for ECR image pulls
    // Gateway endpoints have no hourly charges, only benefit
    this.vpc.addGatewayEndpoint('S3Endpoint', {
      service: ec2.GatewayVpcEndpointAwsService.S3,
    });

    // Apply tags to all resources
    cdk.Tags.of(this.vpc).add('Environment', props.config.envName);
    cdk.Tags.of(this.vpc).add('Project', 'BATbern');
    cdk.Tags.of(this.vpc).add('ManagedBy', 'CDK');
    cdk.Tags.of(this.vpc).add('Component', 'Network');

    // Create ACM Certificate for API Gateway in same region (eu-central-1)
    // Uses automatic DNS validation with hosted zone in same account
    if (props.config.domain?.apiDomain && props.config.domain?.hostedZoneId) {
      // Get domain name based on environment
      const domainName = props.config.envName === 'production' ? 'batbern.ch' : `${props.config.envName}.batbern.ch`;

      // Import hosted zone from same account
      const hostedZone = route53.HostedZone.fromHostedZoneAttributes(this, 'HostedZone', {
        hostedZoneId: props.config.domain.hostedZoneId,
        zoneName: domainName,
      });

      // Create certificate with automatic DNS validation
      this.apiCertificate = new certificatemanager.Certificate(this, 'ApiCertificate', {
        domainName: props.config.domain.apiDomain,
        validation: certificatemanager.CertificateValidation.fromDns(hostedZone),
      }) as certificatemanager.ICertificate;

      new cdk.CfnOutput(this, 'ApiCertificateArn', {
        value: this.apiCertificate.certificateArn,
        description: `ACM Certificate ARN for API Gateway (eu-central-1, automatic DNS validation)`,
        exportName: `${props.config.envName}-ApiCertificateArn`,
      });
    }

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
