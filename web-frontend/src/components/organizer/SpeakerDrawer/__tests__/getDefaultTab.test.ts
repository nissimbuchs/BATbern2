/**
 * getDefaultTab unit tests — Story 11.D.4 (AC7.5) + Epic 11 bug fix 2026-05-18.
 *
 * The drawer collapsed from 3 tabs (Overview/Details/Activity) to 2 tabs
 * (Details/History) in Story 11.D.4; Epic 11 bug fix 2026-05-18 added a third
 * `'content'` tab for READY+ states (conditional). Tab values are now string keys
 * (`'details' | 'content' | 'history'`) so the drawer doesn't need to remap indices
 * when the Content tab is hidden.
 *
 *   - `'history'` for INVITED — surfaces the response-status timeline first.
 *   - `'details'` for every other state, including legacy unknown values.
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

describe('getDefaultTab — Story 11.D.4 + Epic 11 bug fix (3-tab drawer, string-keyed)', () => {
  describe("History tab ('history')", () => {
    it("should return 'history' for INVITED", () => {
      expect(getDefaultTab(makeSpeaker('INVITED'))).toBe('history');
    });
  });

  describe("Details tab ('details')", () => {
    it.each([
      'IDENTIFIED',
      'CONTACTED',
      'READY',
      'ACCEPTED',
      'CONTENT_SUBMITTED',
      'QUALITY_REVIEWED',
      'DECLINED',
    ] as const)("should return 'details' for %s", (status) => {
      expect(getDefaultTab(makeSpeaker(status))).toBe('details');
    });

    it("should return 'details' for unknown/future statuses", () => {
      expect(getDefaultTab(makeSpeaker('FUTURE_STATUS' as SpeakerPoolEntry['status']))).toBe(
        'details'
      );
    });
  });
});
