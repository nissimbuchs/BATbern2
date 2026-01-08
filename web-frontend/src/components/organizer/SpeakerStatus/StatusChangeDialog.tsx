/**
 * Status Change Dialog Component (Story 5.4)
 *
 * Modal dialog for confirming speaker status changes
 * Features:
 * - Optional reason field (max 2000 characters)
 * - Confirmation and cancel buttons
 * - i18n support (German/English)
 * - Validation for reason length
 */

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { components } from '@/types/generated/speakers-api.types';

type SpeakerWorkflowState = components['schemas']['SpeakerWorkflowState'];

export interface StatusChangeDialogProps {
  open: boolean;
  speakerName: string;
  currentStatus: SpeakerWorkflowState;
  newStatus: SpeakerWorkflowState;
  onConfirm: (reason?: string) => void;
  onCancel: () => void;
}

const MAX_REASON_LENGTH = 2000;

export const StatusChangeDialog: React.FC<StatusChangeDialogProps> = ({
  open,
  speakerName,
  currentStatus,
  newStatus,
  onConfirm,
  onCancel,
}) => {
  const { t } = useTranslation(['organizer', 'common']);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  const handleReasonChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setReason(value);

    if (value.length > MAX_REASON_LENGTH) {
      setError(
        t('organizer:speakerStatus.reasonTooLong', {
          current: value.length,
          max: MAX_REASON_LENGTH,
        })
      );
    } else {
      setError('');
    }
  };

  const handleConfirm = () => {
    if (error) return;
    onConfirm(reason.trim() || undefined);
    setReason('');
    setError('');
  };

  const handleCancel = () => {
    setReason('');
    setError('');
    onCancel();
  };

  return (
    <Dialog
      open={open}
      onClose={handleCancel}
      maxWidth="sm"
      fullWidth
      data-testid="status-change-dialog"
    >
      <DialogTitle>{t('organizer:speakerStatus.changeStatus')}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 2 }}>
          {t('organizer:speakerStatus.confirmChangeMessage', {
            speaker: speakerName,
            from: t(`organizer:speakerStatus.${currentStatus}`),
            to: t(`organizer:speakerStatus.${newStatus}`),
          })}
        </Typography>

        <TextField
          fullWidth
          multiline
          rows={4}
          label={t('organizer:speakerStatus.changeReason')}
          value={reason}
          onChange={handleReasonChange}
          error={!!error}
          helperText={
            error || t('organizer:speakerStatus.reasonHelperText', { max: MAX_REASON_LENGTH })
          }
          placeholder={t('organizer:speakerStatus.reasonPlaceholder')}
          data-testid="status-change-reason"
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel} color="secondary" data-testid="status-change-cancel">
          {t('organizer:speakerStatus.cancelChange')}
        </Button>
        <Button
          onClick={handleConfirm}
          variant="contained"
          color="primary"
          disabled={!!error}
          data-testid="status-change-confirm"
        >
          {t('organizer:speakerStatus.confirmChange')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default StatusChangeDialog;
