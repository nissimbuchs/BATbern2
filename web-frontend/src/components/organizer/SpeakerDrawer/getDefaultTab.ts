import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

/**
 * Story 11.D.4 (AC7.5) — the drawer collapses from 3 tabs to 2:
 *   - 0 = Details (response/content/decline info)
 *   - 1 = History (unified status + outreach timeline)
 *
 * Default tab: `History` for INVITED speakers (so the response-status timeline is the
 * first thing the organizer sees on open). Every other state defaults to `Details`.
 */
export function getDefaultTab(speaker: SpeakerPoolEntry): number {
  if (speaker.status === 'INVITED') return 1; // History
  return 0; // Details
}
