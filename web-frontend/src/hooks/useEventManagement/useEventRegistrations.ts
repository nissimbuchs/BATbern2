/**
 * useEventRegistrations Hook
 *
 * React Query hook for fetching event registrations (participants) with filters
 * and pagination.
 *
 * Story 3.3: Event Participants Tab
 */

import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getEventRegistrations } from '@/services/api/eventRegistrationService';
import type { ParticipantFilters, ParticipantPagination } from '@/types/eventParticipant.types';

export interface UseEventRegistrationsOptions {
  eventCode: string;
  filters?: ParticipantFilters;
  pagination?: ParticipantPagination;
  search?: string;
  enabled?: boolean;
}

/**
 * Fetch event registrations with React Query
 *
 * Features:
 * - Automatic caching (5 min stale time)
 * - Keep previous data during refetch (prevents flickering)
 * - Configurable enabled flag
 * - Automatic refetching on filter/pagination changes
 *
 * @param options - Event code, filters, pagination, search, and enabled flag
 * @returns React Query result with registrations data
 */
export const useEventRegistrations = ({
  eventCode,
  filters,
  pagination,
  search,
  enabled = true,
}: UseEventRegistrationsOptions) => {
  // Build query key that includes all parameters affecting the API call
  const queryKey = ['event-registrations', eventCode];

  // Include filters and pagination in query key if provided
  if (filters || pagination) {
    queryKey.push({ filters, pagination });
  }

  // Include search in query key if provided
  if (search) {
    queryKey.push({ search });
  }

  return useQuery({
    queryKey,
    queryFn: () => getEventRegistrations(eventCode, { filters, pagination, search }),
    staleTime: 5 * 60 * 1000, // 5 minutes
    gcTime: 30 * 60 * 1000, // 30 minutes (formerly cacheTime)
    placeholderData: keepPreviousData, // Keep previous data during refetch
    enabled,
  });
};
