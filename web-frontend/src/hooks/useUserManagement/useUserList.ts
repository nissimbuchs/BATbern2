/**
 * useUserList Hook
 *
 * React Query hook for fetching paginated user list with filters.
 * Story 2.5.2: User Management Frontend
 */

import { useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import type { UserFilters, UserPagination } from '@/types/user.types';
import { listUsers } from '@/services/api/userManagementApi';

interface UseUserListOptions {
  filters: UserFilters;
  pagination: UserPagination;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  enabled?: boolean;
}

export const useUserList = ({
  filters,
  pagination,
  sortBy,
  sortDir,
  enabled = true,
}: UseUserListOptions) => {
  const queryClient = useQueryClient();

  // Extract only the properties that affect the API call for the query key
  const { page, limit } = pagination;

  const query = useQuery({
    queryKey: ['users', 'list', { filters, page, limit, sortBy, sortDir }],
    queryFn: () => listUsers(filters, pagination, undefined, sortBy, sortDir),
    staleTime: 5 * 60 * 1000, // 5 minutes
    gcTime: 30 * 60 * 1000, // 30 minutes (formerly cacheTime)
    placeholderData: keepPreviousData, // Keep previous data during refetch (prevents "page reload" effect)
    enabled,
  });

  // Prefetch next page in background
  const prefetchNextPage = () => {
    if (pagination.hasNext && pagination.page) {
      queryClient.prefetchQuery({
        queryKey: [
          'users',
          'list',
          { filters, page: pagination.page + 1, limit: pagination.limit, sortBy, sortDir },
        ],
        queryFn: () =>
          listUsers(
            filters,
            { ...pagination, page: pagination.page! + 1 },
            undefined,
            sortBy,
            sortDir
          ),
        staleTime: 5 * 60 * 1000,
      });
    }
  };

  return {
    ...query,
    prefetchNextPage,
  };
};
