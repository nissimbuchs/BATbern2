import { describe, expect, test } from 'vitest';
import { PERSON_NAME_MAX_LENGTH, isValidPersonName } from './personName';

// #1102: names are free text. Anything a person can be called is accepted.
describe('isValidPersonName', () => {
  test.each([
    'O’Neill',
    "O'Neill",
    'Hông'.normalize('NFD'),
    'Hông Ân',
    '王',
    'Nguyễn',
    'Jean-Luc',
    'Mary (Molly)',
    'Brontë 2nd',
    'van der Berg',
    'محمد',
    'देवनागरी',
    'زهرا‌سادات', // contains ZWNJ (U+200C), legitimate in Persian
    'X Æ A-12',
  ])('should accept %s', (name) => {
    expect(isValidPersonName(name)).toBe(true);
  });

  test.each(['', '   ', '123', '---', 'a\nb', 'a\u0000b'])('should reject %j', (name) => {
    expect(isValidPersonName(name)).toBe(false);
  });

  test('should enforce the 100-character column limit', () => {
    expect(isValidPersonName('a'.repeat(PERSON_NAME_MAX_LENGTH))).toBe(true);
    expect(isValidPersonName('a'.repeat(PERSON_NAME_MAX_LENGTH + 1))).toBe(false);
  });
});
