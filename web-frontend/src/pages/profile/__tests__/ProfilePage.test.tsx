/**
 * Story 12.11 (AC5 + AC6 page-side): role-neutral /profile page with tabs.
 *
 * Story 4.10 moved this suite onto the MSW harness (touch rule): the real services, hooks and
 * child components run; only the network is answered, checked against the specs. The profile is
 * read at `GET /users/me` and written at `PATCH /users/{username}` with the hydrated username;
 * the newsletter state is EMS `/newsletter/my-subscriptions` (#1244).
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { aUser } from '@/test/fixtures';
import { anAuthState, anAuthUser, renderWithAuth } from '@/test/auth';
import type { components } from '@/types/generated/user-api.types';
import ProfilePage from '../ProfilePage';

type UserResponse = components['schemas']['UserResponse'];

const USERNAME = 'matthias.keller.2';

function profile(overrides: Partial<UserResponse> = {}): UserResponse {
  return aUser({
    id: USERNAME,
    email: 'Matthias.Keller@example.com',
    firstName: 'Matthias',
    lastName: 'Keller',
    bio: 'I attend things.',
    companyId: 'elca',
    company: { id: 'elca', name: 'elca', displayName: 'ELCA', industry: null, website: null },
    roles: ['ATTENDEE'],
    termsAcceptedAt: '2026-01-15T10:00:00Z',
    ...overrides,
  });
}

/** The PATCH answer as CUMS sends it: `companyId`, but no expanded `company` (#811). */
const patchAnswer = (stored: UserResponse) => ({ ...stored, company: null });

function renderPage(initialEntry = '/profile', refreshUser = vi.fn().mockResolvedValue(undefined)) {
  const auth = anAuthState(anAuthUser({ role: 'attendee', username: USERNAME }), { refreshUser });
  const result = renderWithAuth(
    <Routes>
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/dashboard" element={<div data-testid="dashboard-stub" />} />
    </Routes>,
    { auth, initialEntries: [initialEntry] }
  );
  return { ...result, refreshUser };
}

describe('ProfilePage — Story 12.11 role-neutral profile with tabs', () => {
  beforeEach(() => {
    api.on('getCurrentUser', () => profile());
    api.on('getCompany', () => ({
      name: 'elca',
      displayName: 'ELCA',
      isVerified: true,
      createdAt: '2026-01-15T10:00:00Z',
      updatedAt: '2026-01-15T10:00:00Z',
      createdBy: 'anna.keller',
    }));
    api.on('getMyNewsletterSubscriptions', () => ({
      addresses: [
        { email: 'matthias.keller@example.com', primary: true, subscribed: false, language: null },
      ],
    }));
  });

  it('should_renderBothTabs_when_nonSpeakerRoleLoadsPage', async () => {
    renderPage();

    expect(await screen.findByTestId('profile-tab')).toBeInTheDocument();
    expect(screen.getByTestId('consent-tab')).toBeInTheDocument();
  });

  it('should_renderCompanyField_when_profileTabActive', async () => {
    renderPage();

    expect(await screen.findByText('Company')).toBeInTheDocument();
  });

  it('should_patchOwnUsernameWithCompanyId_when_profileSaved', async () => {
    api.on('patchUser', ({ body }) => patchAnswer(profile({ firstName: body?.firstName })));
    const user = userEvent.setup();

    renderPage();

    const firstNameInput = await screen.findByLabelText('First Name');
    await user.clear(firstNameInput);
    await user.type(firstNameInput, 'Hông Ân');
    await user.click(screen.getByText('Save Changes'));

    await waitFor(() => expect(api.calls('patchUser')).toHaveLength(1));
    const [call] = api.calls('patchUser');
    // Story 4.10: the hydrated username from GET /users/me, not one from the token.
    expect(call.params).toEqual({ username: USERNAME });
    expect(call.body).toMatchObject({ firstName: 'Hông Ân', companyId: 'elca' });
  });

  it('should_keepCompanyDisplayName_when_profileSavedWithSelectedCompany', async () => {
    // #811: the company chip shows the display name on load but reverted to the slug after
    // Save. The PATCH answer carries `companyId` but no expanded `company` (only GET
    // ?include=company expands it), so the post-save re-sync fell back to the slug.
    api.on('patchUser', ({ body }) => patchAnswer(profile({ firstName: body?.firstName })));
    const user = userEvent.setup();

    renderPage();

    const chip = await screen.findByTestId('registration-company-chip');
    expect(chip).toHaveTextContent('ELCA');

    const firstNameInput = await screen.findByLabelText('First Name');
    await user.clear(firstNameInput);
    await user.type(firstNameInput, 'Changed');
    await user.click(screen.getByText('Save Changes'));

    await waitFor(() => expect(api.calls('patchUser')).toHaveLength(1));

    // After save the chip must still show the display name, never the slug.
    await waitFor(() => {
      expect(screen.getByTestId('registration-company-chip')).toHaveTextContent('ELCA');
    });
    expect(screen.getByTestId('registration-company-chip')).not.toHaveTextContent('elca');
  });

  it('should_showConsentCheckboxWithLinks_when_termsNotAccepted', async () => {
    api.on('getCurrentUser', () => profile({ termsAcceptedAt: null }));
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByTestId('consent-tab'));

    expect(screen.getByTestId('consent-checkbox')).toBeInTheDocument();
    // Scope to the consent card — PublicLayout's footer carries its own policy links.
    const consentSection = within(screen.getByTestId('consent-section'));
    expect(consentSection.getByText('Terms of Service')).toHaveAttribute('href', '/terms');
    expect(consentSection.getByText('Privacy Policy')).toHaveAttribute('href', '/privacy');
    expect(screen.queryByTestId('consent-accepted-on')).not.toBeInTheDocument();
  });

  it('should_showAcceptedOnLineWithoutCheckbox_when_termsAlreadyAccepted', async () => {
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByTestId('consent-tab'));

    expect(screen.getByTestId('consent-accepted-on')).toBeInTheDocument();
    expect(screen.queryByTestId('consent-checkbox')).not.toBeInTheDocument();
  });

  it('should_saveWriteOnceConsentOnOwnUsernameAndRefreshUser_when_consentSaved', async () => {
    api.on('getCurrentUser', () => profile({ termsAcceptedAt: null }));
    api.on('patchUser', () => patchAnswer(profile({ termsAcceptedAt: '2026-06-04T18:00:00Z' })));
    const user = userEvent.setup();

    const { refreshUser } = renderPage();

    await user.click(await screen.findByTestId('consent-tab'));
    const saveButton = screen.getByTestId('consent-save-button');
    expect(saveButton).toBeDisabled(); // checkbox not ticked yet

    await user.click(screen.getByTestId('consent-checkbox'));
    await user.click(saveButton);

    await waitFor(() => expect(api.calls('patchUser')).toHaveLength(1));
    expect(api.calls('patchUser')[0].params).toEqual({ username: USERNAME });
    expect(api.calls('patchUser')[0].body).toEqual({ termsAccepted: true });
    await waitFor(() => {
      // The PATCH answer's termsAcceptedAt is passed as a server-authoritative override so the
      // gate lifts even when the refresh GET transiently fails (hydrateUserFromDb fails open).
      expect(refreshUser).toHaveBeenCalledWith({ termsAcceptedAt: '2026-06-04T18:00:00Z' });
    });
  });

  it('should_preselectConsentTabAndShowNotice_when_onboardingParamSet', async () => {
    api.on('getCurrentUser', () => profile({ termsAcceptedAt: null }));

    renderPage('/profile?onboarding=1');

    // Consent tab content visible without clicking
    expect(await screen.findByTestId('consent-checkbox')).toBeInTheDocument();
    expect(screen.getByTestId('onboarding-notice')).toBeInTheDocument();
  });

  it('should_navigateToDashboard_when_onboardingConsentSaved', async () => {
    api.on('getCurrentUser', () => profile({ termsAcceptedAt: null }));
    api.on('patchUser', () => patchAnswer(profile({ termsAcceptedAt: '2026-06-04T18:00:00Z' })));
    const user = userEvent.setup();

    renderPage('/profile?onboarding=1');

    await user.click(await screen.findByTestId('consent-checkbox'));
    await user.click(screen.getByTestId('consent-save-button'));

    await waitFor(() => {
      expect(screen.getByTestId('dashboard-stub')).toBeInTheDocument();
    });
  });

  it('should_subscribeOneAddress_when_newsletterCheckboxClicked', async () => {
    api.on('patchMyNewsletterSubscriptions', () => ({
      addresses: [
        { email: 'matthias.keller@example.com', primary: true, subscribed: true, language: 'en' },
      ],
    }));
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByTestId('consent-tab'));
    await user.click(await screen.findByTestId('newsletter-address-matthias.keller@example.com'));

    await waitFor(() => expect(api.calls('patchMyNewsletterSubscriptions')).toHaveLength(1));
    // #1244: EMS newsletter per address — NOT a user profile field
    expect(api.calls('patchMyNewsletterSubscriptions')[0].body).toEqual({
      email: 'matthias.keller@example.com',
      subscribed: true,
      language: 'en',
    });
    expect(api.calls('patchUser')).toHaveLength(0);
  });

  it('should_showEmailAddresses_when_emailsTabOpened', async () => {
    api.on('getCurrentUser', () =>
      profile({
        additionalEmails: [
          {
            id: '3b1f6c2e-8a4d-4f5e-9c7b-1a2b3c4d5e6f',
            email: 'private@example.com',
            label: null,
            createdAt: '2026-11-01T10:00:00Z',
            verifiedAt: '2026-11-01T10:05:00Z',
            receiveEventMail: false,
          },
        ],
      })
    );
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByTestId('emails-tab'));

    const section = await screen.findByTestId('email-addresses-section');
    expect(within(section).getByText('Matthias.Keller@example.com')).toBeInTheDocument();
    expect(within(section).getByText('private@example.com')).toBeInTheDocument();
  });

  it('should_renderErrorUi_when_profileFetchFails', async () => {
    api.on('getCurrentUser', () =>
      HttpResponse.json({ status: 403, message: 'Not authorized' }, { status: 403 })
    );

    renderPage();

    await waitFor(() => expect(api.calls('getCurrentUser').length).toBeGreaterThan(0));
    expect(await screen.findByRole('heading', { level: 1, name: 'Error' })).toBeInTheDocument();
    expect(screen.queryByLabelText('First Name')).not.toBeInTheDocument();
  });
});
