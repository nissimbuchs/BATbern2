/**
 * AuthCallbackPage Tests (Story 12.7, SSO Phase 4)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthCallbackPage } from './AuthCallbackPage';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

// Config-race fix (2026-06-05): the completion flow MUST NOT start before the runtime
// config has loaded — ensureAmplifyConfigured() silently no-ops without it, Amplify is
// never configured, the OAuth listener never runs the ?code= exchange, and the whole
// callback dead-ends in the 15s timeout. Default: config present (the common case).
const mockUseOptionalConfig = vi.hoisted(() => vi.fn<() => object | null>(() => ({})));
vi.mock('@/contexts/useConfig', () => ({
  useOptionalConfig: mockUseOptionalConfig,
}));

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
    mockUseOptionalConfig.mockReturnValue({});
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
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

  // Config-race fix (2026-06-05): /auth/callback raced GET /api/v1/config. When the
  // callback effect won, ensureAmplifyConfigured() no-op'd (runtimeConfig null), Amplify
  // was NEVER configured, the OAuth code exchange never ran, and the user burned the full
  // 15s waitForFederatedSession timeout before bouncing to /login ("unexpected error").
  describe('runtime-config gate', () => {
    it('should_notStartCompletion_while_runtimeConfigIsNull', async () => {
      mockUseOptionalConfig.mockReturnValue(null);
      mockCompleteFederatedSignIn.mockResolvedValue({ kind: 'success' });

      render(
        <MemoryRouter>
          <AuthCallbackPage />
        </MemoryRouter>
      );

      // Give any (buggy) effect a tick to fire.
      await new Promise((r) => setTimeout(r, 20));
      expect(mockCompleteFederatedSignIn).not.toHaveBeenCalled();
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it('should_startCompletion_when_runtimeConfigArrives', async () => {
      mockUseOptionalConfig.mockReturnValue(null);
      mockCompleteFederatedSignIn.mockResolvedValue({ kind: 'success' });

      const { rerender } = render(
        <MemoryRouter>
          <AuthCallbackPage />
        </MemoryRouter>
      );
      await new Promise((r) => setTimeout(r, 10));
      expect(mockCompleteFederatedSignIn).not.toHaveBeenCalled();

      // Config lands (ConfigProvider resolves GET /api/v1/config) → effect re-runs.
      mockUseOptionalConfig.mockReturnValue({});
      rerender(
        <MemoryRouter>
          <AuthCallbackPage />
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
      });
      expect(mockCompleteFederatedSignIn).toHaveBeenCalledTimes(1);
    });

    it('should_notAutoRetryLinkAbort_while_runtimeConfigIsNull', async () => {
      // The F6 auto-retry calls signInWithFederated → also needs a configured Amplify.
      mockUseOptionalConfig.mockReturnValue(null);

      render(
        <MemoryRouter initialEntries={[LINK_ABORT_ROUTE]}>
          <AuthCallbackPage />
        </MemoryRouter>
      );

      await new Promise((r) => setTimeout(r, 20));
      expect(mockSignInWithFederated).not.toHaveBeenCalled();
    });

    it('should_navigateToLogin_when_runtimeConfigNeverArrives', async () => {
      // Bounded escape: a failed GET /api/v1/config must not strand the user on the
      // loader forever.
      vi.useFakeTimers();
      mockUseOptionalConfig.mockReturnValue(null);

      render(
        <MemoryRouter>
          <AuthCallbackPage />
        </MemoryRouter>
      );

      await vi.advanceTimersByTimeAsync(15000);
      expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
      expect(mockCompleteFederatedSignIn).not.toHaveBeenCalled();
    });
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
