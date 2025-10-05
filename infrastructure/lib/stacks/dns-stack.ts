import * as cdk from 'aws-cdk-lib';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface DnsStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  domainName: string;
}

/**
 * DNS Stack - Manages Route53 hosted zone and ACM certificates
 *
 * IMPORTANT: This stack must be deployed to us-east-1 for CloudFront certificates
 *
 * Architecture:
 * - Production: batbern.ch hosted zone owns root domain
 * - Staging: staging.batbern.ch hosted zone (delegated subdomain)
 * - Each environment manages its own certificates with automatic DNS validation
 */
export class DnsStack extends cdk.Stack {
  public readonly hostedZone: route53.IHostedZone;
  public readonly certificate: certificatemanager.ICertificate;

  constructor(scope: Construct, id: string, props: DnsStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // Import existing hosted zone (now in same account)
    if (!props.config.domain?.hostedZoneId) {
      throw new Error('hostedZoneId must be provided in domain config');
    }

    this.hostedZone = route53.HostedZone.fromHostedZoneAttributes(this, 'HostedZone', {
      hostedZoneId: props.config.domain.hostedZoneId,
      zoneName: props.domainName,
    });

    // Create ACM certificate for frontend (CloudFront)
    // Automatic DNS validation works because hosted zone is in same account!
    this.certificate = new certificatemanager.Certificate(this, 'Certificate', {
      domainName: props.config.domain!.frontendDomain,
      validation: certificatemanager.CertificateValidation.fromDns(this.hostedZone),
    });

    // Outputs
    new cdk.CfnOutput(this, 'HostedZoneId', {
      value: this.hostedZone.hostedZoneId,
      description: `Route53 Hosted Zone ID`,
      exportName: `${envName}-HostedZoneId`,
    });

    new cdk.CfnOutput(this, 'CertificateArn', {
      value: this.certificate.certificateArn,
      description: `ACM Certificate ARN for ${props.config.domain!.frontendDomain} (us-east-1 for CloudFront)`,
      exportName: `${envName}-FrontendCertificateArn`,
    });

    new cdk.CfnOutput(this, 'CertificateValidation', {
      value: 'Automatic DNS validation (same account)',
      description: 'Certificate Validation Method',
    });

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'DNS');
    cdk.Tags.of(this).add('Project', 'BATbern');
  }
}
