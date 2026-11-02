/**
 * useMailConversations (Story 16.2a)
 *
 * React Query hooks for "Needs an answer". The event's list lives under the event's mail prefix
 * (`['mails', eventCode]`), so a send or a status change refreshes the whole mail page.
 */

import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import * as conversationService from '@/services/mailConversationService';
import { ALL_EVENTS } from '@/hooks/useMailMessages/useMailMessages';
import type {
  MailConversationCounts,
  MailConversationDetail,
  MailConversationPage,
  MailConversationPatch,
  MailConversationReply,
} from '@/services/mailConversationService';

/** One page holds every conversation of an event at today's volume (~6 human mails a week). */
export const CONVERSATION_PAGE_SIZE = 100;

export const MAIL_CONVERSATION_QUERY_KEYS = {
  list: (eventCode: string) => ['mails', eventCode, 'conversations'] as const,
  /** Story 16.7: the Mail page's lists, under the same `'mails'` prefix. */
  global: (params: GlobalConversationParams) =>
    ['mails', ALL_EVENTS, 'conversations', params] as const,
  counts: ['mails', ALL_EVENTS, 'conversation-counts'] as const,
  detail: (conversationKey: string) => ['conversations', conversationKey] as const,
};

export type GlobalConversationParams = Parameters<typeof conversationService.listConversations>[0];

/** Story 16.7 AC2: the badge refreshes at least every 60 s. */
export const CONVERSATION_COUNTS_REFRESH_MS = 60 * 1000;

export function useEventConversations(
  eventCode: string
): UseQueryResult<MailConversationPage, Error> {
  return useQuery({
    queryKey: MAIL_CONVERSATION_QUERY_KEYS.list(eventCode),
    queryFn: () =>
      conversationService.listEventConversations(eventCode, { limit: CONVERSATION_PAGE_SIZE }),
    staleTime: 30 * 1000,
  });
}

/** Story 16.7: conversations across events, e.g. the open ones or the ones without event. */
export function useConversations(
  params: GlobalConversationParams
): UseQueryResult<MailConversationPage, Error> {
  return useQuery({
    queryKey: MAIL_CONVERSATION_QUERY_KEYS.global(params),
    queryFn: () =>
      conversationService.listConversations({ limit: CONVERSATION_PAGE_SIZE, ...params }),
    staleTime: 30 * 1000,
  });
}

/** Story 16.7 AC2: open/waiting/no-event/spam counts; polled for the navigation badge. */
export function useConversationCounts(
  enabled = true
): UseQueryResult<MailConversationCounts, Error> {
  return useQuery({
    queryKey: MAIL_CONVERSATION_QUERY_KEYS.counts,
    queryFn: () => conversationService.getConversationCounts(),
    enabled,
    staleTime: 30 * 1000,
    refetchInterval: CONVERSATION_COUNTS_REFRESH_MS,
  });
}

export function useConversation(
  conversationKey: string | null
): UseQueryResult<MailConversationDetail, Error> {
  return useQuery({
    queryKey: MAIL_CONVERSATION_QUERY_KEYS.detail(conversationKey ?? ''),
    queryFn: () => conversationService.getConversation(conversationKey as string),
    enabled: conversationKey !== null,
    staleTime: 30 * 1000,
  });
}

/**
 * AC12: status and assignee; Story 16.7: the event. Refreshes the conversation and every mail list
 * (a conversation can move between events, and the Mail page and its badge show all of them).
 */
export function usePatchConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, patch }: { key: string; patch: MailConversationPatch }) =>
      conversationService.patchConversation(key, patch),
    onSuccess: (_result, { key }) => {
      void queryClient.invalidateQueries({ queryKey: ['mails'] });
      void queryClient.invalidateQueries({ queryKey: MAIL_CONVERSATION_QUERY_KEYS.detail(key) });
    },
  });
}

/**
 * Story 16.6: answer a conversation. Refreshes the conversation and every mail list, whose rows
 * show the new status.
 */
export function useReplyToConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, reply }: { key: string; reply: MailConversationReply }) =>
      conversationService.replyToConversation(key, reply),
    onSuccess: (_result, { key }) => {
      void queryClient.invalidateQueries({ queryKey: MAIL_CONVERSATION_QUERY_KEYS.detail(key) });
      // Story 16.7: also the Mail page and its badge, not only the event's page
      void queryClient.invalidateQueries({ queryKey: ['mails'] });
    },
  });
}
