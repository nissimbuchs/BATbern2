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

export interface NewsletterSubscribeMutationVars {
  request: NewsletterSubscribeRequest;
  turnstileToken?: string | null;
}

/** Subscribe anonymous email to newsletter. Accepts optional turnstileToken (Story 10.31, AC8). */
export function useNewsletterSubscribe(): UseMutationResult<
  void,
  Error,
  NewsletterSubscribeMutationVars
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ request, turnstileToken }) =>
      newsletterService.subscribe(request, turnstileToken),
    // #818 defect 1: subscribing while logged in was not reflected on My Profile -> Consent.
    // This is the PUBLIC subscribe endpoint and it had no onSuccess at all, so the
    // `mySubscription` query that ProfilePage reads kept serving its cached "not subscribed"
    // answer for the full 5-minute staleTime. Invalidate rather than setQueryData: this
    // endpoint returns void, so the authoritative state has to be re-fetched.
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NEWSLETTER_QUERY_KEYS.mySubscription });
    },
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
export function useMySubscription(options?: {
  enabled?: boolean;
}): UseQueryResult<NewsletterSubscriptionStatusResponse, Error> {
  return useQuery({
    queryKey: NEWSLETTER_QUERY_KEYS.mySubscription,
    queryFn: newsletterService.getMySubscription,
    staleTime: 5 * 60 * 1000,
    // Defaults to enabled so existing callers (ProfilePage) are unchanged. The public
    // NewsletterSubscribeWidget passes `enabled: isAuthenticated` because this endpoint requires
    // auth — querying it for anonymous visitors would fire a guaranteed 401 on every public page
    // view (#818).
    enabled: options?.enabled ?? true,
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
    onSuccess: (_data, sendId) => {
      queryClient.invalidateQueries({ queryKey: NEWSLETTER_QUERY_KEYS.history(eventCode) });
      // Reset the cached send-status so the terminal PARTIAL/FAILED state is cleared
      // and polling resumes (refetchInterval sees undefined status → returns 3000ms).
      queryClient.resetQueries({
        queryKey: ['newsletter', 'send-status', eventCode, sendId],
      });
    },
  });
}
