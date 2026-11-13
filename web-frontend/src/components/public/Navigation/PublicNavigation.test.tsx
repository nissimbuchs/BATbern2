/**
 * PublicNavigation Component Tests
 * Story 4.1.2: Public Layout & Navigation
 *
 * On the real AuthContext (`renderWithAuth`) and the MSW harness: the "What's new" dot reads the
 * user's settings and claim candidates over the network (story 2 of 2026-11-13).
 */

import { describe, it, expect, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PublicNavigation } from './PublicNavigation';
import { anAuthState, anAuthUser, renderWithAuth, signedOut } from '@/test/auth';
import { api } from '@/test/msw/api';
import { aUser, aUserSettings } from '@/test/fixtures';
import type { UserRole } from '@/types/auth';

const renderNav = (auth = signedOut()) => renderWithAuth(<PublicNavigation />, { auth });

const authenticatedAs = (roles: UserRole[]) =>
  anAuthState(anAuthUser({ role: roles[0], roles, email: 'test@example.com' }));

describe('PublicNavigation', () => {
  it('should render logo', () => {
    renderNav();

    expect(screen.getByAltText('BATbern')).toBeInTheDocument();
  });

  it('should render navigation links', () => {
    renderNav();

    expect(screen.getAllByText('Home').length).toBeGreaterThan(0);
    expect(screen.getAllByText('About').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Past Events').length).toBeGreaterThan(0);
  });

  it('should show Login button when not authenticated', () => {
    renderNav();

    // Should show Login button
    const loginButtons = screen.getAllByText('Login');
    expect(loginButtons.length).toBeGreaterThan(0);
  });

  it('should show Go to Portal button when authenticated', () => {
    renderNav(authenticatedAs(['organizer']));

    // Should show Go to Portal button
    const portalButtons = screen.getAllByText('Go to Portal');
    expect(portalButtons.length).toBeGreaterThan(0);
  });

  // 2026-06-05: "My Profile" must be visible to EVERY authenticated user (the
  // role-neutral /profile page serves all roles since Story 12.11), not just speakers.
  describe('My Profile dropdown entry', () => {
    it.each<[UserRole]>([['attendee'], ['speaker'], ['organizer'], ['partner']])(
      'should_showMyProfileLinkingToProfile_when_%sOpensUserDropdown',
      async (role) => {
        const user = userEvent.setup();
        renderNav(authenticatedAs([role]));

        await user.click(screen.getByTestId('public-nav-user-menu'));

        const profileLink = await screen.findByRole('link', { name: /my profile/i });
        expect(profileLink).toHaveAttribute('href', '/profile');
      }
    );

    // 2026-11-13 owner: opening the user menu also opened the language picker. On open the
    // menu moved focus to its first focusable control; Safari skips links, so that was the
    // language <select>, and Safari on touch devices opens a focused <select>.
    it('does not move focus to a control inside the user menu when it opens', async () => {
      const user = userEvent.setup();
      renderNav(authenticatedAs(['attendee']));

      await user.click(screen.getByTestId('public-nav-user-menu'));

      const profileLink = await screen.findByRole('link', { name: /my profile/i });
      const menu = profileLink.closest('[role="dialog"]') as HTMLElement;
      const languageSelector = within(menu).getByTestId('language-selector');
      // Positive control: the menu is open and its controls are rendered.
      expect(languageSelector).toBeVisible();
      const controls = Array.from(menu.querySelectorAll('a, button, select, input'));
      expect(controls.length).toBeGreaterThan(2);
      expect(controls).not.toContain(document.activeElement);
      // Focus is on the menu itself, so keyboard users continue from there.
      expect(document.activeElement).toBe(menu);
    });

    // Story 7.6: "My Events" attendee-dashboard link gating.
    it('shows the "My Events" link for an attendee', () => {
      renderNav(authenticatedAs(['attendee']));
      expect(screen.getByTestId('public-nav-my-events')).toHaveAttribute(
        'href',
        '/attendee/dashboard'
      );
    });

    it('hides "My Events" for a non-attendee (organizer-only)', () => {
      renderNav(authenticatedAs(['organizer']));
      expect(screen.queryByTestId('public-nav-my-events')).not.toBeInTheDocument();
    });

    it('shows BOTH "My Sessions" and "My Events" for a speaker+attendee', () => {
      renderNav(authenticatedAs(['speaker', 'attendee']));
      expect(screen.getByTestId('public-nav-my-sessions')).toBeInTheDocument();
      expect(screen.getByTestId('public-nav-my-events')).toBeInTheDocument();
    });
  });

  // Story 2 of 2026-11-13: "What's new" for every logged-in user, whatever the role.
  describe('What\u2019s new', () => {
    const settingsSeenAt = (whatsNewSeenAt?: string) =>
      api.on('getCurrentUser', () =>
        aUser({ settings: aUserSettings(whatsNewSeenAt ? { whatsNewSeenAt } : {}) })
      );

    it.each<[UserRole]>([['attendee'], ['speaker'], ['organizer'], ['partner']])(
      'should_showTheDotAndTheMenuItem_when_%sHasNotSeenTheLatest',
      async (role) => {
        settingsSeenAt(undefined);
        const user = userEvent.setup();
        renderNav(authenticatedAs([role]));

        expect(await screen.findByTestId('public-nav-whats-new-dot')).toBeInTheDocument();
        await user.click(screen.getByTestId('public-nav-user-menu'));
        expect(await screen.findByTestId('public-nav-whats-new')).toHaveAttribute(
          'href',
          '/attendee/dashboard#whats-new'
        );
      }
    );

    it('should_showTheMenuItemButNoDot_when_upToDate', async () => {
      settingsSeenAt(new Date().toISOString());
      const user = userEvent.setup();
      const { queryClient } = renderNav(authenticatedAs(['attendee']));

      await user.click(screen.getByTestId('public-nav-user-menu'));
      expect(await screen.findByTestId('public-nav-whats-new')).toBeInTheDocument();
      // Both answers are in, and nothing is new.
      await vi.waitFor(() => {
        expect(queryClient.getQueryState(['userProfile'])?.status).toBe('success');
        expect(queryClient.getQueryState(['users', 'me', 'profile-claims'])?.status).toBe(
          'success'
        );
      });
      expect(screen.queryByTestId('public-nav-whats-new-dot')).not.toBeInTheDocument();
    });

    it('should_showNoDotNoItemAndAskNothing_when_loggedOut', async () => {
      renderNav();

      expect(screen.getAllByText('Login').length).toBeGreaterThan(0);
      expect(screen.queryByTestId('public-nav-whats-new-dot')).not.toBeInTheDocument();
      expect(screen.queryByTestId('public-nav-whats-new')).not.toBeInTheDocument();
      expect(screen.queryByTestId('public-nav-mobile-whats-new')).not.toBeInTheDocument();
      expect(api.calls('getCurrentUser')).toHaveLength(0);
      expect(api.calls('listProfileClaimCandidates')).toHaveLength(0);
    });
  });
});
