/**
 * Newsletter Service (Story 10.7)
 *
 * HTTP client for newsletter subscription and sending APIs.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-newsletter-api.types';

type Schemas = components['schemas'];

/**
 * `language` has a spec default (`de`), so the generated type makes it required (openapi-typescript
 * reads a default as always present); on a request it may be omitted and the server applies it.
 */
export type NewsletterSubscribeRequest = Omit<Schemas['NewsletterSubscribeRequest'], 'language'> &
  Partial<Pick<Schemas['NewsletterSubscribeRequest'], 'language'>>;
export type NewsletterUnsubscribeRequest = Schemas['NewsletterUnsubscribeRequest'];
export type NewsletterSubscriptionStatusResponse = Schemas['NewsletterSubscriptionStatusResponse'];
/** #1244: newsletter state of one of the authenticated user's addresses. */
export type NewsletterAddressSubscription = Schemas['NewsletterAddressSubscription'];
export type MyNewsletterSubscriptionsResponse = Schemas['MyNewsletterSubscriptionsResponse'];

/** Subscribe an email to the newsletter (no auth required). Returns 409 if already subscribed.
 *  @param turnstileToken Optional Cloudflare Turnstile token (AC8, Story 10.31). Passed via
 *  X-Turnstile-Token header when present.
 */
export async function subscribe(
  request: NewsletterSubscribeRequest,
  turnstileToken?: string | null
): Promise<void> {
  await apiClient.post('/newsletter/subscribe', request, {
    headers: turnstileToken ? { 'X-Turnstile-Token': turnstileToken } : {},
  });
}

/** Verify an unsubscribe token — returns email if valid, throws 404 if not. */
export async function verifyUnsubscribeToken(token: string): Promise<{ email: string }> {
  const response = await apiClient.get<{ email: string }>(
    `/newsletter/unsubscribe/verify?token=${encodeURIComponent(token)}`
  );
  return response.data;
}

/** Unsubscribe using token (no auth required). Throws 404 if token not found. */
export async function unsubscribeByToken(token: string): Promise<void> {
  await apiClient.post('/newsletter/unsubscribe', { token });
}

/** Get newsletter subscription status for the authenticated user. */
export async function getMySubscription(): Promise<NewsletterSubscriptionStatusResponse> {
  const response = await apiClient.get<NewsletterSubscriptionStatusResponse>(
    '/newsletter/my-subscription'
  );
  return response.data;
}

/** Update newsletter subscription status for the authenticated user. */
export async function patchMySubscription(
  subscribed: boolean,
  language?: string
): Promise<NewsletterSubscriptionStatusResponse> {
  const response = await apiClient.patch<NewsletterSubscriptionStatusResponse>(
    '/newsletter/my-subscription',
    { subscribed, ...(language ? { language } : {}) }
  );
  return response.data;
}

/** #1244: the authenticated user's newsletter state per address (primary first). */
export async function getMySubscriptions(): Promise<MyNewsletterSubscriptionsResponse> {
  const response = await apiClient.get<MyNewsletterSubscriptionsResponse>(
    '/newsletter/my-subscriptions'
  );
  return response.data;
}

/** #1244: subscribe or unsubscribe one of the authenticated user's addresses. */
export async function putMySubscription(
  email: string,
  subscribed: boolean,
  language?: string
): Promise<MyNewsletterSubscriptionsResponse> {
  const response = await apiClient.put<MyNewsletterSubscriptionsResponse>(
    '/newsletter/my-subscriptions',
    { email, subscribed, ...(language ? { language } : {}) }
  );
  return response.data;
}
