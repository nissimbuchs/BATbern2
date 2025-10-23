/**
 * useDeleteUser Hook
 *
 * React Query mutation hook for deleting users (GDPR compliant).
 * Story 2.5.2: User Management Frontend - AC5
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deleteUser } from '@/services/api/userManagementApi';

export const useDeleteUser = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => deleteUser(id),

    onSuccess: () => {
      // Invalidate all user queries to refetch
      queryClient.invalidateQueries({ queryKey: ['users', 'list'] });
      queryClient.invalidateQueries({ queryKey: ['users', 'search'] });
      queryClient.invalidateQueries({ queryKey: ['users', 'detail'] });
    },
  });
};
