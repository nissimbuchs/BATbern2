/**
 * AppHeader Component Tests
 * Story 1.17, Task 6a: TDD for role-adaptive application header
 *
 * On the real AuthContext (`renderWithAuth`), the real UI store, real i18next (en) and the MSW
 * harness; only the screen width (`window.matchMedia`, a browser boundary) and `useNavigate`
 * (react-router) are stubbed.
 */

import { describe, test, expect, beforeEach, afterEach, vi } from 'vitest';
import { screen, fireEvent, within } from '@testing-library/react';
import AppHeader from './AppHeader';
import { useUIStore } from '@/stores/uiStore';
import { anAuthState, anAuthUser, renderWithAuth } from '@/test/auth';
import { api } from '@/test/msw/api';
import { aUserSettings } from '@/test/fixtures';
import type { UserRole } from '@/types/auth';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

/** The window is `width` px wide: MUI's media queries answer from it. */
function setWidth(width: number) {
  window.matchMedia = ((query: string) => {
    const min = /min-width:\s*([\d.]+)px/.exec(query);
    const max = /max-width:\s*([\d.]+)px/.exec(query);
    const matches = (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]));
    return {
      matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    };
  }) as typeof window.matchMedia;
}

const originalMatchMedia = window.matchMedia;

const as = (role: UserRole) =>
  anAuthState(
    anAuthUser({
      userId: 'user-123',
      username: 'test.user',
      email: 'test@batbern.ch',
      role,
      roles: [role],
      companyId: 'company-123',
    })
  );

const renderHeader = (ui = <AppHeader />, role: UserRole = 'organizer') =>
  renderWithAuth(ui, { auth: as(role) });

describe('AppHeader Component', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    useUIStore.getState().reset();
    setWidth(1500);
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  describe('Basic Rendering', () => {
    test('should_renderMaterialUIAppBar_when_headerMounted', () => {
      renderHeader();

      expect(screen.getByRole('banner')).toBeInTheDocument();
    });

    test('should_renderBATbernLogo_when_headerMounted', () => {
      renderHeader();

      expect(screen.getByRole('img', { name: /batbern/i })).toBeInTheDocument();
    });

    test('should_renderNavigationMenu_when_headerMounted', () => {
      renderHeader();

      expect(screen.getByRole('navigation')).toBeInTheDocument();
    });
  });

  describe('Role-Based Navigation', () => {
    test('should_renderOrganizerNavigation_when_userRoleIsOrganizer', () => {
      renderHeader();

      // Organizer should see Events, Partners, Companies menu items
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/partners/i)[0]).toBeInTheDocument();
      expect(screen.getByText(/companies/i)).toBeInTheDocument();
    });

    test('should_renderEmptyNavigation_when_userRoleIsSpeaker', () => {
      // 2026-05-20 (Q#1b / Q#3) — SPEAKER role has no admin-nav entries. Speakers
      // live in the public site and reach their dashboard via the public navigation.
      renderHeader(<AppHeader />, 'speaker');

      expect(screen.queryByText(/^my events$/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/^my content$/i)).not.toBeInTheDocument();
    });

    test('should_renderPartnerNavigation_when_userRoleIsPartner', () => {
      renderHeader(<AppHeader />, 'partner');

      // Partner should see My Company and Topics
      expect(screen.getAllByText(/my company/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/topic/i)[0]).toBeInTheDocument();
    });

    test('should_renderEmptyNavigation_when_userRoleIsAttendee', () => {
      // 2026-05-20 (Q#1b / Q#3) — ATTENDEE role has no admin-nav entries either.
      renderHeader(<AppHeader />, 'attendee');

      expect(screen.queryByText(/my registrations/i)).not.toBeInTheDocument();
    });
  });

  describe('Notification Bell', () => {
    test('should_renderNotificationBell_when_headerMounted', () => {
      renderHeader();

      expect(screen.getByLabelText(/notifications/i)).toBeInTheDocument();
    });

    test('should_showUnreadBadge_when_unreadNotificationsExist', () => {
      renderHeader(
        <AppHeader
          notifications={{
            data: [],
            pagination: { page: 1, limit: 20, totalItems: 0, totalPages: 0 },
            unreadCount: 3,
          }}
        />
      );

      expect(screen.getByText('3')).toBeInTheDocument();
    });

    test('should_navigateToNotifications_when_bellClicked', () => {
      renderHeader();

      fireEvent.click(screen.getByLabelText(/notifications/i));

      expect(mockNavigate).toHaveBeenCalledWith('/organizer/notifications');
    });
  });

  describe('User Menu', () => {
    test('should_renderUserAvatar_when_headerMounted', () => {
      renderHeader();

      expect(screen.getByLabelText(/user menu|profile/i)).toBeInTheDocument();
    });

    test('should_openUserMenu_when_avatarClicked', () => {
      renderHeader();

      fireEvent.click(screen.getByLabelText(/user menu|profile/i));

      expect(useUIStore.getState().userMenuOpen).toBe(true);
      expect(screen.getByTestId('whats-new-link')).toBeInTheDocument();
    });
  });

  // #1198: between 1200 and 1440 px the labelled bar did not fit; icons with names instead.
  describe('Navigation width', () => {
    test('should_showIconsOnly_when_desktopNarrowerThan1440', () => {
      setWidth(1300);

      renderHeader();

      const events = screen.getByTestId('nav-organizer-events');
      expect(events).toHaveAccessibleName('Events');
      expect(within(events).queryByText('Events')).not.toBeInTheDocument();
    });

    test('should_showLabels_when_1440OrWider', () => {
      setWidth(1500);

      renderHeader();

      expect(
        within(screen.getByTestId('nav-organizer-events')).getByText('Events')
      ).toBeInTheDocument();
    });
  });

  describe('Mobile Responsive', () => {
    test('should_renderHamburgerMenu_when_mobile', () => {
      setWidth(600);

      renderHeader();

      expect(screen.getByLabelText('Open menu')).toBeInTheDocument();
    });

    test('should_hideDesktopNavigation_when_mobile', () => {
      setWidth(600);

      renderHeader();

      expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    });
  });

  // Story 2 of 2026-11-13: the dot on the avatar reads the stored "seen" time over the network.
  describe('What’s new dot', () => {
    test('should_showTheDot_when_theOrganizerHasNotSeenTheLatest', async () => {
      api.on('getUserSettings', () => aUserSettings());
      renderHeader();

      await vi.waitFor(() =>
        expect(screen.getByTestId('user-menu-whats-new-dot')).toHaveAttribute(
          'data-visible',
          'true'
        )
      );
    });

    test('should_notShowTheDot_when_upToDate', async () => {
      api.on('getUserSettings', () => aUserSettings({ whatsNewSeenAt: new Date().toISOString() }));
      const { queryClient } = renderHeader();

      await vi.waitFor(() =>
        expect(queryClient.getQueryState(['users', 'me', 'settings', 'whatsNew'])?.status).toBe(
          'success'
        )
      );
      expect(screen.getByTestId('user-menu-whats-new-dot')).toHaveAttribute(
        'data-visible',
        'false'
      );
    });
  });
});
