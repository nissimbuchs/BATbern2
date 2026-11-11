/**
 * Newsletter Service (Story 10.7)
 *
 * HTTP client for newsletter subscription and sending APIs.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-newsletter-api.types';

type Schemas = components['schemas'];

/** `language` may be omitted; the server applies its default (`de`). */
export type NewsletterSubscribeRequest = Schemas['NewsletterSubscribeRequest'];
export type NewsletterUnsubscribeRequest = Schemas['NewsletterUnsubscribeRequest'];
/** #1244: newsletter state of one of the authenticated user's addresses. */
export type NewsletterAddressSubscription = Schemas['NewsletterAddressSubscription'];
export type MyNewsletterSubscriptionsResponse = Schemas['MyNewsletterSubscriptionsResponse'];
/** 4.10c: the address to change and only the fields to change. */
export type PatchMyNewsletterSubscriptionRequest = Schemas['PatchMyNewsletterSubscriptionRequest'];

/** Subscribe an email to the newsletter (no auth required): `POST /newsletter/subscribers`
 *  (Story 4.10c). Returns 409 if already subscribed. Sends no mail.
 *  @param turnstileToken Optional Cloudflare Turnstile token (AC8, Story 10.31). Passed via
 *  X-Turnstile-Token header when present.
 */
export async function subscribe(
  request: NewsletterSubscribeRequest,
  turnstileToken?: string | null
): Promise<void> {
  await apiClient.post('/newsletter/subscribers', request, {
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

/** #1244: the authenticated user's newsletter state per address (primary first). */
export async function getMySubscriptions(): Promise<MyNewsletterSubscriptionsResponse> {
  const response = await apiClient.get<MyNewsletterSubscriptionsResponse>(
    '/newsletter/my-subscriptions'
  );
  return response.data;
}

/**
 * 4.10c: change one of the authenticated user's addresses, only the fields given (`subscribed`,
 * `language`). 403 when the address is not the user's.
 */
export async function patchMySubscription(
  request: PatchMyNewsletterSubscriptionRequest
): Promise<MyNewsletterSubscriptionsResponse> {
  const response = await apiClient.patch<MyNewsletterSubscriptionsResponse>(
    '/newsletter/my-subscriptions',
    request
  );
  return response.data;
}
