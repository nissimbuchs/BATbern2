/**
 * Navigation Config Tests — Multi-Role Support (Story 9.5, Task 9.1)
 * Tests getNavigationForRoles(), getGroupedNavigationForRoles(), deduplication
 */

import { describe, test, expect } from 'vitest';
import {
  getNavigationForRole,
  getNavigationForRoles,
  getGroupedNavigationForRoles,
  navigationConfig,
} from '../navigationConfig';

describe('navigationConfig — Multi-Role Functions (Story 9.5)', () => {
  describe('getNavigationForRoles()', () => {
    test('should return combined items for organizer+speaker', () => {
      const items = getNavigationForRoles(['organizer', 'speaker']);

      // Should include organizer-specific items
      expect(items.some((i) => i.path === '/organizer/events')).toBe(true);
      expect(items.some((i) => i.path === '/organizer/users')).toBe(true);

      // Should include speaker-specific items
      expect(items.some((i) => i.path === '/speaker/dashboard')).toBe(true);
      expect(items.some((i) => i.path === '/speaker/events')).toBe(true);
    });

    test('should deduplicate "Public Site" across roles', () => {
      const items = getNavigationForRoles(['organizer', 'speaker']);
      const publicSiteItems = items.filter((i) => i.path === '/');
      expect(publicSiteItems.length).toBe(1);
    });

    test('should return same items as getNavigationForRole for single role', () => {
      const singleRoleItems = getNavigationForRole('organizer');
      const multiRoleItems = getNavigationForRoles(['organizer']);
      expect(multiRoleItems.map((i) => i.path)).toEqual(singleRoleItems.map((i) => i.path));
    });

    test('should return empty array for empty roles', () => {
      const items = getNavigationForRoles([]);
      expect(items.length).toBe(0);
    });
  });

  describe('getGroupedNavigationForRoles()', () => {
    test('should return two groups for organizer+speaker', () => {
      const groups = getGroupedNavigationForRoles(['organizer', 'speaker']);
      expect(groups.length).toBe(2);
      expect(groups[0].role).toBe('organizer');
      expect(groups[1].role).toBe('speaker');
    });

    test('should have correct label keys', () => {
      const groups = getGroupedNavigationForRoles(['organizer', 'speaker']);
      expect(groups[0].labelKey).toBe('navigation.section.organizer');
      expect(groups[1].labelKey).toBe('navigation.section.speaker');
    });

    test('should contain role-specific items in each group', () => {
      const groups = getGroupedNavigationForRoles(['organizer', 'speaker']);
      const organizerPaths = groups[0].items.map((i) => i.path);
      const speakerPaths = groups[1].items.map((i) => i.path);

      expect(organizerPaths).toContain('/organizer/events');
      expect(speakerPaths).toContain('/speaker/dashboard');
    });

    test('should return single group for single role', () => {
      const groups = getGroupedNavigationForRoles(['attendee']);
      expect(groups.length).toBe(1);
      expect(groups[0].role).toBe('attendee');
    });
  });

  describe('Speaker Portal nav item — Story 11.E.3 (Resolved Q#4)', () => {
    test('should_notExist_when_speakerPortalLoginEntryDropped', () => {
      // Resolved Q#4 (PM 2026-05-17): drop the `/speaker-portal/login` nav entry AND the
      // `navigation.speakerPortal` i18n key. The cherry-pick from 73d94688 introduced both;
      // both were surgically reverted in Task 1.5 of Story 11.E.3.
      const speakerPortalItem = navigationConfig.find((i) => i.path === '/speaker-portal/login');
      expect(speakerPortalItem).toBeUndefined();
    });
  });

  describe('Shared-item deduplication across role sections — code review 2026-05-18 (D4)', () => {
    test('should_deduplicateAcrossSections_when_multipleRolesShareSameItem', () => {
      // Without dedup, an organizer+partner user sees "Public Site" (path="/") in both
      // role sections. The dedup is across the user's groups; the item lands in whichever
      // section comes first in the iteration order (organizer, here).
      const groups = getGroupedNavigationForRoles(['organizer', 'partner']);
      const organizerHasPublic = groups[0].items.some((i) => i.path === '/');
      const partnerHasPublic = groups[1].items.some((i) => i.path === '/');
      expect(organizerHasPublic).toBe(true);
      expect(partnerHasPublic).toBe(false);
    });

    test('should_retainShared_when_singleRole', () => {
      // Single-role users are unaffected by dedup.
      const groups = getGroupedNavigationForRoles(['attendee']);
      const attendeeHasPublic = groups[0].items.some((i) => i.path === '/');
      expect(attendeeHasPublic).toBe(true);
    });
  });
});
