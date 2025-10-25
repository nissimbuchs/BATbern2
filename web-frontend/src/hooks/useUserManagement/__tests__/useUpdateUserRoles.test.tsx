import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUpdateUserRoles } from '../useUpdateUserRoles';
import { updateUserRoles } from '@/services/api/userManagementApi';
import React, { type ReactNode } from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  updateUserRoles: vi.fn(),
}));

describe('useUpdateUserRoles', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const mockUpdatedUser = {
    id: 'user-123',
    email: 'test@example.com',
    roles: ['USER', 'ORGANIZER'],
  };

  it('should_updateRoles_when_mutationCalled', async () => {
    vi.mocked(updateUserRoles).mockResolvedValue(mockUpdatedUser);

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    result.current.mutate({ id: 'user-123', roles: ['USER', 'ORGANIZER'] });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(updateUserRoles).toHaveBeenCalledWith('user-123', ['USER', 'ORGANIZER']);
  });

  it('should_invalidateQueries_when_updateSucceeds', async () => {
    vi.mocked(updateUserRoles).mockResolvedValue(mockUpdatedUser);

    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    result.current.mutate({ id: 'user-123', roles: ['USER', 'ORGANIZER'] });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'detail'] });
  });

  it('should_performOptimisticUpdate_when_mutating', async () => {
    vi.mocked(updateUserRoles).mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(mockUpdatedUser), 100))
    );

    // Pre-populate cache with user list
    queryClient.setQueryData(['users', 'list'], {
      data: [
        { id: 'user-123', email: 'test@example.com', roles: ['USER'] },
        { id: 'user-456', email: 'other@example.com', roles: ['USER'] },
      ],
    });

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    result.current.mutate({ id: 'user-123', roles: ['USER', 'ORGANIZER'] });

    // Check optimistic update
    await waitFor(() => {
      const cachedData = queryClient.getQueryData<{ data: unknown[] }>(['users', 'list']);
      expect(cachedData?.data[0]).toMatchObject({
        id: 'user-123',
        roles: ['USER', 'ORGANIZER'],
      });
    });
  });

  it('should_rollbackOnError_when_updateFails', async () => {
    const error = new Error('Update failed');
    vi.mocked(updateUserRoles).mockRejectedValue(error);

    // Pre-populate cache
    const originalData = {
      data: [{ id: 'user-123', email: 'test@example.com', roles: ['USER'] }],
    };
    queryClient.setQueryData(['users', 'list'], originalData);

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    result.current.mutate({ id: 'user-123', roles: ['USER', 'ORGANIZER'] });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    // Verify rollback
    const cachedData = queryClient.getQueryData(['users', 'list']);
    expect(cachedData).toEqual(originalData);
  });
});
