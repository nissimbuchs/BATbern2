/**
 * /why-an-account (story "Visitors see what a BATbern account gives them") is public: the
 * confirmation mail links it for people without an account.
 */
import { describe, it, expect } from 'vitest';
import { canAccessPath } from '../authRules';
import { anAuthUser } from '@/test/auth';

describe('canAccessPath, account page', () => {
  it('should_admit_when_loggedOut', () => {
    expect(canAccessPath(false, null, '/why-an-account')).toBe(true);
  });

  it('should_admit_when_loggedIn', () => {
    expect(canAccessPath(true, anAuthUser({ role: 'attendee' }), '/why-an-account')).toBe(true);
  });
});
