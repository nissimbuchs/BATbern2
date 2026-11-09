/**
 * useUpdateUserRoles Hook Tests (Story 2.5.2 AC3)
 *
 * The hook runs with the real userManagementApi and apiClient against the MSW harness (Epic 17
 * test fidelity): the request is checked against users-api, and each test answers it or reads
 * what went over the wire. The optimistic-update test holds the answer back with a promise it
 * settles itself, so no answer arrives after the test has ended.
 */

import { describe, it, expect, expectTypeOf, vi, beforeEach, afterEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { HttpResponse } from 'msw';
import { api, type ResponseBody } from '@/test/msw/api';
import { aUser, shapes } from '@/test/fixtures';
import { useUpdateUserRoles } from '../useUpdateUserRoles';
import type { Role } from '@/types/user.types';

const NEW_ROLES: Role[] = ['ATTENDEE', 'ORGANIZER'];

const anna = aUser();
const other = aUser({ id: shapes.vietnamese.id, email: shapes.vietnamese.email });

describe('useUpdateUserRoles', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
  });

  afterEach(() => {
    queryClient.clear();
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('should_sendTheRoles_when_mutationCalled', async () => {
    api.on('updateUserRoles', ({ params, body }) => ({
      username: params.username,
      roles: body?.roles ?? [],
    }));

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    result.current.mutate({ id: anna.id, roles: ['ATTENDEE', 'ORGANIZER'] });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const [call] = api.calls('updateUserRoles');
    expect(call.params.username).toBe(anna.id);
    expect(call.body).toEqual({ roles: ['ATTENDEE', 'ORGANIZER'] });
  });

  it('should_resolveWithTheRolesAnswer_when_updateSucceeds', async () => {
    api.on('updateUserRoles', ({ params, body }) => ({
      username: params.username,
      roles: body?.roles ?? [],
    }));
    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    let answer: Awaited<ReturnType<typeof result.current.mutateAsync>> | undefined;
    await act(async () => {
      answer = await result.current.mutateAsync({ id: anna.id, roles: NEW_ROLES });
    });

    // PUT /users/{username}/roles answers { username, roles }, not a User. The type is pinned by
    // `npm run type-check` (this file is in tsconfig.test-harness.json), the value here.
    expectTypeOf(answer).toEqualTypeOf<ResponseBody<'updateUserRoles', 200> | undefined>();
    expect(answer).toEqual({ username: anna.id, roles: NEW_ROLES });
  });

  it('should_invalidateQueries_when_updateSucceeds', async () => {
    api.on('updateUserRoles', () => ({ username: anna.id, roles: NEW_ROLES }));
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    result.current.mutate({ id: anna.id, roles: ['ATTENDEE', 'ORGANIZER'] });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'detail'] });
  });

  it('should_performOptimisticUpdate_when_mutating', async () => {
    let answer: () => void = () => {};
    const answered = new Promise<void>((resolve) => {
      answer = resolve;
    });
    api.on('updateUserRoles', async () => {
      await answered;
      return { username: anna.id, roles: NEW_ROLES };
    });
    queryClient.setQueryData(['users', 'list'], { data: [anna, other] });

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    result.current.mutate({ id: anna.id, roles: ['ATTENDEE', 'ORGANIZER'] });

    // The list shows the new roles while the request is still open; the other user is untouched.
    await waitFor(() => {
      const cached = queryClient.getQueryData<{ data: (typeof anna)[] }>(['users', 'list']);
      expect(cached?.data[0]).toMatchObject({ id: anna.id, roles: ['ATTENDEE', 'ORGANIZER'] });
      expect(cached?.data[1]).toMatchObject({ id: other.id, roles: ['ATTENDEE'] });
    });
    expect(result.current.isPending).toBe(true);

    answer();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should_rollbackOnError_when_updateFails', async () => {
    api.on('updateUserRoles', () =>
      HttpResponse.json(
        {
          timestamp: '2026-11-09T08:00:00Z',
          status: 409,
          error: 'Conflict',
          message: 'Minimum 2 organizers',
        },
        { status: 409 }
      )
    );
    const originalData = { data: [anna] };
    queryClient.setQueryData(['users', 'list'], originalData);

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper });

    result.current.mutate({ id: anna.id, roles: ['ATTENDEE', 'ORGANIZER'] });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(queryClient.getQueryData(['users', 'list'])).toEqual(originalData);
  });
});
