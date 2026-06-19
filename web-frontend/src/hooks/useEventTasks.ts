/**
 * Event task React Query hooks (Epic 14, Story 14.A.3)
 *
 * Thin React Query wrappers over the EXISTING `taskService` — frontend only,
 * no backend change (AR8/NFR9). They back the Cockpit "Needs your attention"
 * list (Phase B) and the count-driven tab attention badges (Phase A).
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { taskService, type EventTaskResponse } from '@/services/taskService';

/**
 * Fetch all tasks for a single event (`GET /events/{code}/tasks`).
 * Cache: 3 minutes (tasks are moderately volatile).
 */
export const useEventTasks = (
  eventCode: string | undefined
): UseQueryResult<EventTaskResponse[], Error> => {
  return useQuery({
    queryKey: ['eventTasks', eventCode],
    queryFn: () => taskService.listEventTasks(eventCode!),
    enabled: !!eventCode,
    staleTime: 3 * 60 * 1000,
  });
};

/**
 * Fetch the current organizer's tasks (`GET /tasks/my-tasks?critical=`).
 * Cache: 3 minutes.
 */
export const useMyTasks = (
  options: { critical?: boolean } = {}
): UseQueryResult<EventTaskResponse[], Error> => {
  const critical = options.critical ?? false;
  return useQuery({
    queryKey: ['myTasks', { critical }],
    queryFn: () => taskService.getMyTasks(critical),
    staleTime: 3 * 60 * 1000,
  });
};
