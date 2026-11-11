/**
 * useNewsletter hooks against the MSW harness (moved off the service mock in Story 16.8c).
 *
 * The hooks run their real service and apiClient; the harness checks every request against
 * `docs/api/event-newsletter-api.openapi.yml` and answers with the spec example or an override.
 * Covered: useNewsletterSubscribe (and the my-subscriptions refresh), useVerifyUnsubscribeToken
 * (enabled guard), useUnsubscribeByToken, useMySubscriptions, usePatchMySubscription (cache
 * update). Per address since 4.10c.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import {
  NEWSLETTER_QUERY_KEYS,
  useNewsletterSubscribe,
  useVerifyUnsubscribeToken,
  useUnsubscribeByToken,
  useMySubscriptions,
  usePatchMySubscription,
} from './useNewsletter';

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

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

let qc: QueryClient;

beforeEach(() => {
  qc = createQC();
});

describe('useNewsletterSubscribe', () => {
  it('subscribes and marks the cached my-subscriptions stale (#818)', async () => {
    qc.setQueryData(NEWSLETTER_QUERY_KEYS.mySubscriptions, { addresses: [] });
    const { result } = renderHook(() => useNewsletterSubscribe(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ request: { email: 'alice@example.com', language: 'de' } });
    });

    expect(api.calls('createNewsletterSubscriber')[0].body).toEqual({
      email: 'alice@example.com',
      language: 'de',
    });
    expect(qc.getQueryState(NEWSLETTER_QUERY_KEYS.mySubscriptions)?.isInvalidated).toBe(true);
  });

  it('is in error when the server refuses (409)', async () => {
    api.on('createNewsletterSubscriber', () => problem(409));
    const { result } = renderHook(() => useNewsletterSubscribe(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ request: { email: 'dup@example.com' } }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useVerifyUnsubscribeToken', () => {
  it('returns the address of a valid token', async () => {
    api.on('verifyUnsubscribeToken', () => ({ email: 'alice@example.com' }));

    const { result } = renderHook(() => useVerifyUnsubscribeToken('valid-tok'), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.calls('verifyUnsubscribeToken')[0].query.get('token')).toBe('valid-tok');
    expect(result.current.data).toEqual({ email: 'alice@example.com' });
  });

  it('sends nothing without a token', () => {
    const { result } = renderHook(() => useVerifyUnsubscribeToken(null), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(api.calls('verifyUnsubscribeToken')).toHaveLength(0);
  });
});

describe('useUnsubscribeByToken', () => {
  it('posts the token and succeeds', async () => {
    const { result } = renderHook(() => useUnsubscribeByToken(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('unsub-token');
    });

    expect(api.calls('unsubscribeNewsletter')[0].body).toEqual({ token: 'unsub-token' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

const addresses = {
  addresses: [{ email: 'alice@example.com', primary: true, subscribed: true, language: 'de' }],
};

describe('useMySubscriptions', () => {
  it('returns the state per address', async () => {
    api.on('getMyNewsletterSubscriptions', () => addresses);

    const { result } = renderHook(() => useMySubscriptions(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.addresses[0].subscribed).toBe(true);
  });

  it('is in error when the server answers 401', async () => {
    api.on('getMyNewsletterSubscriptions', () => problem(401));

    const { result } = renderHook(() => useMySubscriptions(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('sends nothing when disabled', () => {
    renderHook(() => useMySubscriptions({ enabled: false }), { wrapper: wrapper(qc) });

    expect(api.calls('getMyNewsletterSubscriptions')).toHaveLength(0);
  });
});

describe('usePatchMySubscription', () => {
  it('patches one address and puts the answer into the my-subscriptions cache', async () => {
    const after = {
      addresses: [{ email: 'alice@example.com', primary: true, subscribed: false, language: 'de' }],
    };
    api.on('patchMyNewsletterSubscriptions', () => after);
    const { result } = renderHook(() => usePatchMySubscription(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ email: 'alice@example.com', subscribed: false });
    });

    expect(api.calls('patchMyNewsletterSubscriptions')[0].body).toEqual({
      email: 'alice@example.com',
      subscribed: false,
    });
    expect(qc.getQueryData(NEWSLETTER_QUERY_KEYS.mySubscriptions)).toEqual(after);
  });
});
