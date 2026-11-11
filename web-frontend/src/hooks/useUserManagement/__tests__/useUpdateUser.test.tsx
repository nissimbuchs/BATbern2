/**
 * useUpdateUser Hook Tests
 * Tests for the organizer's user-profile mutation hook. Story 4.10: on the MSW harness (touch
 * rule); the real userManagementApi sends `PATCH /users/{username}` (the PUT twin is gone).
 */

import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { HttpResponse } from 'msw';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUpdateUser } from '../useUpdateUser';
import { api } from '@/test/msw/api';
import { aUser } from '@/test/fixtures';

const stored = aUser({ id: 'matthias.keller.2', firstName: 'Matthias', lastName: 'Keller' });

const createClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

const wrapperFor =
  (queryClient: QueryClient) =>
  ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

describe('useUpdateUser Hook', () => {
  describe('Mutation', () => {
    it('should_patchTheUser_when_mutationInvoked', async () => {
      api.on('patchUser', ({ body }) => ({ ...stored, ...body }));

      const { result } = renderHook(() => useUpdateUser(), {
        wrapper: wrapperFor(createClient()),
      });

      result.current.mutate({
        username: 'matthias.keller.2',
        data: { firstName: 'Hông Ân', lastName: 'O’Neill' },
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      const [call] = api.calls('patchUser');
      expect(call.method).toBe('PATCH');
      expect(call.params).toEqual({ username: 'matthias.keller.2' });
      expect(call.body).toEqual({ firstName: 'Hông Ân', lastName: 'O’Neill' });
      expect(result.current.data).toMatchObject({ firstName: 'Hông Ân', lastName: 'O’Neill' });
    });

    it('should_handleError_when_mutationFails', async () => {
      api.on('patchUser', () => HttpResponse.json({ status: 404 }, { status: 404 }));

      const { result } = renderHook(() => useUpdateUser(), {
        wrapper: wrapperFor(createClient()),
      });

      result.current.mutate({ username: 'matthias.keller.2', data: { firstName: 'Jane' } });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.data).toBeUndefined();
    });
  });

  describe('Query Invalidation', () => {
    it('should_invalidateUsersQuery_when_mutationSucceeds', async () => {
      api.on('patchUser', () => stored);
      const queryClient = createClient();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateUser(), { wrapper: wrapperFor(queryClient) });

      result.current.mutate({ username: 'matthias.keller.2', data: { firstName: 'Jane' } });

      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user', 'matthias.keller.2'] });
      });
    });
  });
});
