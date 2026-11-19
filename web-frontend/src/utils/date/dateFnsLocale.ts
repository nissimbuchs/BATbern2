/**
 * The date-fns locale for a UI language, by the same rule as `intlLocale` (kept in its own module
 * so the date-fns locale data stays out of bundles that only use `Intl`). date-fns has no
 * Romansh, so `rm` formats in German, as does Swiss German.
 */
import { de, enUS, es, fi, frCH, itCH, ja, nl, type Locale } from 'date-fns/locale';
import { primaryLanguage } from './intlLocale';

const BY_LANGUAGE: Record<string, Locale> = {
  en: enUS,
  de,
  gsw: de,
  rm: de,
  fr: frCH,
  it: itCH,
  es,
  fi,
  nl,
  ja,
};

export function dateFnsLocale(uiLanguage: string | null | undefined): Locale {
  return BY_LANGUAGE[primaryLanguage(uiLanguage)] ?? de;
}
