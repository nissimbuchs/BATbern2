import { describe, expect, test } from 'vitest';
import { EMAIL_MAX_LENGTH, emailProblem, isValidEmail } from './email';

// #1102: same rule as shared-kernel EmailAddresses (backend) and the OpenAPI `pattern`.
describe('isValidEmail', () => {
  test.each([
    'john.doe@example.com',
    "o'neill@example.ie",
    'first+batbern@example.com',
    'a!#$%&*/=?^_`{|}~-@example.ch',
    'x@example.engineering',
    'x@müller.ch',
    'x@xn--mller-kva.ch',
    'x@example.xn--p1ai',
    '1234567890@example.com',
  ])('should accept %s when SES can deliver to it', (email) => {
    expect(isValidEmail(email)).toBe(true);
    expect(emailProblem(email)).toBeNull();
  });

  test.each([
    '',
    'plainaddress',
    '@example.com',
    'x@',
    'x@localhost',
    'x y@example.com',
    '.x@example.com',
    'x.@example.com',
    'x..y@example.com',
    'x@example..com',
    '"quoted"@example.com',
  ])('should reject %s when it is not an address', (email) => {
    expect(isValidEmail(email)).toBe(false);
    expect(emailProblem(email)).toBe('invalid');
  });

  test.each(['jürg@example.ch', 'δοκιμή@example.gr'])(
    'should report nonAsciiLocalPart for %s because SES cannot deliver it',
    (email) => {
      expect(isValidEmail(email)).toBe(false);
      expect(emailProblem(email)).toBe('nonAsciiLocalPart');
    }
  );

  test('should ignore surrounding whitespace the way the backend trims', () => {
    expect(isValidEmail('  john.doe@example.com ')).toBe(true);
  });

  test('should reject overlong local part and address', () => {
    expect(isValidEmail(`${'x'.repeat(64)}@example.com`)).toBe(true);
    expect(isValidEmail(`${'x'.repeat(65)}@example.com`)).toBe(false);
    expect(
      isValidEmail(`x@${'a'.repeat(60)}.${'b'.repeat(60)}.${'c'.repeat(60)}.${'d'.repeat(70)}.ch`)
    ).toBe(false);
    expect(EMAIL_MAX_LENGTH).toBe(254);
  });
});
