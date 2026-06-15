/**
 * LifecycleSpine (Epic 14 Phase B — Story 14.B.1; FR8, UX-DR2).
 *
 * Horizontal 8-state stepper: completed = green check, current = highlighted
 * with "Step N of 8". Reuses the existing workflow helpers (this is NOT
 * `WorkflowProgressBar`, which is a %-bar — the spine is a discrete stepper).
 * The `compact` seam (a "Step N/8" bar) is wired for Phase G mobile.
 */

import React from 'react';
import { Box, Stepper, Step, StepLabel, Typography, LinearProgress } from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  WORKFLOW_STATE_ORDER,
  getWorkflowStepNumber,
  getWorkflowProgress,
  getWorkflowStateLabel,
} from '@/utils/workflow/workflowState';

interface LifecycleSpineProps {
  workflowState: string;
  /** Mobile (Phase G): collapse to a "Step N/8" bar instead of the full stepper. */
  compact?: boolean;
}

export const LifecycleSpine: React.FC<LifecycleSpineProps> = ({
  workflowState,
  compact = false,
}) => {
  const { t } = useTranslation('events');
  const total = WORKFLOW_STATE_ORDER.length;
  const stepNumber = getWorkflowStepNumber(workflowState); // 1..8, 0 if invalid
  const activeIndex = stepNumber > 0 ? stepNumber - 1 : 0;

  const stepOfLabel = t('eventPage.cockpit.spine.stepOf', 'Step {{current}} of {{total}}', {
    current: stepNumber || 1,
    total,
  });

  if (compact) {
    return (
      <Box
        data-testid="cockpit-lifecycle-spine"
        data-workflow-state={workflowState}
        aria-label={stepOfLabel}
      >
        <Typography variant="subtitle2" gutterBottom>
          {stepOfLabel}
        </Typography>
        <LinearProgress
          variant="determinate"
          value={getWorkflowProgress(workflowState)}
          sx={{ height: 8, borderRadius: 1 }}
        />
      </Box>
    );
  }

  return (
    <Box data-testid="cockpit-lifecycle-spine" data-workflow-state={workflowState}>
      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
        {stepOfLabel}
      </Typography>
      <Stepper activeStep={activeIndex} alternativeLabel aria-label={stepOfLabel}>
        {WORKFLOW_STATE_ORDER.map((state, i) => (
          <Step key={state} completed={i < activeIndex}>
            <StepLabel aria-current={i === activeIndex ? 'step' : undefined}>
              {getWorkflowStateLabel(state, t)}
            </StepLabel>
          </Step>
        ))}
      </Stepper>
    </Box>
  );
};
