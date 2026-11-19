/**
 * AuthLayout (#1219): the bell in the header of every signed-in page shows the caller's unread
 * notification count, read over REST (`GET /notifications?status=UNREAD`, no push).
 *
 * Real AuthContext (`renderWithAuth`), real header, real i18next (en) and the MSW harness; only
 * the screen width (`window.matchMedia`, a browser boundary) is set. No own-code mocks.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { renderWithAuth, signedInAs, signedOut } from '@/test/auth';
import { useUIStore } from '@/stores/uiStore';
import { AuthLayout } from '../AuthLayout';

type Notifications = ResponseBody<'listNotifications', 200>;
type NotificationRow = NonNullable<Notifications['data']>[number];

const aNotification = (id: string): NotificationRow => ({
  id,
  recipientUsername: 'anna.keller',
  eventCode: 'BATbern61',
  notificationType: 'TASK_ASSIGNED',
  channel: 'IN_APP',
  priority: 'NORMAL',
  subject: 'Lukas Meier assigned you a task',
  body: 'Assigned speaker outreach for BATbern61',
  status: 'UNREAD',
  sentAt: '2026-11-17T09:00:00Z',
  readAt: null,
  failedAt: null,
  failureReason: null,
  metadata: null,
  createdAt: '2026-11-17T09:00:00Z',
  updatedAt: '2026-11-17T09:00:00Z',
});

const unreadPage = (count: number): Notifications => ({
  data: Array.from({ length: count }, (_, i) =>
    aNotification(`c1d2e3f4-0000-4000-8000-00000000000${i + 1}`)
  ),
  pagination: { page: 1, limit: 10, totalItems: count, totalPages: 1 },
});

/** A desktop-wide window, so the header shows its full toolbar. */
function setWidth(width: number) {
  window.matchMedia = ((query: string) => {
    const min = /min-width:\s*([\d.]+)px/.exec(query);
    const max = /max-width:\s*([\d.]+)px/.exec(query);
    const matches = (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]));
    return {
      matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    };
  }) as typeof window.matchMedia;
}

const originalMatchMedia = window.matchMedia;

const renderLayout = (auth = signedInAs('organizer')) =>
  renderWithAuth(
    <AuthLayout>
      <p>page content</p>
    </AuthLayout>,
    { auth }
  );

describe('AuthLayout — notification bell (#1219)', () => {
  beforeEach(() => {
    useUIStore.getState().reset();
    setWidth(1500);
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  it('shows the unread count the server reports', async () => {
    api.on('listNotifications', () => unreadPage(3));

    renderLayout();

    const bell = screen.getByTestId('notifications-button');
    expect(await within(bell).findByText('3 unread notifications')).toBeInTheDocument();
    expect(within(bell).getByText('3')).toBeInTheDocument();
    expect(screen.getByText('page content')).toBeInTheDocument();

    // The caller's unread notifications, the same query the dashboard reads (shared cache).
    const query = api.calls('listNotifications')[0].query;
    expect(query.get('status')).toBe('UNREAD');
  });

  it('counts the total, not just the rows on the first page', async () => {
    api.on('listNotifications', () => ({
      ...unreadPage(10),
      pagination: { page: 1, limit: 10, totalItems: 14, totalPages: 2 },
    }));

    renderLayout();

    const bell = screen.getByTestId('notifications-button');
    expect(await within(bell).findByText('14 unread notifications')).toBeInTheDocument();
  });

  it('shows no badge and does not crash when the request fails', async () => {
    api.on('listNotifications', () =>
      HttpResponse.json(
        { type: 'about:blank', title: 'Internal Server Error', status: 500 },
        { status: 500 }
      )
    );

    renderLayout();

    await waitFor(() => expect(api.calls('listNotifications')).toHaveLength(1));
    const bell = screen.getByTestId('notifications-button');
    expect(bell).not.toHaveAttribute('aria-describedby');
    expect(within(bell).queryByText(/unread notifications/)).not.toBeInTheDocument();
    expect(screen.getByText('page content')).toBeInTheDocument();
  });

  it('sends no notifications request when nobody is signed in', async () => {
    renderLayout(signedOut());

    expect(await screen.findByText('page content')).toBeInTheDocument();
    expect(api.calls('listNotifications')).toHaveLength(0);
  });

  it('sends no notifications request for a non-organizer (the inbox is organizer-only)', async () => {
    renderLayout(signedInAs('partner'));

    expect(await screen.findByText('page content')).toBeInTheDocument();
    expect(api.calls('listNotifications')).toHaveLength(0);
  });
});
