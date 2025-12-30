/**
 * Event Management React Query Hooks (Story 2.5.3 - Task 7b)
 *
 * React Query hooks for Event Management feature:
 * - useEvents: List query with filters, sort, pagination (cache 5min)
 * - useEvent: Detail query with resource expansion (cache 15min)
 * - useEventWorkflow: Workflow state query (cache 10min)
 * - useCriticalTasks: Critical tasks query (cache 3min)
 * - useTeamActivity: Activity feed query (cache 2min)
 * - useCreateEvent: Create mutation
 * - useUpdateEvent: Update mutation with optimistic updates
 * - useDeleteEvent: Delete mutation
 *
 * AC: 14 (Performance), 19 (Service Integration), 18 (State Management)
 */

import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryResult,
  type UseMutationResult,
} from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import type {
  Event,
  EventDetailUI,
  EventFilters,
  EventListResponse,
  WorkflowState,
  CriticalTasksResponse,
  TeamActivityResponse,
  CreateEventRequest,
  PatchEventRequest,
  PaginationParams,
} from '@/types/event.types';

// ============================================================================
// Query Hooks
// ============================================================================

/**
 * useEvents - Fetch paginated list of events with filters
 * Cache: 5 minutes (moderately volatile data)
 *
 * @param pagination - Page number and limit
 * @param filters - Optional filters for events
 * @param options - Optional expand resources (e.g., ['registrations'] for actual counts)
 */
export const useEvents = (
  pagination: PaginationParams,
  filters?: EventFilters,
  options?: { expand?: string[] }
): UseQueryResult<EventListResponse, Error> => {
  return useQuery({
    queryKey: ['events', pagination, filters, options],
    queryFn: () => eventApiClient.getEvents(pagination, filters, options),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

/**
 * useEvent - Fetch single event with optional resource expansion
 * Cache: 15 minutes (full event with includes cached longer)
 *
 * @param eventCode - Event code identifier (e.g., "BATbern56")
 * @param include - Resources to expand (workflow, speakers, sessions, venue, registrations)
 */
export const useEvent = (
  eventCode: string | undefined,
  include?: string[]
): UseQueryResult<EventDetailUI, Error> => {
  return useQuery({
    queryKey: ['event', eventCode, include],
    queryFn: () => eventApiClient.getEvent(eventCode!, include ? { expand: include } : undefined),
    enabled: !!eventCode, // Only fetch if eventCode is provided
    staleTime: 15 * 60 * 1000, // 15 minutes
  });
};

/**
 * useEventWorkflow - Fetch event workflow state
 * Cache: 10 minutes (workflow changes moderately)
 */
export const useEventWorkflow = (
  eventCode: string | undefined
): UseQueryResult<WorkflowState, Error> => {
  return useQuery({
    queryKey: ['eventWorkflow', eventCode],
    queryFn: () => eventApiClient.getEventWorkflow(eventCode!),
    enabled: !!eventCode,
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
};

/**
 * useCriticalTasks - Fetch critical tasks for organizer
 * Cache: 3 minutes (tasks are more volatile)
 *
 * @param organizerUsername - Username of organizer (e.g., "john.doe")
 * @param limit - Number of tasks to fetch (default: 10)
 */
export const useCriticalTasks = (
  organizerUsername: string | undefined,
  limit: number = 10
): UseQueryResult<CriticalTasksResponse, Error> => {
  return useQuery({
    queryKey: ['criticalTasks', organizerUsername, limit],
    queryFn: async () => {
      // TODO: API needs to be updated to support organizerUsername filter
      // For now, returning empty response until backend implements this endpoint
      return {
        data: [],
        total: 0,
      };
    },
    enabled: !!organizerUsername,
    staleTime: 3 * 60 * 1000, // 3 minutes
  });
};

/**
 * useTeamActivity - Fetch team activity feed
 * Cache: 2 minutes (activity feed is very volatile)
 *
 * @param organizerUsername - Username of organizer (e.g., "john.doe")
 * @param limit - Number of activity items to fetch (default: 20)
 */
export const useTeamActivity = (
  organizerUsername: string | undefined,
  limit: number = 20
): UseQueryResult<TeamActivityResponse, Error> => {
  return useQuery({
    queryKey: ['teamActivity', organizerUsername, limit],
    queryFn: async () => {
      // TODO: API needs to be updated to support organizerUsername filter
      // For now, returning empty response until backend implements this endpoint
      return {
        data: [],
        pagination: {
          page: 1,
          limit,
          totalItems: 0,
          hasNext: false,
        },
      };
    },
    enabled: !!organizerUsername,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });
};

// ============================================================================
// Mutation Hooks
// ============================================================================

/**
 * useCreateEvent - Create new event mutation
 * Invalidates 'events' query on success
 */
export const useCreateEvent = (): UseMutationResult<Event, Error, CreateEventRequest> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateEventRequest) => eventApiClient.createEvent(data),
    onSuccess: () => {
      // Invalidate events list to refresh with new event
      queryClient.invalidateQueries({ queryKey: ['events'] });
    },
  });
};

/**
 * useUpdateEvent - Update event mutation with optimistic updates
 * Invalidates 'events' and specific 'event' queries on success
 *
 * Features:
 * - Optimistic updates for instant UI feedback
 * - Rollback on error
 * - Cache invalidation on success
 */
export const useUpdateEvent = (): UseMutationResult<
  Event,
  Error,
  { eventCode: string; data: PatchEventRequest }
> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ eventCode, data }: { eventCode: string; data: PatchEventRequest }) =>
      eventApiClient.patchEvent(eventCode, data),
    // Optimistic update
    onMutate: async ({ eventCode, data }) => {
      // Cancel any outgoing refetches
      await queryClient.cancelQueries({ queryKey: ['event', eventCode] });

      // Snapshot the previous value
      const previousEvent = queryClient.getQueryData(['event', eventCode]);

      // Optimistically update to the new value
      queryClient.setQueryData(['event', eventCode], (old: Event | undefined) => {
        if (!old) return old;
        return { ...old, ...data };
      });

      // Return context with the snapshotted value
      return { previousEvent };
    },
    // Rollback on error
    onError: (_error, { eventCode }, context) => {
      if (context?.previousEvent) {
        queryClient.setQueryData(['event', eventCode], context.previousEvent);
      }
    },
    // Invalidate and refetch on success
    onSuccess: (updatedEvent, { eventCode }) => {
      // Invalidate ALL event-related caches for proper MVC pattern
      // 1. List caches (all pagination/filter combinations)
      queryClient.invalidateQueries({ queryKey: ['events'] });

      if (updatedEvent.eventCode !== eventCode) {
        // EventCode changed (e.g., eventNumber 58 -> 998 regenerates BATbern58 -> BATbern998)
        // REMOVE old eventCode cache (don't invalidate/refetch - it would 404)
        queryClient.removeQueries({ queryKey: ['event', eventCode] });
        // Invalidate new eventCode cache to trigger refetch with new data
        queryClient.invalidateQueries({ queryKey: ['event', updatedEvent.eventCode] });
      } else {
        // EventCode unchanged - normal invalidation to refresh data
        queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      }

      // 3. Current event cache (in case updated event is the current published one)
      queryClient.invalidateQueries({ queryKey: ['events', 'current'] });
    },
  });
};

/**
 * useDeleteEvent - Delete event mutation
 * Invalidates 'events' query on success
 */
export const useDeleteEvent = (): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (eventCode: string) => eventApiClient.deleteEvent(eventCode),
    onSuccess: () => {
      // Invalidate events list to remove deleted event
      queryClient.invalidateQueries({ queryKey: ['events'] });
    },
  });
};
