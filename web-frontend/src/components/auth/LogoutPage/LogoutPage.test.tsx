/**
 * LogoutPage Tests (Story 12.7, SSO Phase 4)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LogoutPage } from './LogoutPage';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

const mockSignOut = vi.fn();
vi.mock('@hooks/useAuth', () => ({
  useAuth: () => ({ signOut: mockSignOut }),
}));

vi.mock('@components/shared/BATbernLoader', () => ({
  BATbernLoader: () => <div data-testid="loader" />,
}));

describe('LogoutPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_signOutAndNavigateHome_when_mounted', async () => {
    mockSignOut.mockResolvedValue(undefined);

    render(
      <MemoryRouter>
        <LogoutPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockSignOut).toHaveBeenCalledTimes(1);
      expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true });
    });
  });
});
