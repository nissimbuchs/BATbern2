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

export interface NewsletterSendRequest {
  isReminder: boolean;
  locale: 'de' | 'en';
  templateKey?: string; // Optional override; service defaults to 'newsletter-event' if omitted
  testMode?: boolean; // Send only to organizer-role subscribers
}

export interface NewsletterSendResponse {
  id: string;
  recipientCount: number;
  sentAt: string;
  /** PENDING | IN_PROGRESS | COMPLETED | PARTIAL | FAILED */
  status: string;
  sentCount: number;
  failedCount: number;
  startedAt?: string;
  completedAt?: string;
  testMode?: boolean;
}

export type NewsletterSendStatusResponse = Schemas['NewsletterSendStatusResponse'];
export type NewsletterPreviewResponse = Schemas['NewsletterPreviewResponse'];

export interface NewsletterSendHistoryItem {
  id: string;
  isReminder: boolean;
  sentAt: string;
  sentByUsername: string;
  recipientCount: number;
  templateKey: string;
  status?: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'PARTIAL' | 'FAILED';
  sentCount?: number;
  failedCount?: number;
  testMode?: boolean;
}

export type SubscriberCountResponse = Schemas['SubscriberCountResponse'];

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

/** Get total active subscriber count (ORGANIZER only). */
export async function getSubscriberCount(): Promise<SubscriberCountResponse> {
  const response = await apiClient.get<SubscriberCountResponse>('/newsletter/subscribers/count');
  return response.data;
}

/** Get newsletter send history for an event (ORGANIZER only). */
export async function getNewsletterHistory(
  eventCode: string
): Promise<NewsletterSendHistoryItem[]> {
  const response = await apiClient.get<NewsletterSendHistoryItem[]>(
    `/events/${encodeURIComponent(eventCode)}/newsletter/history`
  );
  return response.data;
}

/** Preview newsletter email for an event (ORGANIZER only). */
export async function previewNewsletter(
  eventCode: string,
  request: NewsletterSendRequest
): Promise<NewsletterPreviewResponse> {
  const response = await apiClient.post<NewsletterPreviewResponse>(
    `/events/${encodeURIComponent(eventCode)}/newsletter/preview`,
    request
  );
  return response.data;
}

/** Send newsletter for an event (ORGANIZER only). Returns immediately with PENDING status. */
export async function sendNewsletter(
  eventCode: string,
  request: NewsletterSendRequest
): Promise<NewsletterSendResponse> {
  const response = await apiClient.post<NewsletterSendResponse>(
    `/events/${encodeURIComponent(eventCode)}/newsletter/send`,
    request
  );
  return response.data;
}

/** Poll send-job progress (ORGANIZER only). */
export async function getSendStatus(
  eventCode: string,
  sendId: string
): Promise<NewsletterSendStatusResponse> {
  const response = await apiClient.get<NewsletterSendStatusResponse>(
    `/events/${encodeURIComponent(eventCode)}/newsletter/sends/${encodeURIComponent(sendId)}/status`
  );
  return response.data;
}

/** Retry failed recipients for a PARTIAL or FAILED send (ORGANIZER only). */
export async function retryFailedRecipients(
  eventCode: string,
  sendId: string
): Promise<NewsletterSendResponse> {
  const response = await apiClient.post<NewsletterSendResponse>(
    `/events/${encodeURIComponent(eventCode)}/newsletter/sends/${encodeURIComponent(sendId)}/retry`
  );
  return response.data;
}
