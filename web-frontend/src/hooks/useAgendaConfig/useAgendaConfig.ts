/**
 * useAgendaConfig Hook (Story 15.2)
 *
 * React Query hook for the per-event agenda config: reads the resolved config (per-event
 * override if present, else the shared event-type template) and upserts a copy-on-edit
 * override. Saving invalidates the timetable query so the slot grid recomputes.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { timetableService } from '@/services/timetableService/timetableService';
import type {
  EventAgendaConfigResponse,
  UpdateEventAgendaConfigRequest,
} from '@/services/timetableService/timetableService';

export type { EventAgendaConfigResponse, UpdateEventAgendaConfigRequest };

export function useAgendaConfig(eventCode: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ['agenda-config', eventCode],
    queryFn: () => timetableService.getAgendaConfig(eventCode!),
    enabled: !!eventCode && enabled,
    staleTime: 30_000,
  });
}

export function useUpdateAgendaConfig(eventCode: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateEventAgendaConfigRequest) =>
      timetableService.updateAgendaConfig(eventCode, request),
    onSuccess: () => {
      // Config change re-times the slot grid → invalidate timetable + the config itself.
      queryClient.invalidateQueries({ queryKey: ['timetable', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['agenda-config', eventCode] });
    },
  });
}
