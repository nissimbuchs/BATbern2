/**
 * getDefaultTab unit tests (Story 10.30, AC2)
 *
 * Coverage:
 * - All statuses that map to Activity tab (2): IDENTIFIED, CONTACTED
 * - All statuses that map to Details tab (1): DECLINED, CONTENT_SUBMITTED, QUALITY_REVIEWED
 * - All statuses that map to Overview tab (0): INVITED, ACCEPTED, CONFIRMED, READY
 * - Unknown/future statuses fall back to Overview tab (0)
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

describe('getDefaultTab', () => {
  describe('Activity tab (index 2)', () => {
    it('should return 2 for IDENTIFIED', () => {
      expect(getDefaultTab(makeSpeaker('IDENTIFIED'))).toBe(2);
    });

    it('should return 2 for CONTACTED', () => {
      expect(getDefaultTab(makeSpeaker('CONTACTED'))).toBe(2);
    });
  });

  describe('Details tab (index 1)', () => {
    it('should return 1 for DECLINED', () => {
      expect(getDefaultTab(makeSpeaker('DECLINED'))).toBe(1);
    });

    it('should return 1 for CONTENT_SUBMITTED', () => {
      expect(getDefaultTab(makeSpeaker('CONTENT_SUBMITTED'))).toBe(1);
    });

    it('should return 1 for QUALITY_REVIEWED', () => {
      expect(getDefaultTab(makeSpeaker('QUALITY_REVIEWED'))).toBe(1);
    });
  });

  describe('Overview tab (index 0)', () => {
    it('should return 0 for INVITED', () => {
      expect(getDefaultTab(makeSpeaker('INVITED'))).toBe(0);
    });

    it('should return 0 for ACCEPTED', () => {
      expect(getDefaultTab(makeSpeaker('ACCEPTED'))).toBe(0);
    });

    it('should return 0 for CONFIRMED', () => {
      expect(getDefaultTab(makeSpeaker('CONFIRMED'))).toBe(0);
    });

    it('should return 0 for READY', () => {
      expect(getDefaultTab(makeSpeaker('READY'))).toBe(0);
    });

    it('should return 0 for unknown/future statuses via default case', () => {
      expect(getDefaultTab(makeSpeaker('FUTURE_STATUS' as SpeakerPoolEntry['status']))).toBe(0);
    });
  });
});
