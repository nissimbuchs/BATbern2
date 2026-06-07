/**
 * SchedulePreview Component
 *
 * Renders a live vertical timeline from event type configuration values.
 * Updates in real-time as form fields change.
 *
 * Used in the EventTypesTab edit modal (right column).
 */

import React from 'react';
import { Box, Typography, Paper, Divider, Stack, Chip } from '@mui/material';
import CoffeeIcon from '@mui/icons-material/Coffee';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import MicIcon from '@mui/icons-material/Mic';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import {
  buildTimeline,
  parseTime,
  type ScheduleConfig,
  type TimelineEntry,
} from './scheduleTimeline';

interface SchedulePreviewProps {
  config: ScheduleConfig;
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
