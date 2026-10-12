/**
 * useCockpitCards (Epic 14 Phase B — Stories 14.B.2 / 14.B.3 / 14.B.4).
 *
 * Composes EXISTING client data sources into the sorted Cockpit "Needs your
 * attention" list — frontend only, no backend change (NFR9):
 *   - `useEventTasks`     → open backend task cards (this event only; `useMyTasks`
 *                           is deliberately NOT merged — it spans other events).
 *   - `useSpeakerPool`    → awaiting-review count (entries CONTENT_SUBMITTED).
 *   - `useAgendaConfig`   → minSlots for the "fill the pool" threshold (per-event
 *                           override if present, else the event-type template).
 *   - the `event` payload → metrics / sessions / registration counts.
 *
 * The registry (`cockpitCards.ts`) owns the completion-signal model; this hook
 * only gathers inputs and exposes loading/error/refetch for the task fetch.
 */

import { useMemo } from 'react';
import type { Event, EventDetailUI } from '@/types/event.types';
import { useEventTasks } from '@/hooks/useEventTasks';
import { useSpeakerPool } from '@/hooks/useSpeakerPool';
import { useAgendaConfig } from '@/hooks/useAgendaConfig/useAgendaConfig';
import { buildAttentionList, type CockpitCard, type CockpitCardCtx } from './cockpitCards';
import { isStructuralSession } from '@/utils/sessionTypes';

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

  // Resolved agenda config: per-event override wins over the event-type template.
  const agendaConfigQuery = useAgendaConfig(eventCode);

  const tasks = tasksQuery.data;

  const ctx: CockpitCardCtx = useMemo(() => {
    const e = (event ?? {}) as Record<string, unknown> & {
      sessions?: { startTime?: string | null; sessionType?: string | null }[];
    };
    const sessions = e.sessions ?? [];
    // Structural sessions (moderation/break/lunch) are not speaker slots — exclude them
    // so the "needs a slot" card counts only speaker sessions still missing a start time
    // (structural = utils/sessionTypes, shared with the Agenda metric tile and slot assignment).
    const speakerSessions = sessions.filter((s) => !isStructuralSession(s.sessionType));
    const num = (v: unknown) => (typeof v === 'number' ? v : 0);
    return {
      eventCode: eventCode ?? '',
      workflowState: (e.workflowState as string) ?? '',
      // Event moderator = Event.organizerUsername (see EventSettingsTab) — owns the
      // virtual / event-day cards that have no assignable task row.
      moderatorUsername: (e.organizerUsername as string | null | undefined) ?? null,
      eventDate: (e.date as string | null | undefined) ?? null,
      topicCode: (e.topicCode as string | null | undefined) ?? null,
      minSlots: agendaConfigQuery.data?.minSlots ?? 0,
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
  }, [event, eventCode, agendaConfigQuery.data, poolQuery.data]);

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
