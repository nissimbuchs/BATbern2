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

// Keep the loader lightweight in tests (no theme/asset deps).
vi.mock('@components/shared/BATbernLoader', () => ({
  BATbernLoader: () => <div data-testid="loader" />,
}));

describe('AuthCallbackPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
});
