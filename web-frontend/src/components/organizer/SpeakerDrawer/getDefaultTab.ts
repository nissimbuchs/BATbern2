import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

/**
 * Returns the default tab index based on speaker workflow state.
 * - 0 = Overview (action buttons most relevant)
 * - 1 = Details (response/content info most relevant)
 * - 2 = Activity (outreach workflow, contact history most relevant)
 */
export function getDefaultTab(speaker: SpeakerPoolEntry): number {
  switch (speaker.status) {
    case 'IDENTIFIED':
    case 'CONTACTED':
      return 2; // Activity — outreach workflow

    case 'DECLINED':
    case 'CONTENT_SUBMITTED':
    case 'QUALITY_REVIEWED':
      return 1; // Details — response/content info

    case 'INVITED':
    case 'ACCEPTED':
    case 'CONFIRMED':
    case 'READY':
    default:
      return 0; // Overview — action buttons
  }
}
