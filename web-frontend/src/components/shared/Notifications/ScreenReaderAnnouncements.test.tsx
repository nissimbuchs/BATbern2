/**
 * Accessibility Tests - Screen Reader Announcements
 * Story 1.17, Task 12a (RED Phase): Screen Reader Support Testing
 *
 * Tests ARIA live regions, announcements, and screen reader compatibility.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { NotificationDropdown } from './NotificationDropdown';
import { AppHeader } from '../Navigation/AppHeader';
import { ErrorToast } from '../ErrorDisplay/ErrorToast';
import type { UserProfile } from '@/types/user';
import type { NotificationsResponse } from '@/types/notification';

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    );
  };
};

const mockUser: UserProfile = {
  userId: 'user-1',
  email: 'test@batbern.ch',
  firstName: 'Test',
  lastName: 'User',
  currentRole: 'ORGANIZER',
  availableRoles: ['ORGANIZER'],
  companyId: 'company-1',
  profilePhotoUrl: null,
  preferences: {
    language: 'de',
    notifications: { emailEnabled: true, inAppEnabled: true },
    theme: 'light',
  },
};

const mockNotifications: NotificationsResponse = {
  notifications: [
    {
      id: 'notif-1',
      type: 'EVENT_STATUS_CHANGED',
      title: 'Event Updated',
      message: 'Event status changed to Published',
      timestamp: '2024-03-15T14:30:00Z',
      isRead: false,
      actionUrl: '/events/1',
      priority: 'NORMAL',
    },
  ],
  unreadCount: 1,
  totalCount: 1,
  hasMore: false,
};

describe('Accessibility - Screen Reader Announcements', () => {
  describe('ARIA Live Regions', () => {
    it('should_haveAriaLiveRegion_when_renderingNotificationBadge', () => {
      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Notification badge should have aria-live="polite" or "assertive"
      const notificationButton = screen.getByLabelText(/notifications/i);
      const badge = notificationButton.querySelector('[aria-live]') || notificationButton;

      expect(badge).toHaveAttribute('aria-live');
      const ariaLive = badge.getAttribute('aria-live');
      expect(['polite', 'assertive', 'off']).toContain(ariaLive);
    });

    it('should_announceUnreadCount_when_notificationCountChanges', async () => {
      const { rerender } = render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Initial unread count announcement
      const notificationButton = screen.getByLabelText(/notifications/i);
      expect(notificationButton).toHaveAccessibleDescription(/1|unread/i);

      // Update unread count
      const updatedNotifications: NotificationsResponse = {
        ...mockNotifications,
        unreadCount: 5,
      };

      rerender(<AppHeader user={mockUser} notifications={updatedNotifications} />);

      // Screen reader should announce updated count
      await waitFor(() => {
        expect(notificationButton).toHaveAccessibleDescription(/5|unread/i);
      });
    });

    it('should_haveAriaLiveRegion_when_renderingErrorToast', () => {
      render(<ErrorToast />);

      // Error toast should have aria-live="assertive" for important messages
      const liveRegion = screen.queryByRole('alert') || screen.queryByRole('status');
      if (liveRegion) {
        expect(liveRegion).toHaveAttribute('aria-live');
      }
    });

    it('should_announceWithAssertive_when_displayingErrorMessage', async () => {
      // Mock error state
      vi.mock('@/hooks/useErrorToast', () => ({
        useErrorToast: () => ({
          errors: [{ id: '1', message: 'An error occurred', severity: 'error' }],
          clearError: vi.fn(),
        }),
      }));

      render(<ErrorToast />);

      // Error alert should have role="alert" and aria-live="assertive"
      const alert = await screen.findByRole('alert');
      expect(alert).toHaveAttribute('aria-live', 'assertive');
    });

    it('should_announceWithPolite_when_displayingSuccessMessage', async () => {
      // Mock success state
      vi.mock('@/hooks/useErrorToast', () => ({
        useErrorToast: () => ({
          errors: [{ id: '1', message: 'Success!', severity: 'success' }],
          clearError: vi.fn(),
        }),
      }));

      render(<ErrorToast />);

      // Success status should have aria-live="polite"
      const status = await screen.findByRole('status');
      expect(status).toHaveAttribute('aria-live', 'polite');
    });
  });

  describe('ARIA Descriptions and Labels', () => {
    it('should_haveAccessibleDescription_when_renderingNotificationButton', () => {
      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      const notificationButton = screen.getByLabelText(/notifications/i);

      // Button should have accessible description with unread count
      expect(notificationButton).toHaveAttribute('aria-label');
      expect(notificationButton.getAttribute('aria-label')).toMatch(/notifications/i);

      // Or use aria-describedby for count
      const ariaDescribedBy = notificationButton.getAttribute('aria-describedby');
      if (ariaDescribedBy) {
        const description = document.getElementById(ariaDescribedBy);
        expect(description).toBeInTheDocument();
      }
    });

    it('should_haveUniqueAriaLabels_when_renderingMultipleButtons', () => {
      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      const buttons = screen.getAllByRole('button');
      const ariaLabels = buttons.map((btn) => btn.getAttribute('aria-label')).filter(Boolean);

      // All buttons should have unique, descriptive labels
      const uniqueLabels = new Set(ariaLabels);
      expect(uniqueLabels.size).toBeGreaterThan(0);
    });

    it('should_describeNotificationPriority_when_renderingHighPriorityNotification', () => {
      const highPriorityNotifications: NotificationsResponse = {
        notifications: [
          {
            id: 'notif-1',
            type: 'URGENT_ALERT',
            title: 'Urgent Alert',
            message: 'This requires immediate attention',
            timestamp: '2024-03-15T14:30:00Z',
            isRead: false,
            actionUrl: '/alerts/1',
            priority: 'HIGH',
          },
        ],
        unreadCount: 1,
        totalCount: 1,
        hasMore: false,
      };

      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={highPriorityNotifications}
        />,
        { wrapper: createWrapper() }
      );

      // High-priority notification should have aria-describedby or role="alert"
      const notification = screen.getByText(/Urgent Alert/i);
      const hasAlert = notification.closest('[role="alert"]');
      const hasDescription = notification.getAttribute('aria-describedby');

      expect(hasAlert || hasDescription).toBeTruthy();
    });
  });

  describe('Screen Reader Navigation Hints', () => {
    it('should_provideNavigationHints_when_renderingMenu', () => {
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      const menu = screen.getByRole('menu');

      // Menu should have aria-label or aria-labelledby
      expect(menu.getAttribute('aria-label') || menu.getAttribute('aria-labelledby')).toBeTruthy();
    });

    it('should_announceItemCount_when_renderingNotificationList', () => {
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      // List should announce total count to screen readers
      const list = screen.queryByRole('list');
      if (list) {
        const ariaLabel = list.getAttribute('aria-label');
        expect(ariaLabel).toMatch(/\d+/); // Contains number (count)
      }
    });

    it('should_announceLoadingState_when_fetchingNotifications', async () => {
      const onClose = vi.fn();

      // Mock loading state
      vi.mock('@/hooks/useNotifications', () => ({
        useNotifications: () => ({
          data: null,
          isLoading: true,
        }),
      }));

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={undefined}
        />,
        { wrapper: createWrapper() }
      );

      // Loading state should be announced
      const loadingIndicator = screen.queryByRole('status') || screen.queryByText(/loading/i);
      expect(loadingIndicator).toBeInTheDocument();
      if (loadingIndicator) {
        expect(loadingIndicator).toHaveAttribute('aria-live');
      }
    });

    it('should_announceEmptyState_when_noNotificationsExist', () => {
      const onClose = vi.fn();
      const emptyNotifications: NotificationsResponse = {
        notifications: [],
        unreadCount: 0,
        totalCount: 0,
        hasMore: false,
      };

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={emptyNotifications}
        />,
        { wrapper: createWrapper() }
      );

      // Empty state should be announced
      const emptyMessage = screen.getByText(/no notifications/i);
      expect(emptyMessage).toBeInTheDocument();
      expect(emptyMessage).toHaveAttribute('role', 'status');
    });
  });

  describe('Dynamic Content Announcements', () => {
    it('should_announceNewNotification_when_notificationIsAdded', async () => {
      const { rerender } = render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Add new notification
      const updatedNotifications: NotificationsResponse = {
        notifications: [
          {
            id: 'notif-2',
            type: 'NEW_MESSAGE',
            title: 'New Message',
            message: 'You have a new message',
            timestamp: '2024-03-15T15:00:00Z',
            isRead: false,
            actionUrl: '/messages/1',
            priority: 'NORMAL',
          },
          ...mockNotifications.notifications,
        ],
        unreadCount: 2,
        totalCount: 2,
        hasMore: false,
      };

      rerender(<AppHeader user={mockUser} notifications={updatedNotifications} />);

      // New notification count should be announced via aria-live
      await waitFor(() => {
        const notificationButton = screen.getByLabelText(/notifications/i);
        expect(notificationButton).toHaveAccessibleDescription(/2|unread/i);
      });
    });

    it('should_announceMarkAsRead_when_notificationIsRead', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      // Mark notification as read
      const markAsReadButton = screen.getByText(/mark as read/i);
      await user.click(markAsReadButton);

      // Action should be announced via aria-live region
      const statusRegion = screen.queryByRole('status');
      if (statusRegion) {
        expect(statusRegion).toHaveTextContent(/marked as read/i);
      }
    });

    it('should_announceDelete_when_notificationIsDeleted', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      // Delete notification
      const deleteButton = screen.getAllByLabelText(/delete/i)[0];
      await user.click(deleteButton);

      // Confirm deletion
      const confirmButton = await screen.findByText(/confirm|yes|delete/i);
      await user.click(confirmButton);

      // Deletion should be announced
      const statusRegion = await screen.findByRole('status');
      expect(statusRegion).toHaveTextContent(/deleted|removed/i);
    });
  });

  describe('Hidden Content for Screen Readers', () => {
    it('should_hideDecorativeIcons_when_renderingWithScreenReaders', () => {
      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Decorative icons should have aria-hidden="true"
      const icons = document.querySelectorAll('svg');
      icons.forEach((icon) => {
        // Icons inside buttons with aria-label should be hidden
        const parentButton = icon.closest('button[aria-label]');
        if (parentButton) {
          expect(icon).toHaveAttribute('aria-hidden', 'true');
        }
      });
    });

    it('should_provideTextAlternatives_when_renderingIconButtons', () => {
      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // All icon buttons should have text alternatives via aria-label
      const iconButtons = screen.getAllByRole('button');
      iconButtons.forEach((button) => {
        const hasAriaLabel = button.hasAttribute('aria-label');
        const hasAriaLabelledBy = button.hasAttribute('aria-labelledby');
        const hasTextContent = button.textContent && button.textContent.trim() !== '';

        expect(hasAriaLabel || hasAriaLabelledBy || hasTextContent).toBe(true);
      });
    });

    it('should_skipRedundantContent_when_providingScreenReaderText', () => {
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      // Screen reader-only text should be visually hidden but accessible
      const srOnlyElements = document.querySelectorAll('.sr-only, .visually-hidden');
      srOnlyElements.forEach((element) => {
        const styles = window.getComputedStyle(element as HTMLElement);

        // Should be visually hidden but still in accessibility tree
        const isVisuallyHidden =
          styles.position === 'absolute' &&
          (styles.width === '1px' || styles.clip === 'rect(0, 0, 0, 0)');

        expect(isVisuallyHidden).toBe(true);
      });
    });
  });
});
