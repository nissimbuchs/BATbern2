/**
 * useTimetable Hook
 *
 * React Query hook for fetching the authoritative event timetable from the backend.
 * The timetable endpoint returns all slots (structural + speaker) in chronological
 * order, so the slot-assignment grid is driven by backend data rather than a local
 * algorithm — guaranteeing UI/backend parity.
 */

import { useQuery } from '@tanstack/react-query';
import { timetableService } from '@/services/timetableService/timetableService';
import type { TimetableResponse } from '@/services/timetableService/timetableService';

export type { TimetableResponse };

export function useTimetable(eventCode: string | undefined) {
  return useQuery({
    queryKey: ['timetable', eventCode],
    queryFn: () => timetableService.getTimetable(eventCode!),
    enabled: !!eventCode,
    staleTime: 30_000,
  });
}
