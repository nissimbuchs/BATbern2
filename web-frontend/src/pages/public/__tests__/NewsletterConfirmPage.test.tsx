/**
 * NewsletterConfirmPage (#1288, double opt-in): the confirmation mail's link. The real page, hook,
 * service and apiClient against the MSW harness, which answers `POST /newsletter/confirmations`
 * (`confirmNewsletterSubscription`); nothing of ours is mocked.
 */
import { describe, expect, it } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedOut } from '@/test/auth';
import NewsletterConfirmPage from '../NewsletterConfirmPage';

function renderPage(url: string) {
  return renderWithAuth(
    <Routes>
      <Route path="/newsletter/confirm" element={<NewsletterConfirmPage />} />
    </Routes>,
    { auth: signedOut(), initialEntries: [url] }
  );
}

describe('NewsletterConfirmPage', () => {
  it('should_postTheToken_and_showSuccess_when_visitorConfirms', async () => {
    renderPage('/newsletter/confirm?token=3f1c2a9e-7b4d-4e8a-9c21-5d6e7f809a1b');

    // Nothing is posted until the visitor clicks: a mail scanner opening the link confirms nothing.
    expect(
      await screen.findByText('Confirm that you want to receive the BATbern newsletter.')
    ).toBeInTheDocument();
    expect(api.calls('confirmNewsletterSubscription')).toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: 'Confirm subscription' }));

    expect(
      await screen.findByText('Thank you! Your newsletter subscription is confirmed.')
    ).toBeInTheDocument();
    const [call] = api.calls('confirmNewsletterSubscription');
    expect(call.body).toEqual({ token: '3f1c2a9e-7b4d-4e8a-9c21-5d6e7f809a1b' });
    expect(screen.getByRole('link', { name: 'Go to the homepage' })).toHaveAttribute('href', '/');
  });

  it('should_showInvalid_when_tokenIsUnknownOrUsed', async () => {
    api.on('confirmNewsletterSubscription', () => new HttpResponse(null, { status: 404 }));
    renderPage('/newsletter/confirm?token=used-token');

    fireEvent.click(await screen.findByRole('button', { name: 'Confirm subscription' }));

    expect(
      await screen.findByText(/This confirmation link is invalid, has expired/)
    ).toBeInTheDocument();
    expect(screen.queryByText(/is confirmed/)).not.toBeInTheDocument();
  });

  it('should_showInvalid_withoutCallingTheServer_when_linkHasNoToken', async () => {
    renderPage('/newsletter/confirm');

    expect(
      await screen.findByText(/This confirmation link is invalid, has expired/)
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirm subscription' })).not.toBeInTheDocument();
    expect(api.calls('confirmNewsletterSubscription')).toHaveLength(0);
  });

  it('should_showTheGenericError_when_theServerFails', async () => {
    api.on('confirmNewsletterSubscription', () => HttpResponse.error());
    renderPage('/newsletter/confirm?token=some-token');

    fireEvent.click(await screen.findByRole('button', { name: 'Confirm subscription' }));

    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument();
  });
});
