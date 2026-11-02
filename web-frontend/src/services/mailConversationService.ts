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
