/**
 * #1102: the PostConfirmation Lambda's username generator must behave exactly like
 * shared-kernel SlugGenerationService. Both are pinned to the same golden file.
 */
import { describe, it, expect } from '@jest/globals';
import { readFileSync } from 'fs';
import { join } from 'path';
import {
  generateUsername,
  MAX_BASE_USERNAME_LENGTH,
  USERNAME_PATTERN,
} from '../../../lib/lambda/triggers/common/username';

interface GoldenCase {
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  expected: string;
}

const golden: { cases: GoldenCase[] } = JSON.parse(
  readFileSync(
    join(__dirname, '../../../../shared-kernel/src/test/resources/username-generation-cases.json'),
    'utf-8'
  )
);

describe('generateUsername (PostConfirmation)', () => {
  it('should load the shared golden cases', () => {
    expect(golden.cases.length).toBeGreaterThanOrEqual(20);
  });

  it.each(golden.cases.map((c) => [c.firstName, c.lastName, c.email, c.expected]))(
    'should_matchJavaGenerator_when_name_%s_%s_email_%s',
    (firstName, lastName, email, expected) => {
      const username = generateUsername(firstName, lastName, email);
      expect(username).toBe(expected);
      expect(username).toMatch(USERNAME_PATTERN);
    }
  );

  it('should_alwaysSatisfyDbConstraint_when_inputIsHostile', () => {
    const names = [
      '',
      ' ',
      '-',
      "'",
      '...',
      '123',
      '😀',
      '​',
      'a\u0000b',
      '<script>',
      'ⅷ',
      'Ǆemal',
      'ﬁona',
      'Χριστίνα',
      'محمد',
      '김민준',
      'ᚠᛁᚾᚾ',
      'x'.repeat(500),
    ];
    const emails = ['x@example.com', '123@example.com', 'a.b.c.d@example.com', '-@example.com'];
    for (const first of names) {
      for (const last of names) {
        for (const email of emails) {
          const username = generateUsername(first, last, email);
          expect(username).toMatch(USERNAME_PATTERN);
          expect(username.length).toBeLessThanOrEqual(MAX_BASE_USERNAME_LENGTH);
        }
      }
    }
  });
});
