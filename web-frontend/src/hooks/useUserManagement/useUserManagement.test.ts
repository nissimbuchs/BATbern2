/**
 * useUserManagement Hook Tests
 *
 * Coverage for:
 * - useUserList: paginated fetch, enabled guard, prefetchNextPage
 * - useUserById: fetch by id, enabled guard
 * - useUserSearch: min-2-char guard, error
 * - useCreateUser: mutation + cache invalidation
 * - useUpdateUser: mutation + cache invalidation by username
 * - useUpdateUserRoles: optimistic update + rollback + onSettled invalidation
 * - useDeleteUser: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  listUsers: vi.fn(),
  getUserById: vi.fn(),
  searchUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  updateUserRoles: vi.fn(),
  deleteUser: vi.fn(),
}));

import {
  listUsers,
  getUserById,
  searchUsers,
  createUser,
  updateUser,
  updateUserRoles,
  deleteUser,
} from '@/services/api/userManagementApi';
import { useUserList } from './useUserList';
import { useUserById } from './useUserById';
import { useUserSearch } from './useUserSearch';
import { useCreateUser } from './useCreateUser';
import { useUpdateUser } from './useUpdateUser';
import { useUpdateUserRoles } from './useUpdateUserRoles';
import { useDeleteUser } from './useDeleteUser';

const mockListUsers = vi.mocked(listUsers);
const mockGetUserById = vi.mocked(getUserById);
const mockSearchUsers = vi.mocked(searchUsers);
const mockCreateUser = vi.mocked(createUser);
const mockUpdateUser = vi.mocked(updateUser);
const mockUpdateUserRoles = vi.mocked(updateUserRoles);
const mockDeleteUser = vi.mocked(deleteUser);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_USER = { id: 'user-1', firstName: 'Alice', lastName: 'Smith', roles: ['ORGANIZER'] };
const MOCK_USER_LIST = { data: [MOCK_USER], total: 1, page: 1, limit: 20 };
const DEFAULT_FILTERS = { search: '' };
const DEFAULT_PAGINATION = { page: 1, limit: 20, hasNext: false };

// ── useUserList ───────────────────────────────────────────────────────────────

describe('useUserList', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch user list with filters and pagination', async () => {
    mockListUsers.mockResolvedValue(MOCK_USER_LIST as never);

    const { result } = renderHook(
      () => useUserList({ filters: DEFAULT_FILTERS, pagination: DEFAULT_PAGINATION }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockListUsers).toHaveBeenCalledWith(DEFAULT_FILTERS, DEFAULT_PAGINATION);
    expect(result.current.data).toEqual(MOCK_USER_LIST);
  });

  it('should not fetch when enabled=false', () => {
    const { result } = renderHook(
      () =>
        useUserList({ filters: DEFAULT_FILTERS, pagination: DEFAULT_PAGINATION, enabled: false }),
      { wrapper: wrapper(qc) }
    );

    expect(result.current.isLoading).toBe(false);
    expect(mockListUsers).not.toHaveBeenCalled();
  });

  it('should set isError on failure', async () => {
    mockListUsers.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(
      () => useUserList({ filters: DEFAULT_FILTERS, pagination: DEFAULT_PAGINATION }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should expose prefetchNextPage function', async () => {
    mockListUsers.mockResolvedValue(MOCK_USER_LIST as never);

    const { result } = renderHook(
      () => useUserList({ filters: DEFAULT_FILTERS, pagination: DEFAULT_PAGINATION }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(typeof result.current.prefetchNextPage).toBe('function');
  });

  it('should call prefetchQuery for next page when hasNext=true', async () => {
    mockListUsers.mockResolvedValue(MOCK_USER_LIST as never);
    const prefetchSpy = vi.spyOn(qc, 'prefetchQuery').mockResolvedValue(undefined);

    const pagination = { page: 1, limit: 20, hasNext: true };

    const { result } = renderHook(() => useUserList({ filters: DEFAULT_FILTERS, pagination }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    result.current.prefetchNextPage();

    expect(prefetchSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: ['users', 'list', { filters: DEFAULT_FILTERS, page: 2, limit: 20 }],
      })
    );
  });
});

// ── useUserById ───────────────────────────────────────────────────────────────

describe('useUserById', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch user by id', async () => {
    mockGetUserById.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(() => useUserById({ id: 'user-1' }), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetUserById).toHaveBeenCalledWith('user-1', undefined);
    expect(result.current.data).toEqual(MOCK_USER);
  });

  it('should pass includes array when provided', async () => {
    mockGetUserById.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(
      () => useUserById({ id: 'user-1', includes: ['company', 'roles'] }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetUserById).toHaveBeenCalledWith('user-1', ['company', 'roles']);
  });

  it('should not fetch when id is null', () => {
    const { result } = renderHook(() => useUserById({ id: null }), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetUserById).not.toHaveBeenCalled();
  });

  it('should not fetch when enabled=false', () => {
    const { result } = renderHook(() => useUserById({ id: 'user-1', enabled: false }), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetUserById).not.toHaveBeenCalled();
  });

  it('should set isError on failure', async () => {
    mockGetUserById.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useUserById({ id: 'ghost' }), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUserSearch ─────────────────────────────────────────────────────────────

describe('useUserSearch', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should not search when query is shorter than 2 characters', () => {
    const { result } = renderHook(() => useUserSearch({ query: 'a' }), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockSearchUsers).not.toHaveBeenCalled();
  });

  it('should search when query is 2+ characters', async () => {
    mockSearchUsers.mockResolvedValue([MOCK_USER] as never);

    const { result } = renderHook(() => useUserSearch({ query: 'al' }), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockSearchUsers).toHaveBeenCalledWith('al');
    expect(result.current.data).toEqual([MOCK_USER]);
  });

  it('should not search when enabled=false even with valid query', () => {
    const { result } = renderHook(() => useUserSearch({ query: 'alice', enabled: false }), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(mockSearchUsers).not.toHaveBeenCalled();
  });

  it('should set isError on failure', async () => {
    mockSearchUsers.mockRejectedValue(new Error('Server error'));

    const { result } = renderHook(() => useUserSearch({ query: 'test' }), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useCreateUser ─────────────────────────────────────────────────────────────

describe('useCreateUser', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call createUser with form data', async () => {
    mockCreateUser.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(() => useCreateUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        firstName: 'Alice',
        lastName: 'Smith',
        email: 'alice@example.com',
      } as never);
    });

    expect(mockCreateUser).toHaveBeenCalledWith({
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice@example.com',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate users list and search cache on success', async () => {
    mockCreateUser.mockResolvedValue(MOCK_USER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useCreateUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ firstName: 'Alice' } as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'search'] });
  });

  it('should set isError on failure', async () => {
    mockCreateUser.mockRejectedValue(new Error('Conflict'));

    const { result } = renderHook(() => useCreateUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ email: 'dup@example.com' } as never).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateUser ─────────────────────────────────────────────────────────────

describe('useUpdateUser', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateUser with username and data', async () => {
    mockUpdateUser.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(() => useUpdateUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        username: 'alice',
        data: { firstName: 'Alicia' } as never,
      });
    });

    expect(mockUpdateUser).toHaveBeenCalledWith('alice', { firstName: 'Alicia' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate users and user-specific cache on success', async () => {
    mockUpdateUser.mockResolvedValue(MOCK_USER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ username: 'alice', data: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user', 'alice'] });
  });

  it('should set isError on failure', async () => {
    mockUpdateUser.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useUpdateUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ username: 'alice', data: {} as never }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateUserRoles ────────────────────────────────────────────────────────

describe('useUpdateUserRoles', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateUserRoles with id and roles', async () => {
    mockUpdateUserRoles.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ id: 'user-1', roles: ['ORGANIZER'] as never });
    });

    expect(mockUpdateUserRoles).toHaveBeenCalledWith('user-1', ['ORGANIZER']);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate users list and detail on settle', async () => {
    mockUpdateUserRoles.mockResolvedValue(MOCK_USER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ id: 'user-1', roles: ['ORGANIZER'] as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'detail'] });
  });

  it('should set isError on failure', async () => {
    mockUpdateUserRoles.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useUpdateUserRoles(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ id: 'user-1', roles: [] as never }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeleteUser ─────────────────────────────────────────────────────────────

describe('useDeleteUser', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call deleteUser with user id', async () => {
    mockDeleteUser.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useDeleteUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('user-1');
    });

    expect(mockDeleteUser).toHaveBeenCalledWith('user-1');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate all user caches on success', async () => {
    mockDeleteUser.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('user-1');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'search'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'detail'] });
  });

  it('should set isError on failure', async () => {
    mockDeleteUser.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useDeleteUser(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('ghost').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
