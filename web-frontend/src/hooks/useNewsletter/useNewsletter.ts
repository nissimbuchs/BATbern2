/**
 * useNewsletter Hook (Story 10.7)
 *
 * React Query hooks for newsletter subscriptions. Sending is the Mail Center composer's (Story 16.8).
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
  MyNewsletterSubscriptionsResponse,
  NewsletterSubscribeRequest,
  NewsletterSubscriptionStatusResponse,
} from '@/services/newsletterService';

export const NEWSLETTER_QUERY_KEYS = {
  mySubscription: ['newsletter', 'my-subscription'] as const,
  mySubscriptions: ['newsletter', 'my-subscriptions'] as const,
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

/** #1244: the authenticated user's newsletter state per address. */
export function useMySubscriptions(): UseQueryResult<MyNewsletterSubscriptionsResponse, Error> {
  return useQuery({
    queryKey: NEWSLETTER_QUERY_KEYS.mySubscriptions,
    queryFn: newsletterService.getMySubscriptions,
    staleTime: 5 * 60 * 1000,
  });
}

/** #1244: subscribe or unsubscribe one address of the authenticated user. */
export function usePutMySubscription(): UseMutationResult<
  MyNewsletterSubscriptionsResponse,
  Error,
  { email: string; subscribed: boolean; language?: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ email, subscribed, language }) =>
      newsletterService.putMySubscription(email, subscribed, language),
    onSuccess: (data) => {
      queryClient.setQueryData(NEWSLETTER_QUERY_KEYS.mySubscriptions, data);
      queryClient.invalidateQueries({ queryKey: NEWSLETTER_QUERY_KEYS.mySubscription });
    },
  });
}
