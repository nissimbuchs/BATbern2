/**
 * Mail Message Service (Story 16.3)
 *
 * Client for the event's one mail resource (event-mail-api.openapi.yml). ORGANIZER only.
 * Story 16.3: what went out. Story 16.8: preview, send (test or real) and retry.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-mail-api.types';

export type MailAudience = components['schemas']['MailAudience'];
export type MailMessageSummary = components['schemas']['MailMessageSummary'];
export type MailMessagePage = components['schemas']['MailMessagePage'];
export type MailMessageDetail = components['schemas']['MailMessageDetail'];
export type MailRecipientEntry = components['schemas']['MailRecipientEntry'];
export type MailCounts = components['schemas']['MailCounts'];
export type MailProgress = NonNullable<components['schemas']['MailProgress']>;
export type MailComposeRequest = components['schemas']['MailComposeRequest'];
export type MailSendRequest = components['schemas']['MailSendRequest'];
export type MailSendTarget = MailSendRequest['target'];
export type MailPreviewResponse = components['schemas']['MailPreviewResponse'];
export type MailSendResponse = components['schemas']['MailSendResponse'];
export type MailSkippedRecipient = components['schemas']['MailSkippedRecipient'];
/** The audiences the composer sends to (Partners joins with Story 16.9). */
export type ComposeAudience = MailComposeRequest['audience'];

export interface MailMessageListParams {
  audience?: MailAudience;
  templateKey?: string;
  /** Story 16.9: only mails about this subject, e.g. `partner-meeting:{meetingId}`. */
  reference?: string;
  /** yyyy-MM-dd, Europe/Zurich */
  date?: string;
  grouped?: boolean;
  /** Story 16.8: organizer test sends are hidden unless this is true. */
  includeTests?: boolean;
  /** Story 16.7: OUT (default), IN or ANY. */
  direction?: 'OUT' | 'IN' | 'ANY';
  /** Story 16.7: true lists only spam; spam is never listed otherwise. */
  spam?: boolean;
  page?: number;
  limit?: number;
}

/** Story 16.7/16.10: the list without the event in the path (`GET /mails`). */
export interface GlobalMailListParams extends MailMessageListParams {
  /** Only mail of this event. */
  eventCode?: string;
  /** Only mail without an event. */
  noEvent?: boolean;
  /** Story 16.10: mail to or from this user, by every address they have. */
  participantUsername?: string;
  /** Story 16.10: mail to or from the PARTNER contacts of this company. */
  participantCompany?: string;
}

const base = (eventCode: string) => `/events/${encodeURIComponent(eventCode)}/mails`;

/** List what went out for an event, newest first. */
export async function listMailMessages(
  eventCode: string,
  params: MailMessageListParams = {}
): Promise<MailMessagePage> {
  const response = await apiClient.get<MailMessagePage>(base(eventCode), { params });
  return response.data;
}

/** One mail as sent, with its recipients. */
export async function getMailMessage(
  eventCode: string,
  mailKey: string
): Promise<MailMessageDetail> {
  const response = await apiClient.get<MailMessageDetail>(
    `${base(eventCode)}/${encodeURIComponent(mailKey)}`
  );
  return response.data;
}

/** Story 16.7: what went out across events (and, with filters, without event or for one person). */
export async function listMails(params: GlobalMailListParams = {}): Promise<MailMessagePage> {
  const response = await apiClient.get<MailMessagePage>('/mails', { params });
  return response.data;
}

/** Story 16.7: one mail by its key, of any event or none. */
export async function getMail(mailKey: string): Promise<MailMessageDetail> {
  const response = await apiClient.get<MailMessageDetail>(`/mails/${encodeURIComponent(mailKey)}`);
  return response.data;
}

/** Story 16.7 AC3: "Not spam". The mail is threaded like a new arrival. Sends nothing. */
export async function markNotSpam(mailKey: string): Promise<MailMessageDetail> {
  const response = await apiClient.patch<MailMessageDetail>(
    `/mails/${encodeURIComponent(mailKey)}`,
    { spam: false }
  );
  return response.data;
}

/** Counts for "Handled automatically". */
export async function getMailCounts(eventCode: string): Promise<MailCounts> {
  const response = await apiClient.get<MailCounts>(`${base(eventCode)}/counts`);
  return response.data;
}

/** Story 16.8: the mail as recipient `recipientIndex` gets it, credentials redacted. Sends nothing. */
export async function previewMail(
  eventCode: string,
  request: MailComposeRequest
): Promise<MailPreviewResponse> {
  const response = await apiClient.post<MailPreviewResponse>(`${base(eventCode)}/preview`, request);
  return response.data;
}

/**
 * Story 16.8: send to the organizers as a test (`target: ORGANIZERS`) or to the audience
 * (`target: RECIPIENTS`). Returns the recorded mail key(s) at once; delivery runs on.
 */
export async function sendMail(
  eventCode: string,
  request: MailSendRequest
): Promise<MailSendResponse> {
  const response = await apiClient.post<MailSendResponse>(base(eventCode), request);
  return response.data;
}

/** Story 16.8: resend a newsletter's failed recipients (PARTIAL or FAILED only). */
export async function retryMail(eventCode: string, mailKey: string): Promise<MailSendResponse> {
  const response = await apiClient.post<MailSendResponse>(
    `${base(eventCode)}/${encodeURIComponent(mailKey)}/retry`
  );
  return response.data;
}
