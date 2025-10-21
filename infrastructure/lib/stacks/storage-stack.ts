import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as targets from 'aws-cdk-lib/aws-route53-targets';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface StorageStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cdnCertificate?: certificatemanager.ICertificate; // us-east-1 certificate for CloudFront custom domain
  hostedZone?: route53.IHostedZone; // Route53 hosted zone for CDN DNS record
}

/**
 * Storage Stack - Provides S3 and CloudFront infrastructure for BATbern platform
 *
 * Implements:
 * - AC15: S3 Buckets with lifecycle policies
 * - AC16: CloudFront CDN distribution
 * - AC5: Resource Tagging
 */
export class StorageStack extends cdk.Stack {
  public readonly contentBucket: s3.Bucket;
  public readonly logsBucket: s3.Bucket;
  public readonly backupBucket: s3.Bucket;
  public readonly distribution: cloudfront.Distribution;

  constructor(scope: Construct, id: string, props: StorageStackProps) {
    super(scope, id, props);

    const isProd = props.config.envName === 'production';

    // Logs bucket for CloudFront and application logs
    this.logsBucket = new s3.Bucket(this, 'LogsBucket', {
      bucketName: `batbern-logs-${props.config.envName}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      versioned: false,
      objectOwnership: s3.ObjectOwnership.BUCKET_OWNER_PREFERRED, // Required for CloudFront logging
      lifecycleRules: [
        {
          id: 'DeleteOldLogs',
          enabled: true,
          expiration: cdk.Duration.days(isProd ? 90 : 30),
        },
        {
          id: 'TransitionToIA',
          enabled: true,
          transitions: [
            {
              storageClass: s3.StorageClass.INFREQUENT_ACCESS,
              transitionAfter: cdk.Duration.days(30),
            },
          ],
        },
      ],
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: !isProd,
    });

    // Content bucket for presentations, images, and media files
    this.contentBucket = new s3.Bucket(this, 'ContentBucket', {
      bucketName: `batbern-content-${props.config.envName}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      versioned: isProd,
      cors: [
        {
          allowedMethods: [s3.HttpMethods.GET, s3.HttpMethods.HEAD],
          allowedOrigins: ['*'], // Will be restricted to specific domains in production
          allowedHeaders: ['*'],
          maxAge: 3000,
        },
        {
          // CORS for direct uploads (presigned URLs)
          allowedMethods: [s3.HttpMethods.PUT, s3.HttpMethods.POST],
          allowedOrigins: [
            'https://staging.batbern.ch',
            'https://batbern.ch',
            'http://localhost:3000', // For local development
          ],
          allowedHeaders: ['*'],
          exposedHeaders: ['ETag'],
          maxAge: 3000,
        },
      ],
      lifecycleRules: [
        {
          id: 'TransitionOldContentToIA',
          enabled: true,
          transitions: [
            {
              storageClass: s3.StorageClass.INTELLIGENT_TIERING,
              transitionAfter: cdk.Duration.days(90),
            },
          ],
        },
      ],
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: !isProd,
    });

    // Backup bucket for database backups and disaster recovery
    this.backupBucket = new s3.Bucket(this, 'BackupBucket', {
      bucketName: `batbern-backups-${props.config.envName}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      versioned: isProd,
      lifecycleRules: [
        {
          id: 'DeleteOldBackups',
          enabled: true,
          expiration: cdk.Duration.days(isProd ? 365 : 30),
        },
        {
          id: 'TransitionToGlacier',
          enabled: isProd,
          transitions: [
            {
              storageClass: s3.StorageClass.GLACIER,
              transitionAfter: cdk.Duration.days(90),
            },
          ],
        },
      ],
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      autoDeleteObjects: false,
    });

    // CloudFront distribution for content delivery
    this.distribution = new cloudfront.Distribution(this, 'ContentDistribution', {
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(this.contentBucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD,
        compress: true,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
      },
      // Custom domain name for branded CDN URLs (e.g., cdn.staging.batbern.ch)
      domainNames: props.config.domain?.cdnDomain ? [props.config.domain.cdnDomain] : undefined,
      certificate: props.cdnCertificate,
      enableLogging: true,
      logBucket: this.logsBucket,
      logFilePrefix: 'cloudfront/',
      priceClass: isProd
        ? cloudfront.PriceClass.PRICE_CLASS_100 // Use all edge locations for production
        : cloudfront.PriceClass.PRICE_CLASS_100, // Europe and North America
      minimumProtocolVersion: cloudfront.SecurityPolicyProtocol.TLS_V1_2_2021,
      comment: `BATbern Content Distribution - ${props.config.envName}`,
    });

    // Create Route53 A record pointing CDN domain to CloudFront distribution
    if (props.hostedZone && props.config.domain?.cdnDomain) {
      new route53.ARecord(this, 'CdnAliasRecord', {
        zone: props.hostedZone,
        recordName: props.config.domain.cdnDomain,
        target: route53.RecordTarget.fromAlias(new targets.CloudFrontTarget(this.distribution)),
        comment: `CDN domain alias for ${props.config.envName}`,
      });
    }

    // Apply tags
    cdk.Tags.of(this).add('Environment', props.config.envName);
    cdk.Tags.of(this).add('Component', 'Storage');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'ContentBucketName', {
      value: this.contentBucket.bucketName,
      description: 'S3 bucket for content storage',
      exportName: `${props.config.envName}-ContentBucket`,
    });

    new cdk.CfnOutput(this, 'DistributionDomainName', {
      value: this.distribution.distributionDomainName,
      description: 'CloudFront distribution domain name',
      exportName: `${props.config.envName}-CloudFrontDomain`,
    });

    new cdk.CfnOutput(this, 'LogsBucketName', {
      value: this.logsBucket.bucketName,
      description: 'S3 bucket for logs',
      exportName: `${props.config.envName}-LogsBucket`,
    });

    // Output CDN domain (custom domain or CloudFront domain)
    new cdk.CfnOutput(this, 'CdnDomain', {
      value: props.config.domain?.cdnDomain || this.distribution.distributionDomainName,
      description: 'CDN domain for static assets',
      exportName: `${props.config.envName}-CdnDomain`,
    });
  }
}
