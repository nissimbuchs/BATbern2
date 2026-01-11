/**
 * useInfiniteEvents Hook (Story 4.2 - Task 2b)
 *
 * React Query infinite scroll hook for archive browsing
 * Handles pagination, filtering, sorting, and caching
 */

import { useInfiniteQuery } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import type { ArchiveFilters, EventFilters } from '@/types/event.types';

/**
 * Convert ArchiveFilters to EventFilters for API
 * Story BAT-109: Archive browsing filter conversion
 */
function convertToEventFilters(archiveFilters: ArchiveFilters): EventFilters {
  const eventFilters: EventFilters = {
    includeArchived: true, // Archive page shows archived events
    workflowState: ['ARCHIVED'], // Only show archived events
    search: archiveFilters.search,
  };

  // Topic filter - filter by topicCode
  if (archiveFilters.topics && archiveFilters.topics.length > 0) {
    eventFilters.topicCode = archiveFilters.topics;
  }

  return eventFilters;
}

/**
 * Infinite scroll hook for events archive
 *
 * @param filters - Archive filters (time period, topics, search)
 * @param sort - Sort parameter (e.g., '-date' for newest first)
 * @returns React Query infinite query result with pagination controls
 */
export function useInfiniteEvents(filters: ArchiveFilters = {}, sort: string = '-date') {
  // Convert ArchiveFilters to EventFilters for API
  const eventFilters = convertToEventFilters(filters);

  return useInfiniteQuery({
    queryKey: ['events', 'archive', filters, sort],
    queryFn: ({ pageParam = 1 }) =>
      eventApiClient.getEvents({ page: pageParam, limit: 20 }, eventFilters, {
        expand: ['topics', 'sessions', 'speakers'],
        sort,
      }),
    getNextPageParam: (lastPage) => {
      const { page, pages } = lastPage.pagination;
      return page < pages ? page + 1 : undefined;
    },
    initialPageParam: 1,
    staleTime: 5 * 60 * 1000, // 5 minutes - cache results to reduce API calls
  });
}
