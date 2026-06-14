/**
 * AttentionCard (Epic 14 Phase B — Stories 14.B.2 / 14.B.4; FR9, FR12, UX-DR3, UX-DR16).
 *
 * One "Needs your attention" card, laid out vertically like the prototype `.task`:
 * a severity colour strip, the task name, a meta row (due chip + assignee avatar),
 * and — when the card deep-links somewhere — a description line of what pressing it
 * does ("Open Speakers & Agenda →").
 *
 * The WHOLE card is the button: clicking it NAVIGATES only — it never triggers a
 * side-effect (NFR1); any consequential action lives behind the destination tab's
 * own modal. Cards with no target render as a non-interactive panel. Event-day
 * (`live`) cards render in an urgent variant.
 */

import React from 'react';
import { Paper, Box, Typography, Chip, Avatar, ButtonBase } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { ArrowForward as ArrowForwardIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { Organizer } from '@/components/shared/OrganizerSelect';
import type { CockpitCard, CardTarget, CardSeverity } from './cockpitCards';

interface AttentionCardProps {
  card: CockpitCard;
  onNavigate: (target: CardTarget) => void;
  /** Organizer directory (id === username) to resolve the assignee's display name. */
  organizers?: Organizer[];
}

const STRIP_COLOR: Record<CardSeverity, string> = {
  overdue: 'error.main',
  live: 'error.main',
  dueSoon: 'warning.main',
  upcoming: 'success.main',
};

const CHIP_COLOR: Record<CardSeverity, 'error' | 'warning' | 'success'> = {
  overdue: 'error',
  live: 'error',
  dueSoon: 'warning',
  upcoming: 'success',
};

function initials(username?: string | null): string {
  if (!username) return '?';
  const parts = username.split(/[.\s_-]+/).filter(Boolean);
  return (parts[0]?.[0] ?? '').concat(parts[1]?.[0] ?? '').toUpperCase() || '?';
}

export const AttentionCard: React.FC<AttentionCardProps> = ({
  card,
  onNavigate,
  organizers = [],
}) => {
  const { t } = useTranslation('events');

  // Resolve the assignee username → display name via the organizer directory
  // (id === username), mirroring the speaker kanban's assigned-organizer chip.
  const assignedOrg = card.assignee ? organizers.find((o) => o.id === card.assignee) : undefined;
  const assignedName = assignedOrg?.name ?? card.assignee ?? '';

  const label = card.taskBacked
    ? String(card.labelVars?.name ?? '')
    : t(`eventPage.cockpit.cards.${card.labelKey}`, card.labelVars);

  // Due chip text
  let dueText: string;
  if (card.severity === 'live') {
    dueText = t('eventPage.cockpit.due.live', 'Now · event is live');
  } else if (card.dueDays === undefined) {
    dueText = t('eventPage.cockpit.due.upcoming', 'Upcoming');
  } else if (card.severity === 'overdue') {
    dueText = t('eventPage.cockpit.due.overdue', 'Overdue · {{days}} days', {
      days: Math.abs(card.dueDays),
    });
  } else if (card.severity === 'dueSoon') {
    dueText = t('eventPage.cockpit.due.dueSoon', 'Due in {{days}} days', { days: card.dueDays });
  } else {
    dueText = t('eventPage.cockpit.due.upcoming', 'Upcoming');
  }

  // Action description: tab name for tab targets, generic "Open" for routes.
  const target = card.target;
  let actionLabel: string | undefined;
  if (target?.kind === 'tab') {
    actionLabel = t('eventPage.cockpit.attention.open', 'Open {{tab}} →', {
      tab: t(
        `eventPage.tabs.${target.tab === 'speakers' ? 'speakersAgenda' : target.tab}`,
        target.tab
      ),
    });
  } else if (target?.kind === 'route') {
    actionLabel = t('eventPage.cockpit.attention.go', 'Open →');
  }

  const cardSx = {
    display: 'flex',
    alignItems: 'stretch',
    gap: 1.5,
    p: 2,
    width: '100%',
    textAlign: 'left' as const,
    borderRadius: 1.5,
    border: 1,
    borderColor: 'divider',
    borderLeft: 6,
    borderLeftColor: STRIP_COLOR[card.severity],
    ...(card.pinned
      ? {
          bgcolor: (theme: import('@mui/material').Theme) => alpha(theme.palette.error.main, 0.06),
          borderColor: 'error.light',
        }
      : {}),
  };

  const content = (
    <Box
      sx={{
        flexGrow: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-start',
        gap: 0.75,
      }}
    >
      <Typography variant="body1" fontWeight={card.pinned ? 700 : 600}>
        {label}
      </Typography>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}>
        <Chip size="small" color={CHIP_COLOR[card.severity]} variant="outlined" label={dueText} />
        {card.assignee && (
          <Chip
            size="small"
            variant="outlined"
            label={assignedName}
            avatar={
              <Avatar sx={{ width: 18, height: 18, fontSize: '0.6rem' }}>
                {initials(assignedName)}
              </Avatar>
            }
            data-testid={`cockpit-attention-assignee-${card.id}`}
            sx={{ height: 22, '& .MuiChip-label': { fontSize: '0.7rem', px: 0.75 } }}
          />
        )}
      </Box>

      {target && actionLabel && (
        <Typography
          variant="body2"
          color={card.pinned ? 'error' : 'primary'}
          fontWeight={600}
          sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, mt: 0.25 }}
        >
          {actionLabel}
          <ArrowForwardIcon sx={{ fontSize: 16 }} />
        </Typography>
      )}
    </Box>
  );

  // The whole card is the button when it deep-links somewhere; otherwise it is a
  // static panel (e.g. an unmatched task with no target).
  if (target) {
    return (
      <ButtonBase
        focusRipple
        component="div"
        role="button"
        onClick={() => onNavigate(target)}
        aria-label={actionLabel ? `${label} — ${actionLabel}` : label}
        data-testid={`cockpit-attention-card${card.taskBacked ? '-task' : ''}`}
        data-card-id={card.id}
        sx={{
          ...cardSx,
          transition: (theme) => theme.transitions.create(['box-shadow', 'border-color']),
          '&:hover': { borderColor: card.pinned ? 'error.main' : 'primary.light', boxShadow: 2 },
          '&:focus-visible': { outline: 2, outlineColor: 'primary.main', outlineOffset: 2 },
        }}
      >
        {content}
      </ButtonBase>
    );
  }

  return (
    <Paper
      variant="outlined"
      data-testid={`cockpit-attention-card${card.taskBacked ? '-task' : ''}`}
      data-card-id={card.id}
      sx={cardSx}
    >
      {content}
    </Paper>
  );
};
