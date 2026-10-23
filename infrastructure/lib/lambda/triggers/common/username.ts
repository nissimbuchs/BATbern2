/**
 * Username generation for the PostConfirmation Lambda (#1102).
 *
 * A 1:1 port of shared-kernel `SlugGenerationService.generateUsername(first, last, email)`.
 * Both are pinned to the same golden file
 * (`shared-kernel/src/test/resources/username-generation-cases.json`); change them together.
 *
 * Names are free text in any script. The username is the public meaningful ID (ADR-003) and
 * stays an ASCII slug matching `chk_username_format`; this function never returns anything else:
 *   1. both name parts spell something in ASCII -> `first.last` (inner spaces dropped)
 *   2. otherwise the email local part without `+tag` (`taro.yamada@` -> `taro.yamada`)
 *   3. otherwise `user.<the part that did spell>`, finally `user.unknown`
 */

export const USERNAME_PATTERN = /^[a-z]+\.[a-z]+(\.[0-9]+)?$/;
export const MAX_USERNAME_PART_LENGTH = 40;
export const MAX_BASE_USERNAME_LENGTH = 2 * MAX_USERNAME_PART_LENGTH + 1;

const GERMAN: Record<string, string> = { ä: 'ae', ö: 'oe', ü: 'ue', ß: 'ss' };
const LATIN_WITHOUT_DECOMPOSITION: Record<string, string> = {
  ß: 'ss',
  æ: 'ae',
  œ: 'oe',
  ø: 'o',
  ł: 'l',
  đ: 'd',
  ð: 'd',
  þ: 'th',
  ı: 'i',
  ħ: 'h',
  ŋ: 'n',
  ŧ: 't',
  ĸ: 'k',
  ſ: 's',
};

function cap(part: string): string {
  return part.length > MAX_USERNAME_PART_LENGTH ? part.slice(0, MAX_USERNAME_PART_LENGTH) : part;
}

function toAsciiLetters(text: string, separator: string): string {
  let slug = text.normalize('NFC').toLowerCase();
  slug = slug.replace(/[äöüß]/g, (c) => GERMAN[c]);
  slug = Array.from(slug, (c) => LATIN_WITHOUT_DECOMPOSITION[c] ?? c).join('');
  slug = slug.normalize('NFKD').replace(/\p{M}/gu, '');
  return slug.replace(/[^a-z]+/g, separator);
}

function slugifyName(name: string | null | undefined): string {
  return name == null ? '' : cap(toAsciiLetters(name, ''));
}

function usernameFromEmail(email: string | null | undefined): string | null {
  if (email == null) {
    return null;
  }
  const at = email.lastIndexOf('@');
  let local = at < 0 ? email : email.slice(0, at);
  const plus = local.indexOf('+');
  if (plus >= 0) {
    local = local.slice(0, plus);
  }
  // Digits are dropped, not treated as separators: e2e-test -> ee.test, john.doe2 -> john.doe
  const tokens = toAsciiLetters(local.replace(/[0-9]/g, ''), ' ').trim().split(/ +/);
  if (tokens.length === 0 || tokens[0] === '') {
    return null;
  }
  const head = cap(tokens[0]);
  if (tokens.length === 1) {
    return `user.${head}`;
  }
  return `${head}.${cap(tokens.slice(1).join(''))}`;
}

export function generateUsername(
  firstName: string | null | undefined,
  lastName: string | null | undefined,
  email: string | null | undefined
): string {
  const first = slugifyName(firstName);
  const last = slugifyName(lastName);
  if (first !== '' && last !== '') {
    return `${first}.${last}`;
  }
  const fromEmail = usernameFromEmail(email);
  if (fromEmail !== null) {
    return fromEmail;
  }
  const onePart = first !== '' ? first : last;
  return `user.${onePart === '' ? 'unknown' : onePart}`;
}
