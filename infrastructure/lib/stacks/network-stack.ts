import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import * as route53 from 'aws-cdk-lib/aws-route53';
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
 * - ACM certificate for API Gateway (same region)
 */
export class NetworkStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly applicationSecurityGroup: ec2.SecurityGroup;
  public readonly databaseSecurityGroup: ec2.SecurityGroup;
  public readonly cacheSecurityGroup: ec2.SecurityGroup;
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

    // Apply tags to all resources
    cdk.Tags.of(this.vpc).add('Environment', props.config.envName);
    cdk.Tags.of(this.vpc).add('Project', 'BATbern');
    cdk.Tags.of(this.vpc).add('ManagedBy', 'CDK');
    cdk.Tags.of(this.vpc).add('Component', 'Network');

    // Setup ACM Certificate for API Gateway (if domain configured)
    // NOTE: This certificate must be in the same region as API Gateway (eu-central-1)
    if (props.config.domain?.apiDomain) {
      // Use existing certificate if provided (cross-account scenario)
      if (props.config.domain.apiCertificateArn) {
        this.apiCertificate = certificatemanager.Certificate.fromCertificateArn(
          this,
          'ApiCertificate',
          props.config.domain.apiCertificateArn
        );
      }
      // Create new certificate if hosted zone is in same account
      else if (props.config.domain.hostedZoneId) {
        // Extract zone name from domain (e.g., api-staging.batbern.ch -> batbern.ch)
        const zoneName = props.config.domain.apiDomain.split('.').slice(-2).join('.');

        const hostedZone = route53.HostedZone.fromHostedZoneAttributes(this, 'ApiHostedZone', {
          hostedZoneId: props.config.domain.hostedZoneId,
          zoneName,
        });

        this.apiCertificate = new certificatemanager.Certificate(this, 'ApiCertificate', {
          domainName: props.config.domain.apiDomain,
          validation: certificatemanager.CertificateValidation.fromDns(hostedZone),
        });
      }

      if (this.apiCertificate) {
        new cdk.CfnOutput(this, 'ApiCertificateArn', {
          value: this.apiCertificate.certificateArn,
          description: `ACM Certificate ARN for API Gateway - ${props.config.domain.apiDomain}`,
          exportName: `${props.config.envName}-ApiCertificateArn`,
        });
      }
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
