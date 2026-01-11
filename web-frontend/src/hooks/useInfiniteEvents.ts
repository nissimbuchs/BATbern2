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

  // Convert time period to year filter
  if (archiveFilters.timePeriod && archiveFilters.timePeriod !== 'all') {
    const currentYear = new Date().getFullYear();
    switch (archiveFilters.timePeriod) {
      case 'last5y':
        eventFilters.year = currentYear - 5; // Last 5 years (backend >= comparison)
        break;
      case '2020-2024':
        eventFilters.year = 2020;
        // Note: Backend year filter uses >= so this will include 2020+
        break;
      case '2015-2019':
        eventFilters.year = 2015;
        break;
      case '2010-2014':
        eventFilters.year = 2010;
        break;
      case 'before2010':
        eventFilters.year = 1900; // Use very old year for "before 2010"
        break;
    }
  }

  // TODO: Handle topics filter when backend supports topicCode filtering
  // Currently topics expansion is done via include=topics, not filtering
  // if (archiveFilters.topics && archiveFilters.topics.length > 0) {
  //   eventFilters.topicCode = { $in: archiveFilters.topics };
  // }

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
