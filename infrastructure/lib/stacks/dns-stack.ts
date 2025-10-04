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
 * Implements:
 * - Route53 hosted zone for domain management
 * - ACM certificates for HTTPS (CloudFront requires us-east-1)
 * - DNS validation for certificates
 *
 * Features:
 * - Creates hosted zone if hostedZoneId not provided
 * - Creates certificates if frontendCertificateArn not provided
 * - Imports existing resources if IDs/ARNs are provided
 */
export class DnsStack extends cdk.Stack {
  public readonly hostedZone: route53.IHostedZone;
  public readonly certificate: certificatemanager.ICertificate;

  constructor(scope: Construct, id: string, props: DnsStackProps) {
    super(scope, id, props);

    const isProd = props.config.envName === 'production';
    const envName = props.config.envName;

    // Route53 Hosted Zone - Create or Import
    if (props.config.domain?.hostedZoneId) {
      // Import existing hosted zone
      this.hostedZone = route53.HostedZone.fromHostedZoneAttributes(this, 'HostedZone', {
        hostedZoneId: props.config.domain.hostedZoneId,
        zoneName: props.domainName,
      });

      new cdk.CfnOutput(this, 'HostedZoneIdImported', {
        value: this.hostedZone.hostedZoneId,
        description: `Imported Route53 Hosted Zone ID for ${props.domainName}`,
        exportName: `${envName}-HostedZoneId`,
      });
    } else {
      // Create new hosted zone
      this.hostedZone = new route53.HostedZone(this, 'HostedZone', {
        zoneName: props.domainName,
        comment: `BATbern Platform - ${envName} - ${props.domainName}`,
      });

      // Output nameservers for domain registrar configuration
      new cdk.CfnOutput(this, 'NameServers', {
        value: cdk.Fn.join(', ', this.hostedZone.hostedZoneNameServers || []),
        description: 'Route53 nameservers - Configure these at your domain registrar',
      });

      new cdk.CfnOutput(this, 'HostedZoneIdCreated', {
        value: this.hostedZone.hostedZoneId,
        description: `Route53 Hosted Zone ID for ${props.domainName}`,
        exportName: `${envName}-HostedZoneId`,
      });
    }

    // ACM Certificate - Create or Import
    // IMPORTANT: Must be in us-east-1 for CloudFront
    if (props.config.domain?.frontendCertificateArn) {
      // Import existing certificate
      this.certificate = certificatemanager.Certificate.fromCertificateArn(
        this,
        'Certificate',
        props.config.domain.frontendCertificateArn
      );

      new cdk.CfnOutput(this, 'CertificateArnImported', {
        value: this.certificate.certificateArn,
        description: `Imported ACM Certificate ARN for ${props.domainName}`,
        exportName: `${envName}-CertificateArn`,
      });
    } else {
      // Create new certificate with DNS validation
      // This will automatically create the validation DNS records in the hosted zone
      this.certificate = new certificatemanager.Certificate(this, 'Certificate', {
        domainName: props.domainName,
        subjectAlternativeNames: [
          `*.${props.domainName}`, // Wildcard for subdomains
        ],
        validation: certificatemanager.CertificateValidation.fromDns(this.hostedZone),
      });

      new cdk.CfnOutput(this, 'CertificateArnCreated', {
        value: this.certificate.certificateArn,
        description: `ACM Certificate ARN for ${props.domainName} (us-east-1)`,
        exportName: `${envName}-CertificateArn`,
      });
    }

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'DNS');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Additional outputs
    new cdk.CfnOutput(this, 'HostedZoneName', {
      value: this.hostedZone.zoneName,
      description: 'Route53 Hosted Zone Name',
      exportName: `${envName}-HostedZoneName`,
    });

    // Output instructions for manual steps if resources were created
    if (!props.config.domain?.hostedZoneId) {
      new cdk.CfnOutput(this, 'DomainRegistrarInstructions', {
        value: `Update your domain registrar to use the Route53 nameservers listed above`,
        description: 'Action Required',
      });
    }

    if (!props.config.domain?.frontendCertificateArn) {
      new cdk.CfnOutput(this, 'CertificateValidationInstructions', {
        value: `Certificate validation records automatically created in Route53. Validation may take 5-30 minutes.`,
        description: 'Certificate Status',
      });
    }
  }
}
