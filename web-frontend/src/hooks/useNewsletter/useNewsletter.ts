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
  PatchMyNewsletterSubscriptionRequest,
} from '@/services/newsletterService';

export const NEWSLETTER_QUERY_KEYS = {
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
    // #818 defect 1: subscribing while logged in was not reflected on My Profile. This is the
    // PUBLIC subscribe endpoint and it had no onSuccess at all, so the per-address query that
    // the profile reads kept serving its cached "not subscribed" answer for the full 5-minute
    // staleTime. Invalidate rather than setQueryData: this endpoint returns void, so the
    // authoritative state has to be re-fetched.
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NEWSLETTER_QUERY_KEYS.mySubscriptions });
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

/**
 * #1244: the authenticated user's newsletter state per address. `enabled` lets the public
 * NewsletterSubscribeWidget pass `isAuthenticated`: the endpoint requires auth, and querying it
 * for anonymous visitors would fire a guaranteed 401 on every public page view (#818).
 */
export function useMySubscriptions(options?: {
  enabled?: boolean;
}): UseQueryResult<MyNewsletterSubscriptionsResponse, Error> {
  return useQuery({
    queryKey: NEWSLETTER_QUERY_KEYS.mySubscriptions,
    queryFn: newsletterService.getMySubscriptions,
    staleTime: 5 * 60 * 1000,
    enabled: options?.enabled ?? true,
  });
}

/** 4.10c: change one address of the authenticated user, only the fields given. */
export function usePatchMySubscription(): UseMutationResult<
  MyNewsletterSubscriptionsResponse,
  Error,
  PatchMyNewsletterSubscriptionRequest
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: newsletterService.patchMySubscription,
    onSuccess: (data) => {
      queryClient.setQueryData(NEWSLETTER_QUERY_KEYS.mySubscriptions, data);
    },
  });
}
