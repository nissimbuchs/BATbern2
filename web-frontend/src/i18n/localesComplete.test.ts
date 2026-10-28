import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync } from 'fs';
import { join, resolve } from 'path';

// Visitors see a "[MISSING] …" placeholder verbatim, so none may ship (#1149).
const LOCALES_DIR = resolve(__dirname, '../../public/locales');

function placeholders(node: unknown, path: string): string[] {
  if (typeof node === 'string') return node.includes('[MISSING]') ? [path] : [];
  if (node && typeof node === 'object') {
    return Object.entries(node).flatMap(([k, v]) => placeholders(v, path ? `${path}.${k}` : k));
  }
  return [];
}

describe('locale files', () => {
  it('should_containNoMissingPlaceholders_when_shipped', () => {
    const found = readdirSync(LOCALES_DIR).flatMap((locale) =>
      readdirSync(join(LOCALES_DIR, locale))
        .filter((f) => f.endsWith('.json'))
        .flatMap((f) =>
          placeholders(JSON.parse(readFileSync(join(LOCALES_DIR, locale, f), 'utf8')), '').map(
            (key) => `${locale}/${f}: ${key}`
          )
        )
    );
    expect(found).toEqual([]);
  });
});
