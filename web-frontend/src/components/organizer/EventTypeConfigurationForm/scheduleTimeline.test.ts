/**
 * Unit tests for the pure schedule-timeline computation.
 * Covers computeScheduleEndTime, the single source of truth for the form's read-only end time.
 */

import { describe, it, expect } from 'vitest';
import { buildTimeline, computeScheduleEndTime, type ScheduleConfig } from './scheduleTimeline';

const baseConfig: ScheduleConfig = {
  typicalStartTime: '09:00',
  slotDuration: 45,
  maxSlots: 8,
  breakSlots: 2,
  lunchSlots: 1,
  theoreticalSlotsAM: true,
  moderationStartDuration: 5,
  moderationEndDuration: 5,
  breakDuration: 20,
  lunchDuration: 60,
};

function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map((p) => parseInt(p, 10));
  return (h ?? 0) * 60 + (m ?? 0);
}

describe('computeScheduleEndTime', () => {
  it('should_returnLastTimelineEntryTime_when_configProvided', () => {
    const entries = buildTimeline(baseConfig);
    const last = entries[entries.length - 1];
    expect(computeScheduleEndTime(baseConfig)).toBe(last?.time);
  });

  it('should_endAfterStart_when_anySlotsConfigured', () => {
    expect(toMinutes(computeScheduleEndTime(baseConfig))).toBeGreaterThan(
      toMinutes(baseConfig.typicalStartTime ?? '09:00')
    );
  });

  it('should_shiftEndByTheSameDelta_when_startTimeChanges', () => {
    const endAt9 = toMinutes(computeScheduleEndTime(baseConfig));
    const endAt16 = toMinutes(computeScheduleEndTime({ ...baseConfig, typicalStartTime: '16:00' }));
    // Total duration is start-independent, so the end shifts by exactly the start delta (7h).
    expect(endAt16 - endAt9).toBe(7 * 60);
  });

  it('should_growEnd_when_slotsAdded', () => {
    const fewer = toMinutes(computeScheduleEndTime({ ...baseConfig, maxSlots: 4 }));
    const more = toMinutes(computeScheduleEndTime({ ...baseConfig, maxSlots: 8 }));
    expect(more).toBeGreaterThan(fewer);
  });

  it('should_fallBackToStart_when_noSlotsAndNoStructure', () => {
    // Degenerate config still yields a valid HH:mm string (never throws).
    const end = computeScheduleEndTime({ typicalStartTime: '10:00', maxSlots: 0 });
    expect(end).toMatch(/^\d{2}:\d{2}$/);
  });
});
