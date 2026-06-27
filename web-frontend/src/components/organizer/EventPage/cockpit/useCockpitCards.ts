/**
 * useCockpitCards (Epic 14 Phase B — Stories 14.B.2 / 14.B.3 / 14.B.4).
 *
 * Composes EXISTING client data sources into the sorted Cockpit "Needs your
 * attention" list — frontend only, no backend change (NFR9):
 *   - `useEventTasks`     → open backend task cards (this event only; `useMyTasks`
 *                           is deliberately NOT merged — it spans other events).
 *   - `useSpeakerPool`    → awaiting-review count (entries CONTENT_SUBMITTED).
 *   - `useEventType`      → minSlots for the "fill the pool" threshold.
 *   - the `event` payload → metrics / sessions / registration counts.
 *
 * The registry (`cockpitCards.ts`) owns the completion-signal model; this hook
 * only gathers inputs and exposes loading/error/refetch for the task fetch.
 */

import { useMemo } from 'react';
import type { Event, EventDetailUI } from '@/types/event.types';
import type { components } from '@/types/generated/events-core-api.types';
import { useEventTasks } from '@/hooks/useEventTasks';
import { useSpeakerPool } from '@/hooks/useSpeakerPool';
import { useEventType } from '@/hooks/useEventTypes';
import { buildAttentionList, type CockpitCard, type CockpitCardCtx } from './cockpitCards';

type EventType = components['schemas']['EventType'];

export interface UseCockpitCardsResult {
  cards: CockpitCard[];
  /** The task fetch is loading (virtual cards still render meanwhile). */
  isLoading: boolean;
  /** The task fetch failed — show an inline error + retry; the rest still renders. */
  isError: boolean;
  refetch: () => void;
}

export function useCockpitCards(
  event: (Event | EventDetailUI) | undefined,
  eventCode: string | undefined
): UseCockpitCardsResult {
  const tasksQuery = useEventTasks(eventCode);
  const poolQuery = useSpeakerPool(eventCode ?? '');

  // useEventType guards with `enabled: !!type`, so an empty fallback never fetches.
  const eventType = (event as { eventType?: EventType } | undefined)?.eventType;
  const eventTypeQuery = useEventType((eventType ?? '') as EventType);

  const tasks = tasksQuery.data;

  const ctx: CockpitCardCtx = useMemo(() => {
    const e = (event ?? {}) as Record<string, unknown> & {
      sessions?: { startTime?: string | null; sessionType?: string | null }[];
    };
    const sessions = e.sessions ?? [];
    // Structural sessions (moderation/break/lunch) are not speaker slots — exclude them
    // so the "needs a slot" card counts only speaker sessions still missing a start time
    // (mirrors the Agenda metric tile + DragDropSlotAssignment's STRUCTURAL_TYPES).
    const STRUCTURAL_SESSION_TYPES = ['moderation', 'break', 'lunch', 'aperitif'];
    const speakerSessions = sessions.filter(
      (s) => !STRUCTURAL_SESSION_TYPES.includes((s.sessionType ?? '').toLowerCase())
    );
    const num = (v: unknown) => (typeof v === 'number' ? v : 0);
    return {
      eventCode: eventCode ?? '',
      workflowState: (e.workflowState as string) ?? '',
      // Event moderator = Event.organizerUsername (see EventSettingsTab) — owns the
      // virtual / event-day cards that have no assignable task row.
      moderatorUsername: (e.organizerUsername as string | null | undefined) ?? null,
      eventDate: (e.date as string | null | undefined) ?? null,
      topicCode: (e.topicCode as string | null | undefined) ?? null,
      minSlots: eventTypeQuery.data?.minSlots ?? 0,
      confirmedSpeakersCount: num(e.confirmedSpeakersCount),
      sessionsNeedingSlot: speakerSessions.filter((s) => !s.startTime).length,
      sessionsWithMaterialsCount: num(e.sessionsWithMaterialsCount),
      totalSessionsCount: num(e.totalSessionsCount) || sessions.length,
      awaitingReviewCount:
        poolQuery.data?.filter((p) => p.status === 'CONTENT_SUBMITTED').length ?? 0,
      confirmedCount: num(e.confirmedCount),
      waitlistCount: num(e.waitlistCount),
      registrationCapacity:
        typeof e.registrationCapacity === 'number' ? e.registrationCapacity : null,
    };
  }, [event, eventCode, eventTypeQuery.data, poolQuery.data]);

  // Computed each render (not memoized): buildAttentionList is cheap over a handful
  // of cards, and this keeps `Date.now()` fresh so due-date chips don't freeze when
  // the Cockpit stays open across a day boundary.
  const cards = buildAttentionList(tasks ?? [], ctx, Date.now());

  return {
    cards,
    isLoading: tasksQuery.isLoading,
    isError: tasksQuery.isError,
    refetch: () => {
      void tasksQuery.refetch();
    },
  };
}
