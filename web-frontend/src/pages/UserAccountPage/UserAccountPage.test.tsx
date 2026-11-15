/**
 * UserAccountPage: the page container with its Profile and Settings tabs (Story 2.6).
 *
 * Runs the real page, its real tabs, `useUserProfile` and the user services on the MSW harness:
 * `GET /users/me` is checked against users-api and answered with a typed body; every other call of
 * the tabs answers with its spec example. Auth comes from the real `AuthContext`
 * (`renderWithAuth`). No own-code mocks (C6 touch rule, #1222).
 */

import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, delay } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { api } from '@/test/msw/api';
import { renderWithAuth } from '@/test/auth';
import { aUser, aUserSettings } from '@/test/fixtures';
import UserAccountPage from './UserAccountPage';

const me = () =>
  aUser({
    id: 'anna.keller',
    firstName: 'Anna',
    lastName: 'Keller',
    roles: ['ORGANIZER', 'SPEAKER'],
    settings: aUserSettings(),
  });

function renderPage(initialEntry = '/account') {
  return renderWithAuth(
    <Routes>
      <Route path="/account/:tab?" element={<UserAccountPage />} />
    </Routes>,
    { initialEntries: [initialEntry] }
  );
}

const profileTab = () => screen.findByRole('tab', { name: /profile/i });
const settingsTab = () => screen.getByRole('tab', { name: /settings/i });

describe('UserAccountPage', () => {
  it('should_renderPageWithTabs_when_componentMounts', async () => {
    api.on('getCurrentUser', () => me());
    renderPage();

    expect(await profileTab()).toBeInTheDocument();
    expect(settingsTab()).toBeInTheDocument();
    expect(screen.getByRole('tablist')).toBeInTheDocument();
    // The page asks for the caller's own profile with everything the two tabs show.
    expect(api.calls('getCurrentUser')[0].query.get('include')).toBe(
      'company,roles,settings,activity'
    );
  });

  it('should_displayProfileTabByDefault_when_pageLoads', async () => {
    api.on('getCurrentUser', () => me());
    renderPage();

    expect(await profileTab()).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByTestId('user-profile-tab')).toBeInTheDocument();
    expect(screen.queryByTestId('notifications-subtab')).toBeNull();
  });

  it('should_openSettingsTab_when_deepLinkedToAccountSettings', async () => {
    // Tab state is URL-driven: /account/settings selects the Settings tab directly
    api.on('getCurrentUser', () => me());
    renderPage('/account/settings');

    await waitFor(() => expect(settingsTab()).toHaveAttribute('aria-selected', 'true'));
    expect(screen.getByTestId('notifications-subtab')).toBeInTheDocument();
    expect(screen.queryByTestId('user-profile-tab')).toBeNull();
  });

  it('should_switchToSettingsTab_and_back_when_tabsClicked', async () => {
    api.on('getCurrentUser', () => me());
    const user = userEvent.setup();
    renderPage();
    await profileTab();

    await user.click(settingsTab());
    await waitFor(() => expect(settingsTab()).toHaveAttribute('aria-selected', 'true'));
    expect(screen.getByTestId('notifications-subtab')).toBeInTheDocument();

    await user.click(await profileTab());
    await waitFor(async () => expect(await profileTab()).toHaveAttribute('aria-selected', 'true'));
    expect(screen.getByTestId('user-profile-tab')).toBeInTheDocument();
  });

  it('should_showLoadingState_when_dataIsLoading', async () => {
    api.on('getCurrentUser', async () => {
      await delay('infinite');
      return me();
    });
    renderPage();

    expect(await screen.findByTestId('loading-state')).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).toBeNull();
  });

  it('should_displayErrorState_when_dataFetchFails', async () => {
    api.on('getCurrentUser', () =>
      HttpResponse.json(
        { type: 'about:blank', title: 'Internal Server Error', status: 500 },
        { status: 500 }
      )
    );
    renderPage();

    expect(await screen.findByTestId('error-state')).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).toBeNull();
  });
});
