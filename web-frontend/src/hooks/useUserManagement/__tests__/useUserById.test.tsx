import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUserById } from '../useUserById';
import { getUserById } from '@/services/api/userManagementApi';
import React, { type ReactNode } from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  getUserById: vi.fn(),
}));

describe('useUserById', () => {
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

  const mockUser = {
    id: 'user-123',
    email: 'test@example.com',
    firstName: 'John',
    lastName: 'Doe',
    roles: ['USER'],
  };

  it('should_fetchUser_when_idProvided', async () => {
    vi.mocked(getUserById).mockResolvedValue(mockUser);

    const { result } = renderHook(() => useUserById({ id: 'user-123' }), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockUser);
    expect(getUserById).toHaveBeenCalledWith('user-123', undefined);
  });

  it('should_notFetch_when_idIsNull', () => {
    const { result } = renderHook(() => useUserById({ id: null }), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(getUserById).not.toHaveBeenCalled();
  });

  it('should_notFetch_when_disabledIsTrue', () => {
    const { result } = renderHook(() => useUserById({ id: 'user-123', enabled: false }), {
      wrapper,
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(getUserById).not.toHaveBeenCalled();
  });

  it('should_passIncludes_when_provided', async () => {
    vi.mocked(getUserById).mockResolvedValue(mockUser);

    const includes = ['company', 'roles'];
    const { result } = renderHook(() => useUserById({ id: 'user-123', includes }), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(getUserById).toHaveBeenCalledWith('user-123', includes);
  });
});
