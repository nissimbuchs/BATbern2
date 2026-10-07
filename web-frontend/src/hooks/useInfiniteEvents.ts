/**
 * useInfiniteEvents Hook (Story 4.2 - Task 2b)
 *
 * React Query infinite scroll hook for archive browsing
 * Handles pagination, filtering, sorting, and caching
 */

import { useInfiniteQuery } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import type { ArchiveFilters } from '@/types/event.types';

/**
 * Infinite scroll hook for events archive
 *
 * @param filters - Archive filters (time period, topics, search)
 * @param sort - Sort parameter (e.g., '-date' for newest first)
 * @returns React Query infinite query result with pagination controls
 */
export function useInfiniteEvents(filters: ArchiveFilters = {}, sort: string = '-date') {
  // Convert ArchiveFilters to EventFilters for API
  return useInfiniteQuery({
    queryKey: ['events', 'archive', filters, sort],
    queryFn: async ({ pageParam = 1 }) => {
      // Public Events read model (2026-10-07): the server restricts the archive scope to archived
      // events and shapes sessions/speakers; the client only passes search, topics and sort.
      const result = await eventApiClient.getPublicEvents({
        scope: 'archive',
        page: pageParam,
        limit: 20,
        search: filters.search,
        topicCodes: filters.topics,
        // Oldest first for 'date' / '+date'; newest first otherwise (the archive default)
        sort: sort === 'date' || sort === '+date' ? 'date' : '-date',
      });
      return result;
    },
    getNextPageParam: (lastPage) => {
      const { hasNext, page } = lastPage.pagination;
      return hasNext ? page + 1 : undefined;
    },
    initialPageParam: 1,
    staleTime: 5 * 60 * 1000, // 5 minutes - cache results to reduce API calls
  });
}
