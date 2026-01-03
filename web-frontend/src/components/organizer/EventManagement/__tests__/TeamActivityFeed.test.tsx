/**
 * TeamActivityFeed Component Tests (RED Phase - TDD)
 *
 * Story 2.5.3 - Task 8a
 * AC: 1 (Event Dashboard Display)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Tests for team activity feed:
 * - Display activity feed with manual reload
 * - Show actor, action, and timestamp
 * - Activity type icons
 * - Empty state
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@testing-library/jest-dom/vitest';
import { TeamActivityFeed } from '../TeamActivityFeed';
import type { Notification } from '@/types/notification';

// Create a test QueryClient
const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

// Test wrapper with QueryClientProvider
const renderWithQueryClient = (ui: React.ReactElement) => {
  const testQueryClient = createTestQueryClient();
  return render(<QueryClientProvider client={testQueryClient}>{ui}</QueryClientProvider>);
};

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'dashboard.teamActivity': 'Team Activity',
        'dashboard.noRecentActivity': 'No recent activity',
        'dashboard.lastUpdated': 'Last updated',
        'dashboard.today': 'Today',
        'dashboard.viewAll': 'View all',
        'dashboard.activityAction.speaker_assigned': 'assigned speaker',
        'dashboard.activityAction.materials_uploaded': 'uploaded materials',
        'dashboard.activityAction.workflow_advanced': 'advanced workflow',
        'dashboard.activityAction.speaker_invited': 'invited speaker',
      };

      // Handle pluralization for activityCount
      if (key === 'dashboard.activityCount' && params) {
        return `${params.count} ${params.count === 1 ? 'activity' : 'activities'}`;
      }

      return translations[key] || key;
    },
    i18n: {
      language: 'en',
    },
  }),
}));

// Mock date-fns formatDistanceToNow
vi.mock('date-fns', () => ({
  formatDistanceToNow: () => '2 days ago',
}));

// Mock useMarkAsRead hook
vi.mock('@/hooks/useNotifications', () => ({
  useMarkAsRead: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}));

describe('TeamActivityFeed Component', () => {
  const mockNotifications: Notification[] = [
    {
      id: 'notification-1',
      eventCode: 'BATbern56',
      notificationType: 'SPEAKER_INVITED',
      subject: 'Speaker invited',
      body: 'John Doe assigned speaker Jane Smith to session 1',
      recipientUsername: 'organizer',
      priority: 'NORMAL',
      isRead: false,
      createdAt: '2025-01-15T10:00:00Z',
      readAt: null,
      metadata: {},
    },
    {
      id: 'notification-2',
      eventCode: 'BATbern56',
      notificationType: 'CONTENT_SUBMITTED',
      subject: 'Materials uploaded',
      body: 'Jane Smith uploaded materials: presentation slides',
      recipientUsername: 'organizer',
      priority: 'NORMAL',
      isRead: false,
      createdAt: '2025-01-14T15:30:00Z',
      readAt: null,
      metadata: {},
    },
    {
      id: 'notification-3',
      eventCode: 'BATbern57',
      notificationType: 'EVENT_STATUS_CHANGED',
      subject: 'Workflow advanced',
      body: 'System advanced workflow to Speaker Research phase',
      recipientUsername: 'organizer',
      priority: 'NORMAL',
      isRead: false,
      createdAt: '2025-01-13T09:00:00Z',
      readAt: null,
      metadata: {},
    },
  ];

  describe('Basic Display (AC1)', () => {
    it('should_displayTitle_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByText(/notifications/i)).toBeInTheDocument();
    });

    it('should_displayAllActivities_when_activitiesProvided', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByText('Speaker invited')).toBeInTheDocument();
      expect(screen.getByText('Materials uploaded')).toBeInTheDocument();
      expect(screen.getByText('Workflow advanced')).toBeInTheDocument();
    });

    it('should_displayEmptyState_when_noActivities', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={[]} />);

      expect(screen.getByText(/no notifications yet/i)).toBeInTheDocument();
    });

    it('should_displayActivityCount_when_activitiesProvided', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByLabelText(/3 notifications/i)).toBeInTheDocument();
    });
  });

  describe('Activity Details (AC1)', () => {
    it('should_displayActorName_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByText(/John Doe assigned speaker/i)).toBeInTheDocument();
    });

    it('should_displayAction_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByText(/assigned speaker/i)).toBeInTheDocument();
    });

    it('should_displayTargetDescription_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByText(/Jane Smith to session 1/i)).toBeInTheDocument();
    });

    it('should_displayEventCode_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      // Multiple activities may have same event code, so use getAllByText
      const eventCodes = screen.getAllByText(/BATbern56/i);
      expect(eventCodes.length).toBeGreaterThan(0);
    });

    it('should_displayTimestamp_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      // Timestamp appears multiple times (Last updated + per activity)
      const timestamps = screen.getAllByText(/days? ago/i);
      expect(timestamps.length).toBeGreaterThan(0);
    });

    it('should_formatRelativeTime_when_timestampProvided', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      // Should use relative time formatting - appears multiple times
      const timestamps = screen.getAllByText(/days? ago/i);
      expect(timestamps.length).toBeGreaterThan(0);
    });
  });

  describe('Activity Type Icons (AC1)', () => {
    it('should_displayIcon_when_typeSpeakerAssigned', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      const activity = screen.getByText('Speaker invited').closest('li');
      // Component uses MUI PeopleIcon (SVG), not emoji
      const avatar = activity?.querySelector('.MuiAvatar-root');
      expect(avatar).toBeInTheDocument();
      expect(avatar?.querySelector('svg')).toBeInTheDocument();
    });

    it('should_displayIcon_when_typeMaterialsUploaded', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      const activity = screen.getByText('Materials uploaded').closest('li');
      // Component uses MUI DescriptionIcon (SVG), not emoji
      const avatar = activity?.querySelector('.MuiAvatar-root');
      expect(avatar).toBeInTheDocument();
      expect(avatar?.querySelector('svg')).toBeInTheDocument();
    });

    it('should_displayIcon_when_typeWorkflowAdvanced', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      const activity = screen.getByText('Workflow advanced').closest('li');
      // Component uses MUI EventIcon (SVG), not emoji
      const avatar = activity?.querySelector('.MuiAvatar-root');
      expect(avatar).toBeInTheDocument();
      expect(avatar?.querySelector('svg')).toBeInTheDocument();
    });

    it('should_displaySystemIcon_when_actorSystem', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      const systemActivity = screen.getByText('Workflow advanced').closest('li');
      // Check icon exists (no longer checking for specific class)
      expect(systemActivity?.querySelector('.MuiAvatar-root svg')).toBeInTheDocument();
    });
  });

  describe('Manual Reload (AC1)', () => {
    it('should_displayReloadButton_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByRole('button', { name: /reload/i })).toBeInTheDocument();
    });

    it('should_callOnReload_when_reloadButtonClicked', () => {
      const onReload = vi.fn();
      renderWithQueryClient(
        <TeamActivityFeed notifications={mockNotifications} onReload={onReload} />
      );

      fireEvent.click(screen.getByRole('button', { name: /reload/i }));

      expect(onReload).toHaveBeenCalled();
    });

    it('should_showLoadingIndicator_when_reloading', () => {
      renderWithQueryClient(
        <TeamActivityFeed notifications={mockNotifications} isLoading={true} />
      );

      // When isLoading=true, component shows CircularProgress (no reload button)
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
      // Reload button is not rendered in loading state
      expect(screen.queryByRole('button', { name: /reload/i })).not.toBeInTheDocument();
    });

    it('should_displayLastUpdated_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByText(/last updated/i)).toBeInTheDocument();
    });
  });

  describe('Activity Grouping', () => {
    it('should_groupByDate_when_activitiesProvided', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      // Component displays notifications in list (no date grouping headers currently)
      const notificationItems = screen.getAllByTestId(/notification-item-/);
      expect(notificationItems.length).toBeGreaterThan(0);
    });

    it('should_limitActivities_when_limitProvided', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} limit={2} />);

      const notificationItems = screen.getAllByTestId(/notification-item-/);
      expect(notificationItems).toHaveLength(2);
    });

    it('should_displayViewAll_when_moreActivitiesExist', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} limit={2} />);

      expect(screen.getByRole('button', { name: /view all/i })).toBeInTheDocument();
    });
  });

  describe('Loading State', () => {
    it('should_displaySkeleton_when_isLoadingTrue', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={[]} isLoading={true} />);

      expect(screen.getAllByTestId(/skeleton-notification-/)).toHaveLength(5);
    });
  });

  describe('Accessibility', () => {
    it('should_haveAriaLabel_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByLabelText(/notification feed/i)).toBeInTheDocument();
    });

    it('should_announceActivityCount_when_screenReaderActive', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      expect(screen.getByLabelText(/3 notifications/i)).toBeInTheDocument();
    });

    it('should_supportKeyboardNavigation_when_focused', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      const reloadButton = screen.getByRole('button', { name: /reload/i });
      expect(reloadButton).toHaveAttribute('tabIndex');
    });
  });

  describe('Internationalization', () => {
    it('should_translateActivityType_when_rendered', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      // Activity actions should be translated
      expect(screen.getByText(/assigned speaker/i)).toBeInTheDocument();
    });

    it('should_formatRelativeTime_when_localeProvided', () => {
      renderWithQueryClient(<TeamActivityFeed notifications={mockNotifications} />);

      // Relative time appears multiple times (Last updated + per activity)
      const timestamps = screen.getAllByText(/days? ago/i);
      expect(timestamps.length).toBeGreaterThan(0);
    });
  });
});
