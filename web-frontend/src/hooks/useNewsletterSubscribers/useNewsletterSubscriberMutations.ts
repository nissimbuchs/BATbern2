/**
 * useNewsletterSubscriberMutations Hook
 *
 * React Query mutations for newsletter subscriber management actions.
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  unsubscribeNewsletterSubscriber,
  resubscribeNewsletterSubscriber,
  deleteNewsletterSubscriber,
  unsuppressNewsletterSubscriber,
} from '@/services/api/newsletterApi';

export const useUnsubscribeSubscriber = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => unsubscribeNewsletterSubscriber(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['newsletter-subscribers'] });
    },
  });
};

export const useResubscribeSubscriber = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => resubscribeNewsletterSubscriber(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['newsletter-subscribers'] });
    },
  });
};

export const useDeleteSubscriber = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteNewsletterSubscriber(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['newsletter-subscribers'] });
    },
  });
};

/** Story 10.29 AC8: Unsuppress a bounced subscriber. */
export const useUnsuppressSubscriber = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => unsuppressNewsletterSubscriber(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['newsletter-subscribers'] });
    },
  });
};
