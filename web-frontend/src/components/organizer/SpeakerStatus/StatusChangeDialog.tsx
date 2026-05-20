/**
 * Status Change Dialog Component (Story 5.4 + Story 11.D.4 AC5).
 *
 * Modal dialog for confirming speaker status changes.
 * Features:
 * - Optional reason field (max 2000 characters), REQUIRED when `newStatus === 'DECLINED'`.
 * - Confirmation and cancel buttons
 * - i18n support (10 locales)
 * - Validation for reason length
 *
 * Story 11.D.4 — When the kanban dispatcher classifies a drop as `legal-decline`, this
 * dialog opens with `newStatus='DECLINED'` and the confirm button stays disabled until
 * `reason.trim().length > 0`. Otherwise (drawer "Override state" popover with a
 * non-DECLINED target, or legacy callers), reason remains optional.
 */

import React, { useEffect, useState } from 'react';
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
import type { SpeakerWorkflowState } from '@/types/speakerPool.types';

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

  // Review patch — clear reason + error when the dialog closes externally (e.g. the
  // drawer that hosts this always-mounted dialog is closed via Esc/onClose without
  // confirming or cancelling). Without this, opening the dialog for a different
  // speaker after such a close shows the previous speaker's reason text.
  useEffect(() => {
    if (!open) {
      setReason('');
      setError('');
    }
  }, [open]);

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

  // Story 11.D.4 AC5 — required-reason guard for DECLINED.
  // 2026-05-20 — also required for READY → ACCEPTED (organizer-on-behalf path).
  const isDeclining = newStatus === 'DECLINED';
  const isOnBehalfAccept = currentStatus === 'READY' && newStatus === 'ACCEPTED';
  const reasonRequired = isDeclining || isOnBehalfAccept;
  const reasonMissing = reasonRequired && reason.trim().length === 0;
  const confirmDisabled = !!error || reasonMissing;

  const dialogTitle = isDeclining
    ? t('organizer:kanbanDrag.declineDialog.title', { speakerName })
    : isOnBehalfAccept
      ? t('organizer:kanbanDrag.acceptOnBehalfDialog.title', {
          speakerName,
          defaultValue: 'Accept {{speakerName}} on behalf',
        })
      : t('organizer:speakerStatus.changeStatus');

  return (
    <Dialog
      open={open}
      onClose={handleCancel}
      maxWidth="sm"
      fullWidth
      data-testid="status-change-dialog"
    >
      <DialogTitle>{dialogTitle}</DialogTitle>
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
          required={reasonRequired}
          label={t('organizer:speakerStatus.changeReason')}
          value={reason}
          onChange={handleReasonChange}
          error={!!error}
          helperText={
            error ||
            (isDeclining
              ? t('organizer:kanbanDrag.declineDialog.reasonHint')
              : isOnBehalfAccept
                ? t('organizer:kanbanDrag.acceptOnBehalfDialog.reasonHint', {
                    defaultValue:
                      'Record why you are accepting on behalf (e.g. "confirmed by email 2026-05-18"). Lands in the audit trail.',
                  })
                : t('organizer:speakerStatus.reasonHelperText', { max: MAX_REASON_LENGTH }))
          }
          placeholder={t('organizer:speakerStatus.reasonPlaceholder')}
          data-testid="status-change-reason"
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel} color="secondary" data-testid="status-change-cancel">
          {t('common:actions.cancel')}
        </Button>
        <Button
          onClick={handleConfirm}
          variant="contained"
          color="primary"
          disabled={confirmDisabled}
          data-testid="status-change-confirm"
        >
          {t('organizer:speakerStatus.confirmChange')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default StatusChangeDialog;
