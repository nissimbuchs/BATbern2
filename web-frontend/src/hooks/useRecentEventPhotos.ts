/**
 * Hook for recent event photos (homepage marquee).
 * Story 10.21: Event Photos Gallery — AC6
 */
import { useQuery } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';

export const useRecentEventPhotos = (limit = 20, lastNEvents = 5) => {
  return useQuery({
    queryKey: ['recent-event-photos', limit, lastNEvents],
    queryFn: () => eventApiClient.getRecentEventPhotos(limit, lastNEvents),
    staleTime: 5 * 60 * 1000, // 5 min — homepage doesn't need real-time refresh
  });
};
