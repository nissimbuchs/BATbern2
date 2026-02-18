/**
 * WorkflowProgressBarWithQuery Component (Story 5.1a - Task 9)
 *
 * AC: 15-17 (Frontend Integration)
 *
 * Displays workflow progress with React Query integration:
 * - Fetches workflow status from GET /api/v1/events/{code}/workflow/status
 * - Real-time updates via refetchInterval
 * - Loading and error states
 * - Validation blocker messages
 * - Current workflow state display
 */

import React from 'react';
import { Box, LinearProgress, Typography, Alert, Button, Stack, Chip } from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { Warning as WarningIcon, Refresh as RefreshIcon } from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { workflowService } from '@/services/workflowService';
import {
  getWorkflowProgress,
  getWorkflowStateI18nKey,
  getProgressColor,
} from '@/utils/workflow/workflowState';

interface WorkflowProgressBarWithQueryProps {
  eventCode: string;
  refetchInterval?: number; // Optional refetch interval in ms (default: 5000ms)
  compact?: boolean;
}

export const WorkflowProgressBarWithQuery: React.FC<WorkflowProgressBarWithQueryProps> = ({
  eventCode,
  refetchInterval = 5000,
  compact = false,
}) => {
  const { t } = useTranslation('events');

  // Fetch workflow status using React Query
  const {
    data: workflowStatus,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ['workflowStatus', eventCode],
    queryFn: () => workflowService.getWorkflowStatus(eventCode),
    refetchInterval,
    staleTime: 0, // Always refetch
  });

  // Loading state
  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, p: 2 }}>
        <BATbernLoader size={24} />
        <Typography variant="body2" color="text.secondary">
          {t('workflow.loading')}
        </Typography>
      </Box>
    );
  }

  // Error state
  if (isError) {
    return (
      <Alert
        severity="error"
        action={
          <Button size="small" startIcon={<RefreshIcon />} onClick={() => refetch()}>
            {t('common.retry')}
          </Button>
        }
      >
        {t('workflow.error')}
      </Alert>
    );
  }

  // No data state
  if (!workflowStatus) {
    return <Alert severity="info">{t('workflow.noData', 'No workflow status available')}</Alert>;
  }

  const { currentState, validationMessages, blockedTransitions } = workflowStatus;

  // Calculate progress
  const progress = getWorkflowProgress(currentState);
  // Get translated state label
  const stateLabel = t(getWorkflowStateI18nKey(currentState), currentState);

  // Determine progress bar color
  const progressColor = getProgressColor(progress);

  return (
    <Box sx={{ width: '100%' }}>
      <Stack spacing={2}>
        {/* Current State Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="body2" color="text.secondary" fontWeight="medium">
            {t('workflow.currentState', 'Current State')}: <strong>{stateLabel}</strong>
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {progress}%
          </Typography>
        </Box>

        {/* Progress Bar */}
        <LinearProgress
          variant="determinate"
          value={progress}
          color={progressColor}
          sx={{ height: 8, borderRadius: 1 }}
        />

        {/* Validation Messages (Blockers) */}
        {validationMessages && validationMessages.length > 0 && (
          <Alert
            severity="warning"
            icon={<WarningIcon />}
            sx={{ '& .MuiAlert-message': { width: '100%' } }}
          >
            <Typography variant="body2" fontWeight="medium" gutterBottom>
              {t('workflow.blockers', { count: validationMessages.length })}:
            </Typography>
            <Stack spacing={0.5}>
              {validationMessages.map((message, index) => (
                <Typography key={index} variant="caption" component="div">
                  • {message}
                </Typography>
              ))}
            </Stack>
          </Alert>
        )}

        {/* Blocked Transitions Info */}
        {!compact && blockedTransitions && blockedTransitions.length > 0 && (
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Typography variant="caption" color="text.secondary">
              {t('workflow.blockedTransitions', 'Blocked:')}
            </Typography>
            {blockedTransitions.map((state, index) => (
              <Chip
                key={index}
                label={t(getWorkflowStateI18nKey(state), state)}
                size="small"
                variant="outlined"
                color="warning"
              />
            ))}
          </Box>
        )}
      </Stack>
    </Box>
  );
};

export default WorkflowProgressBarWithQuery;
