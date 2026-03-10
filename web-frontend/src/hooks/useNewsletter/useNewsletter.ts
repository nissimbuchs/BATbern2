/**
 * useNewsletter Hook (Story 10.7)
 *
 * React Query hooks for newsletter subscription and sending.
 */

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import * as newsletterService from '@/services/newsletterService';
import type {
  NewsletterSubscribeRequest,
  NewsletterSubscriptionStatusResponse,
  NewsletterSendRequest,
  NewsletterSendResponse,
  NewsletterSendStatusResponse,
  NewsletterPreviewResponse,
  NewsletterSendHistoryItem,
  SubscriberCountResponse,
} from '@/services/newsletterService';

export const NEWSLETTER_QUERY_KEYS = {
  mySubscription: ['newsletter', 'my-subscription'] as const,
  subscriberCount: ['newsletter', 'subscriber-count'] as const,
  history: (eventCode: string) => ['newsletter', 'history', eventCode] as const,
};

/** Subscribe anonymous email to newsletter. */
export function useNewsletterSubscribe(): UseMutationResult<
  void,
  Error,
  NewsletterSubscribeRequest
> {
  return useMutation({
    mutationFn: newsletterService.subscribe,
  });
}

/** Verify unsubscribe token — returns email if valid. */
export function useVerifyUnsubscribeToken(
  token: string | null
): UseQueryResult<{ email: string }, Error> {
  return useQuery({
    queryKey: ['newsletter', 'verify-token', token],
    queryFn: () => newsletterService.verifyUnsubscribeToken(token!),
    enabled: !!token,
    retry: false,
  });
}

/** Unsubscribe by token. */
export function useUnsubscribeByToken(): UseMutationResult<void, Error, string> {
  return useMutation({
    mutationFn: newsletterService.unsubscribeByToken,
  });
}

/** Get authenticated user's subscription status. */
export function useMySubscription(): UseQueryResult<NewsletterSubscriptionStatusResponse, Error> {
  return useQuery({
    queryKey: NEWSLETTER_QUERY_KEYS.mySubscription,
    queryFn: newsletterService.getMySubscription,
    staleTime: 5 * 60 * 1000,
  });
}

/** Toggle authenticated user subscription. */
export function usePatchMySubscription(): UseMutationResult<
  NewsletterSubscriptionStatusResponse,
  Error,
  { subscribed: boolean; language?: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ subscribed, language }) =>
      newsletterService.patchMySubscription(subscribed, language),
    onSuccess: (data) => {
      queryClient.setQueryData(NEWSLETTER_QUERY_KEYS.mySubscription, data);
    },
  });
}

/** Get total active subscriber count (organizer). */
export function useSubscriberCount(): UseQueryResult<SubscriberCountResponse, Error> {
  return useQuery({
    queryKey: NEWSLETTER_QUERY_KEYS.subscriberCount,
    queryFn: newsletterService.getSubscriberCount,
    staleTime: 60 * 1000,
  });
}

/** Get newsletter send history for an event. */
export function useNewsletterHistory(
  eventCode: string
): UseQueryResult<NewsletterSendHistoryItem[], Error> {
  return useQuery({
    queryKey: NEWSLETTER_QUERY_KEYS.history(eventCode),
    queryFn: () => newsletterService.getNewsletterHistory(eventCode),
    staleTime: 30 * 1000,
  });
}

/** Preview newsletter for an event. */
export function useNewsletterPreview(): UseMutationResult<
  NewsletterPreviewResponse,
  Error,
  { eventCode: string; request: NewsletterSendRequest }
> {
  return useMutation({
    mutationFn: ({ eventCode, request }) => newsletterService.previewNewsletter(eventCode, request),
  });
}

/** Send newsletter for an event. Returns immediately with PENDING status and a sendId. */
export function useSendNewsletter(
  eventCode: string
): UseMutationResult<NewsletterSendResponse, Error, NewsletterSendRequest> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request) => newsletterService.sendNewsletter(eventCode, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NEWSLETTER_QUERY_KEYS.history(eventCode) });
      queryClient.invalidateQueries({ queryKey: NEWSLETTER_QUERY_KEYS.subscriberCount });
    },
  });
}

const TERMINAL_STATUSES = new Set(['COMPLETED', 'PARTIAL', 'FAILED']);

/**
 * Poll send-job status every 3 seconds while status is PENDING or IN_PROGRESS.
 * Stops polling automatically when a terminal status is reached.
 */
export function useSendStatus(
  eventCode: string,
  sendId: string | null
): UseQueryResult<NewsletterSendStatusResponse, Error> {
  return useQuery({
    queryKey: ['newsletter', 'send-status', eventCode, sendId],
    queryFn: () => newsletterService.getSendStatus(eventCode, sendId!),
    enabled: !!sendId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (!status || TERMINAL_STATUSES.has(status)) {
        return false;
      }
      return 3000;
    },
    staleTime: 0,
  });
}

/** Retry failed recipients for a PARTIAL or FAILED send. */
export function useRetryFailedRecipients(
  eventCode: string
): UseMutationResult<NewsletterSendResponse, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sendId) => newsletterService.retryFailedRecipients(eventCode, sendId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NEWSLETTER_QUERY_KEYS.history(eventCode) });
    },
  });
}
