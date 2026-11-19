/**
 * Role-neutral pages open for every signed-in role (#1217: the claim link bounced every role to
 * /dashboard because no role listed it). A user without any role opens none of them.
 */
import { describe, it, expect } from 'vitest';
import { canAccessPath } from '../authRules';
import { anAuthUser } from '@/test/auth';
import type { UserRole } from '@/types/auth';

const ROLES: UserRole[] = ['attendee', 'speaker', 'partner', 'organizer'];
const ROLE_NEUTRAL = ['/dashboard', '/profile', '/account/settings', '/attendee/whats-new'];

describe('canAccessPath, pages for every role', () => {
  it.each(ROLES)('should_openTheClaimLink_when_%s', (role) => {
    expect(canAccessPath(true, anAuthUser({ role, roles: [role] }), '/claim-profile?token=x')).toBe(
      true
    );
  });

  it.each(ROLES)('should_openTheRoleNeutralPages_when_%s', (role) => {
    for (const path of ROLE_NEUTRAL) {
      expect(canAccessPath(true, anAuthUser({ role, roles: [role] }), path)).toBe(true);
    }
  });

  it('should_keepRolePagesApart_when_attendee', () => {
    const attendee = anAuthUser({ role: 'attendee', roles: ['attendee'] });
    expect(canAccessPath(true, attendee, '/events')).toBe(true);
    expect(canAccessPath(true, attendee, '/organizer/events')).toBe(false);
  });

  it('should_openNothing_when_userHasNoRole', () => {
    // anAuthUser spreads its overrides, so role: undefined with roles: [] leaves no role at all.
    const roleless = anAuthUser({ role: undefined as unknown as UserRole, roles: [] });
    // Positive control: the same paths open as soon as the user has a role.
    expect(
      canAccessPath(true, anAuthUser({ role: 'attendee', roles: ['attendee'] }), '/dashboard')
    ).toBe(true);
    expect(canAccessPath(true, roleless, '/dashboard')).toBe(false);
    expect(canAccessPath(true, roleless, '/claim-profile')).toBe(false);
  });
});
