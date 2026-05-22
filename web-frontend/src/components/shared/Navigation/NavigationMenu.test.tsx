/**
 * NavigationMenu Component Tests
 * Story 1.17, Task 6a: TDD for role-adaptive navigation menu
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { NavigationMenu } from './NavigationMenu';

// Mock i18next with actual translations
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'navigation.dashboard': 'Dashboard',
        'navigation.events': 'Events',
        'navigation.speakers': 'Speakers',
        'navigation.partners': 'Partners',
        'navigation.partnerTopics': 'Partner Topics',
        'navigation.partnerMeetings': 'Partner Meetings',
        'navigation.analytics': 'Analytics',
        'navigation.content': 'Content',
        'navigation.profile': 'Profile',
        'navigation.myEvents': 'My Events',
        'navigation.myContent': 'My Content',
        'navigation.myRegistrations': 'My Registrations',
        'navigation.myCompany': 'My Company',
        'navigation.topics': 'Topics',
        'navigation.mainNav': 'main navigation',
        'navigation.companies': 'Companies',
        'navigation.users': 'Users',
        'navigation.newsletterSubscribers': 'Newsletter Subscribers',
        'navigation.publicSite': 'Public Site',
        'events:navigation.dashboard': 'Events',
      };
      return translations[key] || key;
    },
    i18n: {
      language: 'en',
    },
  }),
}));

describe('NavigationMenu Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderWithRouter = (component: React.ReactElement) => {
    return render(<BrowserRouter>{component}</BrowserRouter>);
  };

  describe('Organizer Navigation', () => {
    test('should_renderOrganizerMenuItems_when_roleIsOrganizer', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      // Organizer should see: Events, Partners, Companies, Users, Newsletter Subscribers, Analytics
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/partners/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/analytics/i)[0]).toBeInTheDocument();
      expect(screen.getByText(/companies/i)).toBeInTheDocument();
      expect(screen.getByText(/users/i)).toBeInTheDocument();
    });

    test('should_linkToEventsManagement_when_eventsClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const eventsLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(eventsLink).toHaveAttribute('href', '/organizer/events');
    });

    test('should_linkToUsersManagement_when_usersClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const usersLink = screen.getByText(/users/i).closest('a');
      expect(usersLink).toHaveAttribute('href', '/organizer/users');
    });

    test('should_showPartnersDropdown_when_partnersClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      // Partners is now a dropdown group button (queried by testid to avoid JSDOM block-in-inline issues)
      const partnersButton = screen.getByTestId('nav-group-organizer-partners');
      expect(partnersButton).toBeInTheDocument();
      expect(partnersButton).toHaveAttribute('aria-haspopup', 'true');
      expect(partnersButton).toHaveAttribute('aria-expanded', 'false');

      // After clicking, aria-expanded becomes true (dropdown opens)
      fireEvent.click(partnersButton);
      expect(partnersButton).toHaveAttribute('aria-expanded', 'true');
    });
  });

  // 2026-05-20 (Q#3 / Q#1b) — SPEAKER admin-nav entries removed. Speakers live in the
  // public site (their dashboard at /speaker-portal/dashboard, profile at
  // /speaker-portal/profile) and the public nav surfaces "My Sessions" + "My Profile".
  // The role-based admin NavigationMenu therefore renders nothing for a speaker-only
  // user; AppHeader hides the role-selector chip too (RoleSelector filter on
  // getRolesWithNavEntries).
  describe('Speaker Navigation', () => {
    test('should_renderEmptyMenu_when_roleIsSpeaker', () => {
      renderWithRouter(<NavigationMenu userRoles={['speaker']} />);

      // No admin-nav items for speakers — neither the old My Events / My Content nor
      // Partners (the latter never applied) should appear.
      expect(screen.queryByText(/^my events$/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/^my content$/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/^partners$/i)).not.toBeInTheDocument();
    });
  });

  describe('Partner Navigation', () => {
    test('should_renderPartnerMenuItems_when_roleIsPartner', () => {
      renderWithRouter(<NavigationMenu userRoles={['partner']} />);

      // Partner should see: My Company, Topics, and public site
      expect(screen.getAllByText(/my company/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/topics/i)[0]).toBeInTheDocument();
    });

    test('should_notShowSpeakersMenu_when_roleIsPartner', () => {
      renderWithRouter(<NavigationMenu userRoles={['partner']} />);

      // Partner should NOT see Speakers menu
      expect(screen.queryByText(/speakers/i)).not.toBeInTheDocument();
    });

    test('should_linkToPartnerTopics_when_topicsClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['partner']} />);

      const topicsLink = screen.getAllByText(/topics/i)[0].closest('a');
      expect(topicsLink).toHaveAttribute('href', '/partners/topics');
    });
  });

  // 2026-05-20 (Q#3 / Q#1b) — ATTENDEE admin-nav entries removed, same reasoning as
  // the SPEAKER block above. Attendees consume the public website (event browse +
  // registration + archive), not an admin-app surface.
  describe('Attendee Navigation', () => {
    test('should_renderEmptyMenu_when_roleIsAttendee', () => {
      renderWithRouter(<NavigationMenu userRoles={['attendee']} />);

      expect(screen.queryByText(/my registrations/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/analytics/i)).not.toBeInTheDocument();
    });
  });

  describe('Active State', () => {
    test('should_highlightActiveMenuItem_when_onCurrentPage', () => {
      // Mock current location
      window.history.pushState({}, '', '/organizer/events');

      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const eventsLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(eventsLink).toHaveClass(/active|selected/i);
    });

    test('should_notHighlightInactiveMenuItems_when_onDifferentPage', () => {
      // Mock current location
      window.history.pushState({}, '', '/organizer/events');

      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const usersLink = screen.getByText(/users/i).closest('a');
      expect(usersLink).not.toHaveClass(/active|selected/i);
    });
  });

  describe('Accessibility', () => {
    test('should_haveProperAriaLabels_when_rendered', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const navigation = screen.getByRole('navigation');
      expect(navigation).toHaveAttribute('aria-label', 'main navigation');
    });

    test('should_supportKeyboardNavigation_when_rendered', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const firstLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(firstLink).toHaveAttribute('tabindex', '0');
    });
  });

  describe('Multi-Role Navigation', () => {
    test('should_showBothOrganizerAndPartnerItems_when_rolesIncludeBoth', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer', 'partner']} />);

      // Organizer items present
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/partners/i)[0]).toBeInTheDocument();
      // Partner items also present
      expect(screen.getAllByText(/my company/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/topics/i)[0]).toBeInTheDocument();
    });

    test('should_renderRoleSectionsWithDividersAndHeaders_when_multiRole', () => {
      // Story 11.E.3 (cherry-pick 73d94688 / Story 9.5): each role's items appear in its
      // own section, separated by a divider; vertical variant also gets per-section overline
      // headers (horizontal variant uses dividers only — header pollutes the toolbar layout).
      renderWithRouter(<NavigationMenu userRoles={['organizer', 'partner']} variant="vertical" />);

      // The grouped <nav> root carries the regression marker (single-role path lacks it).
      expect(screen.getByTestId('navigation-menu-grouped')).toBeInTheDocument();

      // Section list landmarks land per role.
      expect(screen.getByTestId('nav-section-organizer')).toBeInTheDocument();
      expect(screen.getByTestId('nav-section-partner')).toBeInTheDocument();

      // A divider appears between roles (the second role gets a divider; the first doesn't).
      expect(screen.queryByTestId('nav-section-divider-organizer')).not.toBeInTheDocument();
      expect(screen.getByTestId('nav-section-divider-partner')).toBeInTheDocument();

      // Vertical layout renders the overline section header (translated via i18n).
      expect(screen.getByTestId('nav-section-header-organizer')).toBeInTheDocument();
      expect(screen.getByTestId('nav-section-header-partner')).toBeInTheDocument();
    });

    test('should_deduplicateSharedItems_when_multipleRolesHaveSameItem', () => {
      // Code review 2026-05-18 (D4): shared items like "Public Site" exist in multiple
      // roles' nav configs. Without dedup a dual-role user would see "Public Site" twice
      // (once per section), which (a) clutters the menu and (b) creates duplicate accessible
      // names for screen readers (WCAG 2.4.4). getGroupedNavigationForRoles dedups across
      // the user's groups; the item ends up in whichever role-section appears first in the
      // iteration order.
      renderWithRouter(<NavigationMenu userRoles={['organizer', 'partner']} />);

      const publicLinks = screen.queryAllByRole('link', { name: /public site/i });
      expect(publicLinks).toHaveLength(1);
    });
  });

  describe('Icons', () => {
    test('should_displayIconsForMenuItems_when_rendered', () => {
      const { container } = renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      // Should have Material-UI icons for each menu item
      const icons = container.querySelectorAll('svg');
      expect(icons.length).toBeGreaterThan(0);
    });
  });
});
