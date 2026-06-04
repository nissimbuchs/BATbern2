/**
 * AuthCallbackPage Tests (Story 12.7, SSO Phase 4)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthCallbackPage } from './AuthCallbackPage';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

const mockCompleteFederatedSignIn = vi.fn();
vi.mock('@hooks/useAuth', () => ({
  useAuth: () => ({ completeFederatedSignIn: mockCompleteFederatedSignIn }),
}));

// Story 12.8 F6: the post-link auto-retry calls authService.signInWithFederated directly.
const mockSignInWithFederated = vi.hoisted(() => vi.fn());
vi.mock('@/services/auth/authService', () => ({
  authService: { signInWithFederated: mockSignInWithFederated },
}));

// Keep the loader lightweight in tests (no theme/asset deps).
vi.mock('@components/shared/BATbernLoader', () => ({
  BATbernLoader: () => <div data-testid="loader" />,
}));

describe('AuthCallbackPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('should_navigateToDashboard_when_federatedSignInSucceeds', async () => {
    mockCompleteFederatedSignIn.mockResolvedValue({ kind: 'success' });

    render(
      <MemoryRouter>
        <AuthCallbackPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
    });
    expect(mockCompleteFederatedSignIn).toHaveBeenCalledTimes(1);
  });

  it('should_navigateToLogin_when_federatedSignInFails', async () => {
    mockCompleteFederatedSignIn.mockResolvedValue({ kind: 'failed' });

    render(
      <MemoryRouter>
        <AuthCallbackPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
    });
  });

  // Story 12.8 F6: first-time LINKING sign-ins are aborted by Cognito after
  // AdminLinkProviderForUser — the callback receives ?error_description=Already found an
  // entry for username…; the identity IS linked, so one automatic retry signs the user in.
  const LINK_ABORT_ROUTE =
    '/auth/callback?error=invalid_request&error_description=Already+found+an+entry+for+username+google_109336476016498885621';

  it('should_autoRetryFederatedSignIn_when_linkAbortErrorReturned', async () => {
    render(
      <MemoryRouter initialEntries={[LINK_ABORT_ROUTE]}>
        <AuthCallbackPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockSignInWithFederated).toHaveBeenCalledTimes(1);
      expect(mockSignInWithFederated).toHaveBeenCalledWith('Google');
    });
    // The retry navigates the browser away — no SPA navigation, no completion attempt.
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(mockCompleteFederatedSignIn).not.toHaveBeenCalled();
    // One-shot guard armed for the return trip.
    expect(sessionStorage.getItem('batbern.link-retry')).toBe('1');
  });

  it('should_notRetryAgain_when_retryAlreadyAttempted', async () => {
    // Guard already armed (the retry's return trip aborted AGAIN — unexpected): no loop,
    // fall through to the normal failure path.
    sessionStorage.setItem('batbern.link-retry', '1');
    mockCompleteFederatedSignIn.mockResolvedValue({ kind: 'failed' });

    render(
      <MemoryRouter initialEntries={[LINK_ABORT_ROUTE]}>
        <AuthCallbackPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
    });
    expect(mockSignInWithFederated).not.toHaveBeenCalled();
    // Guard cleared — a later linking attempt may retry again.
    expect(sessionStorage.getItem('batbern.link-retry')).toBeNull();
  });

  it('should_clearRetryGuard_when_signInSucceeds', async () => {
    // The retry's return trip succeeds → guard must be cleared.
    sessionStorage.setItem('batbern.link-retry', '1');
    mockCompleteFederatedSignIn.mockResolvedValue({ kind: 'success' });

    render(
      <MemoryRouter>
        <AuthCallbackPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
    });
    expect(sessionStorage.getItem('batbern.link-retry')).toBeNull();
  });
});
