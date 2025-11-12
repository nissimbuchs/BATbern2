/**
 * useUpdateUser Hook
 * React Query mutation hook for updating user profile
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateUser, type UpdateUserData } from '@/services/api/userManagementApi';
import type { User } from '@/types/user.types';

interface UpdateUserParams {
  username: string;
  data: UpdateUserData;
}

/**
 * Hook to update user profile
 * Invalidates user queries on success
 */
export function useUpdateUser() {
  const queryClient = useQueryClient();

  return useMutation<User, Error, UpdateUserParams>({
    mutationFn: async ({ username, data }: UpdateUserParams) => {
      return await updateUser(username, data);
    },
    onSuccess: (_, variables) => {
      // Invalidate relevant queries
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['user', variables.username] });
    },
  });
}
