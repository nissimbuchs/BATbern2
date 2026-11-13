/**
 * The confirmation link's page (story "Visitors see what a BATbern account gives them"): once the
 * registration is confirmed it shows the four account benefits, sign-up and the account page link.
 * MSW answers `POST /events/{eventCode}/registrations/confirm`; nothing of ours is mocked.
 */
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedOut } from '@/test/auth';
import ConfirmRegistrationPage from '../ConfirmRegistrationPage';

function renderPage() {
  return renderWithAuth(
    <Routes>
      <Route path="/events/:eventCode/confirm-registration" element={<ConfirmRegistrationPage />} />
    </Routes>,
    {
      auth: signedOut(),
      initialEntries: ['/events/BATbern61/confirm-registration?token=confirm-token-61'],
    }
  );
}

describe('ConfirmRegistrationPage', () => {
  it('should_showTheFourBenefits_and_thePageLink_when_confirmed', async () => {
    api.on('confirmRegistration', () => ({
      message: 'Registration confirmed successfully',
      status: 'CONFIRMED' as const,
    }));

    renderPage();

    expect(await screen.findByText('Registration Confirmed!')).toBeInTheDocument();
    expect(api.calls('confirmRegistration')[0].query.get('token')).toBe('confirm-token-61');
    expect(
      within(screen.getByTestId('account-benefits-list')).getAllByRole('listitem')
    ).toHaveLength(4);
    expect(screen.getByRole('link', { name: 'Create a free account' })).toHaveAttribute(
      'href',
      '/auth/register'
    );
    expect(screen.getByRole('link', { name: 'More about the account' })).toHaveAttribute(
      'href',
      '/why-an-account'
    );
    expect(screen.getByRole('link', { name: 'Back to Home' })).toHaveAttribute('href', '/');
  });

  it('should_showTranslatedAlreadyConfirmedText_when_confirmedBefore', async () => {
    api.on('confirmRegistration', () => ({
      message: 'Registration already confirmed',
      status: 'CONFIRMED' as const,
    }));

    renderPage();

    expect(
      await screen.findByText(
        'Your registration was previously confirmed. No further action is needed.'
      )
    ).toBeInTheDocument();
  });
});
