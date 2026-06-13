/**
 * CockpitTab (Epic 14 Phase B — Story 14.B.1; FR7).
 *
 * The task-driven Cockpit landing — three stacked regions:
 *   1. lifecycle spine ("where is this event")
 *   2. "Needs your attention" (sorted, only-open cards + ＋Add task)
 *   3. "At a glance" metric tiles (clickable deep-links)
 *
 * No event-identity block here — identity lives in Details; orientation is the
 * persistent header title + the spine (FR7). Replaces the interim Overview mount.
 */

import React from 'react';
import { Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Event, EventDetailUI } from '@/types/event.types';
import { useAuth } from '@/hooks/useAuth';
import { WORKFLOW_RELEVANCE, type WorkflowStateType } from '@/utils/workflow/workflowState';
import { LifecycleSpine } from './LifecycleSpine';
import { AttentionList } from './AttentionList';
import { MetricTiles } from './MetricTiles';
import { useCockpitCards } from './useCockpitCards';
import type { CardTarget } from './cockpitCards';

interface CockpitTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
  /** Deep-link handler from EventPage (tab/sub-view switch or route navigation). */
  onNavigate: (target: CardTarget) => void;
}

export const CockpitTab: React.FC<CockpitTabProps> = ({ event, eventCode, onNavigate }) => {
  const { t } = useTranslation('events');
  const { user } = useAuth();
  const workflowState = (event as { workflowState?: string }).workflowState ?? '';
  const { cards, isLoading, isError, refetch } = useCockpitCards(event, eventCode);

  const emphasis = WORKFLOW_RELEVANCE[workflowState as WorkflowStateType]?.cockpitEmphasis;

  return (
    <Stack spacing={4} data-testid="cockpit-tab">
      {/* Region 1 — lifecycle spine */}
      <div>
        <LifecycleSpine workflowState={workflowState} />
        {emphasis && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {t(`eventPage.cockpit.emphasis.${emphasis}`, '')}
          </Typography>
        )}
      </div>

      {/* Region 2 — needs your attention */}
      <AttentionList
        cards={cards}
        isLoading={isLoading}
        isError={isError}
        onRetry={refetch}
        onNavigate={onNavigate}
        eventCode={eventCode}
        organizerUsername={user?.username ?? ''}
      />

      {/* Region 3 — at a glance */}
      <div>
        <Typography variant="h6" gutterBottom>
          {t('eventPage.cockpit.atAGlance.title', 'At a glance')}
        </Typography>
        <MetricTiles event={event} onNavigate={onNavigate} />
      </div>
    </Stack>
  );
};

export default CockpitTab;
