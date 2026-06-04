/**
 * Story 12.11 (AC5 + AC6 page-side): role-neutral /profile page with two tabs.
 *
 * Supersedes the Story 11.F.1 ProfileUpdatePage suite (this file was git-mv'd with
 * the page). Profile editing still uses the CUMS /users/me endpoints (Story 11.E.3
 * D1); newsletter state uses the EMS newsletter_subscribers self-service hooks
 * (Story 10.7 — Scope Revision #1 of Story 12.11).
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('@/services/api/userAccountApi', () => ({
  getUserProfile: vi.fn(),
  updateUserProfile: vi.fn(),
  uploadProfilePicture: vi.fn(),
}));

vi.mock('@/services/newsletterService', () => ({
  getMySubscription: vi.fn(),
  patchMySubscription: vi.fn(),
}));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}));

// The photo uploader spins up its own API flows — out of scope here.
vi.mock('@/components/speaker-portal/ProfilePhotoUpload', () => ({
  default: () => <div data-testid="photo-upload-stub" />,
}));

import { getUserProfile, updateUserProfile } from '@/services/api/userAccountApi';
import { getMySubscription, patchMySubscription } from '@/services/newsletterService';
import { useAuth } from '@/hooks/useAuth';
import ProfilePage from '../ProfilePage';

const mockedGetProfile = vi.mocked(getUserProfile);
const mockedUpdateProfile = vi.mocked(updateUserProfile);
const mockedGetMySubscription = vi.mocked(getMySubscription);
const mockedPatchMySubscription = vi.mocked(patchMySubscription);
const mockedUseAuth = vi.mocked(useAuth);

const refreshUser = vi.fn();

function renderPage(initialEntry = '/profile') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/dashboard" element={<div data-testid="dashboard-stub" />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

function profileFixture(overrides: Record<string, unknown> = {}) {
  return {
    user: {
      id: 'test.attendee',
      username: 'test.attendee',
      email: 'attendee@example.com',
      firstName: 'Test',
      lastName: 'Attendee',
      bio: 'I attend things.',
      companyId: 'elca',
      profilePictureUrl: null,
      roles: ['ATTENDEE'],
      termsAcceptedAt: '2026-01-15T10:00:00Z',
      ...overrides,
    },
  };
}

describe('ProfilePage — Story 12.11 role-neutral profile with tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    refreshUser.mockResolvedValue(undefined);
    mockedUseAuth.mockReturnValue({
      user: { username: 'test.attendee', roles: ['attendee'] },
      isAuthenticated: true,
      isLoading: false,
      refreshUser,
    } as never);
    mockedGetMySubscription.mockResolvedValue({ subscribed: false });
  });

  it('should_renderBothTabs_when_nonSpeakerRoleLoadsPage', async () => {
    mockedGetProfile.mockResolvedValue(profileFixture() as never);

    renderPage();

    expect(await screen.findByTestId('profile-tab')).toBeInTheDocument();
    expect(screen.getByTestId('consent-tab')).toBeInTheDocument();
  });

  it('should_renderCompanyField_when_profileTabActive', async () => {
    mockedGetProfile.mockResolvedValue(profileFixture() as never);

    renderPage();

    expect(await screen.findByText('Company')).toBeInTheDocument();
  });

  it('should_includeCompanyId_when_profileSaved', async () => {
    mockedGetProfile.mockResolvedValue(profileFixture() as never);
    mockedUpdateProfile.mockResolvedValue(profileFixture().user as never);
    const user = userEvent.setup();

    renderPage();

    const firstNameInput = await screen.findByLabelText('First Name');
    await user.clear(firstNameInput);
    await user.type(firstNameInput, 'Changed');
    await user.click(screen.getByText('Save Changes'));

    await waitFor(() => {
      expect(mockedUpdateProfile).toHaveBeenCalledWith(
        expect.objectContaining({ firstName: 'Changed', companyId: 'elca' })
      );
    });
  });

  it('should_showConsentCheckboxWithLinks_when_termsNotAccepted', async () => {
    mockedGetProfile.mockResolvedValue(profileFixture({ termsAcceptedAt: null }) as never);
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
    mockedGetProfile.mockResolvedValue(profileFixture() as never);
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByTestId('consent-tab'));

    expect(screen.getByTestId('consent-accepted-on')).toBeInTheDocument();
    expect(screen.queryByTestId('consent-checkbox')).not.toBeInTheDocument();
  });

  it('should_saveWriteOnceConsentAndRefreshUser_when_consentSaved', async () => {
    mockedGetProfile.mockResolvedValue(profileFixture({ termsAcceptedAt: null }) as never);
    mockedUpdateProfile.mockResolvedValue(
      profileFixture({ termsAcceptedAt: '2026-06-04T18:00:00Z' }).user as never
    );
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByTestId('consent-tab'));
    const saveButton = screen.getByTestId('consent-save-button');
    expect(saveButton).toBeDisabled(); // checkbox not ticked yet

    await user.click(screen.getByTestId('consent-checkbox'));
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockedUpdateProfile).toHaveBeenCalledWith({ termsAccepted: true });
    });
    await waitFor(() => {
      // Review patch (2026-06-04): the PUT response's termsAcceptedAt is passed as a
      // server-authoritative override so the gate lifts even when the refresh GET
      // transiently fails (hydrateUserFromDb fails open with the stale null).
      expect(refreshUser).toHaveBeenCalledWith({ termsAcceptedAt: '2026-06-04T18:00:00Z' });
    });
  });

  it('should_preselectConsentTabAndShowNotice_when_onboardingParamSet', async () => {
    mockedGetProfile.mockResolvedValue(profileFixture({ termsAcceptedAt: null }) as never);

    renderPage('/profile?onboarding=1');

    // Consent tab content visible without clicking
    expect(await screen.findByTestId('consent-checkbox')).toBeInTheDocument();
    expect(screen.getByTestId('onboarding-notice')).toBeInTheDocument();
  });

  it('should_navigateToDashboard_when_onboardingConsentSaved', async () => {
    mockedGetProfile.mockResolvedValue(profileFixture({ termsAcceptedAt: null }) as never);
    mockedUpdateProfile.mockResolvedValue(
      profileFixture({ termsAcceptedAt: '2026-06-04T18:00:00Z' }).user as never
    );
    const user = userEvent.setup();

    renderPage('/profile?onboarding=1');

    await user.click(await screen.findByTestId('consent-checkbox'));
    await user.click(screen.getByTestId('consent-save-button'));

    await waitFor(() => {
      expect(screen.getByTestId('dashboard-stub')).toBeInTheDocument();
    });
  });

  it('should_toggleNewsletterViaMySubscription_when_switchClicked', async () => {
    mockedGetProfile.mockResolvedValue(profileFixture() as never);
    mockedGetMySubscription.mockResolvedValue({ subscribed: false });
    mockedPatchMySubscription.mockResolvedValue({ subscribed: true } as never);
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByTestId('consent-tab'));
    await user.click(await screen.findByTestId('newsletter-toggle'));

    await waitFor(() => {
      // EMS newsletter self-service — NOT a /users/me field (Scope Revision #1)
      expect(mockedPatchMySubscription).toHaveBeenCalledWith(true, 'en');
    });
    expect(mockedUpdateProfile).not.toHaveBeenCalled();
  });

  it('should_renderErrorUi_when_profileFetchFails', async () => {
    mockedGetProfile.mockRejectedValue(new Error('Not authorized'));

    renderPage();

    await waitFor(() => {
      expect(mockedGetProfile).toHaveBeenCalled();
    });
    expect(await screen.findByText('Not authorized')).toBeInTheDocument();
  });
});
