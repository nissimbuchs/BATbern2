/**
 * The one mapping from the UI language to the locale every date is formatted with (#1222 item 6,
 * #1223 item 4). Every `toLocaleDateString`, `toLocaleString`, `toLocaleTimeString` and
 * `Intl.DateTimeFormat` that shows a date to a person passes `intlLocale(i18n.language)`; no
 * component names a locale itself. date-fns formatting goes through `dateFnsLocale` (same rule).
 *
 * - `en` is `en-US` ("Oct 22, 2026")
 * - `de` and Swiss German (`gsw-BE`) are `de-CH`
 * - `fr`, `it`, `rm` are their Swiss form; `rm-CH` falls back to `de-CH` where the runtime has
 *   no Romansh data
 * - every other language is its plain tag; no language (or an unknown one) is `de-CH`, the
 *   i18n fallback language
 *
 * `uiIntlLocale()` is the same for the current UI language (the i18next instance), for module
 * helpers and components that do not hold `i18n` themselves.
 */
import i18next from 'i18next';

const SWISS_DEFAULT = 'de-CH';

const PLAIN_TAGS = new Set(['es', 'fi', 'nl', 'ja']);

let romanshSupported: boolean | undefined;

function romansh(): string {
  if (romanshSupported === undefined) {
    try {
      romanshSupported = Intl.DateTimeFormat.supportedLocalesOf(['rm-CH']).length > 0;
    } catch {
      romanshSupported = false;
    }
  }
  return romanshSupported ? 'rm-CH' : SWISS_DEFAULT;
}

/** The primary subtag, lowercased: `gsw-BE` -> `gsw`, `en-GB` -> `en`. */
export function primaryLanguage(uiLanguage: string | null | undefined): string {
  return (uiLanguage ?? '').trim().toLowerCase().split(/[-_]/, 1)[0];
}

/** The `Intl` locale for a UI language (an i18next language code). */
export function intlLocale(uiLanguage: string | null | undefined): string {
  const primary = primaryLanguage(uiLanguage);
  switch (primary) {
    case 'en':
      return 'en-US';
    case 'de':
    case 'gsw':
      return SWISS_DEFAULT;
    case 'fr':
      return 'fr-CH';
    case 'it':
      return 'it-CH';
    case 'rm':
      return romansh();
    default:
      return PLAIN_TAGS.has(primary) ? primary : SWISS_DEFAULT;
  }
}

/** The `Intl` locale of the current UI language. */
export function uiIntlLocale(): string {
  return intlLocale(i18next.language);
}
