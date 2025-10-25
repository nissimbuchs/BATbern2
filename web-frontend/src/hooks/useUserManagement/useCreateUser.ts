/**
 * useCreateUser Hook
 *
 * React Query mutation hook for creating new users.
 * Story 2.5.2: User Management Frontend
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createUser } from '@/services/api/userManagementApi';
import type { CreateUserFormData } from '@/types/user.types';

export const useCreateUser = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateUserFormData) => createUser(data),
    onSuccess: () => {
      // Invalidate all user list queries to refetch
      queryClient.invalidateQueries({ queryKey: ['users', 'list'] });
      queryClient.invalidateQueries({ queryKey: ['users', 'search'] });
    },
  });
};
