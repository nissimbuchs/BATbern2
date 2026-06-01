import { describe, it, expect } from 'vitest';
import { buildCdnImageUrl, buildCdnImageSrcSet } from './cdnImage';

const CDN = 'https://cdn.batbern.ch/logos/2026/events/BATbern59/theme-abc.png';

describe('buildCdnImageUrl', () => {
  it('appends w/h/fit params to a CDN raster URL', () => {
    const out = buildCdnImageUrl(CDN, { w: 1920, h: 1080, fit: 'cover' });
    const u = new URL(out as string);
    expect(u.searchParams.get('w')).toBe('1920');
    expect(u.searchParams.get('h')).toBe('1080');
    expect(u.searchParams.get('fit')).toBe('cover');
  });

  it('supports width-only requests', () => {
    const out = buildCdnImageUrl(CDN, { w: 768 });
    const u = new URL(out as string);
    expect(u.searchParams.get('w')).toBe('768');
    expect(u.searchParams.has('h')).toBe(false);
  });

  it('clamps dimensions to MAX_DIM (2000) and rounds', () => {
    const out = buildCdnImageUrl(CDN, { w: 5000, h: 160.6 });
    const u = new URL(out as string);
    expect(u.searchParams.get('w')).toBe('2000');
    expect(u.searchParams.get('h')).toBe('161');
  });

  it('returns the URL unchanged when no dimensions are given', () => {
    expect(buildCdnImageUrl(CDN, {})).toBe(CDN);
    expect(buildCdnImageUrl(CDN)).toBe(CDN);
  });

  it('matches CDN subdomains like cdn.staging.batbern.ch', () => {
    const staging = 'https://cdn.staging.batbern.ch/logos/x.jpg';
    expect(buildCdnImageUrl(staging, { w: 100 })).toContain('w=100');
  });

  it('leaves SVGs untouched (logos stay vector)', () => {
    const svg = 'https://cdn.batbern.ch/logos/2026/companies/sbb/logo-x.svg';
    expect(buildCdnImageUrl(svg, { h: 128 })).toBe(svg);
  });

  it('leaves non-CDN hosts untouched', () => {
    const other = 'https://example.com/image.png';
    expect(buildCdnImageUrl(other, { w: 100 })).toBe(other);
  });

  it('leaves relative paths untouched', () => {
    expect(buildCdnImageUrl('/BATbern_color_logo.svg', { w: 100 })).toBe('/BATbern_color_logo.svg');
  });

  it('returns null/undefined/empty inputs as-is', () => {
    expect(buildCdnImageUrl(null, { w: 100 })).toBeNull();
    expect(buildCdnImageUrl(undefined, { w: 100 })).toBeUndefined();
    expect(buildCdnImageUrl('', { w: 100 })).toBe('');
  });

  it('preserves a pre-existing query string while adding params', () => {
    const out = buildCdnImageUrl(`${CDN}?v=2`, { w: 100 });
    const u = new URL(out as string);
    expect(u.searchParams.get('v')).toBe('2');
    expect(u.searchParams.get('w')).toBe('100');
  });
});

describe('buildCdnImageSrcSet', () => {
  it('builds a srcset with a descriptor per width', () => {
    const srcset = buildCdnImageSrcSet(CDN, [768, 1280, 1920], { fit: 'cover' });
    expect(srcset).toBeDefined();
    const parts = (srcset as string).split(', ');
    expect(parts).toHaveLength(3);
    expect(parts[0]).toContain('w=768');
    expect(parts[0]).toMatch(/ 768w$/);
    expect(parts[2]).toMatch(/ 1920w$/);
  });

  it('returns undefined for non-resizable URLs (so callers fall back to src)', () => {
    expect(buildCdnImageSrcSet('/local.svg', [768])).toBeUndefined();
    expect(buildCdnImageSrcSet('https://cdn.batbern.ch/logo.svg', [768])).toBeUndefined();
    expect(buildCdnImageSrcSet(null, [768])).toBeUndefined();
    expect(buildCdnImageSrcSet(CDN, [])).toBeUndefined();
  });
});
