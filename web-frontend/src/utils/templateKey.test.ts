import { describe, expect, it } from 'vitest';
import { TEMPLATE_KEY_PATTERN, toTemplateKey } from './templateKey';

describe('toTemplateKey', () => {
  it.each([
    ['partner-info', 'partner-info'],
    ['Partner Info', 'partner-info'],
    ['  Raumwechsel für Partner  ', 'raumwechsel-fuer-partner'],
    ['Grösse & Öffnungszeiten', 'groesse-oeffnungszeiten'],
    ['Straße', 'strasse'],
    ['Café Apéro', 'cafe-apero'],
    ['partner_info__2026', 'partner-info-2026'],
    ['--a--b--', 'a-b'],
  ])('%s -> %s', (name, key) => {
    expect(toTemplateKey(name)).toBe(key);
    expect(TEMPLATE_KEY_PATTERN.test(key)).toBe(true);
  });

  it('is empty when nothing usable is left', () => {
    expect(toTemplateKey('!!! ???')).toBe('');
    expect(toTemplateKey('')).toBe('');
  });

  it('stays within the 100 characters of the contract and never ends on a hyphen', () => {
    const key = toTemplateKey(`${'a'.repeat(99)} b`);
    expect(key.length).toBeLessThanOrEqual(100);
    expect(TEMPLATE_KEY_PATTERN.test(key)).toBe(true);
  });
});
