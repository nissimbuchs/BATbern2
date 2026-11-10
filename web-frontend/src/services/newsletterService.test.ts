/**
 * newsletterService against the MSW harness (moved off the apiClient mock in Story 16.8c).
 *
 * The real apiClient sends each call; the harness checks it against
 * `docs/api/event-newsletter-api.openapi.yml` and answers with the spec example or an override.
 * Covered: subscribe, verifyUnsubscribeToken, unsubscribeByToken, getMySubscription,
 * patchMySubscription.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import {
  subscribe,
  verifyUnsubscribeToken,
  unsubscribeByToken,
  getMySubscription,
  patchMySubscription,
} from './newsletterService';

const problem = (status: number) =>
  HttpResponse.json(
    {
      timestamp: '2026-11-10T10:00:00Z',
      path: '/api/v1/newsletter',
      status,
      error: 'x',
      message: 'x',
    },
    { status }
  );

/** The HTTP status a rejected axios call carries. */
const statusOf = (promise: Promise<unknown>) =>
  promise.then(
    () => {
      throw new Error('expected the call to reject');
    },
    (e: { response?: { status: number } }) => e.response?.status
  );

describe('newsletterService', () => {
  describe('subscribe', () => {
    it('POSTs the request to /newsletter/subscribe without a Turnstile header', async () => {
      await subscribe({ email: 'alice@example.com', firstName: 'Alice', language: 'de' });

      const [call] = api.calls('subscribeNewsletter');
      expect(call.body).toEqual({ email: 'alice@example.com', firstName: 'Alice', language: 'de' });
      expect(call.request.headers.get('X-Turnstile-Token')).toBeNull();
    });

    it('sends the X-Turnstile-Token header when a token is given (Story 10.31, AC8)', async () => {
      await subscribe({ email: 'alice@example.com' }, 'cf-token-xyz');

      const [call] = api.calls('subscribeNewsletter');
      expect(call.request.headers.get('X-Turnstile-Token')).toBe('cf-token-xyz');
    });

    it('sends no Turnstile header when the token is null', async () => {
      await subscribe({ email: 'alice@example.com' }, null);

      expect(
        api.calls('subscribeNewsletter')[0].request.headers.get('X-Turnstile-Token')
      ).toBeNull();
    });

    it('rejects with 409 when the address is already subscribed', async () => {
      api.on('subscribeNewsletter', () => problem(409));

      expect(await statusOf(subscribe({ email: 'dup@example.com' }))).toBe(409);
    });
  });

  describe('verifyUnsubscribeToken', () => {
    it('GETs the verify endpoint with the token and returns the address', async () => {
      api.on('verifyUnsubscribeToken', () => ({ email: 'alice@example.com' }));

      const result = await verifyUnsubscribeToken('tok-abc');

      expect(api.calls('verifyUnsubscribeToken')[0].query.get('token')).toBe('tok-abc');
      expect(result).toEqual({ email: 'alice@example.com' });
    });

    it('sends a token with special characters intact', async () => {
      await verifyUnsubscribeToken('tok/with+special=chars');

      expect(api.calls('verifyUnsubscribeToken')[0].query.get('token')).toBe(
        'tok/with+special=chars'
      );
    });

    it('rejects with 404 for an unknown token', async () => {
      api.on('verifyUnsubscribeToken', () => problem(404));

      expect(await statusOf(verifyUnsubscribeToken('bad-token'))).toBe(404);
    });
  });

  describe('unsubscribeByToken', () => {
    it('POSTs the token to /newsletter/unsubscribe', async () => {
      await unsubscribeByToken('unsubscribe-tok');

      expect(api.calls('unsubscribeNewsletter')[0].body).toEqual({ token: 'unsubscribe-tok' });
    });

    it('rejects with 404 for an unknown token', async () => {
      api.on('unsubscribeNewsletter', () => problem(404));

      expect(await statusOf(unsubscribeByToken('bad'))).toBe(404);
    });
  });

  describe('getMySubscription', () => {
    it('returns the subscription status of the signed-in user', async () => {
      api.on('getMyNewsletterSubscription', () => ({
        subscribed: true,
        email: 'alice@example.com',
      }));

      expect(await getMySubscription()).toEqual({ subscribed: true, email: 'alice@example.com' });
      expect(api.calls('getMyNewsletterSubscription')).toHaveLength(1);
    });

    it('returns an unsubscribed status', async () => {
      api.on('getMyNewsletterSubscription', () => ({ subscribed: false, email: null }));

      expect((await getMySubscription()).subscribed).toBe(false);
    });
  });

  describe('patchMySubscription', () => {
    it('PATCHes subscribed and language and returns the new status', async () => {
      api.on('patchMyNewsletterSubscription', () => ({
        subscribed: true,
        email: 'alice@example.com',
      }));

      const result = await patchMySubscription(true, 'de');

      expect(api.calls('patchMyNewsletterSubscription')[0].body).toEqual({
        subscribed: true,
        language: 'de',
      });
      expect(result).toEqual({ subscribed: true, email: 'alice@example.com' });
    });

    it('PATCHes without language when none is given', async () => {
      await patchMySubscription(false);

      expect(api.calls('patchMyNewsletterSubscription')[0].body).toEqual({ subscribed: false });
    });
  });
});
