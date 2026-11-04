/**
 * Email template keys (`event-newsletter-api.openapi.yml`): kebab-case, at most 100 characters.
 * Organizers type a name; the key is derived from it, so "Partner Info" is never rejected.
 */
export const TEMPLATE_KEY_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const MAX_LENGTH = 100;
const GERMAN = { ä: 'ae', ö: 'oe', ü: 'ue', ß: 'ss' } as Record<string, string>;

/** "Raumwechsel für Partner" → "raumwechsel-fuer-partner"; "" when nothing usable is left. */
export function toTemplateKey(name: string): string {
  return name
    .toLowerCase()
    .replace(/[äöüß]/g, (c) => GERMAN[c])
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .slice(0, MAX_LENGTH)
    .replace(/^-+|-+$/g, '');
}
