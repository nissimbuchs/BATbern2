/**
 * Newsletter Service (Story 10.7)
 *
 * HTTP client for newsletter subscription and sending APIs.
 */

import apiClient from '@/services/api/apiClient';

export interface NewsletterSubscribeRequest {
  email: string;
  firstName?: string;
  language?: 'de' | 'en';
}

export interface NewsletterUnsubscribeRequest {
  token: string;
}

export interface NewsletterSubscriptionStatusResponse {
  subscribed: boolean;
  email?: string;
}

export interface NewsletterSendRequest {
  isReminder: boolean;
  locale: 'de' | 'en';
}

export interface NewsletterSendResponse {
  recipientCount: number;
  sentAt: string;
}

export interface NewsletterPreviewResponse {
  subject: string;
  htmlPreview: string;
  recipientCount: number;
}

export interface NewsletterSendHistoryItem {
  id: string;
  isReminder: boolean;
  sentAt: string;
  sentByUsername: string;
  recipientCount: number;
  templateKey: string;
}

export interface SubscriberCountResponse {
  totalCount: number;
}

/** Subscribe an email to the newsletter (no auth required). Returns 409 if already subscribed. */
export async function subscribe(request: NewsletterSubscribeRequest): Promise<void> {
  await apiClient.post('/newsletter/subscribe', request);
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

/** Get total active subscriber count (ORGANIZER only). */
export async function getSubscriberCount(): Promise<SubscriberCountResponse> {
  const response = await apiClient.get<SubscriberCountResponse>('/newsletter/subscribers');
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

/** Send newsletter for an event (ORGANIZER only). */
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
