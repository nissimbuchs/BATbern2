import { describe, it, expect } from 'vitest';
import { enUS, de, frCH, itCH, ja } from 'date-fns/locale';
import { intlLocale } from './intlLocale';
import { dateFnsLocale } from './dateFnsLocale';
import { UI_LANGUAGES } from '@/i18n/languages';

const OCT_22 = new Date(Date.UTC(2026, 9, 22, 12));
const SHORT = { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' } as const;

describe('intlLocale: the UI language decides the date locale (#1222 item 6)', () => {
  it.each([
    ['en', 'en-US'],
    ['de', 'de-CH'],
    ['gsw-BE', 'de-CH'],
    ['fr', 'fr-CH'],
    ['it', 'it-CH'],
    ['es', 'es'],
    ['fi', 'fi'],
    ['nl', 'nl'],
    ['ja', 'ja'],
  ])('%s -> %s', (ui, expected) => {
    expect(intlLocale(ui)).toBe(expected);
  });

  it('rm is rm-CH where the runtime has Romansh, otherwise de-CH', () => {
    const supported = Intl.DateTimeFormat.supportedLocalesOf(['rm-CH']).length > 0;
    expect(intlLocale('rm')).toBe(supported ? 'rm-CH' : 'de-CH');
  });

  it.each([[undefined], [null], [''], ['xx'], ['cimode']])('falls back to de-CH for %s', (ui) => {
    expect(intlLocale(ui)).toBe('de-CH');
  });

  it('takes the primary subtag of a region-tagged language', () => {
    expect(intlLocale('en-GB')).toBe('en-US');
    expect(intlLocale('de-DE')).toBe('de-CH');
    expect(intlLocale('GSW-be')).toBe('de-CH');
  });

  it('formats Oct 22, 2026 in English and 22. Okt. 2026 in German and Swiss German', () => {
    expect(OCT_22.toLocaleDateString(intlLocale('en'), SHORT)).toBe('Oct 22, 2026');
    expect(OCT_22.toLocaleDateString(intlLocale('de'), SHORT)).toBe('22. Okt. 2026');
    expect(OCT_22.toLocaleDateString(intlLocale('gsw-BE'), SHORT)).toBe('22. Okt. 2026');
  });

  it('gives every UI language a locale Intl accepts', () => {
    for (const ui of UI_LANGUAGES) {
      expect(() => OCT_22.toLocaleDateString(intlLocale(ui), SHORT)).not.toThrow();
      expect(Intl.DateTimeFormat.supportedLocalesOf([intlLocale(ui)])).toHaveLength(1);
    }
  });
});

describe('dateFnsLocale: same rule for date-fns', () => {
  it.each([
    ['en', enUS],
    ['de', de],
    ['gsw-BE', de],
    ['rm', de],
    ['fr', frCH],
    ['it', itCH],
    ['ja', ja],
    [undefined, de],
    ['xx', de],
  ])('%s', (ui, expected) => {
    expect(dateFnsLocale(ui)).toBe(expected);
  });
});
