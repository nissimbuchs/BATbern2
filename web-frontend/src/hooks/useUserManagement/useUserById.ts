/**
 * useUserById Hook
 *
 * React Query hook for fetching a single user by ID with includes.
 * Story 2.5.2: User Management Frontend
 */

import { useQuery } from '@tanstack/react-query';
import { getUserById } from '@/services/api/userManagementApi';

interface UseUserByIdOptions {
  id: string | null;
  includes?: string[];
  enabled?: boolean;
}

export const useUserById = ({ id, includes, enabled = true }: UseUserByIdOptions) => {
  return useQuery({
    queryKey: ['users', 'detail', id, includes],
    queryFn: () => getUserById(id!, includes),
    staleTime: 5 * 60 * 1000, // 5 minutes
    gcTime: 30 * 60 * 1000, // 30 minutes
    enabled: enabled && !!id,
  });
};
