/**
 * The one email rule for every form (#1102). Mirrors shared-kernel `EmailAddresses` and the
 * `pattern` on every request email field in docs/api.
 *
 * Accept every address Amazon SES can deliver to: any RFC 5322 dot-atom local part in ASCII
 * (`+`, `'` and friends included) and any domain, internationalised ones (`müller.ch`) included.
 * Reject only what SES cannot deliver (a Unicode local part: SES has no SMTPUTF8) and what is not
 * an address at all.
 *
 * Do not use `z.string().email()` or `<input type="email">` validation for new data: both reject
 * internationalised domains.
 */

const ATEXT = "A-Za-z0-9!#$%&'*+/=?^_`{|}~-";
const LOCAL_PART = new RegExp(`^[${ATEXT}]+(\\.[${ATEXT}]+)*$`);
const ASCII_LABEL = /^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$/;
const TOP_LEVEL = /^(?:[A-Za-z]{2,}|xn--[A-Za-z0-9-]+)$/;

export const EMAIL_MAX_LENGTH = 254;
const LOCAL_PART_MAX_LENGTH = 64;
const LABEL_MAX_LENGTH = 63;
const DOMAIN_MAX_LENGTH = 253;

export type EmailProblem = 'invalid' | 'nonAsciiLocalPart';

/** Domain in Punycode, or null when it is not a valid domain name. Uses the browser's IDNA. */
function toAsciiDomain(domain: string): string | null {
  if (domain === '' || domain.startsWith('.') || domain.endsWith('.') || domain.includes('..')) {
    return null;
  }
  let ascii: string;
  try {
    ascii = new URL(`http://${domain}`).hostname;
  } catch {
    return null;
  }
  if (ascii.length > DOMAIN_MAX_LENGTH) {
    return null;
  }
  const labels = ascii.split('.');
  if (labels.length < 2) {
    return null;
  }
  if (!labels.every((l) => l.length <= LABEL_MAX_LENGTH && ASCII_LABEL.test(l))) {
    return null;
  }
  return TOP_LEVEL.test(labels[labels.length - 1]) ? ascii : null;
}

/** Null when acceptable, otherwise why not. Surrounding whitespace is ignored (the backend trims). */
export function emailProblem(value: string): EmailProblem | null {
  const email = value.trim();
  const at = email.lastIndexOf('@');
  if (at <= 0 || at === email.length - 1) {
    return 'invalid';
  }
  const local = email.slice(0, at);
  // eslint-disable-next-line no-control-regex
  if (/[^\x00-\x7F]/.test(local)) {
    return 'nonAsciiLocalPart';
  }
  if (local.length > LOCAL_PART_MAX_LENGTH || !LOCAL_PART.test(local)) {
    return 'invalid';
  }
  const domain = toAsciiDomain(email.slice(at + 1));
  if (domain === null || local.length + 1 + domain.length > EMAIL_MAX_LENGTH) {
    return 'invalid';
  }
  return null;
}

export function isValidEmail(value: string): boolean {
  return emailProblem(value) === null;
}

/**
 * Shape check for fields that look up an EXISTING address (login, forgot password,
 * deregistration by email). Deliberately loose: an account created under an older rule must
 * still be able to sign in.
 */
export function looksLikeEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+$/.test(value.trim());
}
