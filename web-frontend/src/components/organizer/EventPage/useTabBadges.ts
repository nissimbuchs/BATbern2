/**
 * useTabBadges (Epic 14, Story 14.A.3)
 *
 * Composes EXISTING client data sources — event sessions + metrics, the event
 * task list (`useEventTasks`), and the publishing-status endpoint (same query
 * key as `usePublishing`, so the cache is shared) — into the count-driven tab
 * attention badges. Frontend only, no backend change (NFR9).
 */

import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { publishingService } from '@/services/publishingService/publishingService';
import type { Event, EventDetailUI } from '@/types/event.types';
import { useEventTasks } from '@/hooks/useEventTasks';
import { computeTabBadges, type TabBadges } from './tabBadges';

export function useTabBadges(
  event: (Event | EventDetailUI) | undefined,
  eventCode: string | undefined
): TabBadges {
  const { data: tasks } = useEventTasks(eventCode);

  // Reuse usePublishing's exact query key so the status fetch is shared (no
  // duplicate request when the Publishing tab is also mounted).
  const { data: publishingStatus } = useQuery({
    queryKey: ['publishing', 'status', eventCode],
    queryFn: () => publishingService.getPublishingStatus(eventCode!),
    enabled: !!eventCode,
    staleTime: 10000,
  });

  const sessions = (event as { sessions?: { startTime?: string | null }[] } | undefined)?.sessions;
  const pendingMaterialsCount = (event as { pendingMaterialsCount?: number } | undefined)
    ?.pendingMaterialsCount;

  return useMemo(
    () =>
      computeTabBadges({
        sessions,
        pendingMaterialsCount,
        tasks,
        publishingStatus,
        now: Date.now(),
      }),
    [sessions, pendingMaterialsCount, tasks, publishingStatus]
  );
}
