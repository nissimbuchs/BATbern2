/**
 * useUserSearch Hook
 *
 * React Query hook for searching users with debouncing.
 * Story 2.5.2: User Management Frontend
 */

import { useQuery } from '@tanstack/react-query';
import { searchUsers } from '@/services/api/userManagementApi';

interface UseUserSearchOptions {
  query: string;
  enabled?: boolean;
}

export const useUserSearch = ({ query, enabled = true }: UseUserSearchOptions) => {
  return useQuery({
    queryKey: ['users', 'search', query],
    queryFn: () => searchUsers(query),
    staleTime: 5 * 60 * 1000, // 5 minutes
    gcTime: 10 * 60 * 1000, // 10 minutes
    enabled: enabled && query.length >= 2, // Only search if query is at least 2 characters
  });
};
