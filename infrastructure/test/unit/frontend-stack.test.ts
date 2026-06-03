import { App, Stack } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as fs from 'fs';
import * as path from 'path';
import { FrontendStack } from '../../lib/stacks/frontend-stack';
import { stagingConfig } from '../../lib/config/staging-config';
import { devConfig } from '../../lib/config/dev-config';
import { EnvironmentConfig } from '../../lib/config/environment-config';

// FrontendStack's BucketDeployment reads `../web-frontend/dist` as a CDK asset at
// synth time. Ensure a (placeholder) dist exists so the stack synthesizes in CI
// where the frontend may not have been built — mirrors the deploy workflow's
// "Ensure frontend dist exists for CDK synthesis" placeholder step.
const DIST = path.resolve(__dirname, '../../../web-frontend/dist');
beforeAll(() => {
  if (!fs.existsSync(path.join(DIST, 'index.html'))) {
    fs.mkdirSync(DIST, { recursive: true });
    fs.writeFileSync(path.join(DIST, 'index.html'), '<!-- test placeholder -->');
  }
});

function synth(config: EnvironmentConfig, extraProps: { variant?: string } = {}): Template {
  const app = new App();
  // logsBucket is a required cross-stack dependency (CloudFront access logs).
  const deps = new Stack(app, 'DepsStack', {
    env: { account: '123456789012', region: 'eu-central-1' },
  });
  const logsBucket = new s3.Bucket(deps, 'LogsBucket');

  const stack = new FrontendStack(app, 'TestFrontendStack', {
    config,
    logsBucket,
    env: { account: '123456789012', region: 'eu-central-1' },
    ...extraProps,
  });
  return Template.fromStack(stack);
}

describe('FrontendStack — stable snapshot bucket (frontend rollback)', () => {
  // The frontend rollback companion to the backend ECR `staging-stable` tag.
  // See docs/plans/playwright-staging-hardening.md §"Frontend rollback".

  test('should_createLiveAndStableBuckets_when_deployed', () => {
    const template = synth(stagingConfig);

    // Exactly two buckets in this stack: the live website bucket + the stable
    // snapshot bucket. (The logs bucket lives in DepsStack, not counted here.)
    template.resourceCountIs('AWS::S3::Bucket', 2);

    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-frontend-staging',
    });
    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-frontend-stable-staging',
    });
  });

  test('should_retainStableBucket_when_production', () => {
    const template = synth(stagingConfig); // isProduction: true

    // The stable snapshot must survive stack updates/replacements — it is the
    // only copy of the last known-good frontend (the live bucket is pruned on
    // every deploy). Retain on prod, like the live bucket.
    template.hasResource('AWS::S3::Bucket', {
      Properties: Match.objectLike({ BucketName: 'batbern-frontend-stable-staging' }),
      DeletionPolicy: 'Retain',
      UpdateReplacePolicy: 'Retain',
    });
  });

  test('should_blockPublicAccessAndEncrypt_when_stableBucketCreated', () => {
    const template = synth(stagingConfig);

    // The stable bucket is not a CloudFront origin; it must stay fully private
    // and encrypted like the live bucket.
    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-frontend-stable-staging',
      PublicAccessBlockConfiguration: {
        BlockPublicAcls: true,
        BlockPublicPolicy: true,
        IgnorePublicAcls: true,
        RestrictPublicBuckets: true,
      },
      BucketEncryption: Match.objectLike({
        ServerSideEncryptionConfiguration: Match.arrayWith([
          Match.objectLike({
            ServerSideEncryptionByDefault: { SSEAlgorithm: 'AES256' },
          }),
        ]),
      }),
    });
  });

  test('should_exportStableBucketName_when_deployed', () => {
    const template = synth(stagingConfig);

    // The deploy pipeline resolves the bucket via this CFN export.
    template.hasOutput('StableBucketName', {
      Export: { Name: 'staging-FrontendStableBucket' },
    });
  });

  test('should_nameStableBucketPerEnv_when_development', () => {
    const template = synth(devConfig);

    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-frontend-stable-development',
    });
  });
});

describe('FrontendStack — variant canary (beta.batbern.ch)', () => {
  // A second FrontendStack instance with `variant: 'beta'` must coexist with the primary
  // prod site without colliding on physical names, and must NOT carry the rollback machinery.
  // See docs/plans/beta-frontend-canary.md Phase 0.

  test('should_suffixBucketWithVariant_keepingEnvForIamGrant', () => {
    const template = synth(stagingConfig, { variant: 'beta' });
    // -${envName} suffix is retained so the bucket still matches the cicd S3 grant
    // `batbern-*-${envName}`.
    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-frontend-beta-staging',
    });
  });

  test('should_omitStableBucket_when_variant', () => {
    const template = synth(stagingConfig, { variant: 'beta' });
    // The canary is itself the pre-prod check — no rollback gate, no stable snapshot.
    template.resourceCountIs('AWS::S3::Bucket', 1);
    expect(() =>
      template.hasResourceProperties('AWS::S3::Bucket', {
        BucketName: 'batbern-frontend-stable-beta-staging',
      })
    ).toThrow();
    // ...and no stable-bucket export.
    expect(() =>
      template.hasOutput('StableBucketName', {})
    ).toThrow();
  });

  test('should_prefixCloudFrontResourceNames_when_variant', () => {
    const template = synth(stagingConfig, { variant: 'beta' });
    template.hasResourceProperties('AWS::CloudFront::Function', {
      Name: 'beta-spa-router',
    });
    template.hasResourceProperties('AWS::CloudFront::ResponseHeadersPolicy', {
      ResponseHeadersPolicyConfig: Match.objectLike({ Name: 'beta-static-assets-headers' }),
    });
  });

  test('should_routePrerenderedPublicRoutes_inSpaRouterFunction', () => {
    const template = synth(stagingConfig);
    // The SPA router CloudFront Function must map the prerendered public routes to their
    // route-specific static HTML (see web-frontend/scripts/prerender.mjs):
    //   - `/` (and `/index.html`) → /home/index.html  (homepage loading shell)
    //   - `/privacy`, `/about`, `/support` → their /<route>/index.html
    // while everything else still falls back to the neutral /index.html SPA shell.
    const functions = template.findResources('AWS::CloudFront::Function');
    const router = Object.values(functions).find(
      (fn) => fn.Properties?.Name === 'staging-spa-router'
    );
    expect(router).toBeDefined();
    const code: string = router!.Properties.FunctionCode;
    expect(code).toContain("request.uri = '/home/index.html'");
    expect(code).toContain("['/privacy', '/about', '/support']");
    expect(code).toContain("request.uri = '/index.html'"); // neutral SPA fallback preserved
  });

  test('should_makeBucketDestroyable_when_variant_evenThoughProd', () => {
    const template = synth(stagingConfig, { variant: 'beta' }); // isProduction: true
    // The canary bucket must be torn down cleanly despite isProduction — unlike the primary
    // site which retains.
    template.hasResource('AWS::S3::Bucket', {
      Properties: Match.objectLike({ BucketName: 'batbern-frontend-beta-staging' }),
      DeletionPolicy: 'Delete',
      UpdateReplacePolicy: 'Delete',
    });
  });

  test('should_useCheaperPriceClass_when_variant', () => {
    const template = synth(stagingConfig, { variant: 'beta' });
    // PRICE_CLASS_100 (Europe & US) for the canary; the primary prod site uses PRICE_CLASS_ALL.
    template.hasResourceProperties('AWS::CloudFront::Distribution', {
      DistributionConfig: Match.objectLike({ PriceClass: 'PriceClass_100' }),
    });
  });

  test('should_exportVariantPrefixedOutputs_when_variant', () => {
    const template = synth(stagingConfig, { variant: 'beta' });
    template.hasOutput('WebsiteBucketName', {
      Export: { Name: 'beta-FrontendBucket' },
    });
  });

  test('should_addNoindexRobotsHeader_when_variant', () => {
    const template = synth(stagingConfig, { variant: 'beta' });
    // The canary must stay out of search indexes so it never competes with www.
    template.hasResourceProperties('AWS::CloudFront::ResponseHeadersPolicy', {
      ResponseHeadersPolicyConfig: Match.objectLike({
        Name: 'beta-security-headers',
        CustomHeadersConfig: {
          Items: Match.arrayWith([
            { Header: 'X-Robots-Tag', Value: 'noindex, nofollow', Override: true },
          ]),
        },
      }),
    });
  });

  test('should_notAddNoindexRobotsHeader_when_primarySite', () => {
    const template = synth(stagingConfig); // no variant
    // The primary www site must NOT be deindexed.
    template.hasResourceProperties('AWS::CloudFront::ResponseHeadersPolicy', {
      ResponseHeadersPolicyConfig: Match.objectLike({
        Name: 'staging-security-headers',
        CustomHeadersConfig: {
          Items: Match.not(Match.arrayWith([Match.objectLike({ Header: 'X-Robots-Tag' })])),
        },
      }),
    });
  });
});

describe('FrontendStack — browser caching of static assets', () => {
  // PageSpeed reported "Cache TTL: None" — CloudFront edge-cached the hashed
  // assets but emitted no Cache-Control header, so browsers re-downloaded
  // ~5.6 MiB on repeat visits. A dedicated ResponseHeadersPolicy adds an
  // immutable 1-year Cache-Control to the content-hashed behaviors only.
  // See docs/plans/public-homepage-performance.md (Phase 6).

  test('should_createSeparateSecurityAndStaticAssetsPolicies', () => {
    const template = synth(stagingConfig);
    template.resourceCountIs('AWS::CloudFront::ResponseHeadersPolicy', 2);
  });

  test('should_setImmutableCacheControl_on_staticAssetsPolicy', () => {
    const template = synth(stagingConfig);

    template.hasResourceProperties('AWS::CloudFront::ResponseHeadersPolicy', {
      ResponseHeadersPolicyConfig: Match.objectLike({
        Name: 'staging-static-assets-headers',
        CustomHeadersConfig: {
          Items: Match.arrayWith([
            {
              Header: 'Cache-Control',
              Value: 'public, max-age=31536000, immutable',
              Override: true,
            },
          ]),
        },
      }),
    });
  });

  test('should_notSetCacheControl_on_htmlSecurityPolicy', () => {
    const template = synth(stagingConfig);

    // The HTML/SEO policy must NOT carry Cache-Control — index.html stays
    // uncached so SPA deploys are picked up immediately. Its custom headers are
    // exactly COOP + CORP.
    template.hasResourceProperties('AWS::CloudFront::ResponseHeadersPolicy', {
      ResponseHeadersPolicyConfig: Match.objectLike({
        Name: 'staging-security-headers',
        CustomHeadersConfig: {
          Items: Match.not(Match.arrayWith([Match.objectLike({ Header: 'Cache-Control' })])),
        },
      }),
    });
  });
});
