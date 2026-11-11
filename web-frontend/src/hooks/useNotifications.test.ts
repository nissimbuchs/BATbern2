/**
 * Notification hooks (Story BAT-7) on the MSW harness (Story 4.10b, Epic 17 test fidelity): the
 * real notificationApiClient and apiClient run, the requests are checked against the spec. The
 * server scopes every call to the signed-in organizer, so the hooks send no username; the
 * signed-in username (real AuthContext, `AuthTestProvider`) is part of the query keys. Each
 * mutation is checked by its effect on the cache: read, mutate, read again.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import { AuthTestProvider, signedInAs, signedOut } from '@/test/auth';
import type { UseAuthReturn } from '@/contexts/AuthContext';
import type { components } from '@/types/generated/event-notifications-api.types';
import {
  useNotifications,
  useUnreadCount,
  useMarkAsRead,
  useBatchMarkAsRead,
  useDeleteNotification,
} from './useNotifications';

type NotificationResponse = components['schemas']['NotificationResponse'];

const ID = '3f2a9c1e-7b4d-4e8a-9c0f-1a2b3c4d5e6f';
const ID_2 = '8d1e2f3a-4b5c-4d6e-8f70-112233445566';

const notification = (id: string, status: string): NotificationResponse => ({
  id,
  recipientUsername: 'hongan.nguyen',
  eventCode: 'BATbern57',
  notificationType: 'SPEAKER_ACCEPTED',
  channel: 'IN_APP',
  priority: 'NORMAL',
  subject: 'Hông Ân hat zugesagt',
  body: 'Zusage für BATbern57',
  status,
  sentAt: null,
  readAt: null,
  failedAt: null,
  failureReason: null,
  metadata: null,
  createdAt: '2026-11-10T08:00:00Z',
  updatedAt: '2026-11-10T08:00:00Z',
});

/** An inbox the mutations change, as the server would. */
let inbox: NotificationResponse[];

let auth: UseAuthReturn;

const createWrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(
      QueryClientProvider,
      { client: qc },
      React.createElement(AuthTestProvider, { auth, children })
    );

let qc: QueryClient;

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  auth = signedInAs('organizer', { username: 'anna.keller' });
  inbox = [notification(ID, 'UNREAD'), notification(ID_2, 'UNREAD')];
  api.on('listNotifications', ({ query }) => {
    const status = query.get('status');
    const data = inbox.filter((n) => !status || n.status === status);
    return { data, pagination: { page: 1, limit: 20, totalItems: data.length, totalPages: 1 } };
  });
  api.on('getUnreadCount', () => ({ count: inbox.filter((n) => n.status === 'UNREAD').length }));
  api.on('updateNotification', ({ params }) => {
    inbox = inbox.map((n) => (n.id === params.id ? { ...n, status: 'READ' } : n));
    return { success: true, markedCount: 1, updatedAt: '2026-11-10T09:00:00Z' };
  });
  api.on('updateNotifications', ({ body }) => {
    const ids = body?.notificationIds ?? [];
    inbox = inbox.map((n) => (ids.includes(n.id) ? { ...n, status: 'READ' } : n));
    return { success: true, markedCount: ids.length, updatedAt: '2026-11-10T09:00:00Z' };
  });
  api.on('deleteNotification', ({ params }) => {
    inbox = inbox.filter((n) => n.id !== params.id);
    return { success: true };
  });
});

/** The unread list and a mutation hook, rendered together on one query client. */
const renderInboxWith = <T>(useMutationHook: () => T) =>
  renderHook(
    () => ({
      unread: useNotifications({ status: 'UNREAD' }),
      count: useUnreadCount(),
      mutation: useMutationHook(),
    }),
    { wrapper: createWrapper(qc) }
  );

describe('useNotifications', () => {
  it('should_loadTheCallersInboxWithUiFields_when_enabled', async () => {
    const { result } = renderHook(() => useNotifications({}, { page: 1, limit: 20 }), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data.map((n) => n.id)).toEqual([ID, ID_2]);
    expect(result.current.data?.data[0]).toMatchObject({
      isRead: false,
      title: 'Hông Ân hat zugesagt',
    });
    expect(api.calls('listNotifications')[0].query.has('username')).toBe(false);
  });

  it('should_notFetch_when_disabledBecauseNobodyIsSignedIn', () => {
    const { result } = renderHook(() => useNotifications({}, {}, { enabled: false }), {
      wrapper: createWrapper(qc),
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(api.calls('listNotifications')).toHaveLength(0);
  });

  it('should_surfaceTheServerMessage_when_listingFails', async () => {
    api.on('listNotifications', () =>
      HttpResponse.json({ status: 500, message: 'Notification store down' }, { status: 500 })
    );

    const { result } = renderHook(() => useNotifications(), { wrapper: createWrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('Notification store down');
  });
});

describe('a second user in the same tab', () => {
  it('should_notFetch_when_nobodyIsSignedIn', () => {
    auth = signedOut();
    renderHook(() => ({ list: useNotifications(), count: useUnreadCount() }), {
      wrapper: createWrapper(qc),
    });

    expect(api.calls('listNotifications')).toHaveLength(0);
    expect(api.calls('getUnreadCount')).toHaveLength(0);
  });

  it('should_neverShowTheFirstUsersInbox_when_anotherUserSignsIn', async () => {
    // Anna's inbox is cached (2 unread); she signs out and Hông Ân signs in, whose inbox is empty.
    // A cache that keeps unused data (default gcTime), as the app's does.
    qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const first = renderHook(() => ({ list: useNotifications(), count: useUnreadCount() }), {
      wrapper: createWrapper(qc),
    });
    await waitFor(() => expect(first.result.current.count.data).toBe(2));
    first.unmount();
    // Positive control: Anna's count is still in the cache.
    expect(qc.getQueryData(['notifications', 'unread-count', 'anna.keller'])).toBe(2);

    inbox = [];
    auth = signedInAs('organizer', { username: 'hongan.nguyen' });
    const second = renderHook(() => ({ list: useNotifications(), count: useUnreadCount() }), {
      wrapper: createWrapper(qc),
    });

    // Never Anna's cached values: nothing before the fetch, then Hông Ân's own.
    expect(second.result.current.list.data).toBeUndefined();
    expect(second.result.current.count.data).toBeUndefined();
    await waitFor(() => expect(second.result.current.count.data).toBe(0));
    expect(second.result.current.list.data?.data).toEqual([]);
  });
});

describe('useUnreadCount', () => {
  it('should_countTheCallersUnread_when_enabled', async () => {
    const { result } = renderHook(() => useUnreadCount(), { wrapper: createWrapper(qc) });

    await waitFor(() => expect(result.current.data).toBe(2));
    expect(api.calls('getUnreadCount')[0].query.get('status')).toBe('UNREAD');
  });

  it('should_notFetch_when_disabled', () => {
    renderHook(() => useUnreadCount({ enabled: false }), { wrapper: createWrapper(qc) });

    expect(api.calls('getUnreadCount')).toHaveLength(0);
  });
});

describe('mutations refresh the inbox', () => {
  it('should_dropTheNotificationFromUnread_when_markedRead', async () => {
    const { result } = renderInboxWith(useMarkAsRead);
    await waitFor(() => expect(result.current.unread.data?.data).toHaveLength(2));

    await act(() => result.current.mutation.mutateAsync(ID));

    await waitFor(() => expect(result.current.unread.data?.data.map((n) => n.id)).toEqual([ID_2]));
    await waitFor(() => expect(result.current.count.data).toBe(1));
    expect(api.calls('updateNotification')[0].body).toEqual({ status: 'READ' });
  });

  it('should_emptyUnread_when_manyMarkedRead', async () => {
    const { result } = renderInboxWith(useBatchMarkAsRead);
    await waitFor(() => expect(result.current.unread.data?.data).toHaveLength(2));

    await act(() => result.current.mutation.mutateAsync([ID, ID_2]));

    await waitFor(() => expect(result.current.unread.data?.data).toEqual([]));
    await waitFor(() => expect(result.current.count.data).toBe(0));
  });

  it('should_removeTheNotification_when_deleted', async () => {
    const { result } = renderInboxWith(useDeleteNotification);
    await waitFor(() => expect(result.current.unread.data?.data).toHaveLength(2));

    await act(() => result.current.mutation.mutateAsync(ID_2));

    await waitFor(() => expect(result.current.unread.data?.data.map((n) => n.id)).toEqual([ID]));
    expect(api.calls('deleteNotification')[0].params.id).toBe(ID_2);
  });
});
