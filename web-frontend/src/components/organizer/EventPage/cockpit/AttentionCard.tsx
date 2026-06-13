/**
 * AttentionCard (Epic 14 Phase B — Stories 14.B.2 / 14.B.4; FR9, FR12, UX-DR3).
 *
 * One "Needs your attention" card: severity colour strip, label, due chip,
 * assignee avatar, and a deep-link button. Event-day (`live`) cards render in an
 * urgent variant. The button NAVIGATES only — it never triggers a side-effect
 * (NFR1); any consequential action lives behind the destination tab's own modal.
 */

import React from 'react';
import { Paper, Box, Typography, Chip, Avatar, Button, Tooltip } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { ArrowForward as ArrowForwardIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { CockpitCard, CardTarget, CardSeverity } from './cockpitCards';

interface AttentionCardProps {
  card: CockpitCard;
  onNavigate: (target: CardTarget) => void;
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

export const AttentionCard: React.FC<AttentionCardProps> = ({ card, onNavigate }) => {
  const { t } = useTranslation('events');

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

  // Deep-link button label: tab name for tab targets, generic "Open" for routes.
  const target = card.target;
  let buttonLabel: string | undefined;
  if (target?.kind === 'tab') {
    buttonLabel = t('eventPage.cockpit.attention.open', 'Open {{tab}} →', {
      tab: t(
        `eventPage.tabs.${target.tab === 'speakers' ? 'speakersAgenda' : target.tab}`,
        target.tab
      ),
    });
  } else if (target?.kind === 'route') {
    buttonLabel = t('eventPage.cockpit.attention.go', 'Open →');
  }

  return (
    <Paper
      variant="outlined"
      data-testid={`cockpit-attention-card${card.taskBacked ? '-task' : ''}`}
      data-card-id={card.id}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        p: 2,
        borderLeft: 6,
        borderLeftColor: STRIP_COLOR[card.severity],
        ...(card.pinned
          ? {
              bgcolor: (theme) => alpha(theme.palette.error.main, 0.06),
              borderColor: 'error.light',
            }
          : {}),
      }}
    >
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Typography variant="body1" fontWeight={card.pinned ? 700 : 500} noWrap>
          {label}
        </Typography>
        <Chip
          size="small"
          color={CHIP_COLOR[card.severity]}
          variant="outlined"
          label={dueText}
          sx={{ mt: 0.5 }}
        />
      </Box>

      {card.assignee && (
        <Tooltip title={card.assignee}>
          <Avatar sx={{ width: 28, height: 28, fontSize: 13 }} aria-label={card.assignee}>
            {initials(card.assignee)}
          </Avatar>
        </Tooltip>
      )}

      {target && buttonLabel && (
        <Button
          size="small"
          variant={card.pinned ? 'contained' : 'outlined'}
          color={card.pinned ? 'error' : 'primary'}
          endIcon={<ArrowForwardIcon />}
          onClick={() => onNavigate(target)}
          data-testid={`cockpit-attention-deeplink-${card.id}`}
        >
          {buttonLabel}
        </Button>
      )}
    </Paper>
  );
};
