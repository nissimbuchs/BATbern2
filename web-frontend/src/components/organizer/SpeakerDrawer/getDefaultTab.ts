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
 * Drawer tab layout. Every state defaults to `Details` — the brainstorm/identity
 * metadata is the most "stable" view and the one organizers expect to see when
 * they click a card. 2026-05-20 (Q#B) — INVITED previously defaulted to `History`
 * to surface the response-status timeline, but that confused organizers who
 * expected the card click to show "speaker details". The timeline is one click
 * away via the History tab.
 *
 * Epic 11 bug fix 2026-05-18 — third Content tab added for READY+ states.
 */
export function getDefaultTab(speaker: SpeakerPoolEntry): DrawerTabKey {
  void speaker;
  return 'details';
}
