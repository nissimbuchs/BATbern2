import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ActivityTimeline } from '@/components/shared/Company/ActivityTimeline';

const mockActivities = [
  {
    id: 'activity-1',
    type: 'event_participation',
    title: 'Presented at BATbern 2024',
    description: 'John Doe presented "Cloud Security Best Practices"',
    timestamp: '2024-10-01T14:30:00Z',
    icon: 'presentation',
  },
  {
    id: 'activity-2',
    type: 'partnership_created',
    title: 'Became a partner',
    description: 'Company became a Gold partner',
    timestamp: '2024-09-15T10:00:00Z',
    icon: 'handshake',
  },
  {
    id: 'activity-3',
    type: 'user_linked',
    title: 'New user linked',
    description: 'Jane Smith joined the company',
    timestamp: '2024-08-20T09:15:00Z',
    icon: 'person_add',
  },
];

describe('ActivityTimeline Component', () => {
  describe('Display activity history', () => {
    it('should_displayActivityTimeline_when_activitiesExist', () => {
      render(<ActivityTimeline companyId="company-123" activities={mockActivities} />);

      // Verify activity titles are displayed
      expect(screen.getByText('Presented at BATbern 2024')).toBeInTheDocument();
      expect(screen.getByText('Became a partner')).toBeInTheDocument();
      expect(screen.getByText('New user linked')).toBeInTheDocument();
    });

    it('should_displayActivityDescriptions_when_activitiesExist', () => {
      render(<ActivityTimeline companyId="company-123" activities={mockActivities} />);

      // Verify descriptions are displayed
      expect(screen.getByText(/John Doe presented/i)).toBeInTheDocument();
      expect(screen.getByText(/Gold partner/i)).toBeInTheDocument();
      expect(screen.getByText(/Jane Smith joined/i)).toBeInTheDocument();
    });

    it('should_displayTimestamps_when_activitiesExist', () => {
      render(<ActivityTimeline companyId="company-123" activities={mockActivities} />);

      // Verify timestamps are displayed (formatted)
      expect(screen.getByText(/Oct 1, 2024/i)).toBeInTheDocument();
      expect(screen.getByText(/Sep 15, 2024/i)).toBeInTheDocument();
      expect(screen.getByText(/Aug 20, 2024/i)).toBeInTheDocument();
    });

    it('should_displayActivityIcons_when_activitiesExist', () => {
      render(<ActivityTimeline companyId="company-123" activities={mockActivities} />);

      // Verify activity icons are rendered
      expect(screen.getByTestId('activity-icon-presentation')).toBeInTheDocument();
      expect(screen.getByTestId('activity-icon-handshake')).toBeInTheDocument();
      expect(screen.getByTestId('activity-icon-person_add')).toBeInTheDocument();
    });

    it('should_displayEmptyState_when_noActivities', () => {
      render(<ActivityTimeline companyId="company-123" activities={[]} />);

      // Verify empty state message
      expect(screen.getByText(/no recent activity/i)).toBeInTheDocument();
    });

    it('should_displayLoadingSkeleton_when_loading', () => {
      render(<ActivityTimeline companyId="company-123" activities={[]} isLoading={true} />);

      // Verify skeleton loader
      expect(screen.getByTestId('timeline-skeleton')).toBeInTheDocument();
    });
  });

  describe('Timeline visualization', () => {
    it('should_renderTimelineConnector_when_multipleActivities', () => {
      render(<ActivityTimeline companyId="company-123" activities={mockActivities} />);

      // Verify timeline connectors are rendered between activities
      const connectors = screen.getAllByTestId('timeline-connector');
      expect(connectors).toHaveLength(2); // n-1 connectors for n activities
    });

    it('should_sortActivitiesByTimestamp_when_displaying', () => {
      render(<ActivityTimeline companyId="company-123" activities={mockActivities} />);

      // Verify activities are displayed in reverse chronological order (newest first)
      const activityTitles = screen.getAllByTestId(/^activity-title-/);
      expect(activityTitles[0]).toHaveTextContent('Presented at BATbern 2024');
      expect(activityTitles[1]).toHaveTextContent('Became a partner');
      expect(activityTitles[2]).toHaveTextContent('New user linked');
    });
  });

  describe('Activity grouping', () => {
    it('should_groupActivitiesByDate_when_multipleOnSameDay', () => {
      const activitiesSameDay = [
        {
          ...mockActivities[0],
          timestamp: '2024-10-01T14:30:00Z',
        },
        {
          ...mockActivities[1],
          id: 'activity-4',
          timestamp: '2024-10-01T16:45:00Z',
        },
      ];

      render(<ActivityTimeline companyId="company-123" activities={activitiesSameDay} />);

      // Verify date header appears once for grouped activities
      const dateHeaders = screen.getAllByText(/Oct 1, 2024/i);
      expect(dateHeaders).toHaveLength(1); // Only one date header for both activities
    });
  });

  describe('Pagination', () => {
    it('should_displayLoadMoreButton_when_moreActivitiesAvailable', () => {
      render(
        <ActivityTimeline
          companyId="company-123"
          activities={mockActivities}
          hasMore={true}
        />
      );

      // Verify "Load More" button is displayed
      expect(screen.getByRole('button', { name: /load more/i })).toBeInTheDocument();
    });

    it('should_hideLoadMoreButton_when_allActivitiesLoaded', () => {
      render(
        <ActivityTimeline
          companyId="company-123"
          activities={mockActivities}
          hasMore={false}
        />
      );

      // Verify "Load More" button is not displayed
      expect(screen.queryByRole('button', { name: /load more/i })).not.toBeInTheDocument();
    });
  });

  describe('Error handling', () => {
    it('should_displayErrorMessage_when_loadingFails', () => {
      render(
        <ActivityTimeline
          companyId="company-123"
          activities={[]}
          error="Failed to load activities"
        />
      );

      // Verify error message is displayed
      expect(screen.getByText(/Failed to load activities/i)).toBeInTheDocument();
    });
  });
});
