/**
 * useUpdateUserRoles Hook
 *
 * React Query mutation hook for updating user roles with optimistic updates.
 * Story 2.5.2: User Management Frontend - AC3
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateUserRoles } from '@/services/api/userManagementApi';
import type { User, Role } from '@/types/user.types';

interface UpdateRolesParams {
  id: string;
  roles: Role[];
}

export const useUpdateUserRoles = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, roles }: UpdateRolesParams) => updateUserRoles(id, roles),

    // Optimistic update
    onMutate: async ({ id, roles }) => {
      // Cancel any outgoing refetches
      await queryClient.cancelQueries({ queryKey: ['users', 'list'] });

      // Snapshot the previous value
      const previousData = queryClient.getQueriesData({ queryKey: ['users', 'list'] });

      // Optimistically update all user list queries
      queryClient.setQueriesData(
        { queryKey: ['users', 'list'] },
        (old: { data: User[] } | undefined) => {
          if (!old?.data) return old;

          return {
            ...old,
            data: old.data.map((user: User) => (user.id === id ? { ...user, roles } : user)),
          };
        }
      );

      // Return context with previous data
      return { previousData };
    },

    // On error, rollback to previous state
    onError: (_err, _variables, context) => {
      if (context?.previousData) {
        context.previousData.forEach(([queryKey, data]) => {
          queryClient.setQueryData(queryKey, data);
        });
      }
    },

    // Always refetch after success or error
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['users', 'list'] });
      queryClient.invalidateQueries({ queryKey: ['users', 'detail'] });
    },
  });
};
