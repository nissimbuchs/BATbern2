/**
 * useMailConversations (Story 16.2a)
 *
 * React Query hooks for "Needs an answer". The event's list lives under the event's mail prefix
 * (`['mails', eventCode]`), so a send or a status change refreshes the whole mail page.
 */

import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import * as conversationService from '@/services/mailConversationService';
import type {
  MailConversationDetail,
  MailConversationPage,
  MailConversationPatch,
} from '@/services/mailConversationService';

/** One page holds every conversation of an event at today's volume (~6 human mails a week). */
export const CONVERSATION_PAGE_SIZE = 100;

export const MAIL_CONVERSATION_QUERY_KEYS = {
  list: (eventCode: string) => ['mails', eventCode, 'conversations'] as const,
  detail: (conversationKey: string) => ['conversations', conversationKey] as const,
};

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

/** AC12: status and assignee. Refreshes the event's list and the conversation. */
export function usePatchConversation(eventCode: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, patch }: { key: string; patch: MailConversationPatch }) =>
      conversationService.patchConversation(key, patch),
    onSuccess: (_result, { key }) => {
      void queryClient.invalidateQueries({
        queryKey: MAIL_CONVERSATION_QUERY_KEYS.list(eventCode),
      });
      void queryClient.invalidateQueries({ queryKey: MAIL_CONVERSATION_QUERY_KEYS.detail(key) });
    },
  });
}
