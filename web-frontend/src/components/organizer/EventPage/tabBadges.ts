/**
 * Count-driven tab attention badges (Epic 14, Story 14.A.3 / FR6).
 *
 * Pure, framework-free derivation so it can be unit-tested in isolation. All
 * inputs come from EXISTING client data (event sessions + metrics, the task
 * list, the publishing-status endpoint) — no backend change (NFR9).
 */

import type { EventTaskResponse } from '@/services/taskService';
import type { PublishingStatusResponse, PublishingPhase } from '@/types/event.types';

export interface TabBadges {
  /** Speakers & Agenda: sessions needing a slot + speakers needing a content review. 0 = no badge. */
  speakers: number;
  /** Publishing: the next unpublished phase passes validation and is ready to publish. */
  publishingReady: boolean;
  /** Communications: at least one comms task is overdue (renders as a red dot). */
  commsOverdue: boolean;
  /**
   * Registrations: active registration total (confirmed + waitlist). Rendered as a
   * SUBTLE MUTED count on the tab label, not a heavy attention badge (Epic 14 FR24).
   * 0 = no count shown.
   */
  registrations: number;
}

interface SessionLike {
  startTime?: string | null;
}

export interface ComputeTabBadgesInput {
  sessions?: SessionLike[];
  /** Speakers with pending materials (a content review proxy, from metrics expansion). */
  pendingMaterialsCount?: number;
  tasks?: EventTaskResponse[];
  publishingStatus?: PublishingStatusResponse;
  /** Active registration total (confirmed + waitlist) for the muted Registrations count. */
  registrationsCount?: number;
  /** Epoch ms used for overdue comparison (injected for testability). */
  now: number;
}

const PHASE_ORDER: PublishingPhase[] = ['topic', 'speakers', 'agenda'];

/**
 * Heuristic match for clearly communications-owned tasks (DE + EN). Kept
 * deliberately narrow — newsletter / registrant-notice surfaces only — to avoid
 * false positives from generic tasks (e.g. "Book the venue", "Task reminder").
 * Interim: Phase E replaces this with a first-class audience model, at which
 * point venue/caterer-coordination tasks can also feed the dot. (See deferred-work.)
 */
const COMMS_TASK_PATTERN = /newsletter|registrant|teilnehmer-?info|kommunikat|notice/i;

/** Is the next unpublished publishing phase valid (i.e. ready to publish)? */
function isPublishingReady(status: PublishingStatusResponse | undefined): boolean {
  if (!status) return false;
  const published = status.publishedPhases ?? [];
  const next = PHASE_ORDER.find((p) => !published.includes(p));
  if (!next) return false; // everything already published
  const validation =
    next === 'topic' ? status.topic : next === 'speakers' ? status.speakers : status.sessions;
  return !!validation?.isValid;
}

function isOverdue(task: EventTaskResponse, now: number): boolean {
  if (task.status === 'completed') return false;
  if (!task.dueDate) return false;
  const due = new Date(task.dueDate).getTime();
  return !Number.isNaN(due) && due < now;
}

export function computeTabBadges(input: ComputeTabBadgesInput): TabBadges {
  const {
    sessions = [],
    pendingMaterialsCount = 0,
    tasks = [],
    publishingStatus,
    registrationsCount = 0,
    now,
  } = input;

  const sessionsNeedingSlot = sessions.filter((s) => !s.startTime).length;
  const speakers = sessionsNeedingSlot + Math.max(0, pendingMaterialsCount);

  const commsOverdue = tasks.some(
    (task) => COMMS_TASK_PATTERN.test(task.taskName ?? '') && isOverdue(task, now)
  );

  return {
    speakers,
    publishingReady: isPublishingReady(publishingStatus),
    commsOverdue,
    registrations: Math.max(0, registrationsCount),
  };
}
