/**
 * MetricTiles (Epic 14 Phase B — Story 14.B.5; FR13, UX-DR4).
 *
 * Four "At a glance" tiles that double as deep-links into their destination
 * tab/sub-view. Values come from the existing `useEvent(['metrics','registrations'])`
 * payload — no backend change. Each tile is keyboard-operable (ButtonBase) and
 * screen-reader labelled (NFR7). Sub-view keys are the stable `pool|agenda|slots`
 * that survive Phase C's relabel.
 */

import React from 'react';
import { Box, Paper, ButtonBase, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Event, EventDetailUI } from '@/types/event.types';
import type { CardTarget } from './cockpitCards';

interface MetricTilesProps {
  event: Event | EventDetailUI;
  onNavigate: (target: CardTarget) => void;
}

interface Tile {
  id: string;
  icon: string;
  labelKey: string;
  value: number;
  total?: number | null;
  /** Secondary line (e.g. "72% filled · 3 on waitlist"). */
  sub?: string;
  target: CardTarget;
}

export const MetricTiles: React.FC<MetricTilesProps> = ({ event, onNavigate }) => {
  const { t } = useTranslation('events');
  const e = event as Record<string, unknown>;
  const num = (v: unknown) => (typeof v === 'number' ? v : 0);

  // Use the canonical `confirmedCount` (registrations expansion) — same field the
  // Cockpit "watch registrations" card reads, so the tile and the card never diverge.
  const confirmedCount = num(e.confirmedCount);
  const registrationCapacity =
    typeof e.registrationCapacity === 'number' ? e.registrationCapacity : null;
  const waitlistCount = num(e.waitlistCount);
  const confirmedSpeakers = num(e.confirmedSpeakersCount);
  const maxSpeakerSlots = num(e.maxSpeakerSlots);
  const materials = num(e.sessionsWithMaterialsCount);
  const totalSessions =
    num(e.totalSessionsCount) || ((e.sessions as unknown[] | undefined)?.length ?? 0);
  // Agenda tile = speaker sessions that have a start time, out of the event's max
  // speaker slots. Structural sessions (moderation/break/lunch) are NOT speaker slots,
  // so they must be excluded — counting them inflated the fraction (e.g. 8/8 for an
  // event with 5 talks slotted + 2 moderation + 1 break). The structural set mirrors
  // STRUCTURAL_TYPES in DragDropSlotAssignment (the timetable's non-SPEAKER_SLOT types).
  const STRUCTURAL_SESSION_TYPES = ['moderation', 'break', 'lunch', 'aperitif'];
  const sessionsArr =
    (e.sessions as { startTime?: string | null; sessionType?: string | null }[] | undefined) ?? [];
  const speakerSessions = sessionsArr.filter(
    (s) => !STRUCTURAL_SESSION_TYPES.includes((s.sessionType ?? '').toLowerCase())
  );
  const sessionsSlotted = speakerSessions.filter((s) => !!s.startTime).length;
  // Denominator = max speaker slots (the agenda capacity); fall back to the count of
  // speaker sessions, then the metric, when maxSpeakerSlots isn't hydrated.
  const agendaTotal = maxSpeakerSlots || speakerSessions.length || totalSessions;

  const pctFilled =
    registrationCapacity && registrationCapacity > 0
      ? Math.round((confirmedCount / registrationCapacity) * 100)
      : null;

  const tiles: Tile[] = [
    {
      id: 'registrations',
      icon: '🎟️',
      labelKey: 'registrations',
      value: confirmedCount,
      total: registrationCapacity,
      sub: [
        pctFilled !== null
          ? t('eventPage.cockpit.tiles.filled', '{{percent}}% filled', { percent: pctFilled })
          : null,
        waitlistCount > 0
          ? t('eventPage.cockpit.tiles.waitlist', '{{count}} on waitlist', { count: waitlistCount })
          : null,
      ]
        .filter(Boolean)
        .join(' · '),
      target: { kind: 'tab', tab: 'registrations' },
    },
    {
      id: 'speakers',
      icon: '🎤',
      labelKey: 'speakers',
      value: confirmedSpeakers,
      total: maxSpeakerSlots,
      target: { kind: 'tab', tab: 'speakers', view: 'pool' },
    },
    {
      id: 'materials',
      icon: '📋',
      labelKey: 'materials',
      value: materials,
      total: totalSessions,
      target: { kind: 'tab', tab: 'speakers', view: 'agenda' },
    },
    {
      id: 'agenda',
      icon: '🗓️',
      labelKey: 'agenda',
      value: sessionsSlotted,
      total: agendaTotal,
      target: { kind: 'tab', tab: 'speakers', view: 'slots' },
    },
  ];

  return (
    <Box
      data-testid="cockpit-metric-tiles"
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' },
        gap: 2,
      }}
    >
      {tiles.map((tile) => {
        const label = t(`eventPage.cockpit.tiles.${tile.labelKey}`, tile.labelKey);
        const fraction = tile.total != null ? `${tile.value} / ${tile.total}` : String(tile.value);
        return (
          <ButtonBase
            key={tile.id}
            onClick={() => onNavigate(tile.target)}
            data-testid={`cockpit-metric-tile-${tile.id}`}
            aria-label={`${label}: ${fraction}${tile.sub ? `. ${tile.sub}` : ''}`}
            sx={{ display: 'block', textAlign: 'left', borderRadius: 1, width: '100%' }}
          >
            <Paper variant="outlined" sx={{ p: 2, width: '100%', height: '100%' }}>
              <Typography variant="overline" color="text.secondary">
                {tile.icon} {label}
              </Typography>
              <Typography variant="h5" sx={{ mt: 0.5 }}>
                {fraction}
              </Typography>
              {tile.sub && (
                <Typography variant="caption" color="text.secondary">
                  {tile.sub}
                </Typography>
              )}
            </Paper>
          </ButtonBase>
        );
      })}
    </Box>
  );
};
