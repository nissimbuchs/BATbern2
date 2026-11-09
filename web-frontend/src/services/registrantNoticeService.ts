/**
 * Registrant Notice Service (Story 7.3 hardening)
 *
 * HTTP client for the dedicated, registrant-targeted notice send (e.g. the "slides are online"
 * mail). This is SEPARATE from newsletterService — it sends to an event's active registrants,
 * never the global newsletter-subscriber pool.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-newsletter-api.types';

export type RegistrantNoticePreviewRequest =
  components['schemas']['RegistrantNoticePreviewRequest'];
export type RegistrantNoticePreviewResponse =
  components['schemas']['RegistrantNoticePreviewResponse'];

export interface RegistrantNoticeSendResponse {
  sendId: string;
  /** PENDING | IN_PROGRESS | COMPLETED | PARTIAL | FAILED */
  status: string;
  recipientCount: number;
}

/** Preview a registrant-notice template in a chosen language (ORGANIZER). */
export async function preview(
  eventCode: string,
  request: RegistrantNoticePreviewRequest
): Promise<RegistrantNoticePreviewResponse> {
  const { data } = await apiClient.post<RegistrantNoticePreviewResponse>(
    `/events/${eventCode}/registrant-notices/preview`,
    request
  );
  return data;
}

/** Send a registrant-notice mail to the event's active registrants (ORGANIZER). Runs async. */
export async function send(
  eventCode: string,
  templateKey: string
): Promise<RegistrantNoticeSendResponse> {
  const { data } = await apiClient.post<RegistrantNoticeSendResponse>(
    `/events/${eventCode}/registrant-notices/send`,
    { templateKey }
  );
  return data;
}
