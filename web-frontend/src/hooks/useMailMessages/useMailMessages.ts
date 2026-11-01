/**
 * useMailMessages (Story 16.3)
 *
 * React Query hooks for the event's mail page ("What went out", "Handled automatically").
 */

import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from '@tanstack/react-query';
import * as mailMessageService from '@/services/mailMessageService';
import type {
  MailComposeRequest,
  MailCounts,
  MailMessageDetail,
  MailMessageListParams,
  MailMessagePage,
  MailPreviewResponse,
  MailSendRequest,
} from '@/services/mailMessageService';

export const MAIL_MESSAGE_QUERY_KEYS = {
  /** Prefix of every mail query of an event; invalidate it after a send (Story 16.4). */
  event: (eventCode: string) => ['mails', eventCode] as const,
  list: (eventCode: string, params: MailMessageListParams) =>
    ['mails', eventCode, 'list', params] as const,
  detail: (eventCode: string, mailKey: string) => ['mails', eventCode, 'detail', mailKey] as const,
  counts: (eventCode: string) => ['mails', eventCode, 'counts'] as const,
  preview: (eventCode: string, request: MailComposeRequest) =>
    ['mails', eventCode, 'preview', request] as const,
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

/**
 * One mail as sent. With `poll` it refetches every 3 s while its newsletter job is still sending
 * (`progress.status` PENDING or IN_PROGRESS), as the old `useSendStatus` did (Story 16.8 AC13).
 */
export function useMailMessage(
  eventCode: string,
  mailKey: string | null,
  options: { poll?: boolean } = {}
): UseQueryResult<MailMessageDetail, Error> {
  return useQuery({
    queryKey: MAIL_MESSAGE_QUERY_KEYS.detail(eventCode, mailKey ?? ''),
    queryFn: () => mailMessageService.getMailMessage(eventCode, mailKey as string),
    enabled: mailKey !== null,
    staleTime: 30 * 1000,
    refetchInterval: (query) => {
      const status = query.state.data?.progress?.status;
      return options.poll && status && ACTIVE_NEWSLETTER_STATUSES.includes(status)
        ? ACTIVE_POLL_MS
        : false;
    },
  });
}

/**
 * Story 16.8: the composer preview. Keyed on the whole request, so stepping ‹ › (recipientIndex)
 * or changing a field fetches again; the previous preview stays visible meanwhile. `null` = no
 * request yet (e.g. a required field is missing).
 */
export function useMailPreview(
  eventCode: string,
  request: MailComposeRequest | null
): UseQueryResult<MailPreviewResponse, Error> {
  return useQuery({
    queryKey: MAIL_MESSAGE_QUERY_KEYS.preview(eventCode, request as MailComposeRequest),
    queryFn: () => mailMessageService.previewMail(eventCode, request as MailComposeRequest),
    enabled: request !== null,
    placeholderData: keepPreviousData,
    staleTime: 0,
    retry: false,
  });
}

/** Every query of the event's mail page except the composer preview, which a send does not change. */
const notPreview = (eventCode: string) => ({
  queryKey: MAIL_MESSAGE_QUERY_KEYS.event(eventCode),
  predicate: (query: { queryKey: readonly unknown[] }) => query.queryKey[2] !== 'preview',
});

/** Story 16.8: test send (organizers) or real send. Either refreshes the event's mail list. */
export function useSendMail(eventCode: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: MailSendRequest) => mailMessageService.sendMail(eventCode, request),
    onSuccess: () => {
      void queryClient.invalidateQueries(notPreview(eventCode));
    },
  });
}

/** Story 16.8 AC8: retry a newsletter's failed recipients. */
export function useRetryMail(eventCode: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mailKey: string) => mailMessageService.retryMail(eventCode, mailKey),
    onSuccess: () => {
      void queryClient.invalidateQueries(notPreview(eventCode));
    },
  });
}

export function useMailCounts(eventCode: string): UseQueryResult<MailCounts, Error> {
  return useQuery({
    queryKey: MAIL_MESSAGE_QUERY_KEYS.counts(eventCode),
    queryFn: () => mailMessageService.getMailCounts(eventCode),
    staleTime: 30 * 1000,
  });
}
