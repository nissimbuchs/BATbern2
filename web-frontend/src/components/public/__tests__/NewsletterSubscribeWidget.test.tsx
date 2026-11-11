/**
 * NewsletterSubscribeWidget Tests (Story 10.7 — AC4, AC12; Story 10.31 — AC10; Story 4.10c)
 *
 * The real widget, hooks, service and apiClient against the MSW harness (4.10c moved it off the
 * hook mocks): subscribing posts to `POST /newsletter/subscribers` (`createNewsletterSubscriber`),
 * a signed-in visitor's state comes from `GET /newsletter/my-subscriptions`. Auth state comes
 * from the real AuthContext (`renderWithAuth`). Without runtime config Turnstile is off, so
 * `getToken()` answers null and no header is sent; the header itself is covered by
 * newsletterService.test.ts.
 */
import { describe, it, expect } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedInAs, signedOut } from '@/test/auth';
import { NewsletterSubscribeWidget } from '../NewsletterSubscribeWidget';

const problem = (status: number, extra: Record<string, unknown> = {}) =>
  HttpResponse.json(
    {
      timestamp: '2026-11-11T10:00:00Z',
      path: '/api/v1/newsletter/subscribers',
      status,
      error: 'x',
      message: 'x',
      ...extra,
    },
    { status }
  );

function submit(email: string) {
  fireEvent.change(screen.getByPlaceholderText('your@email.com'), { target: { value: email } });
  fireEvent.click(screen.getByRole('button', { name: 'Subscribe' }));
}

describe('NewsletterSubscribeWidget', () => {
  it('renders title, email input, and subscribe button', () => {
    renderWithAuth(<NewsletterSubscribeWidget />, { auth: signedOut() });

    expect(screen.getByText('Stay updated with BATbern')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('your@email.com')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Subscribe' })).toBeInTheDocument();
  });

  it('shows email validation error for invalid email and sends nothing', async () => {
    renderWithAuth(<NewsletterSubscribeWidget />, { auth: signedOut() });

    submit('invalid-email');

    expect(await screen.findByText('Please enter a valid email address.')).toBeInTheDocument();
    expect(api.calls('createNewsletterSubscriber')).toHaveLength(0);
  });

  it('posts the address to /newsletter/subscribers without a Turnstile header when it is off', async () => {
    renderWithAuth(<NewsletterSubscribeWidget />, { auth: signedOut() });

    submit('test@example.com');

    expect(await screen.findByText('Thank you! You are now subscribed.')).toBeInTheDocument();
    const [call] = api.calls('createNewsletterSubscriber');
    expect(call.body).toEqual({ email: 'test@example.com' });
    expect(call.request.headers.get('X-Turnstile-Token')).toBeNull();
  });

  it('shows already-subscribed message on 409 response', async () => {
    api.on('createNewsletterSubscriber', () => problem(409));
    renderWithAuth(<NewsletterSubscribeWidget />, { auth: signedOut() });

    submit('existing@example.com');

    expect(await screen.findByText('You are already subscribed.')).toBeInTheDocument();
  });

  it('shows the error on 403 turnstile_failed (AC10)', async () => {
    api.on('createNewsletterSubscriber', () => problem(403, { error: 'turnstile_failed' }));
    renderWithAuth(<NewsletterSubscribeWidget />, { auth: signedOut() });

    submit('test@example.com');

    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument();
  });

  it('shows generic error message on a network error', async () => {
    api.on('createNewsletterSubscriber', () => HttpResponse.error());
    renderWithAuth(<NewsletterSubscribeWidget />, { auth: signedOut() });

    submit('test@example.com');

    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument();
  });

  // ── #818 defect 2: already-subscribed users were still shown the form ──────────────
  describe('#818 — existing subscription state', () => {
    it('should_hideTheForm_when_authenticatedUserIsAlreadySubscribed', async () => {
      api.on('getMyNewsletterSubscriptions', () => ({
        addresses: [
          { email: 'me@example.com', primary: true, subscribed: false, language: null },
          { email: 'me.work@example.com', primary: false, subscribed: true, language: 'de' },
        ],
      }));
      renderWithAuth(<NewsletterSubscribeWidget />, { auth: signedInAs('attendee') });

      expect(await screen.findByText('You are already subscribed.')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Subscribe' })).not.toBeInTheDocument();
    });

    it('should_showTheForm_when_authenticatedUserIsNotSubscribed', async () => {
      api.on('getMyNewsletterSubscriptions', () => ({
        addresses: [{ email: 'me@example.com', primary: true, subscribed: false, language: null }],
      }));
      renderWithAuth(<NewsletterSubscribeWidget />, { auth: signedInAs('attendee') });

      await waitFor(() => expect(api.calls('getMyNewsletterSubscriptions')).toHaveLength(1));
      expect(screen.getByRole('button', { name: 'Subscribe' })).toBeInTheDocument();
    });

    it('should_notQueryTheAuthenticatedEndpoint_when_visitorIsAnonymous', async () => {
      const { rerenderWithAuth } = renderWithAuth(<NewsletterSubscribeWidget />, {
        auth: signedOut(),
      });

      expect(await screen.findByRole('button', { name: 'Subscribe' })).toBeInTheDocument();
      // Querying it anonymously would fire a guaranteed 401 on every public page view.
      expect(api.calls('getMyNewsletterSubscriptions')).toHaveLength(0);

      // Positive control: the same widget, signed in, does ask.
      rerenderWithAuth(<NewsletterSubscribeWidget />, signedInAs('attendee'));
      await waitFor(() => expect(api.calls('getMyNewsletterSubscriptions')).toHaveLength(1));
    });
  });
});
