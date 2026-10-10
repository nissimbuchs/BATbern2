/**
 * EventWorkflowStateSection
 *
 * Lets an organizer change the event workflow state by hand, optionally overriding the state
 * machine's validation with an audited reason (POST /events/{code}/workflow/transition).
 *
 * The same controls lived in the EventForm edit modal, which PR #788 (lifecycle-aware event page
 * redesign) left without an entry point on the event page. They now sit in Details → Settings.
 */

import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Divider,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { AccountTree as WorkflowIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { workflowService, type EventWorkflowState } from '@/services/workflowService';

const WORKFLOW_STATES: EventWorkflowState[] = [
  'CREATED',
  'TOPIC_SELECTION',
  'SPEAKER_IDENTIFICATION',
  'SLOT_ASSIGNMENT',
  'AGENDA_PUBLISHED',
  'EVENT_LIVE',
  'EVENT_COMPLETED',
  'ARCHIVED',
];

interface EventWorkflowStateSectionProps {
  eventCode: string;
  workflowState?: string | null;
}

const serverMessage = (err: unknown): string | undefined => {
  const data = (err as { response?: { data?: { message?: string } } })?.response?.data;
  return data?.message;
};

export const EventWorkflowStateSection: React.FC<EventWorkflowStateSectionProps> = ({
  eventCode,
  workflowState,
}) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();

  const [selectedState, setSelectedState] = useState<string>(workflowState ?? '');
  const [overrideValidation, setOverrideValidation] = useState(false);
  const [overrideReason, setOverrideReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [isPending, setIsPending] = useState(false);

  // Follow the event when it changes underneath us (refetch, automatic transitions).
  useEffect(() => {
    setSelectedState(workflowState ?? '');
  }, [workflowState]);

  const unchanged = selectedState === (workflowState ?? '');
  const reasonMissing = overrideValidation && overrideReason.trim() === '';
  const applyDisabled = isPending || !selectedState || unchanged || reasonMissing;

  const handleApply = async () => {
    setError(null);
    setSuccess(false);
    setIsPending(true);
    try {
      await workflowService.transitionWorkflowState(
        eventCode,
        selectedState as EventWorkflowState,
        overrideValidation,
        overrideValidation ? overrideReason.trim() : undefined
      );
      await queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      setOverrideValidation(false);
      setOverrideReason('');
      setSuccess(true);
    } catch (err) {
      setError(
        serverMessage(err) ??
          t('eventPage.settings.workflowState.error', 'The event state could not be changed.')
      );
    } finally {
      setIsPending(false);
    }
  };

  return (
    <Paper sx={{ p: 3 }} data-testid="workflow-state-section">
      <Stack direction="row" spacing={1} alignItems="center" mb={2}>
        <WorkflowIcon color="action" />
        <Typography variant="h6">
          {t('eventPage.settings.workflowState.title', 'Event state')}
        </Typography>
      </Stack>
      <Divider sx={{ mb: 2 }} />
      <Stack spacing={2}>
        <Typography variant="body2" color="text.secondary">
          {t(
            'eventPage.settings.workflowState.description',
            'Change the event state by hand. Without override, the normal transition rules apply.'
          )}
        </Typography>

        <FormControl fullWidth sx={{ maxWidth: 400 }}>
          <InputLabel id="workflow-state-select-label">{t('common:labels.status')}</InputLabel>
          <Select
            labelId="workflow-state-select-label"
            label={t('common:labels.status')}
            value={selectedState}
            onChange={(e) => setSelectedState(e.target.value)}
            disabled={isPending}
            data-testid="workflow-state-select"
          >
            {WORKFLOW_STATES.map((state) => (
              <MenuItem key={state} value={state} data-testid={`workflow-state-option-${state}`}>
                {t(`workflow.states.${state.toLowerCase()}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Box
          sx={{
            p: 2,
            bgcolor: 'warning.light',
            borderRadius: 1,
            border: '1px solid',
            borderColor: 'warning.main',
          }}
        >
          <FormControlLabel
            control={
              <Checkbox
                checked={overrideValidation}
                onChange={(e) => setOverrideValidation(e.target.checked)}
                disabled={isPending}
                inputProps={
                  {
                    'data-testid': 'override-workflow-validation-checkbox',
                  } as React.InputHTMLAttributes<HTMLInputElement>
                }
              />
            }
            label={t('form.overrideValidation')}
          />
          {overrideValidation && (
            <TextField
              fullWidth
              multiline
              rows={2}
              required
              label={t('form.overrideReason')}
              value={overrideReason}
              onChange={(e) => setOverrideReason(e.target.value)}
              placeholder={t('form.overrideReasonPlaceholder')}
              helperText={t('form.overrideReasonHelp')}
              disabled={isPending}
              sx={{ mt: 1 }}
              inputProps={{ 'data-testid': 'override-reason-input', maxLength: 500 }}
            />
          )}
        </Box>

        <Box>
          <Button
            variant="contained"
            onClick={handleApply}
            disabled={applyDisabled}
            data-testid="apply-workflow-state-button"
          >
            {t('eventPage.settings.workflowState.apply', 'Change state')}
          </Button>
        </Box>

        {error && (
          <Alert severity="error" onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        {success && (
          <Alert severity="success" onClose={() => setSuccess(false)}>
            {t('eventPage.settings.workflowState.success', 'Event state changed.')}
          </Alert>
        )}
      </Stack>
    </Paper>
  );
};

export default EventWorkflowStateSection;
