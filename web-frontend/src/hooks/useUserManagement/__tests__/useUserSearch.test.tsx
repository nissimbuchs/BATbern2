import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUserSearch } from '../useUserSearch';
import { searchUsers } from '@/services/api/userManagementApi';
import React, { type ReactNode } from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  searchUsers: vi.fn(),
}));

describe('useUserSearch', () => {
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

  const mockResults = [
    { id: 'user-1', email: 'john@example.com', firstName: 'John', lastName: 'Doe' },
    { id: 'user-2', email: 'jane@example.com', firstName: 'Jane', lastName: 'Doe' },
  ];

  it('should_searchUsers_when_queryIsAtLeast2Chars', async () => {
    vi.mocked(searchUsers).mockResolvedValue(mockResults);

    const { result } = renderHook(() => useUserSearch({ query: 'john' }), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockResults);
    expect(searchUsers).toHaveBeenCalledWith('john');
  });

  it('should_notSearch_when_queryIsLessThan2Chars', () => {
    const { result } = renderHook(() => useUserSearch({ query: 'j' }), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(searchUsers).not.toHaveBeenCalled();
  });

  it('should_notSearch_when_disabledIsTrue', () => {
    const { result } = renderHook(() => useUserSearch({ query: 'john', enabled: false }), {
      wrapper,
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(searchUsers).not.toHaveBeenCalled();
  });

  it('should_search_when_queryIsExactly2Chars', async () => {
    vi.mocked(searchUsers).mockResolvedValue(mockResults);

    const { result } = renderHook(() => useUserSearch({ query: 'jo' }), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(searchUsers).toHaveBeenCalledWith('jo');
  });
});
