/**
 * useCurrentEvent Hook
 * Story 4.1.3: Event Landing Page Hero Section
 *
 * React Query hook for fetching the current published event.
 * Used by the public website landing page.
 */

import { useQuery } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import type { EventDetail } from '@/types/event.types';

/**
 * Fetch current published event with optional resource expansion
 *
 * @param options Optional configuration for the query
 * @returns React Query result with current event data
 */
export const useCurrentEvent = (options?: { retry?: number | false }) => {
  return useQuery<EventDetail | null, Error>({
    queryKey: ['events', 'public', 'current'],
    // Public Events read model (2026-10-07): sessions/speakers are shaped by the publishing
    // phase on the server, the same for anonymous visitors and logged-in organizers.
    queryFn: () => eventApiClient.getPublicCurrentEvent(),
    staleTime: 5 * 60 * 1000, // 5 minutes (event data doesn't change frequently)
    retry: options?.retry ?? 2, // Default to 2 retries, but allow override for testing
    refetchOnWindowFocus: false,
  });
};
