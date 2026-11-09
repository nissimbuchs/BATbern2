/**
 * Topic usage totals (17.0 deferred work): `attendance` is null until the event has taken place
 * and `engagementScore` is null when nothing is stored. The heat maps summed and averaged the raw
 * values, so a null became 0, "null attendees" or NaN.
 */
import { describe, expect, it } from 'vitest';
import type { TopicUsageHistory } from '@/types/topic.types';
import { knownAverage, knownSum, quarterlyUsage } from './usageTotals';

const usage = (
  eventNumber: number,
  usedDate: string,
  attendance: number | null,
  engagementScore: number | null
): TopicUsageHistory => ({ eventNumber, usedDate, attendance, engagementScore });

describe('knownSum / knownAverage', () => {
  it('ignores unknown values and answers null when none is known', () => {
    expect(knownSum([120, null, 30])).toBe(150);
    expect(knownSum([null, null])).toBeNull();
    expect(knownAverage([0.8, null, 0.6])).toBeCloseTo(0.7);
    expect(knownAverage([null])).toBeNull();
  });
});

describe('quarterlyUsage', () => {
  it('counts every usage but totals only known attendance and engagement', () => {
    const points = quarterlyUsage([
      usage(60, '2026-02-10T18:00:00Z', 120, null),
      usage(61, '2026-03-10T18:00:00Z', null, null),
      usage(62, '2026-07-10T18:00:00Z', null, null),
    ]);

    const q1 = points.find((p) => p.quarter === 'Q1 2026')!;
    expect(q1.usageCount).toBe(2);
    expect(q1.totalAttendance).toBe(120);
    expect(q1.avgEngagement).toBeNull();
    expect(q1.events).toEqual([60, 61]);

    const q3 = points.find((p) => p.quarter === 'Q3 2026')!;
    expect(q3.totalAttendance).toBeNull();
    expect(q3.avgEngagement).toBeNull();
    expect(q1.intensity).toBe(100);
    expect(q3.intensity).toBe(50);
  });
});
