/**
 * Primary action surface for the speaker detail drawer (Story 11.D.4 — AC7.1).
 *
 * Renders the same primary-action button (or "Publishable" chip) that the kanban card
 * surfaces along its bottom edge — but at `size="large"` and as a full-width
 * `variant="contained"` button atop the drawer body. Re-uses `getPrimaryAction.ts`
 * verbatim so the label, click handler, and disabled/tooltip state stay in sync between
 * card and drawer (per plan §8.5 "Leads with the same primary-action button at the top,
 * big and prominent").
 */
import React from 'react';
import { Box, Button, Chip, Stack, Tooltip } from '@mui/material';
import {
  CheckCircleOutline as CheckCircleOutlineIcon,
  Schedule as ScheduleIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import {
  getPrimaryAction,
  type PrimaryActionCallbacks,
  type SlotCapacityState,
} from '@/components/organizer/SpeakerStatus/getPrimaryAction';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

export interface PrimaryActionSurfaceProps {
  speaker: SpeakerPoolEntry;
  callbacks: PrimaryActionCallbacks;
  slotCapacity: SlotCapacityState;
}

export const PrimaryActionSurface: React.FC<PrimaryActionSurfaceProps> = ({
  speaker,
  callbacks,
  slotCapacity,
}) => {
  const { t } = useTranslation(['organizer']);
  const action = getPrimaryAction(speaker, callbacks, slotCapacity, t);

  if (action.kind === 'none') return null;

  if (action.kind === 'chip') {
    return (
      <Box sx={{ px: 3, py: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tooltip title={action.tooltip ?? ''}>
          <Chip
            label={action.label}
            icon={<CheckCircleOutlineIcon />}
            color="success"
            variant="outlined"
            sx={{ width: '100%', height: 40, fontSize: '1rem' }}
            data-testid={`drawer-primary-action-chip-${speaker.id}`}
            data-action={action.testIdSuffix}
          />
        </Tooltip>
      </Box>
    );
  }

  const button = (
    <Button
      variant="contained"
      size="large"
      fullWidth
      disabled={action.disabled}
      onClick={action.onClick}
      data-testid={`drawer-primary-action-button-${speaker.id}`}
      data-action={action.testIdSuffix}
    >
      {action.label}
    </Button>
  );

  // Review patch — surface the invitation response deadline alongside the primary
  // action for INVITED speakers. Restores the deadline visibility that the old
  // Overview tab carried (information regression noted by the code-review).
  const showDeadlinePill = speaker.status === 'INVITED' && Boolean(speaker.responseDeadline);
  const deadlinePill = showDeadlinePill ? (
    <Chip
      size="small"
      icon={<ScheduleIcon fontSize="small" />}
      label={t('organizer:speakerDrawer.responseDeadline', {
        date: speaker.responseDeadline,
        defaultValue: 'Response due {{date}}',
      })}
      color="default"
      variant="outlined"
      data-testid={`drawer-primary-action-deadline-${speaker.id}`}
    />
  ) : null;

  return (
    <Box sx={{ px: 3, py: 2, borderBottom: 1, borderColor: 'divider' }}>
      <Stack spacing={1}>
        {action.tooltip ? (
          <Tooltip title={action.tooltip}>
            <span data-testid={`drawer-primary-action-tooltip-${speaker.id}`}>{button}</span>
          </Tooltip>
        ) : (
          button
        )}
        {deadlinePill}
      </Stack>
    </Box>
  );
};
