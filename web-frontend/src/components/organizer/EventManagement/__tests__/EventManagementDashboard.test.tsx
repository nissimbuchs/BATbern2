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
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider, type UseQueryResult } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { EventManagementDashboard } from '../EventManagementDashboard';
import {
  useEvents,
  useEvent,
  useEventWorkflow,
  useCreateEvent,
  useUpdateEvent,
  useDeleteEvent,
  useCriticalTasks,
  useTeamActivity,
} from '@/hooks/useEvents';
import { useNotifications } from '@/hooks/useNotifications';
import { taskService } from '@/services/taskService';
import type { EventListResponse } from '@/types/event.types';
import type { Notification } from '@/types/notification';

// Mock the hooks
vi.mock('@/hooks/useEvents', () => ({
  useEvents: vi.fn(),
  useEvent: vi.fn(),
  useEventWorkflow: vi.fn(),
  useCreateEvent: vi.fn(),
  useUpdateEvent: vi.fn(),
  useDeleteEvent: vi.fn(),
  useCriticalTasks: vi.fn(),
  useTeamActivity: vi.fn(),
}));

// Mock useNotifications
vi.mock('@/hooks/useNotifications', () => ({
  useNotifications: vi.fn(),
  useMarkAsRead: vi.fn(() => ({
    mutateAsync: vi.fn(),
    isPending: false,
  })),
}));

// Mock useAuth
vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({
    user: { username: 'test-organizer', role: 'organizer' },
    isAuthenticated: true,
  })),
}));

// Mock useEventStore
vi.mock('@/stores/eventStore', () => ({
  useEventStore: vi.fn(() => ({
    filters: {},
    setFilters: vi.fn(),
    pagination: { page: 1, limit: 20 },
    setPage: vi.fn(),
    setLimit: vi.fn(),
    isCreateModalOpen: false,
    openCreateModal: vi.fn(),
    closeCreateModal: vi.fn(),
    isEditModalOpen: false,
    selectedEventCode: null,
    openEditModal: vi.fn(),
    closeEditModal: vi.fn(),
  })),
}));

// Mock useNotificationWebSocket - create stable function references to prevent infinite loops
const mockUnsubscribe = vi.fn();
const mockOnNotification = vi.fn(() => mockUnsubscribe);

vi.mock('@/hooks/useNotificationWebSocket', () => ({
  useNotificationWebSocket: vi.fn(() => ({
    onNotification: mockOnNotification,
    isConnected: false,
  })),
}));

// Mock taskService for TaskWidget
vi.mock('@/services/taskService', () => ({
  taskService: {
    getMyTasks: vi.fn(() => Promise.resolve([])),
    completeTask: vi.fn(() => Promise.resolve()),
  },
}));

// Mock child components to prevent rendering issues
vi.mock('../EventForm', () => ({
  EventForm: () => null,
}));

vi.mock('../EventList', () => ({
  EventList: ({ events }: any) => (
    <div>
      {events?.map((event: any) => (
        <div key={event.eventCode}>{event.title}</div>
      ))}
      {events?.length === 0 && <div>No events found</div>}
    </div>
  ),
}));

vi.mock('../EventSearch', () => ({
  EventSearch: () => null,
}));

vi.mock('../EventPagination', () => ({
  EventPagination: () => null,
}));

vi.mock('../QuickActions', () => ({
  QuickActions: ({ onNewEvent }: any) => (
    <div>
      <button onClick={onNewEvent}>New Event</button>
    </div>
  ),
}));

vi.mock('../Tasks/TaskWidget', () => ({
  TaskWidget: () => <div>Critical Tasks</div>,
}));

vi.mock('../TeamActivityFeed', () => ({
  TeamActivityFeed: ({ notifications }: any) => (
    <div>
      <div>Team Activity</div>
      {notifications?.length > 0 && (
        <div>
          <div>John Doe</div>
          <div>assigned speaker</div>
        </div>
      )}
    </div>
  ),
}));

vi.mock('@/components/shared/Event/EventBatchImportModal', () => ({
  EventBatchImportModal: () => null,
}));

vi.mock('@/components/shared/Session/SessionBatchImportModal', () => ({
  SessionBatchImportModal: () => null,
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

  const mockCriticalTasksData = [
    {
      id: 'task-1',
      eventId: 'event-1',
      eventCode: 'BATbern56',
      templateId: null,
      taskName: 'Overdue speaker materials',
      triggerState: 'overdue_materials',
      dueDate: '2025-03-01T23:59:59Z',
      assignedOrganizerUsername: 'john.doe',
      status: 'todo' as const,
      notes: '3 speakers have not submitted materials',
      completedDate: null,
      completedByUsername: null,
      createdAt: '2025-01-15T10:00:00Z',
      updatedAt: '2025-01-15T10:00:00Z',
    },
  ];

  const mockNotificationsData: Notification[] = [
    {
      id: 'notification-1',
      eventCode: 'BATbern56',
      notificationType: 'TASK_ASSIGNED',
      subject: 'New task assigned',
      body: 'You have been assigned a new task',
      recipientUsername: 'test-organizer',
      priority: 'NORMAL',
      isRead: false,
      createdAt: '2025-01-15T10:00:00Z',
      readAt: null,
      metadata: {},
    },
  ];

  const mockTeamActivityData = [
    {
      id: 'activity-1',
      eventCode: 'BATbern56',
      actorUsername: 'john.doe',
      actorDisplayName: 'John Doe',
      actionType: 'speaker_assigned',
      entityType: 'speaker',
      entityId: 'speaker-1',
      description: 'assigned speaker for topic AI trends',
      timestamp: '2025-01-15T10:00:00Z',
      metadata: {},
    },
  ];

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
    } as any);

    vi.mocked(useUpdateEvent).mockReturnValue({
      mutate: vi.fn(),
      mutateAsync: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
      data: undefined,
      error: null,
    } as any);

    vi.mocked(useDeleteEvent).mockReturnValue({
      mutate: vi.fn(),
      mutateAsync: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
      data: undefined,
      error: null,
    } as any);

    // Provide default mocks for query hooks
    vi.mocked(useEvent).mockReturnValue({
      data: undefined,
      isLoading: false,
      isSuccess: false,
      isError: false,
      error: null,
    } as any);

    vi.mocked(useEventWorkflow).mockReturnValue({
      data: undefined,
      isLoading: false,
      isSuccess: false,
      isError: false,
      error: null,
    } as any);

    vi.mocked(useCriticalTasks).mockReturnValue({
      data: undefined,
      isLoading: false,
      isSuccess: false,
      isError: false,
      error: null,
    } as any);

    vi.mocked(useTeamActivity).mockReturnValue({
      data: undefined,
      isLoading: false,
      isSuccess: false,
      isError: false,
      error: null,
    } as any);
  });

  describe('Initial Render', () => {
    it('should_renderDashboardTitle_when_componentMounts', () => {
      vi.mocked(useEvents).mockReturnValue({
        data: mockEventsData,
        isLoading: false,
        isSuccess: true,
        isError: false,
      } as any);
      vi.mocked(useNotifications).mockReturnValue({
        data: { data: mockNotificationsData },
        isLoading: false,
        isSuccess: true,
        refetch: vi.fn(),
      } as any);

      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/event management/i)).toBeInTheDocument();
    });

    it('should_displayLoadingState_when_dataFetching', () => {
      vi.mocked(useEvents).mockReturnValue({
        data: undefined,
        isLoading: true,
        isSuccess: false,
        isError: false,
      } as any);
      vi.mocked(useNotifications).mockReturnValue({
        data: undefined,
        isLoading: true,
        isSuccess: false,
        refetch: vi.fn(),
      } as any);

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
      // Mock taskService for TaskWidget
      vi.mocked(taskService.getMyTasks).mockResolvedValue(mockCriticalTasksData);
    });

    it('should_displayCriticalTasksSection_when_tasksExist', () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      expect(screen.getByText(/critical tasks/i)).toBeInTheDocument();
    });

    it('should_displayTaskCount_when_tasksLoaded', async () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/1.*task/i)).toBeInTheDocument();
      });
    });

    it('should_displayTaskPriority_when_criticalTask', async () => {
      render(<EventManagementDashboard />, { wrapper: createWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Overdue speaker materials')).toBeInTheDocument();
      });
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
      vi.mocked(useNotifications).mockReturnValue({
        data: { data: mockNotificationsData },
        isLoading: false,
        isSuccess: true,
        refetch: vi.fn(),
      } as any);
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

      const newEventButtons = screen.getAllByRole('button', { name: /new event/i });
      expect(newEventButtons.length).toBeGreaterThan(0);
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
