/**
 * WorkflowProgressBar Component
 *
 * Story 2.5.3 - Task 10b (GREEN Phase)
 * AC: 5 (Workflow Progress Display)
 * Wireframe: docs/wireframes/story-1.16-workflow-visualization.md
 *
 * Displays workflow progress with:
 * - Progress bar showing completion percentage (0-100%)
 * - Current step indicator (Step X/16: Step Name)
 * - Clickable progress bar navigation to workflow visualization
 * - Warning indicators for blockers (⚠️)
 * - [View Workflow Details] button
 * - Color-coded progress (warning <30%, primary 30-70%, success >70%)
 * - Full accessibility (keyboard navigation, ARIA labels)
 */

import React from 'react';
import {
  Box,
  LinearProgress,
  Typography,
  Button,
  Tooltip,
  Stack,
  Chip,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import {
  ArrowForward as ArrowForwardIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { WorkflowState } from '@/types/event.types';

interface WorkflowProgressBarProps {
  workflow: WorkflowState;
  eventCode: string;
  compact?: boolean;
}

// Get progress bar color based on completion percentage
const getProgressColor = (progress: number): 'warning' | 'primary' | 'success' => {
  if (progress < 30) return 'warning';
  if (progress < 70) return 'primary';
  return 'success';
};

export const WorkflowProgressBar: React.FC<WorkflowProgressBarProps> = ({
  workflow,
  eventCode,
  compact = false,
}) => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  // Handle undefined/null workflow
  const safeWorkflow: WorkflowState = workflow || {
    currentStep: 1,
    totalSteps: 16,
    completionPercentage: 0,
    steps: [],
    blockers: [],
  };

  const { currentStep, totalSteps, completionPercentage, steps, blockers } = safeWorkflow;

  // Find current step details
  const currentStepDetail = steps.find((s) => s.stepNumber === currentStep);
  const stepName = currentStepDetail?.name || '';

  // Check for critical blockers
  const hasCriticalBlockers = blockers.some((b) => b.severity === 'critical');

  // Navigation handler
  const handleNavigateToWorkflow = () => {
    navigate(`/organizer/events/${eventCode}/workflow`);
  };

  // Keyboard navigation handler
  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      handleNavigateToWorkflow();
    }
  };

  // Get progress bar color
  const progressColor = getProgressColor(completionPercentage);

  // Build blocker tooltip content
  const blockerTooltipContent = (
    <Stack spacing={1}>
      {blockers.map((blocker, index) => (
        <Box key={index}>
          <Typography variant="caption" fontWeight="bold">
            {blocker.severity === 'critical' ? '🔴' : '⚠️'} Step {blocker.stepNumber}
          </Typography>
          <Typography variant="caption" display="block">
            {blocker.message}
          </Typography>
        </Box>
      ))}
    </Stack>
  );

  return (
    <Box
      sx={{
        width: '100%',
        display: 'flex',
        flexDirection: compact || isMobile ? 'column' : 'row',
        alignItems: compact || isMobile ? 'stretch' : 'center',
        gap: 2,
      }}
      className={isMobile ? 'mobile stacked' : ''}
    >
      {/* Progress Bar Section */}
      <Box sx={{ flex: 1 }}>
        <Stack spacing={1}>
          {/* Current Step Indicator */}
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 1,
            }}
          >
            <Typography variant="body2" color="text.secondary">
              {currentStep === totalSteps
                ? t('workflow.completed')
                : t('workflow.stepIndicator', { current: currentStep, total: totalSteps })}
              {stepName && `: ${stepName}`}
            </Typography>

            {/* Blocker Warning Indicator */}
            {blockers.length > 0 && (
              <Tooltip title={blockerTooltipContent} arrow>
                <Chip
                  icon={hasCriticalBlockers ? <ErrorIcon /> : <WarningIcon />}
                  label={
                    <>
                      {hasCriticalBlockers && '🔴 '}⚠️{' '}
                      {t('workflow.blockers', { count: blockers.length })}
                    </>
                  }
                  size="small"
                  color={hasCriticalBlockers ? 'error' : 'warning'}
                  variant="outlined"
                />
              </Tooltip>
            )}
          </Box>

          {/* Clickable Progress Bar */}
          <Box
            role="progressbar"
            aria-valuenow={completionPercentage}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={t('workflow.progressBarLabel', {
              percentage: completionPercentage,
            })}
            tabIndex={0}
            onClick={handleNavigateToWorkflow}
            onKeyDown={handleKeyDown}
            sx={{
              cursor: 'pointer',
              borderRadius: 1,
              overflow: 'hidden',
              '&:hover': {
                opacity: 0.9,
                outline: `2px solid ${theme.palette.primary.main}`,
                outlineOffset: '2px',
              },
              '&:focus': {
                outline: `2px solid ${theme.palette.primary.main}`,
                outlineOffset: '2px',
              },
            }}
          >
            <LinearProgress
              variant="determinate"
              value={completionPercentage}
              color={progressColor}
              sx={{
                height: 8,
                borderRadius: 1,
              }}
            />
          </Box>

          {/* Percentage Display */}
          <Typography variant="caption" color="text.secondary" textAlign="right">
            {completionPercentage}%
          </Typography>
        </Stack>
      </Box>

      {/* View Details Button (Hidden on mobile - progress bar is clickable) */}
      {!isMobile && !compact && (
        <Button
          variant="outlined"
          size="small"
          endIcon={<ArrowForwardIcon />}
          onClick={handleNavigateToWorkflow}
          sx={{ whiteSpace: 'nowrap' }}
        >
          {t('workflow.viewDetails')}
        </Button>
      )}
    </Box>
  );
};

export default WorkflowProgressBar;
