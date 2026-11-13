/**
 * /registration-success after an anonymous registration (story "Visitors see what a BATbern
 * account gives them"): the four account benefits, a sign-up button with the e-mail prefilled and
 * a link to the account page. Real components, real `en` bundles; nothing of ours is mocked.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { renderWithAuth, signedOut } from '@/test/auth';
import RegistrationSuccessPage from '../RegistrationSuccessPage';

const EMAIL = 'anna+bb61@example.com';

describe('RegistrationSuccessPage', () => {
  beforeEach(() => {
    sessionStorage.setItem(
      'pendingRegistration',
      JSON.stringify({ email: EMAIL, expiresAt: Date.now() + 60_000 })
    );
  });

  it('should_showTheFourBenefits_signUpWithEmail_and_thePageLink', () => {
    renderWithAuth(<RegistrationSuccessPage />, { auth: signedOut() });

    expect(
      screen.getByRole('heading', { name: 'What a free account gives you' })
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('account-benefits-list')).getAllByRole('listitem')
    ).toHaveLength(4);
    expect(screen.getByRole('link', { name: 'Create a free account' })).toHaveAttribute(
      'href',
      `/auth/register?email=${encodeURIComponent(EMAIL)}`
    );
    expect(screen.getByRole('link', { name: 'More about the account' })).toHaveAttribute(
      'href',
      '/why-an-account'
    );
    expect(screen.getByText(EMAIL)).toBeInTheDocument();
  });

  it('should_translateTheFormerlyHardcodedLabels', () => {
    renderWithAuth(<RegistrationSuccessPage />, { auth: signedOut() });

    expect(screen.getByRole('link', { name: 'Back to Home' })).toHaveAttribute('href', '/');
    expect(screen.getByText(/We've sent a confirmation email to/)).toBeInTheDocument();
    expect(screen.getByText(/The link is valid for 48 hours/)).toBeInTheDocument();
  });
});
