/**
 * Topic usage totals (17.0 deferred work): `attendance` is null until the event has taken place.
 * The heat maps summed the raw values, so a null became 0, "null attendees" or NaN. Engagement was
 * never measured and is gone from the API and the heat maps (owner, 2026-11-09).
 */
import { describe, expect, it } from 'vitest';
import type { TopicUsageHistory } from '@/types/topic.types';
import { knownSum, quarterlyUsage } from './usageTotals';
import { aTopicUsage } from '@/test/fixtures';

const usage = (
  eventNumber: number,
  usedDate: string,
  attendance: number | null
): TopicUsageHistory => ({ ...aTopicUsage(), eventNumber, usedDate, attendance });

describe('knownSum', () => {
  it('ignores unknown values and answers null when none is known', () => {
    expect(knownSum([120, null, 30])).toBe(150);
    expect(knownSum([null, null])).toBeNull();
  });
});

describe('quarterlyUsage', () => {
  it('counts every usage but totals only known attendance', () => {
    const points = quarterlyUsage([
      usage(60, '2026-02-10T18:00:00Z', 120),
      usage(61, '2026-03-10T18:00:00Z', null),
      usage(62, '2026-07-10T18:00:00Z', null),
    ]);

    const q1 = points.find((p) => p.quarter === 'Q1 2026')!;
    expect(q1.usageCount).toBe(2);
    expect(q1.totalAttendance).toBe(120);
    expect(q1).not.toHaveProperty('avgEngagement');
    expect(q1.events).toEqual([60, 61]);

    const q3 = points.find((p) => p.quarter === 'Q3 2026')!;
    expect(q3.totalAttendance).toBeNull();
    expect(q1.intensity).toBe(100);
    expect(q3.intensity).toBe(50);
  });
});
