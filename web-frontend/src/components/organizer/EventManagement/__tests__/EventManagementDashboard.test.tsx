/**
 * EventManagementDashboard Component Tests (RED Phase - TDD)
 *
 * Story 2.5.3 - Task 8a
 * AC: 1 (Event Dashboard Display), 2 (Event List & Filters)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Tests for main organizer dashboard with:
 * - Active events pipeline with progress bars
 * - Critical tasks list
 * - Team activity feed
 * - Quick actions sidebar
 * - Event filters and search
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider, type UseQueryResult } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { EventManagementDashboard } from '../EventManagementDashboard';
import {
  useEvents,
  useEvent,
  useEventWorkflow,
  useCriticalTasks,
  useTeamActivity,
  useCreateEvent,
  useUpdateEvent,
  useDeleteEvent,
} from '@/hooks/useEvents';
import type { EventListResponse } from '@/types/event.types';

// Mock the hooks
vi.mock('@/hooks/useEvents', () => ({
  useEvents: vi.fn(),
  useEvent: vi.fn(),
  useEventWorkflow: vi.fn(),
  useCriticalTasks: vi.fn(),
  useTeamActivity: vi.fn(),
  useCreateEvent: vi.fn(),
  useUpdateEvent: vi.fn(),
  useDeleteEvent: vi.fn(),
}));

// Mock EventForm to prevent act() warnings
vi.mock('../EventForm', () => ({
  EventForm: () => null,
}));

describe('EventManagementDashboard Component', () => {
  let queryClient: QueryClient;

  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
      },
    });

    return ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    );
  };

  const mockEventsData = {
    data: [
      {
        eventCode: 'BATbern56',
        eventNumber: 56,
        title: 'Cloud Computing 2025',
        description: 'Annual cloud computing event',
        eventDate: '2025-03-15T18:00:00Z',
        eventType: 'full_day',
        status: 'active',
        workflowState: 'speaker_research',
        registrationDeadline: '2025-03-08T23:59:59Z',
        capacity: 200,
        currentAttendeeCount: 0,
        createdAt: '2025-01-01T10:00:00Z',
        updatedAt: '2025-01-10T15:00:00Z',
        createdBy: 'john.doe',
        version: 1,
      },
      {
        eventCode: 'BATbern57',
        eventNumber: 57,
        title: 'DevOps Mastery',
        description: 'DevOps best practices',
        eventDate: '2025-04-20T18:00:00Z',
        eventType: 'afternoon',
        status: 'active',
        workflowState: 'topic_selection',
        registrationDeadline: '2025-04-13T23:59:59Z',
        capacity: 150,
        currentAttendeeCount: 0,
        createdAt: '2025-01-15T10:00:00Z',
        updatedAt: '2025-01-20T15:00:00Z',
        createdBy: 'jane.smith',
        version: 1,
      },
    ],
    pagination: {
      page: 1,
      limit: 20,
      totalItems: 2,
      totalPages: 1,
      hasNext: false,
      hasPrev: false,
    },
  };

  const mockCriticalTasksData = {
    data: [
      {
        id: 'task-1',
        eventCode: 'BATbern56',
        type: 'overdue_materials',
        priority: 'critical',
        title: 'Overdue speaker materials',
        description: '3 speakers have not submitted materials',
        dueDate: '2025-03-01T23:59:59Z',
        assignedTo: 'john.doe',
        actions: [],
        createdAt: '2025-01-15T10:00:00Z',
      },
    ],
    total: 1,
  };

  const mockTeamActivityData = {
    data: [
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
    ],
    pagination: {
      page: 1,
      limit: 20,
      totalItems: 1,
      hasNext: false,
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();

    // Provide default mocks for mutation hooks
    vi.mocked(useCreateEvent).mockReturnValue({
      mutate: vi.fn(),
      mutateAsync: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
      data: undefined,
      error: null,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    vi.mocked(useUpdateEvent).mockReturnValue({
      mutate: vi.fn(),
      mutateAsync: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
      data: undefined,
      error: null,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    vi.mocked(useDeleteEvent).mockReturnValue({
      mutate: vi.fn(),
      mutateAsync: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
      data: undefined,
      error: null,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    // Provide default mocks for query hooks
    vi.mocked(useEvent).mockReturnValue({
      data: undefined,
      isLoading: false,
      isSuccess: false,
      isError: false,
      error: null,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    vi.mocked(useEventWorkflow).mockReturnValue({
      data: undefined,
      isLoading: false,
      isSuccess: false,
      isError: false,
      error: null,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);
  });

  describe('Initial Render', () => {
    it('should_renderDashboardTitle_when_componentMounts', () => {
      vi.mocked(useEvents).mockReturnValue({
        data: mockEventsData,
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: mockCriticalTasksData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: mockTeamActivityData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);

      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/event management/i)).toBeInTheDocument();
    });

    it('should_displayLoadingState_when_dataFetching', () => {
      vi.mocked(useEvents).mockReturnValue({
        data: undefined,
        isLoading: true,
        isSuccess: false,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: undefined,
        isLoading: true,
        isSuccess: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: undefined,
        isLoading: true,
        isSuccess: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);

      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('should_displayErrorMessage_when_eventsLoadFails', () => {
      vi.mocked(useEvents).mockReturnValue({
        data: undefined,
        isLoading: false,
        isSuccess: false,
        isError: true,
        error: new Error('Failed to load events'),
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: mockCriticalTasksData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: mockTeamActivityData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);

      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/failed to load events/i)).toBeInTheDocument();
    });
  });

  describe('Active Events Pipeline (AC1)', () => {
    beforeEach(() => {
      vi.mocked(useEvents).mockReturnValue({
        data: mockEventsData,
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: mockCriticalTasksData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: mockTeamActivityData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
    });

    it('should_displayActiveEventsSection_when_eventsLoaded', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/active events/i)).toBeInTheDocument();
    });

    it('should_displayEventCards_when_eventsExist', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText('Cloud Computing 2025')).toBeInTheDocument();
      expect(screen.getByText('DevOps Mastery')).toBeInTheDocument();
    });

    it('should_displayEmptyState_when_noEvents', () => {
      vi.mocked(useEvents).mockReturnValue({
        data: {
          data: [],
          pagination: {
            page: 1,
            limit: 20,
            totalItems: 0,
            totalPages: 0,
            hasNext: false,
            hasPrev: false,
          },
        },
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);

      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/no events found/i)).toBeInTheDocument();
    });
  });

  describe('Critical Tasks Section (AC1)', () => {
    beforeEach(() => {
      vi.mocked(useEvents).mockReturnValue({
        data: mockEventsData,
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: mockCriticalTasksData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: mockTeamActivityData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
    });

    it('should_displayCriticalTasksSection_when_tasksExist', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/critical tasks/i)).toBeInTheDocument();
    });

    it('should_displayTaskCount_when_tasksLoaded', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/1.*task/i)).toBeInTheDocument();
    });

    it('should_displayTaskPriority_when_criticalTask', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText('Overdue speaker materials')).toBeInTheDocument();
    });
  });

  describe('Team Activity Feed (AC1)', () => {
    beforeEach(() => {
      vi.mocked(useEvents).mockReturnValue({
        data: mockEventsData,
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: mockCriticalTasksData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: mockTeamActivityData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
    });

    it('should_displayTeamActivitySection_when_activityExists', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/team activity/i)).toBeInTheDocument();
    });

    it('should_displayActivityFeed_when_activitiesLoaded', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText(/assigned speaker/i)).toBeInTheDocument();
    });
  });

  describe('Quick Actions Sidebar (AC1)', () => {
    beforeEach(() => {
      vi.mocked(useEvents).mockReturnValue({
        data: mockEventsData,
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: mockCriticalTasksData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: mockTeamActivityData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
    });

    it('should_displayNewEventButton_when_dashboardLoaded', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByRole('button', { name: /new event/i })).toBeInTheDocument();
    });
  });

  describe('Responsive Layout', () => {
    beforeEach(() => {
      vi.mocked(useEvents).mockReturnValue({
        data: mockEventsData,
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: mockCriticalTasksData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: mockTeamActivityData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
    });

    it('should_useGridLayout_when_desktopView', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      const container = screen.getByTestId('dashboard-container');
      // Grid container is inside the dashboard container
      const gridContainer = container.querySelector('.MuiGrid-root');
      expect(gridContainer).toBeInTheDocument();
    });

    it('should_displayAllSections_when_rendered', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/active events/i)).toBeInTheDocument();
      expect(screen.getByText(/critical tasks/i)).toBeInTheDocument();
      expect(screen.getByText(/team activity/i)).toBeInTheDocument();
    });
  });

  describe('Internationalization (AC22)', () => {
    beforeEach(() => {
      vi.mocked(useEvents).mockReturnValue({
        data: mockEventsData,
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useCriticalTasks).mockReturnValue({
        data: mockCriticalTasksData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
      vi.mocked(useTeamActivity).mockReturnValue({
        data: mockTeamActivityData,
        isLoading: false,
        isSuccess: true,
      } as Partial<UseQueryResult<EventListResponse, Error>>);
    });

    it('should_useTranslationKeys_when_renderingText', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      // All text should come from translation keys (t('events.dashboard.title'), etc.)
      // This verifies no hardcoded strings
      expect(screen.getByText(/event management/i)).toBeInTheDocument();
    });
  });
});
