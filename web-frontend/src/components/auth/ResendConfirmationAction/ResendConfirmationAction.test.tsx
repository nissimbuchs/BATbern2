/**
 * ResendConfirmationAction Component Tests
 * Issue #1141: users who registered but never confirmed their email address (Cognito
 * UNCONFIRMED) need one action that sends a fresh confirmation link and takes them to
 * the verification page — shared by login, forgot-password and sign-up step 2.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { I18nextProvider } from 'react-i18next';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';
import * as emailVerificationService from '@/services/auth/emailVerificationService';
import { ResendConfirmationAction } from './ResendConfirmationAction';

vi.mock('@/services/auth/emailVerificationService', () => ({
  verifyEmail: vi.fn(),
  resendVerificationCode: vi.fn(),
}));

const VerifyPageProbe = () => {
  const location = useLocation();
  return <div data-testid="verify-page">{`${location.pathname}${location.search}`}</div>;
};

const renderAction = (email: string) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <MemoryRouter initialEntries={['/start']}>
          <Routes>
            <Route path="/start" element={<ResendConfirmationAction email={email} />} />
            <Route path="/auth/verify-email" element={<VerifyPageProbe />} />
          </Routes>
        </MemoryRouter>
      </I18nextProvider>
    </QueryClientProvider>
  );
};

describe('ResendConfirmationAction', () => {
  const mockResend = vi.mocked(emailVerificationService.resendVerificationCode);

  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
  });

  it('should_resendCodeAndNavigateToVerifyPage_when_clicked', async () => {
    mockResend.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    renderAction('anna+test@example.com');

    await user.click(screen.getByRole('button', { name: /send new confirmation link/i }));

    expect(mockResend).toHaveBeenCalledWith('anna+test@example.com');
    await waitFor(() => {
      expect(screen.getByTestId('verify-page')).toHaveTextContent(
        '/auth/verify-email?email=anna%2Btest%40example.com'
      );
    });
  });

  it('should_showAlreadyConfirmedMessage_when_cognitoSaysAlreadyConfirmed', async () => {
    // Cognito answers resendSignUpCode for a CONFIRMED user with InvalidParameterException.
    mockResend.mockResolvedValue({
      success: false,
      error: { code: 'INVALID_PARAMETER', message: 'User is already confirmed.' },
    });
    const user = userEvent.setup();
    renderAction('anna@example.com');

    await user.click(screen.getByRole('button', { name: /send new confirmation link/i }));

    await waitFor(() => {
      expect(screen.getByText(/already confirmed/i)).toBeInTheDocument();
    });
    expect(screen.queryByTestId('verify-page')).not.toBeInTheDocument();
  });

  it('should_showRateLimitMessage_when_resendRateLimited', async () => {
    mockResend.mockResolvedValue({
      success: false,
      error: { code: 'RATE_LIMITED', message: 'Too many requests' },
    });
    const user = userEvent.setup();
    renderAction('anna@example.com');

    await user.click(screen.getByRole('button', { name: /send new confirmation link/i }));

    await waitFor(() => {
      expect(screen.getByText(/too many resend attempts/i)).toBeInTheDocument();
    });
  });

  it('should_disableButton_when_emailMissing', () => {
    renderAction('');
    expect(screen.getByRole('button', { name: /send new confirmation link/i })).toBeDisabled();
  });
});
