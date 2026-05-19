// Redeploy marker — bump when the Lambda needs a forced rebuild without code
// changes (e.g. after a sharp transitive-dep regression). The deploy-staging
// workflow watches infrastructure/lib/lambda/** for changes; touching this
// file is what triggers a full layer-based deploy that re-bundles native
// dependencies through the Docker bundler. Previous touches:
//   - 2026-05-16 (commit 436c4c9c): sharp transitive deps (detect-libc, color, semver)
//   - 2026-05-18: forced rebuild after Tier-1 abuse-defense direct-update bypass
//                 reverted the @img/* native packages back to the pre-fix state.
import { S3Client, GetObjectCommand } from '@aws-sdk/client-s3';
import type { CloudFrontRequestEvent, CloudFrontRequestResult } from 'aws-lambda';

// Injected at CDK build time via esbuild --define (Lambda@Edge has no env vars)
declare const CONTENT_BUCKET_NAME: string;
declare const CONTENT_BUCKET_REGION: string;

const s3 = new S3Client({ region: CONTENT_BUCKET_REGION });
const MAX_DIM = 2000;

type Fit = 'cover' | 'contain' | 'fill' | 'inside' | 'outside';
const VALID_FIT = new Set<string>(['cover', 'contain', 'fill', 'inside', 'outside']);

export const handler = async (event: CloudFrontRequestEvent): Promise<CloudFrontRequestResult> => {
  const request = event.Records[0].cf.request;
  const params = new URLSearchParams(request.querystring);

  const rawW = params.get('w');
  const rawH = params.get('h');
  const w = rawW ? Math.min(Math.abs(Number(rawW)), MAX_DIM) || undefined : undefined;
  const h = rawH ? Math.min(Math.abs(Number(rawH)), MAX_DIM) || undefined : undefined;

  if (!w && !h) return request;

  // Dynamic import keeps the module loadable even if sharp is absent from the Lambda package.
  // A static top-level import would crash module initialisation and break ALL requests with 503.
  let sharpFn: typeof import('sharp');
  try {
    sharpFn = (await import('sharp')).default as unknown as typeof import('sharp');
  } catch (err) {
    console.error('image-resize: sharp import failed, falling back to pass-through', err);
    return request;
  }

  const fitRaw = params.get('fit') ?? 'cover';
  const fit: Fit = VALID_FIT.has(fitRaw) ? (fitRaw as Fit) : 'cover';
  const key = request.uri.replace(/^\//, '');

  try {
    const s3Resp = await s3.send(new GetObjectCommand({ Bucket: CONTENT_BUCKET_NAME, Key: key }));
    const chunks: Uint8Array[] = [];
    for await (const chunk of s3Resp.Body as AsyncIterable<Uint8Array>) {
      chunks.push(chunk);
    }
    const resized = await sharpFn(Buffer.concat(chunks))
      .resize(w, h, { fit, withoutEnlargement: true })
      .webp({ quality: 80 })
      .toBuffer();

    return {
      status: '200',
      statusDescription: 'OK',
      headers: {
        'content-type': [{ key: 'Content-Type', value: 'image/webp' }],
        'cache-control': [{ key: 'Cache-Control', value: 'public, max-age=31536000, immutable' }],
      },
      body: resized.toString('base64'),
      bodyEncoding: 'base64',
    };
  } catch (err) {
    console.error('image-resize: resize failed, falling back to pass-through', { key, err });
    return request;
  }
};
