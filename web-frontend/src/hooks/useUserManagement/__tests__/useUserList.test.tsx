import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUserList } from '../useUserList';
import { listUsers } from '@/services/api/userManagementApi';
import React, { type ReactNode } from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  listUsers: vi.fn(),
}));

describe('useUserList', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const mockUsers = {
    data: [
      { id: 'user-1', email: 'user1@example.com' },
      { id: 'user-2', email: 'user2@example.com' },
    ],
    pagination: { total: 2, page: 1, limit: 10, totalPages: 1 },
  };

  it('should_fetchUsers_when_enabled', async () => {
    vi.mocked(listUsers).mockResolvedValue(mockUsers);

    const { result } = renderHook(
      () =>
        useUserList({
          filters: {},
          pagination: { page: 1, limit: 10 },
        }),
      { wrapper }
    );

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockUsers);
  });

  it('should_notFetch_when_disabled', () => {
    const { result } = renderHook(
      () =>
        useUserList({
          filters: {},
          pagination: { page: 1, limit: 10 },
          enabled: false,
        }),
      { wrapper }
    );

    expect(result.current.fetchStatus).toBe('idle');
    expect(listUsers).not.toHaveBeenCalled();
  });

  it('should_prefetchNextPage_when_hasNext', async () => {
    vi.mocked(listUsers).mockResolvedValue(mockUsers);

    const prefetchSpy = vi.spyOn(queryClient, 'prefetchQuery');

    const { result } = renderHook(
      () =>
        useUserList({
          filters: {},
          pagination: { page: 1, limit: 10, hasNext: true },
        }),
      { wrapper }
    );

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.prefetchNextPage();

    expect(prefetchSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: ['users', 'list', { filters: {}, page: 2, limit: 10 }],
      })
    );
  });

  it('should_notPrefetch_when_noNextPage', async () => {
    vi.mocked(listUsers).mockResolvedValue(mockUsers);

    const prefetchSpy = vi.spyOn(queryClient, 'prefetchQuery');

    const { result } = renderHook(
      () =>
        useUserList({
          filters: {},
          pagination: { page: 1, limit: 10, hasNext: false },
        }),
      { wrapper }
    );

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.prefetchNextPage();

    expect(prefetchSpy).not.toHaveBeenCalled();
  });
});
