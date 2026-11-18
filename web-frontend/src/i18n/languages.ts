/**
 * The UI languages BATbern ships (CLAUDE.md "Localization": all 10 locales). One list for the
 * language switchers, the auth context and the users-api `language` setting, which accepts
 * exactly these codes (#1222 item 5: `gsw-BE` included).
 */
export const UI_LANGUAGES = [
  'de',
  'en',
  'fr',
  'it',
  'rm',
  'es',
  'fi',
  'nl',
  'ja',
  'gsw-BE',
] as const;

export type UiLanguage = (typeof UI_LANGUAGES)[number];

export function isUiLanguage(value: string | null | undefined): value is UiLanguage {
  return (UI_LANGUAGES as readonly string[]).includes(value ?? '');
}

/**
 * The mail language for a UI language, by the backend's rule (`MailLocales`, #1222): mails exist
 * in German and English only; German and Swiss German get German, every other language English.
 */
export function mailLanguage(uiLanguage: string | null | undefined): 'de' | 'en' {
  const lower = (uiLanguage ?? '').toLowerCase();
  return lower.startsWith('de') || lower.startsWith('gsw') ? 'de' : 'en';
}
