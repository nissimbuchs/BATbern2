import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as targets from 'aws-cdk-lib/aws-route53-targets';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';
import * as fs from 'fs';
import * as path from 'path';
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

    const isProd = props.config.isProduction ?? props.config.envName === 'production';

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
          expiration: cdk.Duration.days(30),
        },
        {
          id: 'TransitionToIA',
          enabled: true,
          transitions: [
            {
              // Priority 8: Transition to IA after 30 days (AWS minimum for STANDARD_IA)
              storageClass: s3.StorageClass.INFREQUENT_ACCESS,
              transitionAfter: cdk.Duration.days(30),
            },
            {
              // Priority 8: Further transition to Glacier Instant Retrieval after 60 days
              storageClass: s3.StorageClass.GLACIER_INSTANT_RETRIEVAL,
              transitionAfter: cdk.Duration.days(60),
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
            'https://www.batbern.ch',
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
              // Priority 8: Transition to Intelligent Tiering after 30 days (optimized from 90)
              storageClass: s3.StorageClass.INTELLIGENT_TIERING,
              transitionAfter: cdk.Duration.days(30),
            },
          ],
          // Priority 8: Delete non-current versions after 90 days for versioned buckets
          ...(isProd && {
            noncurrentVersionExpiration: cdk.Duration.days(90),
            noncurrentVersionTransitions: [
              {
                storageClass: s3.StorageClass.GLACIER,
                transitionAfter: cdk.Duration.days(30),
              },
            ],
          }),
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
          expiration: cdk.Duration.days(isProd ? 90 : 30),
        },
        {
          id: 'TransitionToGlacier',
          enabled: false,
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

    // Lambda@Edge for on-the-fly image resizing (?w=256&h=192&fit=cover → WebP)
    const contentBucketName = `batbern-content-${props.config.envName}`;
    const contentBucketRegion = 'eu-central-1';
    const lambdaSrcDir = path.join(__dirname, '../lambda/image-resize');

    const imageResizeFn = new cloudfront.experimental.EdgeFunction(this, 'ImageResizeFn', {
      runtime: lambda.Runtime.NODEJS_24_X,
      handler: 'index.handler',
      memorySize: 512,
      description: `BATbern image resize Lambda@Edge - ${props.config.envName}`,
      code: lambda.Code.fromAsset(lambdaSrcDir, {
        bundling: {
          image: lambda.Runtime.NODEJS_24_X.bundlingImage,
          command: [
            'bash',
            '-c',
            [
              // NOTE: the flags below are left exactly as deployed. `--platform`/`--arch` are not
              // npm's documented filters (`--os`/`--cpu` are), so they look wrong — but this
              // install is known to produce the glibc binary the function needs, and I could not
              // reproduce the flag behaviour of this image's npm locally. Changing them would be
              // an unverified change on the path that serves every CDN image, so determinism is
              // enforced below instead, where it costs nothing to be sure.
              'npm ci --cache /tmp/.npm --platform=linux --arch=x64 --libc=glibc',
              [
                './node_modules/.bin/esbuild index.ts',
                '--bundle --platform=node --target=node24 --external:sharp',
                `--define:CONTENT_BUCKET_NAME='"${contentBucketName}"'`,
                `--define:CONTENT_BUCKET_REGION='"${contentBucketRegion}"'`,
                '--outfile=/asset-output/index.js',
              ].join(' '),
              // Sharp can't be bundled by esbuild (native .node binary), so it stays external and
              // is loaded from node_modules at runtime. Sharp's JS wrapper also requires several
              // transitive deps (detect-libc, color, semver, …) which must be present at runtime.
              // Prune dev deps then copy the entire production node_modules to /asset-output.
              'npm prune --omit=dev --cache /tmp/.npm',
              // Drop the musl variants unconditionally. This is what makes the bundle
              // DETERMINISTIC, and it is why the deploys of 2026-08-12 kept failing their smoke
              // test.
              //
              // Measured from the deployed artifacts (Lambda@Edge versions 74-80), the bundle
              // alternated between two sizes:
              //
              //   15,763,107 B  @img/{sharp-linux-x64, sharp-libvips-linux-x64, sharp-wasm32, colour}
              //   24,050,269 B  the same PLUS @img/{sharp-linuxmusl-x64, sharp-libvips-linuxmusl-x64}
              //
              // The ~18.7 MB delta is exactly the musl pair; everything else was byte-identical
              // across both. A changed bundle means a new Lambda@Edge version on every deploy,
              // which re-replicates the function to all edge locations — and CDN resize requests
              // 503 during that window. It also strands the old version: CloudFormation reports
              // DELETE_FAILED (skipped) because a replicated version cannot be deleted until it
              // drains.
              //
              // Removing musl is provably safe rather than a judgement call: Lambda runs Amazon
              // Linux (glibc), so a musl binary can never load there, and the glibc pair sharp
              // actually uses is present in BOTH observed variants.
              'rm -rf node_modules/@img/sharp-linuxmusl-x64 node_modules/@img/sharp-libvips-linuxmusl-x64',
              'cp -r node_modules /asset-output/node_modules',
            ].join(' && '),
          ],
          local: {
            // Jest unit tests get a lightweight stub (no Docker required).
            // Real CDK deploys always use the Docker bundler so that npm ci installs the
            // correct Linux x64 sharp binary — the local esbuild path never copies node_modules/sharp.
            tryBundle(outputDir: string): boolean {
              if (process.env.JEST_WORKER_ID || process.env.NODE_ENV === 'test') {
                fs.writeFileSync(
                  path.join(outputDir, 'index.js'),
                  'exports.handler = async () => ({});'
                );
                return true;
              }
              return false; // fall back to Docker for all real deployments
            },
          },
        },
      }),
    });
    this.contentBucket.grantRead(imageResizeFn);

    // Cache policy that keys on resize params so different sizes cache independently
    const imageResizeCachePolicy = new cloudfront.CachePolicy(this, 'ImageResizeCachePolicy', {
      cachePolicyName: `batbern-image-resize-${props.config.envName}`,
      comment: 'Cache key includes w/h/fit query params for image resizing',
      defaultTtl: cdk.Duration.days(365),
      maxTtl: cdk.Duration.days(365),
      minTtl: cdk.Duration.seconds(0),
      queryStringBehavior: cloudfront.CacheQueryStringBehavior.allowList('w', 'h', 'fit'),
      enableAcceptEncodingGzip: true,
      enableAcceptEncodingBrotli: true,
    });

    // Add an immutable Cache-Control so browsers cache media (logos, profile
    // pictures, event theme images) on repeat visits. Media keys are
    // content-addressed (UUID filenames) so they are effectively immutable.
    // override:false — the image-resize Lambda already emits this exact header
    // on resized (WebP) responses, so let its value stand; the policy only fills
    // it in for pass-through originals/SVGs that reached the browser with no
    // Cache-Control ("Cache TTL: None" in the PageSpeed report).
    //
    // Story 12.12 review (finding #3): users can upload SVGs (profile pictures,
    // logos) which CloudFront serves with Content-Type image/svg+xml. An SVG can
    // carry <script>, which executes when the object URL is opened top-level —
    // stored XSS on the cdn origin. Neutralize without breaking <img> embedding:
    //  - CSP `sandbox` blocks script execution in top-level SVG documents (a
    //    resource's CSP only applies when it IS the document; <img> rendering of
    //    PNG/JPEG/WebP/SVG is unaffected).
    //  - X-Content-Type-Options: nosniff stops MIME-sniffing surprises.
    const contentCacheHeadersPolicy = new cloudfront.ResponseHeadersPolicy(
      this,
      'ContentCacheHeaders',
      {
        responseHeadersPolicyName: `batbern-content-cache-${props.config.envName}`,
        comment: 'Immutable Cache-Control + SVG-safe security headers for content-addressed media',
        customHeadersBehavior: {
          customHeaders: [
            {
              header: 'Cache-Control',
              value: 'public, max-age=31536000, immutable',
              override: false,
            },
          ],
        },
        securityHeadersBehavior: {
          contentSecurityPolicy: {
            contentSecurityPolicy: 'sandbox',
            override: true,
          },
          contentTypeOptions: {
            override: true,
          },
        },
      }
    );

    // CloudFront distribution for content delivery
    this.distribution = new cloudfront.Distribution(this, 'ContentDistribution', {
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(this.contentBucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD,
        compress: true,
        cachePolicy: imageResizeCachePolicy,
        responseHeadersPolicy: contentCacheHeadersPolicy,
        edgeLambdas: [
          {
            functionVersion: imageResizeFn.currentVersion,
            eventType: cloudfront.LambdaEdgeEventType.ORIGIN_REQUEST,
          },
        ],
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
