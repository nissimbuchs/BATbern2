/**
 * Totals over topic usage records. `attendance` is null until the event has taken place, so a
 * total covers the known values only and is null when none is known; an unknown value never
 * counts as 0.
 */
import type { TopicUsageHistory } from '@/types/topic.types';

/** Sum of the known values; null when none is known. */
export function knownSum(values: ReadonlyArray<number | null | undefined>): number | null {
  const known = values.filter((v): v is number => typeof v === 'number' && Number.isFinite(v));
  return known.length === 0 ? null : known.reduce((sum, v) => sum + v, 0);
}

export interface QuarterlyUsage {
  quarter: string; // e.g. "Q1 2024"
  year: number;
  quarterNum: number;
  usageCount: number;
  totalAttendance: number | null;
  events: number[]; // event numbers (GitHub Issue #379: no UUIDs in the API)
  intensity: number; // 0-100, usage count relative to the busiest quarter
}

/** Usage grouped by calendar quarter of `usedDate`, in first-seen order. */
export function quarterlyUsage(usageHistory: ReadonlyArray<TopicUsageHistory>): QuarterlyUsage[] {
  const buckets = new Map<string, { point: QuarterlyUsage; usages: TopicUsageHistory[] }>();
  for (const usage of usageHistory) {
    const date = new Date(usage.usedDate);
    const year = date.getFullYear();
    const quarterNum = Math.floor(date.getMonth() / 3) + 1;
    const quarter = `Q${quarterNum} ${year}`;
    const bucket = buckets.get(quarter) ?? {
      point: {
        quarter,
        year,
        quarterNum,
        usageCount: 0,
        totalAttendance: null,
        events: [],
        intensity: 0,
      },
      usages: [],
    };
    bucket.usages.push(usage);
    bucket.point.events.push(usage.eventNumber);
    buckets.set(quarter, bucket);
  }
  const points = Array.from(buckets.values()).map(({ point, usages }) => ({
    ...point,
    usageCount: usages.length,
    totalAttendance: knownSum(usages.map((u) => u.attendance)),
  }));
  const maxUsage = Math.max(0, ...points.map((p) => p.usageCount));
  return points.map((p) => ({
    ...p,
    intensity: maxUsage > 0 ? (p.usageCount / maxUsage) * 100 : 0,
  }));
}
