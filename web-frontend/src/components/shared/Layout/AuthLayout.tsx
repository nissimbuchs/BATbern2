/**
 * AuthLayout: the layout of every signed-in page (App.tsx wraps the protected routes in it).
 *
 * Feeds the header bell with the caller's unread notifications (#1219). The inbox is
 * organizer-only on the server (`NotificationController`, `hasRole('ORGANIZER')`), so nothing is
 * requested for anyone else or while signed out. The query is the dashboard's
 * (`{ status: 'UNREAD' }`, first page of 10): one cache entry, and marking a notification read
 * anywhere invalidates `['notifications']` and so updates the bell. The count is the page's
 * `totalItems`, i.e. all unread notifications, not only the rows on the first page. A failed
 * request leaves the bell without a badge.
 *
 * The count is not live: there is no push and no polling. It refreshes on window focus or a
 * remount once the 2-minute stale time has passed, and on any `['notifications']` invalidation
 * (e.g. marking one read).
 */
import type React from 'react';
import { useMemo } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { useNotifications } from '@/hooks/useNotifications';
import type { NotificationPage } from '@/types/notification';
import { BaseLayout } from './BaseLayout';

const UNREAD = { status: 'UNREAD' } as const;
const FIRST_PAGE = { page: 1, limit: 10 } as const;

export const AuthLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, hasRole } = useAuth();
  const isOrganizer = !!user && hasRole('organizer');
  const { data } = useNotifications(UNREAD, FIRST_PAGE, { enabled: isOrganizer });

  const notifications = useMemo<NotificationPage | undefined>(
    () =>
      isOrganizer && data ? { ...data, unreadCount: data.pagination?.totalItems ?? 0 } : undefined,
    [isOrganizer, data]
  );

  return <BaseLayout notifications={notifications}>{children}</BaseLayout>;
};
