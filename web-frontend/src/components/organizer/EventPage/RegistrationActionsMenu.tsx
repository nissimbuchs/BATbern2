/**
 * Registration Actions Menu Component
 *
 * Provides action buttons for managing event registrations.
 * - Cancel: Sets registration status to CANCELLED
 * - Delete: Permanently removes the registration (with confirmation dialog)
 */

import React, { useState } from 'react';
import {
  Box,
  IconButton,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  Snackbar,
  Alert,
} from '@mui/material';
import {
  Cancel as CancelIcon,
  Delete as DeleteIcon,
  Email as EmailIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  cancelRegistration,
  deleteRegistration,
  resendConfirmationEmail,
} from '../../../services/api/eventRegistrationService';
import type { EventParticipant } from '../../../types/eventParticipant.types';

interface RegistrationActionsMenuProps {
  participant: EventParticipant;
}

const RegistrationActionsMenu: React.FC<RegistrationActionsMenuProps> = ({ participant }) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [resendSuccess, setResendSuccess] = useState(false);
  const [resendError, setResendError] = useState<string | null>(null);

  // Cancel registration mutation
  const cancelMutation = useMutation({
    mutationFn: () => cancelRegistration(participant.eventCode, participant.registrationCode),
    onSuccess: () => {
      // Invalidate queries to refresh the list (use hyphenated key to match hook)
      queryClient.invalidateQueries({ queryKey: ['event-registrations', participant.eventCode] });
    },
  });

  // Resend confirmation email mutation
  const resendMutation = useMutation({
    mutationFn: () => resendConfirmationEmail(participant.eventCode, participant.registrationCode),
    onSuccess: () => setResendSuccess(true),
    onError: (err: Error) =>
      setResendError(err.message || t('eventPage.participantTable.actions.resendError')),
  });

  // Delete registration mutation
  const deleteMutation = useMutation({
    mutationFn: () => deleteRegistration(participant.eventCode, participant.registrationCode),
    onSuccess: () => {
      // Invalidate queries to refresh the list (use hyphenated key to match hook)
      queryClient.invalidateQueries({ queryKey: ['event-registrations', participant.eventCode] });
      setDeleteDialogOpen(false);
    },
  });

  const handleCancelClick = (e: React.MouseEvent) => {
    e.stopPropagation(); // Prevent row click event
    cancelMutation.mutate();
  };

  const handleDeleteClick = (e: React.MouseEvent) => {
    e.stopPropagation(); // Prevent row click event
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    deleteMutation.mutate();
  };

  const handleDeleteCancel = () => {
    setDeleteDialogOpen(false);
  };

  // Don't show cancel button if already cancelled
  const showCancelButton = participant.status !== 'CANCELLED';
  // Only show resend for pending (REGISTERED) registrations
  const showResendButton = participant.status === 'REGISTERED';

  return (
    <>
      <Box sx={{ display: 'flex', gap: 0.5 }}>
        {showResendButton && (
          <Tooltip title={t('eventPage.participantTable.actions.resendConfirmation')}>
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation();
                resendMutation.mutate();
              }}
              disabled={resendMutation.isPending}
              sx={{ color: 'info.main' }}
            >
              <EmailIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        {showCancelButton && (
          <Tooltip title={t('eventPage.participantTable.actions.cancel')}>
            <IconButton
              size="small"
              onClick={handleCancelClick}
              disabled={cancelMutation.isPending}
              sx={{ color: 'warning.main' }}
            >
              <CancelIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}

        <Tooltip title={t('eventPage.participantTable.actions.delete')}>
          <IconButton
            size="small"
            onClick={handleDeleteClick}
            disabled={deleteMutation.isPending}
            sx={{ color: 'error.main' }}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Resend success/error feedback */}
      <Snackbar
        open={resendSuccess}
        autoHideDuration={4000}
        onClose={() => setResendSuccess(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={() => setResendSuccess(false)}>
          {t('eventPage.participantTable.actions.resendSuccess')}
        </Alert>
      </Snackbar>
      <Snackbar
        open={!!resendError}
        autoHideDuration={6000}
        onClose={() => setResendError(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="error" onClose={() => setResendError(null)}>
          {resendError}
        </Alert>
      </Snackbar>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteDialogOpen}
        onClose={handleDeleteCancel}
        aria-labelledby="delete-dialog-title"
        aria-describedby="delete-dialog-description"
      >
        <DialogTitle id="delete-dialog-title">
          {t('eventPage.participantTable.deleteDialog.title')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="delete-dialog-description">
            {t('eventPage.participantTable.deleteDialog.message', {
              name: `${participant.firstName} ${participant.lastName}`,
            })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDeleteCancel} disabled={deleteMutation.isPending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleteMutation.isPending}
            autoFocus
          >
            {t('common:actions.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default RegistrationActionsMenu;
