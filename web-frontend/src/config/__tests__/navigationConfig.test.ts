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
      expect(items.some((i) => i.path === '/organizer/speakers')).toBe(true);

      // Should include speaker-specific items
      expect(items.some((i) => i.path === '/speaker/dashboard')).toBe(true);
      expect(items.some((i) => i.path === '/speaker/events')).toBe(true);
    });

    test('should deduplicate "Public Site" across roles', () => {
      const items = getNavigationForRoles(['organizer', 'speaker']);
      const publicSiteItems = items.filter((i) => i.path === '/');
      expect(publicSiteItems.length).toBe(1);
    });

    test('should include Speaker Portal item for speaker role', () => {
      const items = getNavigationForRoles(['speaker']);
      expect(items.some((i) => i.path === '/speaker-portal/login')).toBe(true);
    });

    test('should NOT include Speaker Portal for organizer-only user', () => {
      const items = getNavigationForRoles(['organizer']);
      expect(items.some((i) => i.path === '/speaker-portal/login')).toBe(false);
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

  describe('Speaker Portal nav item', () => {
    test('should exist in navigationConfig with correct properties', () => {
      const speakerPortalItem = navigationConfig.find((i) => i.path === '/speaker-portal/login');
      expect(speakerPortalItem).toBeDefined();
      expect(speakerPortalItem!.labelKey).toBe('navigation.speakerPortal');
      expect(speakerPortalItem!.roles).toEqual(['speaker']);
    });
  });
});
