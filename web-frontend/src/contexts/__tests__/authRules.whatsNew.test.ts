/**
 * "What's new" (story 2 of 2026-11-13): every logged-in user, whatever the role, reaches the card
 * on the attendee dashboard through the user menu.
 */
import { describe, it, expect } from 'vitest';
import { canAccessPath } from '../authRules';
import { anAuthUser } from '@/test/auth';
import type { UserRole } from '@/types/auth';

describe('canAccessPath, attendee dashboard', () => {
  it.each<UserRole>(['organizer', 'partner', 'speaker', 'attendee'])(
    'should_admitThe%sRoleAlone_when_openingTheWhatsNewCard',
    (role) => {
      expect(canAccessPath(true, anAuthUser({ role }), '/attendee/dashboard')).toBe(true);
    }
  );

  it('should_refuse_when_loggedOut', () => {
    expect(canAccessPath(false, null, '/attendee/dashboard')).toBe(false);
  });
});
