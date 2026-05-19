import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

/**
 * Drawer tab keys. The Content tab is conditional (only visible for READY+ states);
 * MUI `Tabs` `value` is keyed by these strings so we don't need to remap indices
 * when the Content tab is hidden.
 *
 * Numeric aliases (0/1) preserve backward compatibility with existing tests/callers
 * that still pass numeric indices via `setTab(0)` / `setTab(1)`.
 */
export type DrawerTabKey = 'details' | 'content' | 'history';

/**
 * Story 11.D.4 (AC7.5) — drawer tab layout. Default tab: `History` for INVITED
 * speakers (so the response-status timeline is the first thing the organizer sees on
 * open). Every other state defaults to `Details`.
 *
 * Epic 11 bug fix 2026-05-18 — third Content tab added for READY+ states.
 */
export function getDefaultTab(speaker: SpeakerPoolEntry): DrawerTabKey {
  if (speaker.status === 'INVITED') return 'history';
  return 'details';
}
