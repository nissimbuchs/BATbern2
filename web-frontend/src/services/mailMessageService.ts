/**
 * Mail Message Service (Story 16.3)
 *
 * Read-only client for what went out for an event (event-mail-api.openapi.yml). ORGANIZER only.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-mail-api.types';

export type MailAudience = components['schemas']['MailAudience'];
export type MailMessageSummary = components['schemas']['MailMessageSummary'];
export type MailMessagePage = components['schemas']['MailMessagePage'];
export type MailMessageDetail = components['schemas']['MailMessageDetail'];
export type MailRecipientEntry = components['schemas']['MailRecipientEntry'];
export type MailCounts = components['schemas']['MailCounts'];

export interface MailMessageListParams {
  audience?: MailAudience;
  templateKey?: string;
  /** yyyy-MM-dd, Europe/Zurich */
  date?: string;
  grouped?: boolean;
  page?: number;
  limit?: number;
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

/** Counts for "Handled automatically". */
export async function getMailCounts(eventCode: string): Promise<MailCounts> {
  const response = await apiClient.get<MailCounts>(`${base(eventCode)}/counts`);
  return response.data;
}
