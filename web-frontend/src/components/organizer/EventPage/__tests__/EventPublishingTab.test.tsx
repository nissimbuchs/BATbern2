/**
 * EventPublishingTab Component Tests (Story 5.7 - Task 7)
 *
 * Tests for the publishing tab with real API integration.
 * Validates that the component correctly fetches and displays publishing status.
 */

import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventPublishingTab } from '../EventPublishingTab';
import type { Event, PublishingStatusResponse } from '@/types/event.types';

// Mock child components
vi.mock('@/components/Publishing/ValidationDashboard/ValidationDashboard', () => ({
  ValidationDashboard: ({ eventCode, phase }: { eventCode: string; phase: string }) => (
    <div data-testid="validation-dashboard">
      {eventCode}-{phase}
    </div>
  ),
}));

vi.mock('@/components/Publishing/PublishingControls/PublishingControls', () => ({
  PublishingControls: ({
    eventCode,
    currentPhase,
  }: {
    eventCode: string;
    currentPhase: string;
  }) => (
    <div data-testid="publishing-controls">
      {eventCode}-{currentPhase}
    </div>
  ),
}));

vi.mock('@/components/Publishing/PublishingTimeline/PublishingTimeline', () => ({
  PublishingTimeline: ({
    eventCode,
    currentPhase,
    publishedPhases,
  }: {
    eventCode: string;
    currentPhase: string;
    publishedPhases: string[];
  }) => (
    <div data-testid="publishing-timeline">
      {eventCode}-{currentPhase}-{publishedPhases.join(',')}
    </div>
  ),
}));

vi.mock('@/components/Publishing/LivePreview/LivePreview', () => ({
  LivePreview: ({ eventCode, phase }: { eventCode: string; phase: string }) => (
    <div data-testid="live-preview">
      {eventCode}-{phase}
    </div>
  ),
}));

vi.mock('@/components/Publishing/VersionControl/VersionControl', () => ({
  VersionControl: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="version-control">{eventCode}</div>
  ),
}));

// Mock hooks
const mockUsePublishing = vi.fn();
const mockUseSlotAssignment = vi.fn();

vi.mock('@/hooks/usePublishing/usePublishing', () => ({
  usePublishing: (eventCode: string) => mockUsePublishing(eventCode),
}));

vi.mock('@/hooks/useSlotAssignment/useSlotAssignment', () => ({
  useSlotAssignment: (eventCode: string) => mockUseSlotAssignment(eventCode),
}));

// Mock event data
const mockEvent: Event = {
  eventId: '123e4567-e89b-12d3-a456-426614174000',
  eventCode: 'BAT2025-FULL-DAY',
  eventNumber: 2025,
  title: 'BATbern 2025 Full Day Conference',
  description: 'Advanced software architecture conference',
  date: '2025-06-15T09:00:00Z',
  registrationDeadline: '2025-06-08T23:59:59Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3, 3013 Bern',
  venueCapacity: 300,
  status: 'published',
  workflowState: 'SLOT_ASSIGNMENT',
  organizerUsername: 'test.organizer',
  currentAttendeeCount: 0,
  createdAt: '2025-01-01T10:00:00Z',
  updatedAt: '2025-01-02T14:30:00Z',
};

// Mock publishing status responses
const mockPublishingStatusNoPhase: PublishingStatusResponse = {
  currentPhase: null,
  publishedPhases: [],
  topic: { isValid: true, errors: [] },
  speakers: { isValid: true, errors: [] },
  sessions: {
    isValid: true,
    errors: [],
    assignedCount: 2,
    totalCount: 2,
    unassignedSessions: [],
  },
};

const mockPublishingStatusTopicPublished: PublishingStatusResponse = {
  currentPhase: 'TOPIC',
  publishedPhases: ['TOPIC'],
  topic: { isValid: true, errors: [] },
  speakers: { isValid: true, errors: [] },
  sessions: {
    isValid: true,
    errors: [],
    assignedCount: 2,
    totalCount: 2,
    unassignedSessions: [],
  },
};

const mockPublishingStatusWithInvalidSessions: PublishingStatusResponse = {
  currentPhase: 'SPEAKERS',
  publishedPhases: ['TOPIC', 'SPEAKERS'],
  topic: { isValid: true, errors: [] },
  speakers: { isValid: true, errors: [] },
  sessions: {
    isValid: false,
    errors: ['Some sessions do not have timings assigned'],
    assignedCount: 2,
    totalCount: 3,
    unassignedSessions: [{ sessionSlug: 'bob-jones-cloudco', title: 'Kubernetes Best Practices' }],
  },
};

describe('EventPublishingTab Component (Story 5.7 - Task 7)', () => {
  let queryClient: QueryClient;

  // Test wrapper with providers
  const renderWithProviders = (ui: React.ReactElement) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });

    // Default mock return values
    mockUseSlotAssignment.mockReturnValue({
      unassignedSessions: [],
    });
  });

  afterEach(() => {
    queryClient.clear();
  });

  describe('Loading State', () => {
    it('should_displayLoadingSkeleton_while_fetching', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: undefined,
        isLoadingStatus: true,
        validationErrors: [],
      });

      const { container } = renderWithProviders(
        <EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />
      );

      // Verify skeletons are displayed (MUI Skeleton uses MuiSkeleton-root class)
      const skeletons = container.querySelectorAll('.MuiSkeleton-root');
      expect(skeletons.length).toBeGreaterThan(0);
    });
  });

  describe('Publishing Status Integration', () => {
    it('should_fetchPublishingStatus_on_mount', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusNoPhase,
        isLoadingStatus: false,
        validationErrors: [],
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Verify usePublishing was called with eventCode
      expect(mockUsePublishing).toHaveBeenCalledWith('BAT2025-FULL-DAY');
    });

    it('should_displayValidationData_from_API', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusNoPhase,
        isLoadingStatus: false,
        validationErrors: [],
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Verify ValidationDashboard receives data
      const validationDashboard = screen.getByTestId('validation-dashboard');
      expect(validationDashboard).toBeInTheDocument();
      expect(validationDashboard).toHaveTextContent('BAT2025-FULL-DAY');
    });

    it('should_passCorrectPhase_to_childComponents', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusTopicPublished,
        isLoadingStatus: false,
        validationErrors: [],
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Verify all components receive correct phase data
      expect(screen.getByTestId('validation-dashboard')).toHaveTextContent('topic');
      expect(screen.getByTestId('publishing-controls')).toHaveTextContent('topic');
      expect(screen.getByTestId('publishing-timeline')).toHaveTextContent('topic');
      expect(screen.getByTestId('live-preview')).toHaveTextContent('topic');
    });

    it('should_displayPublishedPhases_correctly', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusTopicPublished,
        isLoadingStatus: false,
        validationErrors: [],
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Verify timeline shows published phases
      const timeline = screen.getByTestId('publishing-timeline');
      expect(timeline).toHaveTextContent('topic');
    });

    it('should_handleInvalidSessions_from_status', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusWithInvalidSessions,
        isLoadingStatus: false,
        validationErrors: [],
      });

      mockUseSlotAssignment.mockReturnValue({
        unassignedSessions: [
          { sessionSlug: 'bob-jones-cloudco', title: 'Kubernetes Best Practices' },
        ],
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Component should render without crashing
      expect(screen.getByTestId('validation-dashboard')).toBeInTheDocument();
    });

    it('should_fallbackToDefault_when_statusIsNull', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: null,
        isLoadingStatus: false,
        validationErrors: [],
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Should default to 'topic' phase
      expect(screen.getByTestId('validation-dashboard')).toHaveTextContent('topic');
    });
  });

  describe('Component Integration', () => {
    it('should_renderAllChildComponents', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusNoPhase,
        isLoadingStatus: false,
        validationErrors: [],
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Verify all child components are rendered
      expect(screen.getByTestId('validation-dashboard')).toBeInTheDocument();
      expect(screen.getByTestId('publishing-controls')).toBeInTheDocument();
      expect(screen.getByTestId('publishing-timeline')).toBeInTheDocument();
      expect(screen.getByTestId('live-preview')).toBeInTheDocument();
      expect(screen.getByTestId('version-control')).toBeInTheDocument();
    });

    it('should_passEventCode_to_allComponents', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusNoPhase,
        isLoadingStatus: false,
        validationErrors: [],
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Verify eventCode is passed to all components
      expect(screen.getByTestId('validation-dashboard')).toHaveTextContent('BAT2025-FULL-DAY');
      expect(screen.getByTestId('publishing-controls')).toHaveTextContent('BAT2025-FULL-DAY');
      expect(screen.getByTestId('publishing-timeline')).toHaveTextContent('BAT2025-FULL-DAY');
      expect(screen.getByTestId('live-preview')).toHaveTextContent('BAT2025-FULL-DAY');
      expect(screen.getByTestId('version-control')).toHaveTextContent('BAT2025-FULL-DAY');
    });
  });

  describe('Edge Cases', () => {
    it('should_handleMissingEventDate_gracefully', () => {
      const eventWithoutDate = { ...mockEvent, date: undefined };

      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusNoPhase,
        isLoadingStatus: false,
        validationErrors: [],
      });

      renderWithProviders(
        <EventPublishingTab event={eventWithoutDate as Event} eventCode="BAT2025-FULL-DAY" />
      );

      // Should render without crashing
      expect(screen.getByTestId('publishing-timeline')).toBeInTheDocument();
    });

    it('should_handleUndefinedUnassignedSessions', () => {
      mockUsePublishing.mockReturnValue({
        publishingStatus: mockPublishingStatusNoPhase,
        isLoadingStatus: false,
        validationErrors: [],
      });

      mockUseSlotAssignment.mockReturnValue({
        unassignedSessions: undefined,
      });

      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT2025-FULL-DAY" />);

      // Should handle undefined gracefully
      expect(screen.getByTestId('validation-dashboard')).toBeInTheDocument();
    });
  });
});
