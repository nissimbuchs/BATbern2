/**
 * getDefaultTab unit tests — Story 11.D.4 (AC7.5).
 *
 * The drawer collapsed from 3 tabs (Overview/Details/Activity) to 2 tabs
 * (Details/History). `getDefaultTab` now returns:
 *   - 1 (History) for INVITED — surfaces the response-status timeline.
 *   - 0 (Details) for every other state, including legacy unknown values.
 */

import { describe, it, expect } from 'vitest';
import { getDefaultTab } from '../getDefaultTab';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

function makeSpeaker(status: SpeakerPoolEntry['status']): SpeakerPoolEntry {
  return {
    id: 'sp-1',
    eventId: 'evt-1',
    speakerName: 'Test Speaker',
    status,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

describe('getDefaultTab — Story 11.D.4 (2-tab drawer)', () => {
  describe('History tab (index 1)', () => {
    it('should return 1 for INVITED', () => {
      expect(getDefaultTab(makeSpeaker('INVITED'))).toBe(1);
    });
  });

  describe('Details tab (index 0)', () => {
    it.each([
      'IDENTIFIED',
      'CONTACTED',
      'READY',
      'ACCEPTED',
      'CONTENT_SUBMITTED',
      'QUALITY_REVIEWED',
      'DECLINED',
    ] as const)('should return 0 for %s', (status) => {
      expect(getDefaultTab(makeSpeaker(status))).toBe(0);
    });

    it('should return 0 for unknown/future statuses', () => {
      expect(getDefaultTab(makeSpeaker('FUTURE_STATUS' as SpeakerPoolEntry['status']))).toBe(0);
    });
  });
});
