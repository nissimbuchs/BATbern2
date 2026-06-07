/**
 * Regression test for the image-resize Lambda@Edge handler.
 *
 * KEY INVARIANT: The handler module must be importable even when 'sharp' is not installed
 * in the Lambda package. A static top-level `import sharp from 'sharp'` breaks this —
 * the module crashes at init and CloudFront returns 503 for ALL requests, including plain
 * pass-throughs that never touch sharp.
 *
 * These tests run without sharp installed (infrastructure devDependencies does not include it).
 * If someone reverts to a static import, the `import()` call below will throw
 * "Cannot find module 'sharp'" and the test suite will fail — catching the regression before deploy.
 */

// Globals injected by esbuild --define at CDK build time; set them here for Jest.
(global as unknown as Record<string, string>).CONTENT_BUCKET_NAME = 'test-bucket';
(global as unknown as Record<string, string>).CONTENT_BUCKET_REGION = 'eu-central-1';

import type { CloudFrontRequestEvent } from 'aws-lambda';

function makeEvent(uri: string, querystring: string): CloudFrontRequestEvent {
  return {
    Records: [
      {
        cf: {
          config: { distributionDomainName: 'd.cloudfront.net', distributionId: 'TEST', eventType: 'origin-request', requestId: '1' },
          request: {
            clientIp: '1.2.3.4',
            headers: {},
            method: 'GET',
            querystring,
            uri,
          },
        },
      },
    ],
  } as CloudFrontRequestEvent;
}

describe('image-resize Lambda handler', () => {
  // Re-import fresh each test to avoid module cache across test runs
  beforeEach(() => {
    jest.resetModules();
  });

  test('module loads without crashing when sharp is not installed', async () => {
    // This assertion is the primary regression guard. With a static `import sharp from 'sharp'`
    // the dynamic import below throws and the test fails, surfacing the bug before any deploy.
    const mod = await import('../../../lib/lambda/image-resize/index');
    expect(typeof mod.handler).toBe('function');
  });

  test('passes through requests that have no resize params', async () => {
    const { handler } = await import('../../../lib/lambda/image-resize/index');
    const event = makeEvent('/events/BATbern58/photos/test.jpg', '');
    const result = await handler(event);
    // Should return the original request object unchanged
    expect(result).toBe(event.Records[0].cf.request);
  });

  test('passes through requests where only unrelated query params are present', async () => {
    const { handler } = await import('../../../lib/lambda/image-resize/index');
    const event = makeEvent('/events/BATbern58/photos/test.jpg', 'v=1&t=abc');
    const result = await handler(event);
    expect(result).toBe(event.Records[0].cf.request);
  });

  test('passes through resize requests when sharp is unavailable (fail-open)', async () => {
    // sharp is not in infrastructure devDependencies, so the dynamic import inside the
    // handler catches the error and falls back to passing through to the S3 origin.
    const { handler } = await import('../../../lib/lambda/image-resize/index');
    const event = makeEvent('/events/BATbern58/photos/test.jpg', 'w=256&h=192&fit=cover');
    const result = await handler(event);
    // Fail-open: result is the original request (CloudFront fetches from S3 directly)
    expect(result).toBe(event.Records[0].cf.request);
  });
});
