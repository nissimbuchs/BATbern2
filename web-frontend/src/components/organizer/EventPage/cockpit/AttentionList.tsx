/**
 * AttentionList (Epic 14 Phase B — Story 14.B.2; FR9–FR11, UX-DR16).
 *
 * The Cockpit "Needs your attention" region: the sorted, only-open card list
 * (done cards already filtered by the registry), a "＋ Add task" affordance that
 * reuses the existing `CustomTaskModal`, plus friendly empty + error/retry states.
 *
 * `CustomTaskModal` invalidates `['tasks']` on success, which does NOT match the
 * Cockpit's `['eventTasks', code]` key — so we invalidate the event-task queries
 * when the modal closes, guaranteeing a freshly-created task appears (FR11).
 */

import React, { useState } from 'react';
import { Box, Stack, Typography, Button, Alert } from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { CustomTaskModal } from '@/components/organizer/Tasks/CustomTaskModal';
import { AttentionCard } from './AttentionCard';
import type { CockpitCard, CardTarget } from './cockpitCards';

interface AttentionListProps {
  cards: CockpitCard[];
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onNavigate: (target: CardTarget) => void;
  eventCode: string;
  organizerUsername: string;
}

export const AttentionList: React.FC<AttentionListProps> = ({
  cards,
  isLoading,
  isError,
  onRetry,
  onNavigate,
  eventCode,
  organizerUsername,
}) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);

  const handleModalClose = () => {
    setModalOpen(false);
    // Refresh the Cockpit task list (CustomTaskModal invalidates only ['tasks']).
    void queryClient.invalidateQueries({ queryKey: ['eventTasks', eventCode] });
    void queryClient.invalidateQueries({ queryKey: ['myTasks'] });
  };

  return (
    <Box data-testid="cockpit-attention-region">
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1.5}>
        <Typography variant="h6">
          {t('eventPage.cockpit.attention.title', 'Needs your attention')}
        </Typography>
        <Button
          size="small"
          startIcon={<AddIcon />}
          onClick={() => setModalOpen(true)}
          data-testid="cockpit-add-task"
        >
          {t('eventPage.cockpit.attention.addTask', 'Add task')}
        </Button>
      </Stack>

      {isError && (
        <Alert
          severity="error"
          action={
            <Button
              color="inherit"
              size="small"
              onClick={onRetry}
              data-testid="cockpit-attention-retry"
            >
              {t('eventPage.cockpit.attention.retry', 'Retry')}
            </Button>
          }
          sx={{ mb: 2 }}
        >
          {t('eventPage.cockpit.attention.loadError', "Couldn't load your tasks.")}
        </Alert>
      )}

      {!isError && !isLoading && cards.length === 0 && (
        <Alert severity="success" data-testid="cockpit-attention-empty">
          {t(
            'eventPage.cockpit.attention.allCaught',
            "You're all caught up — nothing needs your attention right now."
          )}
        </Alert>
      )}

      {/* Responsive auto-fill grid (prototype `.attn`, UX-DR16): cards fill
          horizontally and reflow to fewer columns as the width shrinks, rather
          than stacking one per row. Inline grid template keeps the column rule
          deterministic (and testable) across themes. */}
      <Box
        data-testid="cockpit-attention-grid"
        style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))' }}
        sx={{ gap: 1.5 }}
      >
        {cards.map((card) => (
          <AttentionCard key={card.id} card={card} onNavigate={onNavigate} />
        ))}
      </Box>

      {modalOpen && (
        <CustomTaskModal
          open={modalOpen}
          onClose={handleModalClose}
          eventId={eventCode}
          organizerUsername={organizerUsername}
        />
      )}
    </Box>
  );
};
