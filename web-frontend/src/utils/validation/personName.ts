/**
 * Person names are free text (#1102): whatever someone is called is accepted. Letters of any
 * script, combining marks (decomposed Vietnamese, Devanagari), digits, apostrophes of any kind,
 * parentheses, ZWNJ, ... The only limits: not blank, at least one letter, no control characters
 * (newlines, NUL), and the 100-character column `user_profiles.first_name/last_name`.
 *
 * The username is derived from the name by shared-kernel SlugGenerationService, which handles any
 * input, so nothing here needs to restrict names for the username's sake.
 */
export const PERSON_NAME_MAX_LENGTH = 100;

export function isValidPersonName(value: string): boolean {
  const name = value.trim();
  return (
    name.length > 0 &&
    name.length <= PERSON_NAME_MAX_LENGTH &&
    /\p{L}/u.test(name) &&
    !/\p{Cc}/u.test(name)
  );
}
