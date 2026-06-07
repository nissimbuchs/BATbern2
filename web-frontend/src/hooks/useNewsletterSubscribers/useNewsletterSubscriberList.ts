/**
 * useNewsletterSubscriberList Hook
 *
 * React Query hook for fetching paginated newsletter subscriber list with filters.
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import type { NewsletterSubscriberFilters } from '@/stores/newsletterSubscriberStore';
import { listNewsletterSubscribers } from '@/services/api/newsletterApi';

interface UseNewsletterSubscriberListOptions {
  filters: NewsletterSubscriberFilters;
  pagination: { page: number; limit: number };
  enabled?: boolean;
}

export const useNewsletterSubscriberList = ({
  filters,
  pagination,
  enabled = true,
}: UseNewsletterSubscriberListOptions) => {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['newsletter-subscribers', 'list', { filters, ...pagination }],
    queryFn: () => listNewsletterSubscribers(filters, pagination),
    staleTime: 5 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    placeholderData: keepPreviousData,
    enabled,
  });

  // Prefetch next page when hasNext
  if (query.data?.pagination?.hasNext) {
    queryClient.prefetchQuery({
      queryKey: [
        'newsletter-subscribers',
        'list',
        { filters, page: pagination.page + 1, limit: pagination.limit },
      ],
      queryFn: () =>
        listNewsletterSubscribers(filters, { ...pagination, page: pagination.page + 1 }),
      staleTime: 5 * 60 * 1000,
    });
  }

  return query;
};
