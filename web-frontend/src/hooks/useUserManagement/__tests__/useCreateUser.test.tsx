import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCreateUser } from '../useCreateUser';
import { createUser } from '@/services/api/userManagementApi';
import React, { type ReactNode } from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  createUser: vi.fn(),
}));

describe('useCreateUser', () => {
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

  const mockFormData = {
    email: 'newuser@example.com',
    firstName: 'New',
    lastName: 'User',
    roles: ['USER'],
    companyId: 'company-123',
  };

  const mockCreatedUser = {
    id: 'user-new',
    ...mockFormData,
  };

  it('should_createUser_when_mutationCalled', async () => {
    vi.mocked(createUser).mockResolvedValue(mockCreatedUser);

    const { result } = renderHook(() => useCreateUser(), { wrapper });

    result.current.mutate(mockFormData);

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(createUser).toHaveBeenCalledWith(mockFormData);
    expect(result.current.data).toEqual(mockCreatedUser);
  });

  it('should_invalidateQueries_when_createSucceeds', async () => {
    vi.mocked(createUser).mockResolvedValue(mockCreatedUser);

    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCreateUser(), { wrapper });

    result.current.mutate(mockFormData);

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'search'] });
  });

  it('should_handleError_when_createFails', async () => {
    const error = new Error('Creation failed');
    vi.mocked(createUser).mockRejectedValue(error);

    const { result } = renderHook(() => useCreateUser(), { wrapper });

    result.current.mutate(mockFormData);

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toEqual(error);
  });
});
