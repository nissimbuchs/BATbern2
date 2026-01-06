/**
 * React Query hook for fetching public organizer information
 * Used on public About page - no authentication required
 */

import { useQuery } from '@tanstack/react-query';
import { publicOrganizerService } from '@/services/publicOrganizerService';
import type { User } from '@/types/user.types';

/**
 * Fetch all organizers (public information only)
 * No authentication required
 */
export const usePublicOrganizers = () => {
  return useQuery<User[], Error>({
    queryKey: ['public', 'organizers'],
    queryFn: publicOrganizerService.getPublicOrganizers,
    staleTime: 10 * 60 * 1000, // 10 minutes - organizer data doesn't change often
    gcTime: 30 * 60 * 1000, // 30 minutes cache time
    retry: 2,
  });
};
