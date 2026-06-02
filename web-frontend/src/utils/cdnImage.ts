/**
 * CDN image helpers
 *
 * The BATbern content CDN (cdn.*.batbern.ch) is fronted by an image-resize
 * Lambda@Edge (`infrastructure/lib/lambda/image-resize/index.ts`) that, when a
 * `w` and/or `h` query param is present, resizes the origin object with `sharp`,
 * converts it to WebP (quality 80), and returns it with
 * `Cache-Control: public, max-age=31536000, immutable`. Without `w`/`h` the
 * Lambda passes the request through unchanged.
 *
 * These helpers append the resize params to CDN raster URLs so callers stop
 * shipping full-resolution PNG/JPEG originals to the browser. Non-CDN URLs,
 * relative paths, and vector (SVG) assets are returned untouched.
 */

export type CdnFit = 'cover' | 'contain' | 'fill' | 'inside' | 'outside';

export interface CdnImageOptions {
  /** Target width in CSS pixels (request 2× the display size for retina). */
  w?: number;
  /** Target height in CSS pixels. */
  h?: number;
  /** Resize fit mode. Defaults to the Lambda's own default (`cover`) when omitted. */
  fit?: CdnFit;
}

// Mirror of MAX_DIM in the Lambda — requesting larger is clamped server-side
// anyway, but clamping here keeps generated URLs honest and cache-friendly.
const MAX_DIM = 2000;

// Raster formats sharp can decode and re-encode to WebP. SVGs are intentionally
// excluded: logos should stay vector (crisp at any size, already tiny).
const RASTER_RE = /\.(png|jpe?g|webp|avif|tiff?|gif)$/i;

/** The resize Lambda only lives on the content CDN host (cdn.*.batbern.ch). */
const isResizableCdnHost = (hostname: string): boolean => /(^|\.)cdn\./i.test(hostname);

const clampDim = (value: number): number => Math.min(Math.max(Math.round(value), 1), MAX_DIM);

/**
 * Append `?w=&h=&fit=` to a CDN raster URL so it is served as a resized WebP.
 *
 * Returns the input unchanged when:
 * - it is empty / not a string,
 * - neither `w` nor `h` is provided (the Lambda would pass through anyway),
 * - it is a relative path or non-http(s) URL,
 * - its host is not the content CDN, or
 * - it does not point at a raster image (e.g. an SVG).
 */
export function buildCdnImageUrl(
  url: string | null | undefined,
  { w, h, fit }: CdnImageOptions = {}
): string | null | undefined {
  if (!url) return url;
  if (!w && !h) return url;

  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return url; // relative path (e.g. /logo.svg) or malformed — leave it alone
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return url;
  if (!isResizableCdnHost(parsed.hostname)) return url;
  if (!RASTER_RE.test(parsed.pathname)) return url;

  if (w) parsed.searchParams.set('w', String(clampDim(w)));
  if (h) parsed.searchParams.set('h', String(clampDim(h)));
  if (fit) parsed.searchParams.set('fit', fit);

  return parsed.toString();
}

/**
 * Build a `srcset` of WebP variants at the given widths for responsive images
 * (e.g. a full-bleed hero). Returns `undefined` when the URL is not a
 * resizable CDN image, so callers can fall back to a plain `src`.
 */
export function buildCdnImageSrcSet(
  url: string | null | undefined,
  widths: number[],
  { fit }: Pick<CdnImageOptions, 'fit'> = {}
): string | undefined {
  if (!url || widths.length === 0) return undefined;

  const entries = widths
    .map((w) => {
      const resized = buildCdnImageUrl(url, { w, fit });
      return resized && resized !== url ? `${resized} ${clampDim(w)}w` : null;
    })
    .filter((entry): entry is string => entry !== null);

  return entries.length > 0 ? entries.join(', ') : undefined;
}
