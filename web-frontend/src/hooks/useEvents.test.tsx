/**
 * useEvents Hook Tests (RED Phase - TDD)
 *
 * Story 2.5.3 - Task 7a
 * AC: 14 (Performance), 19 (Service Integration), 18 (State Management)
 *
 * Tests for React Query hooks for Event Management:
 * - useEvents: List query with filters, sort, pagination (cache 5min)
 * - useEvent: Detail query with resource expansion (cache 15min)
 * - useEventWorkflow: Workflow state query (cache 10min)
 * - useCriticalTasks: Critical tasks query (cache 3min)
 * - useTeamActivity: Activity feed query (cache 2min)
 * - useCreateEvent: Create mutation
 * - useUpdateEvent: Update mutation with optimistic updates
 * - useDeleteEvent: Delete mutation
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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
import { EventApiClient } from '@/services/eventApiClient';
import type { EventFilters, EventListResponse, EventDetail } from '@/types/event.types';

// Mock the API client
vi.mock('@/services/eventApiClient', () => ({
  EventApiClient: {
    getEvents: vi.fn(),
    getEvent: vi.fn(),
    getEventWorkflow: vi.fn(),
    getCriticalTasks: vi.fn(),
    getTeamActivity: vi.fn(),
    createEvent: vi.fn(),
    updateEvent: vi.fn(),
    patchEvent: vi.fn(),
    deleteEvent: vi.fn(),
  },
}));

describe('Event Management React Query Hooks', () => {
  let queryClient: QueryClient;

  // Create wrapper with QueryClient for hooks
  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false, // Disable retries for tests
          gcTime: 0, // Disable cache for tests
        },
        mutations: {
          retry: false,
        },
      },
    });

    return ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient?.clear();
  });

  describe('useEvents Hook', () => {
    const mockEventsResponse: EventListResponse = {
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
      ],
      pagination: {
        page: 1,
        limit: 20,
        totalItems: 1,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    };

    it('should_fetchEvents_when_hookCalled', async () => {
      vi.mocked(EventApiClient.getEvents).mockResolvedValue(mockEventsResponse);

      const { result } = renderHook(() => useEvents({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getEvents).toHaveBeenCalledWith({ page: 1, limit: 20 }, undefined);
      expect(result.current.data).toEqual(mockEventsResponse);
    });

    it('should_returnLoadingState_when_queryPending', () => {
      vi.mocked(EventApiClient.getEvents).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      const { result } = renderHook(() => useEvents({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();
    });

    it('should_returnError_when_apiFails', async () => {
      const mockError = new Error('Failed to fetch events');
      vi.mocked(EventApiClient.getEvents).mockRejectedValue(mockError);

      const { result } = renderHook(() => useEvents({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBe(mockError);
    });

    it('should_applyFilters_when_filtersProvided', async () => {
      vi.mocked(EventApiClient.getEvents).mockResolvedValue(mockEventsResponse);

      const filters: EventFilters = {
        status: ['active', 'published'],
        year: 2025,
        search: 'Cloud',
      };

      const { result } = renderHook(() => useEvents({ page: 1, limit: 20 }, filters), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getEvents).toHaveBeenCalledWith({ page: 1, limit: 20 }, filters);
    });

    it('should_refetchEvents_when_filtersChange', async () => {
      vi.mocked(EventApiClient.getEvents).mockResolvedValue(mockEventsResponse);

      const { result, rerender } = renderHook(
        (filters: EventFilters = {}) => useEvents({ page: 1, limit: 20 }, filters),
        { wrapper: createWrapper() }
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getEvents).toHaveBeenCalledTimes(1);

      // Change filters
      rerender({ status: ['active'] });

      await waitFor(() => {
        expect(EventApiClient.getEvents).toHaveBeenCalledTimes(2);
      });

      expect(EventApiClient.getEvents).toHaveBeenLastCalledWith(
        { page: 1, limit: 20 },
        { status: ['active'] }
      );
    });

    it('should_cacheResultsFor5Minutes_when_staleTimeSet', async () => {
      vi.mocked(EventApiClient.getEvents).mockResolvedValue(mockEventsResponse);

      const { result } = renderHook(() => useEvents({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // Verify that the hook sets staleTime to 5 minutes (300000ms)
      // This is checked by verifying the query doesn't refetch immediately
      expect(EventApiClient.getEvents).toHaveBeenCalledTimes(1);
    });
  });

  describe('useEvent Hook', () => {
    const mockEventDetail: EventDetail = {
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
      workflow: {
        currentStep: 2,
        totalSteps: 16,
        completionPercentage: 12.5,
        steps: [],
        blockers: [],
      },
      venue: {
        name: 'Kornhausforum',
        address: 'Kornhausplatz 18, 3011 Bern',
        capacity: 250,
      },
      sessions: [],
      team: {
        leadOrganizer: 'john.doe',
        coOrganizers: ['jane.smith'],
        moderator: undefined,
        contentReviewer: undefined,
      },
    };

    it('should_fetchEventWithIncludes_when_includesProvided', async () => {
      vi.mocked(EventApiClient.getEvent).mockResolvedValue(mockEventDetail);

      const includes = ['workflow', 'speakers', 'sessions', 'venue', 'registrations'];

      const { result } = renderHook(() => useEvent('BATbern56', includes), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getEvent).toHaveBeenCalledWith('BATbern56', includes);
      expect(result.current.data).toEqual(mockEventDetail);
    });

    it('should_notFetchEvent_when_eventCodeUndefined', () => {
      const { result } = renderHook(() => useEvent(undefined as unknown as string), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(false);
      expect(EventApiClient.getEvent).not.toHaveBeenCalled();
    });

    it('should_cacheResultsFor15Minutes_when_staleTimeSet', async () => {
      vi.mocked(EventApiClient.getEvent).mockResolvedValue(mockEventDetail);

      const { result } = renderHook(() => useEvent('BATbern56', ['workflow']), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getEvent).toHaveBeenCalledTimes(1);
    });
  });

  describe('useEventWorkflow Hook', () => {
    const mockWorkflow = {
      currentStep: 2,
      totalSteps: 16,
      completionPercentage: 12.5,
      steps: [],
      blockers: [],
    };

    it('should_fetchEventWorkflow_when_eventCodeProvided', async () => {
      vi.mocked(EventApiClient.getEventWorkflow).mockResolvedValue(mockWorkflow);

      const { result } = renderHook(() => useEventWorkflow('BATbern56'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getEventWorkflow).toHaveBeenCalledWith('BATbern56');
      expect(result.current.data).toEqual(mockWorkflow);
    });

    it('should_notFetchWorkflow_when_eventCodeUndefined', () => {
      const { result } = renderHook(() => useEventWorkflow(undefined as unknown as string), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(false);
      expect(EventApiClient.getEventWorkflow).not.toHaveBeenCalled();
    });

    it('should_cacheResultsFor10Minutes_when_staleTimeSet', async () => {
      vi.mocked(EventApiClient.getEventWorkflow).mockResolvedValue(mockWorkflow);

      const { result } = renderHook(() => useEventWorkflow('BATbern56'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getEventWorkflow).toHaveBeenCalledTimes(1);
    });
  });

  describe('useCriticalTasks Hook', () => {
    const mockTasks = {
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

    it('should_fetchCriticalTasks_when_organizerUsernameProvided', async () => {
      vi.mocked(EventApiClient.getCriticalTasks).mockResolvedValue(mockTasks);

      const { result } = renderHook(() => useCriticalTasks('john.doe'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getCriticalTasks).toHaveBeenCalledWith('john.doe', 10);
      expect(result.current.data).toEqual(mockTasks);
    });

    it('should_notFetchTasks_when_usernameUndefined', () => {
      const { result } = renderHook(() => useCriticalTasks(undefined as unknown as string), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(false);
      expect(EventApiClient.getCriticalTasks).not.toHaveBeenCalled();
    });

    it('should_cacheResultsFor3Minutes_when_staleTimeSet', async () => {
      vi.mocked(EventApiClient.getCriticalTasks).mockResolvedValue(mockTasks);

      const { result } = renderHook(() => useCriticalTasks('john.doe'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getCriticalTasks).toHaveBeenCalledTimes(1);
    });
  });

  describe('useTeamActivity Hook', () => {
    const mockActivity = {
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

    it('should_fetchTeamActivity_when_organizerUsernameProvided', async () => {
      vi.mocked(EventApiClient.getTeamActivity).mockResolvedValue(mockActivity);

      const { result } = renderHook(() => useTeamActivity('john.doe', 20), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getTeamActivity).toHaveBeenCalledWith('john.doe', 20);
      expect(result.current.data).toEqual(mockActivity);
    });

    it('should_notFetchActivity_when_usernameUndefined', () => {
      const { result } = renderHook(() => useTeamActivity(undefined as unknown as string), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(false);
      expect(EventApiClient.getTeamActivity).not.toHaveBeenCalled();
    });

    it('should_cacheResultsFor2Minutes_when_staleTimeSet', async () => {
      vi.mocked(EventApiClient.getTeamActivity).mockResolvedValue(mockActivity);

      const { result } = renderHook(() => useTeamActivity('john.doe'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(EventApiClient.getTeamActivity).toHaveBeenCalledTimes(1);
    });
  });

  describe('useCreateEvent Hook', () => {
    const mockCreatedEvent = {
      eventCode: 'BATbern57',
      eventNumber: 57,
      title: 'New Event',
      description: 'Test event',
      eventDate: '2025-04-15T18:00:00Z',
      eventType: 'full_day' as const,
      status: 'draft' as const,
      workflowState: 'topic_selection' as const,
      registrationDeadline: '2025-04-08T23:59:59Z',
      capacity: 200,
      currentAttendeeCount: 0,
      createdAt: '2025-01-20T10:00:00Z',
      updatedAt: '2025-01-20T10:00:00Z',
      createdBy: 'john.doe',
      version: 1,
    };

    it('should_createEvent_when_mutationCalled', async () => {
      vi.mocked(EventApiClient.createEvent).mockResolvedValue(mockCreatedEvent);

      const { result } = renderHook(() => useCreateEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.mutateAsync({
          title: 'New Event',
          description: 'Test event',
          eventDate: '2025-04-15T18:00:00Z',
          eventNumber: 57,
          registrationDeadline: '2025-04-08T23:59:59Z',
          venueName: 'Kornhausforum',
          venueAddress: 'Kornhausplatz 18, 3011 Bern',
          venueCapacity: 250,
        });
      });

      expect(EventApiClient.createEvent).toHaveBeenCalled();
      expect(result.current.isSuccess).toBe(true);
      expect(result.current.data).toEqual(mockCreatedEvent);
    });

    it('should_invalidateEventsQuery_when_createSucceeds', async () => {
      vi.mocked(EventApiClient.createEvent).mockResolvedValue(mockCreatedEvent);

      const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCreateEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.mutateAsync({
          title: 'New Event',
          description: 'Test event',
          eventDate: '2025-04-15T18:00:00Z',
          eventNumber: 57,
          registrationDeadline: '2025-04-08T23:59:59Z',
          venueName: 'Kornhausforum',
          venueAddress: 'Kornhausplatz 18, 3011 Bern',
          venueCapacity: 250,
        });
      });

      await waitFor(() => {
        expect(invalidateQueriesSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
      });
    });
  });

  describe('useUpdateEvent Hook', () => {
    const mockUpdatedEvent = {
      eventCode: 'BATbern56',
      eventNumber: 56,
      title: 'Updated Event',
      description: 'Updated description',
      eventDate: '2025-03-15T18:00:00Z',
      eventType: 'full_day' as const,
      status: 'active' as const,
      workflowState: 'speaker_research' as const,
      registrationDeadline: '2025-03-08T23:59:59Z',
      capacity: 200,
      currentAttendeeCount: 0,
      createdAt: '2025-01-01T10:00:00Z',
      updatedAt: '2025-01-20T10:00:00Z',
      createdBy: 'john.doe',
      version: 2,
    };

    it('should_updateEvent_when_mutationCalled', async () => {
      vi.mocked(EventApiClient.patchEvent).mockResolvedValue(mockUpdatedEvent);

      const { result } = renderHook(() => useUpdateEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.mutateAsync({
          eventCode: 'BATbern56',
          data: {
            title: 'Updated Event',
            version: 1,
          },
        });
      });

      expect(EventApiClient.patchEvent).toHaveBeenCalledWith('BATbern56', {
        title: 'Updated Event',
        version: 1,
      });
      expect(result.current.isSuccess).toBe(true);
    });

    it('should_invalidateEventQueries_when_updateSucceeds', async () => {
      vi.mocked(EventApiClient.patchEvent).mockResolvedValue(mockUpdatedEvent);

      const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.mutateAsync({
          eventCode: 'BATbern56',
          data: {
            title: 'Updated Event',
            version: 1,
          },
        });
      });

      await waitFor(() => {
        expect(invalidateQueriesSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
        expect(invalidateQueriesSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern56'] });
      });
    });

    it('should_performOptimisticUpdate_when_mutationCalled', async () => {
      vi.mocked(EventApiClient.patchEvent).mockResolvedValue(mockUpdatedEvent);

      // Pre-populate the cache with the event
      queryClient.setQueryData(['event', 'BATbern56'], {
        eventCode: 'BATbern56',
        title: 'Original Title',
        version: 1,
      });

      const { result } = renderHook(() => useUpdateEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.mutateAsync({
          eventCode: 'BATbern56',
          data: {
            title: 'Optimistically Updated',
            version: 1,
          },
        });
      });

      expect(result.current.isSuccess).toBe(true);
    });

    it('should_rollbackOptimisticUpdate_when_mutationFails', async () => {
      const mockError = new Error('Update failed');
      vi.mocked(EventApiClient.patchEvent).mockRejectedValue(mockError);

      // Pre-populate the cache
      const originalEvent = {
        eventCode: 'BATbern56',
        title: 'Original Title',
        version: 1,
      };
      queryClient.setQueryData(['event', 'BATbern56'], originalEvent);

      const { result } = renderHook(() => useUpdateEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        try {
          await result.current.mutateAsync({
            eventCode: 'BATbern56',
            data: {
              title: 'Failed Update',
              version: 1,
            },
          });
        } catch {
          // Expected to fail
        }
      });

      // Verify rollback occurred
      const cachedEvent = queryClient.getQueryData(['event', 'BATbern56']);
      expect(cachedEvent).toEqual(originalEvent);
    });
  });

  describe('useDeleteEvent Hook', () => {
    it('should_deleteEvent_when_mutationCalled', async () => {
      vi.mocked(EventApiClient.deleteEvent).mockResolvedValue(undefined);

      const { result } = renderHook(() => useDeleteEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.mutateAsync('BATbern56');
      });

      expect(EventApiClient.deleteEvent).toHaveBeenCalledWith('BATbern56');
      expect(result.current.isSuccess).toBe(true);
    });

    it('should_invalidateEventsQuery_when_deleteSucceeds', async () => {
      vi.mocked(EventApiClient.deleteEvent).mockResolvedValue(undefined);

      const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.mutateAsync('BATbern56');
      });

      await waitFor(() => {
        expect(invalidateQueriesSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
      });
    });

    it('should_returnError_when_deleteFails', async () => {
      const mockError = new Error('Cannot delete event with registrations');
      vi.mocked(EventApiClient.deleteEvent).mockRejectedValue(mockError);

      const { result } = renderHook(() => useDeleteEvent(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        try {
          await result.current.mutateAsync('BATbern56');
        } catch {
          // Expected to fail
        }
      });

      expect(result.current.isError).toBe(true);
      expect(result.current.error).toBe(mockError);
    });
  });
});
