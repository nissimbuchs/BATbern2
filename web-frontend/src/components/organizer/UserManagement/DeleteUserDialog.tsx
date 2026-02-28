import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  IconButton,
  Alert,
} from '@mui/material';
import { Close as CloseIcon, Warning as WarningIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useDeleteUser } from '../../../hooks/useUserManagement';
import type { User } from '../../../types/user.types';

interface DeleteUserDialogProps {
  user: User | null;
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

const DeleteUserDialog: React.FC<DeleteUserDialogProps> = ({ user, open, onClose, onSuccess }) => {
  const { t } = useTranslation('userManagement');
  const deleteUserMutation = useDeleteUser();

  const handleDelete = async () => {
    if (!user) return;

    try {
      await deleteUserMutation.mutateAsync(user.id);
      onSuccess?.();
      onClose();
    } catch (error) {
      console.error('Failed to delete user:', error);
      // Error is handled by React Query mutation error state
    }
  };

  if (!user) return null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      aria-labelledby="delete-user-dialog-title"
    >
      <DialogTitle id="delete-user-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Box display="flex" alignItems="center" gap={1}>
            <WarningIcon color="error" />
            <Typography variant="h6">{t('modal.deleteConfirm.title')}</Typography>
          </Box>
          <IconButton edge="end" color="inherit" onClick={onClose} aria-label="close">
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        {/* User Information */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="body1" gutterBottom>
            {t('modal.deleteConfirm.message')}
          </Typography>
          <Typography variant="body1" sx={{ mt: 2 }}>
            <strong>
              {user.firstName} {user.lastName}
            </strong>
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {user.email}
          </Typography>
        </Box>

        {/* GDPR Warning */}
        <Alert severity="error" sx={{ mb: 2 }}>
          <Typography variant="body2" fontWeight="bold">
            {t('modal.deleteConfirm.gdprWarning')}
          </Typography>
        </Alert>

        {/* Cascade Warning */}
        <Alert severity="warning">
          <Typography variant="body2">{t('modal.deleteConfirm.cascadeWarning')}</Typography>
        </Alert>

        {/* Mutation Error */}
        {deleteUserMutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {t('error.deleteFailed')}
          </Alert>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} variant="outlined" data-testid="delete-user-cancel">
          {t('common:actions.cancel')}
        </Button>
        <Button
          onClick={handleDelete}
          variant="contained"
          color="error"
          disabled={deleteUserMutation.isPending}
          data-testid="delete-user-confirm"
        >
          {deleteUserMutation.isPending ? t('common:actions.deleting') : t('common:actions.delete')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DeleteUserDialog;
