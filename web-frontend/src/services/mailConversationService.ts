/**
 * Mail Conversation Service (Story 16.2a)
 *
 * Client for the conversations of the Mail Center ("Needs an answer", event-mail-api.openapi.yml,
 * tag MailConversation). ORGANIZER only. Keys are global (`conv-{n}`), so the per-conversation
 * operations are not nested under an event.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-mail-api.types';

export type MailConversationStatus = components['schemas']['MailConversationStatus'];
export type MailConversationSummary = components['schemas']['MailConversationSummary'];
export type MailConversationPage = components['schemas']['MailConversationPage'];
export type MailConversationDetail = components['schemas']['MailConversationDetail'];
export type MailConversationPatch = components['schemas']['MailConversationPatch'];
export type MailConversationReply = components['schemas']['MailConversationReply'];
export type MailMessageDetail = components['schemas']['MailMessageDetail'];

export async function listEventConversations(
  eventCode: string,
  params: { status?: MailConversationStatus; page?: number; limit?: number } = {}
): Promise<MailConversationPage> {
  const response = await apiClient.get<MailConversationPage>(
    `/events/${encodeURIComponent(eventCode)}/conversations`,
    { params }
  );
  return response.data;
}

export type MailConversationCounts = components['schemas']['MailConversationCounts'];

/** Story 16.7: conversations of all events and without event (global Mail page). */
export async function listConversations(
  params: {
    status?: MailConversationStatus[];
    eventCode?: string;
    /** true: only without event; false: only with one; absent: both. */
    noEvent?: boolean;
    page?: number;
    limit?: number;
  } = {}
): Promise<MailConversationPage> {
  // apiClient sends arrays as status=OPEN&status=WAITING, as the API expects
  const response = await apiClient.get<MailConversationPage>('/conversations', { params });
  return response.data;
}

/** Story 16.7 AC2: the Mail badge and the section counts. */
export async function getConversationCounts(): Promise<MailConversationCounts> {
  const response = await apiClient.get<MailConversationCounts>('/conversations/counts');
  return response.data;
}

export async function getConversation(conversationKey: string): Promise<MailConversationDetail> {
  const response = await apiClient.get<MailConversationDetail>(
    `/conversations/${encodeURIComponent(conversationKey)}`
  );
  return response.data;
}

export async function patchConversation(
  conversationKey: string,
  patch: MailConversationPatch
): Promise<MailConversationSummary> {
  const response = await apiClient.patch<MailConversationSummary>(
    `/conversations/${encodeURIComponent(conversationKey)}`,
    patch
  );
  return response.data;
}

/** Story 16.6: answer the conversation by mail. Sends a real mail. */
export async function replyToConversation(
  conversationKey: string,
  reply: MailConversationReply
): Promise<MailMessageDetail> {
  const response = await apiClient.post<MailMessageDetail>(
    `/conversations/${encodeURIComponent(conversationKey)}/replies`,
    reply
  );
  return response.data;
}

/**
 * Story 16.6: presigned download URL of a kept inbound attachment (5 minutes), same shape as the
 * session materials' download URL.
 */
export async function getConversationAttachmentDownloadUrl(
  conversationKey: string,
  mailKey: string,
  index: number
): Promise<string> {
  const response = await apiClient.get<{ downloadUrl: string }>(
    `/conversations/${encodeURIComponent(conversationKey)}/messages/${encodeURIComponent(
      mailKey
    )}/attachments/${index}/download`
  );
  return response.data.downloadUrl;
}
