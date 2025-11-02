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

      expect(screen.getByText(/BATbern56/i)).toBeInTheDocument();
    });

    it('should_displayTimestamp_when_rendered', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      // Timestamp should be relative (e.g., "2 days ago")
      expect(screen.getByText(/days? ago/i)).toBeInTheDocument();
    });

    it('should_formatRelativeTime_when_timestampProvided', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      // Should use relative time formatting
      expect(screen.getByText(/days? ago/i)).toBeInTheDocument();
    });
  });

  describe('Activity Type Icons (AC1)', () => {
    it('should_displayIcon_when_typeSpeakerAssigned', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const activity = screen.getByText('John Doe').closest('li');
      expect(activity).toContainHTML('👥');
    });

    it('should_displayIcon_when_typeMaterialsUploaded', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const activity = screen.getByText('Jane Smith').closest('li');
      expect(activity).toContainHTML('📄');
    });

    it('should_displayIcon_when_typeWorkflowAdvanced', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const activity = screen.getByText(/System.*advanced workflow/i).closest('li');
      expect(activity).toContainHTML('✅');
    });

    it('should_displaySystemIcon_when_actorSystem', () => {
      render(<TeamActivityFeed activities={mockActivities} />);

      const systemActivity = screen.getByText(/System.*advanced workflow/i).closest('li');
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

      const reloadButton = screen.getByRole('button', { name: /reload/i });
      expect(reloadButton).toBeDisabled();
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
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

      // Relative time should be formatted according to locale
      expect(screen.getByText(/days? ago/i)).toBeInTheDocument();
    });
  });
});
