/**
 * Pure schedule-timeline computation shared by the SchedulePreview component and the
 * EventTypeConfigurationForm (which shows a read-only, derived end time).
 *
 * Kept in its own module (no React exports) so both a component and the form can import
 * it without tripping react-refresh's "only export components" rule.
 */

export interface ScheduleConfig {
  typicalStartTime?: string | null;
  typicalEndTime?: string | null;
  moderationStartDuration?: number | null;
  moderationEndDuration?: number | null;
  breakDuration?: number | null;
  lunchDuration?: number | null;
  slotDuration?: number | null;
  maxSlots?: number | null;
  breakSlots?: number | null;
  lunchSlots?: number | null;
  theoreticalSlotsAM?: boolean | null;
}

export interface TimelineEntry {
  time: string;
  title: string;
  duration: number;
  kind: 'moderation' | 'break' | 'lunch' | 'session-slot';
}

export function parseTime(timeStr: string | null | undefined): { h: number; m: number } {
  if (!timeStr) return { h: 9, m: 0 };
  const parts = timeStr.split(':');
  return { h: parseInt(parts[0] ?? '9', 10), m: parseInt(parts[1] ?? '0', 10) };
}

export function formatTime(h: number, m: number): string {
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

function addMinutes(h: number, m: number, minutes: number): { h: number; m: number } {
  const total = h * 60 + m + minutes;
  return { h: Math.floor(total / 60), m: total % 60 };
}

export function buildTimeline(config: ScheduleConfig): TimelineEntry[] {
  const entries: TimelineEntry[] = [];

  const start = parseTime(config.typicalStartTime);
  let cursor = { ...start };

  const modStartDur = config.moderationStartDuration ?? 5;
  const modEndDur = config.moderationEndDuration ?? 5;
  const breakDur = config.breakDuration ?? 20;
  const lunchDur = config.lunchDuration ?? 60;
  const slotDur = config.slotDuration ?? 45;
  const maxSlots = config.maxSlots ?? 0;
  const breakSlots = config.breakSlots ?? 0;
  const lunchSlots = config.lunchSlots ?? 0;
  const amSplit = config.theoreticalSlotsAM && lunchSlots > 0;

  // Moderation Start
  entries.push({
    time: formatTime(cursor.h, cursor.m),
    title: 'Moderation Start',
    duration: modStartDur,
    kind: 'moderation',
  });
  cursor = addMinutes(cursor.h, cursor.m, modStartDur);

  if (amSplit) {
    const amSlots = Math.ceil(maxSlots / 2);
    const amBreakAfter = Math.ceil(amSlots / 2);
    let amBreaksUsed = 0;

    for (let i = 0; i < amSlots; i++) {
      entries.push({
        time: formatTime(cursor.h, cursor.m),
        title: `Session Slot ${entries.filter((e) => e.kind === 'session-slot').length + 1}`,
        duration: slotDur,
        kind: 'session-slot',
      });
      cursor = addMinutes(cursor.h, cursor.m, slotDur);

      if (i === amBreakAfter - 1 && breakSlots > 0 && amBreaksUsed < breakSlots) {
        entries.push({
          time: formatTime(cursor.h, cursor.m),
          title: 'Kaffee-Pause',
          duration: breakDur,
          kind: 'break',
        });
        cursor = addMinutes(cursor.h, cursor.m, breakDur);
        amBreaksUsed++;
      }
    }

    // Lunch
    entries.push({
      time: formatTime(cursor.h, cursor.m),
      title: 'Mittagessen',
      duration: lunchDur,
      kind: 'lunch',
    });
    cursor = addMinutes(cursor.h, cursor.m, lunchDur);

    // PM block
    const pmSlots = maxSlots - amSlots;
    const pmBreakAfter = Math.ceil(pmSlots / 2);
    let remainingBreaks = breakSlots - amBreaksUsed;

    for (let i = 0; i < pmSlots; i++) {
      entries.push({
        time: formatTime(cursor.h, cursor.m),
        title: `Session Slot ${entries.filter((e) => e.kind === 'session-slot').length + 1}`,
        duration: slotDur,
        kind: 'session-slot',
      });
      cursor = addMinutes(cursor.h, cursor.m, slotDur);

      if (i === pmBreakAfter - 1 && remainingBreaks > 0) {
        entries.push({
          time: formatTime(cursor.h, cursor.m),
          title: 'Pause',
          duration: breakDur,
          kind: 'break',
        });
        cursor = addMinutes(cursor.h, cursor.m, breakDur);
        remainingBreaks--;
      }
    }
  } else {
    // Simple linear
    const breakAfter = breakSlots > 0 ? Math.ceil(maxSlots / 2) : -1;

    for (let i = 0; i < maxSlots; i++) {
      entries.push({
        time: formatTime(cursor.h, cursor.m),
        title: `Session Slot ${i + 1}`,
        duration: slotDur,
        kind: 'session-slot',
      });
      cursor = addMinutes(cursor.h, cursor.m, slotDur);

      if (i === breakAfter - 1 && breakSlots > 0) {
        entries.push({
          time: formatTime(cursor.h, cursor.m),
          title: 'Pause',
          duration: breakDur,
          kind: 'break',
        });
        cursor = addMinutes(cursor.h, cursor.m, breakDur);
      }
    }
  }

  // Moderation End
  entries.push({
    time: formatTime(cursor.h, cursor.m),
    title: 'Moderation End',
    duration: modEndDur,
    kind: 'moderation',
  });
  cursor = addMinutes(cursor.h, cursor.m, modEndDur);

  // End marker (no duration)
  entries.push({
    time: formatTime(cursor.h, cursor.m),
    title: 'Ende',
    duration: 0,
    kind: 'session-slot',
  });

  return entries;
}

/**
 * Compute the schedule's end time (HH:mm) from the configuration, by running the same
 * timeline the preview renders and taking its final "Ende" marker. This is the single
 * source of truth for the (read-only) typical end time shown in the edit form.
 */
export function computeScheduleEndTime(config: ScheduleConfig): string {
  const entries = buildTimeline(config);
  const last = entries[entries.length - 1];
  if (last) {
    return last.time;
  }
  const start = parseTime(config.typicalStartTime);
  return formatTime(start.h, start.m);
}
