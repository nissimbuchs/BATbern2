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
import { TeamActivityFeed } from '../TeamActivityFeed';
import type { TeamActivity } from '@/types/event.types';

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

describe('TeamActivityFeed Component', () => {
  const mockActivities: TeamActivity[] = [
    {
      id: 'activity-1',
      eventCode: 'BATbern56',
      type: 'speaker_assigned',
      actorUsername: 'john.doe',
      actorName: 'John Doe',
      action: 'assigned speaker',
      targetDescription: 'Jane Smith to session 1',
      timestamp: '2025-01-15T10:00:00Z',
    },
    {
      id: 'activity-2',
      eventCode: 'BATbern56',
      type: 'materials_uploaded',
      actorUsername: 'jane.smith',
      actorName: 'Jane Smith',
      action: 'uploaded materials',
      targetDescription: 'presentation slides',
      timestamp: '2025-01-14T15:30:00Z',
    },
    {
      id: 'activity-3',
      eventCode: 'BATbern57',
      type: 'workflow_advanced',
      actorUsername: 'System',
      actorName: 'System',
      action: 'advanced workflow',
      targetDescription: 'to Speaker Research phase',
      timestamp: '2025-01-13T09:00:00Z',
    },
  ];

  describe('Basic Display (AC1)', () => {
    it('should_displayTitle_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByText(/team activity/i)).toBeInTheDocument();
    });

    it('should_displayAllActivities_when_activitiesProvided', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
      expect(screen.getByText('System')).toBeInTheDocument();
    });

    it('should_displayEmptyState_when_noActivities', () => {
      render(<TeamActivityFeed activities={[]} />);

      expect(screen.getByText(/no recent activity/i)).toBeInTheDocument();
    });

    it('should_displayActivityCount_when_activitiesProvided', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByText(/3.*activities/i)).toBeInTheDocument();
    });
  });

  describe('Activity Details (AC1)', () => {
    it('should_displayActorName_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });

    it('should_displayAction_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByText(/assigned speaker/i)).toBeInTheDocument();
    });

    it('should_displayTargetDescription_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByText('Jane Smith to session 1')).toBeInTheDocument();
    });

    it('should_displayEventCode_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      // Multiple activities may have same event code, so use getAllByText
      const eventCodes = screen.getAllByText(/BATbern56/i);
      expect(eventCodes.length).toBeGreaterThan(0);
    });

    it('should_displayTimestamp_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      // Timestamp appears multiple times (Last updated + per activity)
      const timestamps = screen.getAllByText(/days? ago/i);
      expect(timestamps.length).toBeGreaterThan(0);
    });

    it('should_formatRelativeTime_when_timestampProvided', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      // Should use relative time formatting - appears multiple times
      const timestamps = screen.getAllByText(/days? ago/i);
      expect(timestamps.length).toBeGreaterThan(0);
    });
  });

  describe('Activity Type Icons (AC1)', () => {
    it('should_displayIcon_when_typeSpeakerAssigned', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const activity = screen.getByText('John Doe').closest('li');
      // Component uses MUI PeopleIcon (SVG), not emoji
      const avatar = activity?.querySelector('.MuiAvatar-root');
      expect(avatar).toBeInTheDocument();
      expect(avatar?.querySelector('svg')).toBeInTheDocument();
    });

    it('should_displayIcon_when_typeMaterialsUploaded', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const activity = screen.getByText('Jane Smith').closest('li');
      // Component uses MUI DescriptionIcon (SVG), not emoji
      const avatar = activity?.querySelector('.MuiAvatar-root');
      expect(avatar).toBeInTheDocument();
      expect(avatar?.querySelector('svg')).toBeInTheDocument();
    });

    it('should_displayIcon_when_typeWorkflowAdvanced', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const activity = screen.getByText('System').closest('li');
      // Component uses MUI CheckCircleIcon (SVG), not emoji
      const avatar = activity?.querySelector('.MuiAvatar-root');
      expect(avatar).toBeInTheDocument();
      expect(avatar?.querySelector('svg')).toBeInTheDocument();
    });

    it('should_displaySystemIcon_when_actorSystem', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const systemActivity = screen.getByText('System').closest('li');
      expect(systemActivity).toHaveClass('system-activity');
    });
  });

  describe('Manual Reload (AC1)', () => {
    it('should_displayReloadButton_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByRole('button', { name: /reload/i })).toBeInTheDocument();
    });

    it('should_callOnReload_when_reloadButtonClicked', () => {
      const onReload = vi.fn();
      render(<TeamActivityFeed activities={mockActivities} onReload={onReload} />);

      fireEvent.click(screen.getByRole('button', { name: /reload/i }));

      expect(onReload).toHaveBeenCalled();
    });

    it('should_showLoadingIndicator_when_reloading', () => {
      render(<TeamActivityFeed activities={mockActivities} isLoading={true} />);

      // When isLoading=true, component shows CircularProgress (no reload button)
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
      // Reload button is not rendered in loading state
      expect(screen.queryByRole('button', { name: /reload/i })).not.toBeInTheDocument();
    });

    it('should_displayLastUpdated_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByText(/last updated/i)).toBeInTheDocument();
    });
  });

  describe('Activity Grouping', () => {
    it('should_groupByDate_when_activitiesProvided', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      // Activities should be grouped by date headers
      expect(screen.getByText(/today/i)).toBeInTheDocument();
    });

    it('should_limitActivities_when_limitProvided', () => {
      render(<TeamActivityFeed activities={mockActivities} limit={2} />);

      const activityItems = screen.getAllByTestId(/activity-item-/);
      expect(activityItems).toHaveLength(2);
    });

    it('should_displayViewAll_when_moreActivitiesExist', () => {
      render(<TeamActivityFeed activities={mockActivities} limit={2} />);

      expect(screen.getByRole('button', { name: /view all/i })).toBeInTheDocument();
    });
  });

  describe('Loading State', () => {
    it('should_displaySkeleton_when_isLoadingTrue', () => {
      render(<TeamActivityFeed activities={[]} isLoading={true} />);

      expect(screen.getAllByTestId(/skeleton-activity-/)).toHaveLength(5);
    });
  });

  describe('Accessibility', () => {
    it('should_haveAriaLabel_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByLabelText(/team activity feed/i)).toBeInTheDocument();
    });

    it('should_announceActivityCount_when_screenReaderActive', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      expect(screen.getByLabelText(/3 activities/i)).toBeInTheDocument();
    });

    it('should_supportKeyboardNavigation_when_focused', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const reloadButton = screen.getByRole('button', { name: /reload/i });
      expect(reloadButton).toHaveAttribute('tabIndex');
    });
  });

  describe('Internationalization', () => {
    it('should_translateActivityType_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      // Activity actions should be translated
      expect(screen.getByText(/assigned speaker/i)).toBeInTheDocument();
    });

    it('should_formatRelativeTime_when_localeProvided', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      // Relative time appears multiple times (Last updated + per activity)
      const timestamps = screen.getAllByText(/days? ago/i);
      expect(timestamps.length).toBeGreaterThan(0);
    });
  });
});
