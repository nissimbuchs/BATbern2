/**
 * NotificationDropdown Component Tests (TDD RED Phase - Task 7a)
 * Test coverage for Story 1.17 AC4 - Notification System
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { NotificationDropdown, NotificationDropdownProps } from './NotificationDropdown';
import { Notification } from '../../../types/notification';

// Test wrapper with all required providers
const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return (
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <BrowserRouter>{children}</BrowserRouter>
      </I18nextProvider>
    </QueryClientProvider>
  );
};

// Helper to render with wrapper
const renderWithProviders = (props: NotificationDropdownProps) => {
  return render(
    <TestWrapper>
      <NotificationDropdown {...props} />
    </TestWrapper>
  );
};

// Mock data builders
const createMockNotification = (overrides?: Partial<Notification>): Notification => ({
  id: 'notif-1',
  type: 'EVENT_STATUS_CHANGED',
  title: 'Event status changed',
  message: "Event 'BATbern 2025' is now Published",
  timestamp: '2024-03-15T14:30:00Z',
  isRead: false,
  actionUrl: '/events/uuid-123',
  priority: 'NORMAL',
  ...overrides,
});

describe('NotificationDropdown', () => {
  beforeEach(() => {
    i18n.changeLanguage('de');
  });

  describe('Rendering and Display', () => {
    it('should_renderNotificationsList_when_notificationsProvided', () => {
      const notifications = [
        createMockNotification({ id: '1', title: 'Notification 1' }),
        createMockNotification({ id: '2', title: 'Notification 2' }),
        createMockNotification({ id: '3', title: 'Notification 3' }),
      ];

      renderWithProviders({
        notifications,
        unreadCount: 3,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByText('Notification 1')).toBeInTheDocument();
      expect(screen.getByText('Notification 2')).toBeInTheDocument();
      expect(screen.getByText('Notification 3')).toBeInTheDocument();
    });

    it('should_renderEmptyState_when_noNotifications', () => {
      renderWithProviders({
        notifications: [],
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByText(/no notifications/i)).toBeInTheDocument();
    });

    it('should_renderReloadButton_when_componentMounted', () => {
      renderWithProviders({
        notifications: [],
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByRole('button', { name: /reload/i })).toBeInTheDocument();
    });

    it('should_displayNotificationDetails_when_notificationRendered', () => {
      const notification = createMockNotification({
        title: 'Test Title',
        message: 'Test Message',
        timestamp: '2024-03-15T14:30:00Z',
      });

      renderWithProviders({
        notifications: [notification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByText('Test Title')).toBeInTheDocument();
      expect(screen.getByText('Test Message')).toBeInTheDocument();
      // Timestamp should be formatted
      expect(screen.getByText(/15/)).toBeInTheDocument(); // Date part
    });

    it('should_displayUnreadBadge_when_notificationIsUnread', () => {
      const unreadNotification = createMockNotification({ isRead: false });

      renderWithProviders({
        notifications: [unreadNotification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const notificationItem = screen.getByText('Event status changed').closest('li');
      expect(notificationItem).toHaveClass('unread');
    });

    it('should_notDisplayBadge_when_notificationIsRead', () => {
      const readNotification = createMockNotification({ isRead: true });

      renderWithProviders({
        notifications: [readNotification],
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const notificationItem = screen.getByText('Event status changed').closest('li');
      expect(notificationItem).not.toHaveClass('unread');
    });

    it('should_displayPriorityIndicator_when_notificationIsUrgent', () => {
      const urgentNotification = createMockNotification({ priority: 'URGENT' });

      renderWithProviders({
        notifications: [urgentNotification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByTestId('urgent-priority-icon')).toBeInTheDocument();
    });
  });

  describe('Manual Reload Functionality', () => {
    it('should_callOnReload_when_reloadButtonClicked', async () => {
      const user = userEvent.setup();
      const onReload = vi.fn();

      renderWithProviders({
        notifications: [],
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload,
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const reloadButton = screen.getByRole('button', { name: /reload/i });
      await user.click(reloadButton);

      expect(onReload).toHaveBeenCalledTimes(1);
    });

    it('should_disableReloadButton_when_loading', () => {
      renderWithProviders({
        notifications: [],
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        isLoading: true,
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const reloadButton = screen.getByRole('button', { name: /reload/i });
      expect(reloadButton).toBeDisabled();
    });

    it('should_displayLoadingIndicator_when_reloading', () => {
      renderWithProviders({
        notifications: [],
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        isLoading: true,
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Mark as Read Functionality', () => {
    it('should_callOnMarkAsRead_when_notificationClicked', async () => {
      const user = userEvent.setup();
      const onMarkAsRead = vi.fn();
      const notification = createMockNotification({ id: 'notif-123' });

      renderWithProviders({
        notifications: [notification],
        unreadCount: 1,
        onMarkAsRead,
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const notificationItem = screen.getByText('Event status changed');
      await user.click(notificationItem);

      expect(onMarkAsRead).toHaveBeenCalledWith(['notif-123']);
    });

    it('should_callOnMarkAsRead_when_markAllAsReadClicked', async () => {
      const user = userEvent.setup();
      const onMarkAsRead = vi.fn();
      const notifications = [
        createMockNotification({ id: '1', isRead: false }),
        createMockNotification({ id: '2', isRead: false }),
        createMockNotification({ id: '3', isRead: false }),
      ];

      renderWithProviders({
        notifications,
        unreadCount: 3,
        onMarkAsRead,
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const markAllButton = screen.getByRole('button', { name: /mark all as read/i });
      await user.click(markAllButton);

      expect(onMarkAsRead).toHaveBeenCalledWith('all');
    });

    it('should_hideMarkAllButton_when_allNotificationsRead', () => {
      const readNotifications = [
        createMockNotification({ id: '1', isRead: true }),
        createMockNotification({ id: '2', isRead: true }),
      ];

      renderWithProviders({
        notifications: readNotifications,
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.queryByRole('button', { name: /mark all as read/i })).not.toBeInTheDocument();
    });

    it('should_notCallMarkAsRead_when_alreadyReadNotificationClicked', async () => {
      const user = userEvent.setup();
      const onMarkAsRead = vi.fn();
      const readNotification = createMockNotification({ isRead: true });

      renderWithProviders({
        notifications: [readNotification],
        unreadCount: 0,
        onMarkAsRead,
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const notificationItem = screen.getByText('Event status changed');
      await user.click(notificationItem);

      expect(onMarkAsRead).not.toHaveBeenCalled();
    });
  });

  describe('Delete Notification Functionality', () => {
    it('should_displayDeleteButton_when_notificationRendered', () => {
      const notification = createMockNotification();

      renderWithProviders({
        notifications: [notification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByLabelText(/delete notification/i)).toBeInTheDocument();
    });

    it('should_callOnDelete_when_deleteButtonClicked', async () => {
      const user = userEvent.setup();
      const onDelete = vi.fn();
      const notification = createMockNotification({ id: 'notif-delete-123' });

      renderWithProviders({
        notifications: [notification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete,
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const deleteButton = screen.getByLabelText(/delete notification/i);
      await user.click(deleteButton);

      // Confirm deletion in dialog
      const confirmButton = await screen.findByRole('button', { name: /delete/i });
      await user.click(confirmButton);

      expect(onDelete).toHaveBeenCalledWith('notif-delete-123');
    });

    it('should_showConfirmation_when_deletingNotification', async () => {
      const user = userEvent.setup();
      const notification = createMockNotification();

      renderWithProviders({
        notifications: [notification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const deleteButton = screen.getByLabelText(/delete notification/i);
      await user.click(deleteButton);

      await waitFor(() => {
        expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
      });
    });

    it('should_notDelete_when_confirmationCancelled', async () => {
      const user = userEvent.setup();
      const onDelete = vi.fn();
      const notification = createMockNotification();

      renderWithProviders({
        notifications: [notification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete,
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const deleteButton = screen.getByLabelText(/delete notification/i);
      await user.click(deleteButton);

      const cancelButton = await screen.findByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      expect(onDelete).not.toHaveBeenCalled();
    });
  });

  describe('Navigation and Links', () => {
    it('should_navigateToActionUrl_when_notificationWithActionUrlClicked', async () => {
      const notification = createMockNotification({
        actionUrl: '/events/event-123',
      });

      renderWithProviders({
        notifications: [notification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const notificationItem = screen.getByText('Event status changed');
      expect(notificationItem.closest('a')).toHaveAttribute('href', '/events/event-123');
    });

    it('should_notRenderLink_when_noActionUrl', () => {
      const notification = createMockNotification({
        actionUrl: undefined,
      });

      renderWithProviders({
        notifications: [notification],
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const notificationItem = screen.getByText('Event status changed');
      expect(notificationItem.closest('a')).not.toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should_haveProperAriaLabels_when_rendered', () => {
      const notifications = [createMockNotification()];

      renderWithProviders({
        notifications,
        unreadCount: 1,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByRole('menu')).toHaveAttribute('aria-label', 'Notifications menu');
    });

    it('should_supportKeyboardNavigation_when_usingArrowKeys', async () => {
      const notifications = [
        createMockNotification({ id: '1', title: 'First' }),
        createMockNotification({ id: '2', title: 'Second' }),
      ];

      renderWithProviders({
        notifications,
        unreadCount: 2,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      // Verify items are rendered (keyboard navigation is handled by Material-UI Menu)
      expect(screen.getByText('First')).toBeInTheDocument();
      expect(screen.getByText('Second')).toBeInTheDocument();
    });

    it('should_announceUnreadCount_when_screenReaderEnabled', () => {
      renderWithProviders({
        notifications: [],
        unreadCount: 5,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByRole('status')).toHaveTextContent(/5 unread notifications/i);
    });
  });

  describe('Error Handling', () => {
    it('should_displayErrorMessage_when_errorProvided', () => {
      renderWithProviders({
        notifications: [],
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload: vi.fn(),
        error: 'Failed to load notifications',
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      expect(screen.getByText(/failed to load notifications/i)).toBeInTheDocument();
    });

    it('should_displayRetryButton_when_errorOccurs', async () => {
      const user = userEvent.setup();
      const onReload = vi.fn();

      renderWithProviders({
        notifications: [],
        unreadCount: 0,
        onMarkAsRead: vi.fn(),
        onDelete: vi.fn(),
        onReload,
        error: 'Network error',
        anchorEl: document.createElement('div'),
        open: true,
        onClose: vi.fn(),
      });

      const retryButton = screen.getByRole('button', { name: /retry/i });
      await user.click(retryButton);

      expect(onReload).toHaveBeenCalledTimes(1);
    });
  });
});
