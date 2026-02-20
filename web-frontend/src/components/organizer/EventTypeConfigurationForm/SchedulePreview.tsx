/**
 * SchedulePreview Component
 *
 * Renders a live vertical timeline from event type configuration values.
 * Updates in real-time as form fields change.
 *
 * Used in EventTypeConfigurationAdmin edit modal (right column).
 */

import React from 'react';
import { Box, Typography, Paper, Divider, Stack, Chip } from '@mui/material';
import CoffeeIcon from '@mui/icons-material/Coffee';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import MicIcon from '@mui/icons-material/Mic';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
interface ScheduleConfig {
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

interface SchedulePreviewProps {
  config: ScheduleConfig;
}

interface TimelineEntry {
  time: string;
  title: string;
  duration: number;
  kind: 'moderation' | 'break' | 'lunch' | 'session-slot';
}

function parseTime(timeStr: string | null | undefined): { h: number; m: number } {
  if (!timeStr) return { h: 9, m: 0 };
  const parts = timeStr.split(':');
  return { h: parseInt(parts[0] ?? '9', 10), m: parseInt(parts[1] ?? '0', 10) };
}

function formatTime(h: number, m: number): string {
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

function addMinutes(h: number, m: number, minutes: number): { h: number; m: number } {
  const total = h * 60 + m + minutes;
  return { h: Math.floor(total / 60), m: total % 60 };
}

function buildTimeline(config: ScheduleConfig): TimelineEntry[] {
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

const kindColors: Record<TimelineEntry['kind'], string> = {
  moderation: 'grey.200',
  break: 'orange.100',
  lunch: 'green.100',
  'session-slot': 'primary.50',
};

const kindBorderColors: Record<TimelineEntry['kind'], string> = {
  moderation: 'grey.400',
  break: 'warning.main',
  lunch: 'success.main',
  'session-slot': 'primary.main',
};

function EntryIcon({ kind }: { kind: TimelineEntry['kind'] }) {
  if (kind === 'moderation') return <MicIcon fontSize="small" sx={{ color: 'grey.600' }} />;
  if (kind === 'break') return <CoffeeIcon fontSize="small" sx={{ color: 'warning.main' }} />;
  if (kind === 'lunch') return <RestaurantIcon fontSize="small" sx={{ color: 'success.main' }} />;
  return <CalendarTodayIcon fontSize="small" sx={{ color: 'primary.main', opacity: 0.5 }} />;
}

export const SchedulePreview: React.FC<SchedulePreviewProps> = ({ config }) => {
  const entries = buildTimeline(config);

  const totalMinutes = (() => {
    const start = parseTime(config.typicalStartTime);
    const last = entries[entries.length - 1];
    if (!last) return 0;
    const parts = last.time.split(':');
    const endH = parseInt(parts[0] ?? '0', 10);
    const endM = parseInt(parts[1] ?? '0', 10);
    return endH * 60 + endM - (start.h * 60 + start.m);
  })();

  const totalHours = Math.floor(totalMinutes / 60);
  const remainingMin = totalMinutes % 60;
  const totalLabel =
    totalHours > 0
      ? `${totalHours}h ${remainingMin > 0 ? `${remainingMin}min` : ''}`.trim()
      : `${totalMinutes}min`;

  return (
    <Box>
      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
        Zeitplan-Vorschau
      </Typography>
      <Paper
        variant="outlined"
        sx={{ maxHeight: 480, overflowY: 'auto', p: 1.5, bgcolor: 'background.default' }}
      >
        <Stack spacing={0.5}>
          {entries.map((entry, idx) => {
            const isLast = idx === entries.length - 1;
            const isSlot = entry.kind === 'session-slot';
            const isDashed = isSlot && !isLast;

            return (
              <Box key={idx} sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
                {/* Time column */}
                <Typography
                  variant="caption"
                  sx={{
                    minWidth: 40,
                    pt: 0.5,
                    color: 'text.secondary',
                    fontFamily: 'monospace',
                  }}
                >
                  {entry.time}
                </Typography>

                {/* Timeline line */}
                <Box
                  sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    minWidth: 20,
                  }}
                >
                  <Box
                    sx={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      bgcolor: isLast ? 'text.disabled' : kindBorderColors[entry.kind],
                      mt: 0.8,
                    }}
                  />
                  {!isLast && (
                    <Box
                      sx={{
                        width: 2,
                        flex: 1,
                        minHeight: 24,
                        bgcolor: isDashed ? 'divider' : kindBorderColors[entry.kind],
                        opacity: isDashed ? 0.4 : 0.6,
                      }}
                    />
                  )}
                </Box>

                {/* Entry block */}
                {!isLast && (
                  <Box
                    sx={{
                      flex: 1,
                      mb: 0.5,
                      px: 1,
                      py: 0.5,
                      borderRadius: 1,
                      border: '1px solid',
                      borderColor: kindBorderColors[entry.kind],
                      borderStyle: isDashed ? 'dashed' : 'solid',
                      bgcolor: kindColors[entry.kind],
                      display: 'flex',
                      alignItems: 'center',
                      gap: 0.5,
                    }}
                  >
                    <EntryIcon kind={entry.kind} />
                    <Typography variant="caption" sx={{ flex: 1 }}>
                      {entry.title}
                    </Typography>
                    <Chip
                      label={`${entry.duration}min`}
                      size="small"
                      sx={{ height: 18, fontSize: '0.65rem' }}
                    />
                  </Box>
                )}
              </Box>
            );
          })}
        </Stack>
      </Paper>
      <Box sx={{ mt: 1, display: 'flex', justifyContent: 'flex-end' }}>
        <Divider sx={{ flex: 1, mr: 1, alignSelf: 'center' }} />
        <Typography variant="caption" color="text.secondary">
          Gesamt: {totalLabel}
        </Typography>
      </Box>
    </Box>
  );
};
