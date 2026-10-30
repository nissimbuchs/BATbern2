/**
 * useMailMessages (Story 16.3)
 *
 * React Query hooks for the event's mail page ("What went out", "Handled automatically").
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import * as mailMessageService from '@/services/mailMessageService';
import type {
  MailCounts,
  MailMessageDetail,
  MailMessageListParams,
  MailMessagePage,
} from '@/services/mailMessageService';

export const MAIL_MESSAGE_QUERY_KEYS = {
  /** Prefix of every mail query of an event; invalidate it after a send (Story 16.4). */
  event: (eventCode: string) => ['mail-messages', eventCode] as const,
  list: (eventCode: string, params: MailMessageListParams) =>
    ['mail-messages', eventCode, 'list', params] as const,
  detail: (eventCode: string, mailKey: string) =>
    ['mail-messages', eventCode, 'detail', mailKey] as const,
  counts: (eventCode: string) => ['mail-messages', eventCode, 'counts'] as const,
};

/** Newsletter send-job states that are still sending. */
const ACTIVE_NEWSLETTER_STATUSES = ['PENDING', 'IN_PROGRESS'];
const ACTIVE_POLL_MS = 3000;

export function useMailMessages(
  eventCode: string,
  params: MailMessageListParams,
  enabled = true
): UseQueryResult<MailMessagePage, Error> {
  return useQuery({
    queryKey: MAIL_MESSAGE_QUERY_KEYS.list(eventCode, params),
    queryFn: () => mailMessageService.listMailMessages(eventCode, params),
    staleTime: 30 * 1000,
    enabled,
    // Story 16.4 AC3: a newsletter shows its live progress, so poll while one is still sending.
    refetchInterval: (query) =>
      query.state.data?.data.some(
        (row) => row.newsletterStatus && ACTIVE_NEWSLETTER_STATUSES.includes(row.newsletterStatus)
      )
        ? ACTIVE_POLL_MS
        : false,
  });
}

export function useMailMessage(
  eventCode: string,
  mailKey: string | null
): UseQueryResult<MailMessageDetail, Error> {
  return useQuery({
    queryKey: MAIL_MESSAGE_QUERY_KEYS.detail(eventCode, mailKey ?? ''),
    queryFn: () => mailMessageService.getMailMessage(eventCode, mailKey as string),
    enabled: mailKey !== null,
    staleTime: 30 * 1000,
  });
}

export function useMailCounts(eventCode: string): UseQueryResult<MailCounts, Error> {
  return useQuery({
    queryKey: MAIL_MESSAGE_QUERY_KEYS.counts(eventCode),
    queryFn: () => mailMessageService.getMailCounts(eventCode),
    staleTime: 30 * 1000,
  });
}
