import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useDeleteUser } from '../useDeleteUser';
import { deleteUser } from '@/services/api/userManagementApi';
import React, { type ReactNode } from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  deleteUser: vi.fn(),
}));

describe('useDeleteUser', () => {
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

  it('should_deleteUser_when_mutationCalled', async () => {
    vi.mocked(deleteUser).mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteUser(), { wrapper });

    result.current.mutate('user-123');

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(deleteUser).toHaveBeenCalledWith('user-123');
  });

  it('should_invalidateQueries_when_deleteSucceeds', async () => {
    vi.mocked(deleteUser).mockResolvedValue(undefined);

    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteUser(), { wrapper });

    result.current.mutate('user-123');

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'search'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'detail'] });
  });

  it('should_handleError_when_deleteFails', async () => {
    const error = new Error('Deletion failed');
    vi.mocked(deleteUser).mockRejectedValue(error);

    const { result } = renderHook(() => useDeleteUser(), { wrapper });

    result.current.mutate('user-123');

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toEqual(error);
  });
});
