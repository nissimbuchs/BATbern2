import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53targets from 'aws-cdk-lib/aws-route53-targets';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface FrontendStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  logsBucket: s3.IBucket;
  domainName?: string;
  apexDomainName?: string;
  hostedZoneId?: string;
  certificateArn?: string;
  /**
   * Optional site variant discriminator (e.g. `'beta'`). Lets a SECOND FrontendStack
   * instance coexist with the primary one in the same account without colliding on the
   * physical resource names — which are all derived from `envName`. See
   * docs/plans/beta-frontend-canary.md.
   *
   * When OMITTED, every name is produced exactly as before (the primary site is unchanged —
   * `cdk diff` must show zero changes). When set, names are prefixed/suffixed with the variant
   * (`beta-spa-router`, `batbern-frontend-beta-staging`, …), the rollback `stableBucket` is
   * skipped (the canary IS the pre-prod check), the buckets are made destroyable, and the
   * distribution uses the cheaper PRICE_CLASS_100.
   */
  variant?: string;
}

/**
 * Frontend Stack - Deploys React web application to S3 + CloudFront
 *
 * Implements:
 * - AC15: S3 bucket for static website hosting
 * - AC16: CloudFront CDN distribution with SPA routing
 * - AC4: Security with HTTPS and proper access controls
 */
export class FrontendStack extends cdk.Stack {
  public readonly websiteBucket: s3.Bucket;
  /**
   * Known-good snapshot of the deployed frontend. The staging-deploy pipeline
   * syncs `websiteBucket → stableBucket` on every green deploy (after Bruno AND
   * Playwright @smoke pass) and restores `stableBucket → websiteBucket` (+ a
   * CloudFront invalidation) when the gate trips. This is the frontend analogue
   * of the ECR `staging-stable` tag the backend rollback uses: `websiteBucket`
   * is unversioned and the deploy prunes it, so without a separate copy there is
   * no previous frontend to roll back to. See
   * docs/plans/playwright-staging-hardening.md §"Frontend rollback".
   */
  public readonly stableBucket?: s3.Bucket;
  public readonly distribution: cloudfront.Distribution;
  public readonly websiteUrl: string;

  constructor(scope: Construct, id: string, props: FrontendStackProps) {
    super(scope, id, props);

    const isProd = props.config.isProduction ?? (props.config.envName === 'production');
    const envName = props.config.envName;
    const variant = props.variant;

    // Name discriminators. With no variant these reproduce the original strings byte-for-byte
    // (primary site untouched — see docs/plans/beta-frontend-canary.md Phase 0):
    //   - `prefix`  drives the ${envName}-... CloudFront function / cache / header-policy /
    //     output names and the log path → 'staging' (default) or 'beta'.
    //   - `bucketSuffix` keeps the trailing -${envName} so a beta bucket
    //     (`batbern-frontend-beta-staging`) still matches the cicd-stack S3 grant `batbern-*-${envName}`
    //     → 'staging' (default) or 'beta-staging'.
    //   - `isPrimary` gates primary-only concerns (the rollback stableBucket, prod retention).
    const isPrimary = !variant;
    const prefix = variant ?? envName;
    const bucketSuffix = variant ? `${variant}-${envName}` : envName;

    // Whether buckets survive stack teardown. The PRIMARY prod site retains (it holds live
    // content / the only rollback copy); a variant canary is disposable, so it is always
    // destroyable even though isProd is true.
    const retainBuckets = isProd && isPrimary;
    const bucketRemoval = retainBuckets ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY;
    const bucketAutoDelete = !retainBuckets;

    // S3 bucket for frontend static files
    this.websiteBucket = new s3.Bucket(this, 'WebsiteBucket', {
      bucketName: `batbern-frontend-${bucketSuffix}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      versioned: false,
      removalPolicy: bucketRemoval,
      autoDeleteObjects: bucketAutoDelete,
    });

    // Stable snapshot bucket — holds the last known-good frontend so the deploy
    // pipeline can roll the frontend back when the gate trips (see the field
    // doc above). NOT a CloudFront origin and NOT a BucketDeployment target, so
    // the live bucket's `prune: true` can never wipe it. Name matches the
    // existing GitHub-Actions-role S3 grant pattern `batbern-*-${envName}`
    // (cicd-stack.ts), so no IAM change is needed for the sync/restore CLI steps.
    //
    // PRIMARY site only: a variant canary is itself the pre-prod check, so it has no
    // rollback gate and needs no stable snapshot.
    if (isPrimary) {
      this.stableBucket = new s3.Bucket(this, 'StableBucket', {
        bucketName: `batbern-frontend-stable-${bucketSuffix}`,
        encryption: s3.BucketEncryption.S3_MANAGED,
        blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
        versioned: false,
        removalPolicy: bucketRemoval,
        autoDeleteObjects: bucketAutoDelete,
      });
    }

    // CloudFront Origin Access Control for S3
    const oac = new cloudfront.S3OriginAccessControl(this, 'OAC', {
      signing: cloudfront.Signing.SIGV4_NO_OVERRIDE,
    });

    // CloudFront Functions for SPA routing
    const routerFunction = new cloudfront.Function(this, 'RouterFunction', {
      functionName: `${prefix}-spa-router`,
      code: cloudfront.FunctionCode.fromInline(`
function handler(event) {
  var request = event.request;
  var uri = request.uri;

  // Check if the URI is missing a file extension (likely a SPA route)
  if (!uri.includes('.')) {
    request.uri = '/index.html';
  }

  // Check if URI ends with '/'
  if (uri.endsWith('/')) {
    request.uri += 'index.html';
  }

  return request;
}
      `),
      comment: 'SPA routing handler for React application',
    });

    // CloudFront Function to prevent browser caching of HTML responses.
    // Content-addressed assets (JS/CSS with hash in filename) are served via
    // /assets/* and /*.js behaviors and are NOT affected by this function.
    // Without this, browsers apply heuristic caching to index.html, causing
    // stale JS file references after deployments (MIME type errors).
    const htmlNoCacheFunction = new cloudfront.Function(this, 'HtmlNoCacheFunction', {
      functionName: `${prefix}-html-no-cache`,
      code: cloudfront.FunctionCode.fromInline(`
function handler(event) {
  var response = event.response;
  response.headers['cache-control'] = { value: 'no-store, no-cache, must-revalidate' };
  return response;
}
      `),
      comment: 'Prevents browser caching of HTML responses to avoid stale JS references after deployments',
    });

    // Cache policy for static assets
    const staticAssetsCachePolicy = new cloudfront.CachePolicy(this, 'StaticAssetsCache', {
      cachePolicyName: `${prefix}-static-assets`,
      comment: 'Cache policy for static assets (JS, CSS, images)',
      defaultTtl: cdk.Duration.days(30),
      maxTtl: cdk.Duration.days(365),
      minTtl: cdk.Duration.days(1),
      headerBehavior: cloudfront.CacheHeaderBehavior.none(),
      queryStringBehavior: cloudfront.CacheQueryStringBehavior.none(),
      cookieBehavior: cloudfront.CacheCookieBehavior.none(),
      enableAcceptEncodingGzip: true,
      enableAcceptEncodingBrotli: true,
    });

    // Cache policy for index.html (no caching for SPA)
    const htmlCachePolicy = new cloudfront.CachePolicy(this, 'HtmlCachePolicy', {
      cachePolicyName: `${prefix}-html-no-cache`,
      comment: 'No caching for HTML files',
      defaultTtl: cdk.Duration.seconds(0),
      maxTtl: cdk.Duration.seconds(0),
      minTtl: cdk.Duration.seconds(0),
      headerBehavior: cloudfront.CacheHeaderBehavior.none(),
      queryStringBehavior: cloudfront.CacheQueryStringBehavior.none(),
      cookieBehavior: cloudfront.CacheCookieBehavior.none(),
    });

    // Cache policy for SEO files (robots.txt, sitemap.xml) - Story 4.1.8a
    const seoCachePolicy = new cloudfront.CachePolicy(this, 'SeoCachePolicy', {
      cachePolicyName: `${prefix}-seo-cache`,
      comment: 'Cache policy for SEO files (robots.txt, sitemap.xml)',
      defaultTtl: cdk.Duration.hours(1),
      maxTtl: cdk.Duration.hours(24),
      minTtl: cdk.Duration.seconds(0),
      headerBehavior: cloudfront.CacheHeaderBehavior.none(),
      queryStringBehavior: cloudfront.CacheQueryStringBehavior.none(),
      cookieBehavior: cloudfront.CacheCookieBehavior.none(),
      enableAcceptEncodingGzip: true,
      enableAcceptEncodingBrotli: true,
    });

    // Shared security headers behavior — reused by both the HTML policy and the
    // static-assets policy so the CSP / HSTS / frame options never drift between them.
    const securityHeadersBehavior: cloudfront.ResponseSecurityHeadersBehavior = {
      contentTypeOptions: { override: true },
      frameOptions: {
        frameOption: cloudfront.HeadersFrameOption.SAMEORIGIN,
        override: true,
      },
      referrerPolicy: {
        referrerPolicy: cloudfront.HeadersReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
        override: true,
      },
      strictTransportSecurity: {
        accessControlMaxAge: cdk.Duration.days(365),
        includeSubdomains: true,
        override: true,
      },
      xssProtection: {
        protection: true,
        modeBlock: true,
        override: true,
      },
      contentSecurityPolicy: {
        contentSecurityPolicy:
          "default-src 'self'; " +
          "script-src 'self' 'unsafe-inline' blob: https://cdn.jsdelivr.net https://cdn.tiny.cloud https://challenges.cloudflare.com; " +
          "script-src-elem 'self' 'unsafe-inline' blob: https://cdn.jsdelivr.net https://cdn.tiny.cloud https://challenges.cloudflare.com; " +
          "worker-src 'self' blob: https://cdn.jsdelivr.net; " +
          "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.tiny.cloud https://cdn.jsdelivr.net; " +
          "img-src 'self' data: https:; " +
          "font-src 'self' data: https://fonts.gstatic.com https://assets.unicorn.studio https://cdn.tiny.cloud; " +
          "connect-src 'self' blob: https://*.amazonaws.com https://*.amazoncognito.com https://*.cloudfront.net https://fonts.googleapis.com https://fonts.gstatic.com https://storage.googleapis.com https://api.batbern.ch https://cdn.tiny.cloud https://cdn.jsdelivr.net https://challenges.cloudflare.com; " +
          "frame-src 'self' https://maps.google.com https://www.google.com https://challenges.cloudflare.com; " +
          "object-src 'none'; " +
          "base-uri 'self'; " +
          "form-action 'self'; " +
          "frame-ancestors 'self';",
        override: true,
      },
    };

    const baseCustomHeaders: cloudfront.ResponseCustomHeader[] = [
      // Cross-Origin-Opener-Policy: isolates the browsing context from cross-origin openers
      // SAME_ORIGIN_ALLOW_POPUPS allows OAuth popups (Cognito)
      {
        header: 'Cross-Origin-Opener-Policy',
        value: 'same-origin-allow-popups',
        override: true,
      },
      // Cross-Origin-Resource-Policy: cross-origin allows CDN assets and Google Maps embeds
      {
        header: 'Cross-Origin-Resource-Policy',
        value: 'cross-origin',
        override: true,
      },
      // Variant canary (e.g. beta.batbern.ch): keep it out of search indexes so it never
      // competes with the primary www site. Spread is empty for the primary site, so its
      // synthesized template is unchanged.
      ...(variant
        ? [
            {
              header: 'X-Robots-Tag',
              value: 'noindex, nofollow',
              override: true,
            },
          ]
        : []),
    ];

    // Response headers policy for security (HTML + SEO — no Cache-Control here so
    // the htmlCachePolicy/htmlNoCacheFunction keep index.html uncached).
    const responseHeadersPolicy = new cloudfront.ResponseHeadersPolicy(this, 'SecurityHeaders', {
      responseHeadersPolicyName: `${prefix}-security-headers`,
      comment: 'Security headers for frontend',
      securityHeadersBehavior,
      customHeadersBehavior: { customHeaders: baseCustomHeaders },
    });

    // Same security headers PLUS a 1-year immutable Cache-Control for content-hashed
    // assets (/assets/*, /*.js, /*.css, /static/*). CloudFront already edge-caches
    // these via staticAssetsCachePolicy, but without a Cache-Control response header
    // the *browser* never caches them — PageSpeed reported "Cache TTL: None" and
    // ~5.6 MiB re-downloaded on repeat visits. Hashed filenames make immutable safe.
    const staticAssetsResponseHeadersPolicy = new cloudfront.ResponseHeadersPolicy(
      this,
      'StaticAssetsHeaders',
      {
        responseHeadersPolicyName: `${prefix}-static-assets-headers`,
        comment: 'Security headers + immutable Cache-Control for content-hashed assets',
        securityHeadersBehavior,
        customHeadersBehavior: {
          customHeaders: [
            ...baseCustomHeaders,
            {
              header: 'Cache-Control',
              value: 'public, max-age=31536000, immutable',
              override: true,
            },
          ],
        },
      }
    );

    // Get certificate if domain provided
    let certificate: certificatemanager.ICertificate | undefined;
    if (props.domainName && props.certificateArn) {
      certificate = certificatemanager.Certificate.fromCertificateArn(
        this,
        'Certificate',
        props.certificateArn
      );
    }

    // CloudFront distribution configuration
    this.distribution = new cloudfront.Distribution(this, 'Distribution', {
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(this.websiteBucket, {
          originAccessControl: oac,
        }),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD_OPTIONS,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD_OPTIONS,
        compress: true,
        cachePolicy: htmlCachePolicy,
        responseHeadersPolicy,
        functionAssociations: [
          {
            function: routerFunction,
            eventType: cloudfront.FunctionEventType.VIEWER_REQUEST,
          },
          {
            function: htmlNoCacheFunction,
            eventType: cloudfront.FunctionEventType.VIEWER_RESPONSE,
          },
        ],
      },
      additionalBehaviors: {
        '/static/*': {
          origin: origins.S3BucketOrigin.withOriginAccessControl(this.websiteBucket, {
            originAccessControl: oac,
          }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: true, // Priority 9: Enable compression for static assets
          cachePolicy: staticAssetsCachePolicy,
          responseHeadersPolicy: staticAssetsResponseHeadersPolicy,
        },
        '/assets/*': {
          origin: origins.S3BucketOrigin.withOriginAccessControl(this.websiteBucket, {
            originAccessControl: oac,
          }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: true, // Priority 9: Enable compression for assets
          cachePolicy: staticAssetsCachePolicy,
          responseHeadersPolicy: staticAssetsResponseHeadersPolicy,
        },
        '/*.js': {
          origin: origins.S3BucketOrigin.withOriginAccessControl(this.websiteBucket, {
            originAccessControl: oac,
          }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: true, // Priority 9: Enable compression for JS files
          cachePolicy: staticAssetsCachePolicy,
          responseHeadersPolicy: staticAssetsResponseHeadersPolicy,
        },
        '/*.css': {
          origin: origins.S3BucketOrigin.withOriginAccessControl(this.websiteBucket, {
            originAccessControl: oac,
          }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: true, // Priority 9: Enable compression for CSS files
          cachePolicy: staticAssetsCachePolicy,
          responseHeadersPolicy: staticAssetsResponseHeadersPolicy,
        },
        '/robots.txt': {
          origin: origins.S3BucketOrigin.withOriginAccessControl(this.websiteBucket, {
            originAccessControl: oac,
          }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: false, // robots.txt should not be compressed
          cachePolicy: seoCachePolicy,
          responseHeadersPolicy,
        },
        '/sitemap.xml': {
          origin: origins.S3BucketOrigin.withOriginAccessControl(this.websiteBucket, {
            originAccessControl: oac,
          }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          compress: true,
          cachePolicy: seoCachePolicy,
          responseHeadersPolicy,
        },
      },
      defaultRootObject: 'index.html',
      errorResponses: [
        {
          httpStatus: 403,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
          ttl: cdk.Duration.seconds(0),
        },
        {
          httpStatus: 404,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
          ttl: cdk.Duration.seconds(0),
        },
      ],
      enableLogging: true,
      logBucket: props.logsBucket,
      logFilePrefix: `frontend-cloudfront/${prefix}/`,
      // Priority 9: Optimize price class — full edge coverage only for the PRIMARY prod site;
      // non-prod and the beta canary use the cheapest class (Europe & US only).
      priceClass:
        isProd && isPrimary
          ? cloudfront.PriceClass.PRICE_CLASS_ALL
          : cloudfront.PriceClass.PRICE_CLASS_100,
      minimumProtocolVersion: cloudfront.SecurityPolicyProtocol.TLS_V1_2_2021,
      comment: `BATbern Frontend Distribution - ${prefix}`,
      certificate,
      domainNames: props.domainName
        ? [props.domainName, ...(props.apexDomainName ? [props.apexDomainName] : [])]
        : undefined,
    });

    // Create Route 53 record if hosted zone provided
    if (props.domainName && props.hostedZoneId) {
      // Extract zone name from domain (e.g., staging.batbern.ch -> batbern.ch)
      const zoneName = props.domainName.split('.').slice(-2).join('.');

      const hostedZone = route53.HostedZone.fromHostedZoneAttributes(
        this,
        'HostedZone',
        {
          hostedZoneId: props.hostedZoneId,
          zoneName,
        }
      );

      new route53.ARecord(this, 'AliasRecord', {
        zone: hostedZone,
        recordName: props.domainName,
        target: route53.RecordTarget.fromAlias(
          new route53targets.CloudFrontTarget(this.distribution)
        ),
      });

      // Create A record for apex domain (e.g., batbern.ch → same CloudFront distribution)
      if (props.apexDomainName) {
        new route53.ARecord(this, 'ApexAliasRecord', {
          zone: hostedZone,
          recordName: props.apexDomainName,
          target: route53.RecordTarget.fromAlias(
            new route53targets.CloudFrontTarget(this.distribution)
          ),
        });
      }
    }

    // Deploy frontend files to S3
    // Note: This requires web-frontend/dist to exist before CDK deploy
    // In CI/CD: frontend is built before running CDK
    // For local deploys: run `npm run build` in web-frontend first
    const frontendDistPath = '../web-frontend/dist';

    new s3deploy.BucketDeployment(this, 'DeployWebsite', {
      sources: [s3deploy.Source.asset(frontendDistPath)],
      destinationBucket: this.websiteBucket,
      distribution: this.distribution,
      distributionPaths: ['/*'],
      prune: true, // Remove files that don't exist in source
      memoryLimit: 512,
    });

    // Set website URL
    this.websiteUrl = props.domainName
      ? `https://${props.domainName}`
      : `https://${this.distribution.distributionDomainName}`;

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'Frontend');
    cdk.Tags.of(this).add('Project', 'BATbern');
    // Only tag variants — keeps the primary site's synthesized template byte-identical.
    if (variant) {
      cdk.Tags.of(this).add('Variant', variant);
    }

    // Outputs
    new cdk.CfnOutput(this, 'WebsiteBucketName', {
      value: this.websiteBucket.bucketName,
      description: 'S3 bucket for frontend static files',
      exportName: `${prefix}-FrontendBucket`,
    });

    // PRIMARY site only — a variant canary has no stableBucket (see above).
    if (this.stableBucket) {
      new cdk.CfnOutput(this, 'StableBucketName', {
        value: this.stableBucket.bucketName,
        description: 'S3 bucket holding the last known-good frontend (rollback source)',
        exportName: `${prefix}-FrontendStableBucket`,
      });
    }

    new cdk.CfnOutput(this, 'DistributionId', {
      value: this.distribution.distributionId,
      description: 'CloudFront distribution ID',
      exportName: `${prefix}-FrontendDistributionId`,
    });

    new cdk.CfnOutput(this, 'DistributionDomainName', {
      value: this.distribution.distributionDomainName,
      description: 'CloudFront distribution domain name',
      exportName: `${prefix}-FrontendDistributionDomain`,
    });

    new cdk.CfnOutput(this, 'WebsiteUrl', {
      value: this.websiteUrl,
      description: 'Frontend application URL',
      exportName: `${prefix}-FrontendUrl`,
    });
  }
}
