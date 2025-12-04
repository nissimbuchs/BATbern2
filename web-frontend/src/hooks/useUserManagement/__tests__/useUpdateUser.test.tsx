/**
 * useUpdateUser Hook Tests
 * Tests for updating user profile mutation hook
 */

import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useUpdateUser } from '../useUpdateUser';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as userManagementApi from '@/services/api/userManagementApi';
import type { User } from '@/types/user.types';

// Mock the API
vi.mock('@/services/api/userManagementApi', () => ({
  updateUser: vi.fn(),
}));

const mockUser: User = {
  id: 'user-1',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john.doe@example.com',
  companyId: 'company-1',
  roles: ['ORGANIZER'],
  active: true,
  profilePictureUrl: null,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-02T00:00:00Z',
};

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

describe('useUpdateUser Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Mutation', () => {
    it('should_callUpdateUserApi_when_mutationInvoked', async () => {
      vi.mocked(userManagementApi.updateUser).mockResolvedValue(mockUser);

      const { result } = renderHook(() => useUpdateUser(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({
        username: 'john.doe',
        data: {
          firstName: 'Jane',
          lastName: 'Doe',
        },
      });

      await waitFor(() => {
        expect(userManagementApi.updateUser).toHaveBeenCalledWith('john.doe', {
          firstName: 'Jane',
          lastName: 'Doe',
        });
      });
    });

    it('should_returnUpdatedUser_when_mutationSucceeds', async () => {
      const updatedUser = { ...mockUser, firstName: 'Jane' };
      vi.mocked(userManagementApi.updateUser).mockResolvedValue(updatedUser);

      const { result } = renderHook(() => useUpdateUser(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({
        username: 'john.doe',
        data: {
          firstName: 'Jane',
        },
      });

      await waitFor(() => {
        expect(result.current.data).toEqual(updatedUser);
      });
    });

    it('should_handleError_when_mutationFails', async () => {
      const error = new Error('Update failed');
      vi.mocked(userManagementApi.updateUser).mockRejectedValue(error);

      const { result } = renderHook(() => useUpdateUser(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({
        username: 'john.doe',
        data: {
          firstName: 'Jane',
        },
      });

      await waitFor(() => {
        expect(result.current.error).toEqual(error);
      });
    });
  });

  describe('Query Invalidation', () => {
    it('should_invalidateUsersQuery_when_mutationSucceeds', async () => {
      vi.mocked(userManagementApi.updateUser).mockResolvedValue(mockUser);

      const queryClient = new QueryClient({
        defaultOptions: {
          queries: { retry: false },
          mutations: { retry: false },
        },
      });

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      );

      const { result } = renderHook(() => useUpdateUser(), { wrapper });

      result.current.mutate({
        username: 'john.doe',
        data: {
          firstName: 'Jane',
        },
      });

      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user', 'john.doe'] });
      });
    });
  });
});
