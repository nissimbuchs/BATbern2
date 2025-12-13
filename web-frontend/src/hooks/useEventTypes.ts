/**
 * Event Types React Query Hooks (Story 5.1 - Task 3b)
 *
 * Custom hooks for event type data fetching and mutations
 * Features:
 * - React Query for server state caching
 * - Generated types from OpenAPI spec (ADR-006)
 * - Automatic cache invalidation on updates
 * - 1 hour stale time (event types rarely change)
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { eventTypeService } from '@/services/eventTypeService';
import type { components } from '@/types/generated/events-api.types';

// Import generated types from OpenAPI spec (ADR-006 compliance)
type EventType = components['schemas']['EventType'];
type UpdateEventSlotConfigurationRequest =
  components['schemas']['UpdateEventSlotConfigurationRequest'];

/**
 * Query key factory for event types
 * Centralizes query key management for cache invalidation
 */
export const eventTypeKeys = {
  all: ['eventTypes'] as const,
  detail: (type: EventType) => ['eventTypes', type] as const,
};

/**
 * Hook to fetch all event type configurations
 *
 * @returns Query result with array of event type configurations
 * @example
 * const { data: eventTypes, isLoading } = useEventTypes();
 */
export function useEventTypes() {
  return useQuery({
    queryKey: eventTypeKeys.all,
    queryFn: () => eventTypeService.getAllEventTypes(),
    staleTime: 1000 * 60 * 60, // 1 hour - event types rarely change
  });
}

/**
 * Hook to fetch specific event type configuration
 *
 * @param type - Event type enum value (FULL_DAY, AFTERNOON, EVENING)
 * @returns Query result with event type configuration
 * @example
 * const { data: config } = useEventType('FULL_DAY');
 */
export function useEventType(type: EventType) {
  return useQuery({
    queryKey: eventTypeKeys.detail(type),
    queryFn: () => eventTypeService.getEventType(type),
    enabled: !!type,
    staleTime: 1000 * 60 * 60, // 1 hour
  });
}

/**
 * Hook to update event type configuration (ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const updateMutation = useUpdateEventType();
 * updateMutation.mutate({ type: 'FULL_DAY', config: {...} });
 */
export function useUpdateEventType() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      type,
      config,
    }: {
      type: EventType;
      config: UpdateEventSlotConfigurationRequest;
    }) => eventTypeService.updateEventType(type, config),
    onSuccess: () => {
      // Invalidate all event type queries to refetch fresh data
      queryClient.invalidateQueries({ queryKey: eventTypeKeys.all });
    },
  });
}
