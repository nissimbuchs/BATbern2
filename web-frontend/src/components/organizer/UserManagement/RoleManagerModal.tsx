import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  FormGroup,
  FormControlLabel,
  Checkbox,
  Box,
  Typography,
  IconButton,
  Alert,
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useUpdateUserRoles } from '../../../hooks/useUserManagement';
import type { User, Role } from '../../../types/user.types';

interface RoleManagerModalProps {
  user: User | null;
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

const RoleManagerModal: React.FC<RoleManagerModalProps> = ({ user, open, onClose, onSuccess }) => {
  const { t } = useTranslation('userManagement');
  const updateRolesMutation = useUpdateUserRoles();

  const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
  const [error, setError] = useState<string>('');

  // Initialize selected roles when modal opens or user changes
  useEffect(() => {
    if (open && user) {
      setSelectedRoles(user.roles as Role[]);
      setError('');
    }
  }, [open, user]);

  const handleRoleToggle = (role: Role) => {
    setSelectedRoles((prev) => {
      const isSelected = prev.includes(role);
      if (isSelected) {
        return prev.filter((r) => r !== role);
      } else {
        return [...prev, role];
      }
    });

    // Clear error when user makes changes
    if (error) {
      setError('');
    }
  };

  const handleSave = async () => {
    // Validation: At least one role must be selected
    if (selectedRoles.length === 0) {
      setError(t('error.minOneRole') || 'At least one role must be selected');
      return;
    }

    if (!user) return;

    try {
      await updateRolesMutation.mutateAsync({
        id: user.id,
        roles: selectedRoles,
      });

      onSuccess?.();
      onClose();
    } catch (error) {
      console.error('Failed to update user roles:', error);
      // Error is handled by React Query mutation error state
    }
  };

  const roleOptions: { value: Role; label: string }[] = [
    { value: 'ORGANIZER', label: t('common:role.organizer') },
    { value: 'SPEAKER', label: t('common:role.speaker') },
    { value: 'PARTNER', label: t('common:role.partner') },
    { value: 'ATTENDEE', label: t('common:role.attendee') },
  ];

  if (!user) return null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      aria-labelledby="role-manager-dialog-title"
    >
      <DialogTitle id="role-manager-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">{t('modal.editRoles.title')}</Typography>
          <IconButton edge="end" color="inherit" onClick={onClose} aria-label="close">
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        <Box sx={{ mb: 2 }}>
          <Typography variant="body1" gutterBottom>
            {t('modal.editRoles.user')}:{' '}
            <strong>
              {user.firstName} {user.lastName}
            </strong>
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {user.email}
          </Typography>
        </Box>

        <Typography variant="subtitle2" gutterBottom>
          {t('modal.editRoles.selectRoles')}:
        </Typography>

        <FormGroup data-testid="role-manager-checkboxes">
          {roleOptions.map((role) => (
            <FormControlLabel
              key={role.value}
              control={
                <Checkbox
                  data-testid={`role-manager-role-${role.value}`}
                  value={role.value}
                  checked={selectedRoles.includes(role.value)}
                  onChange={() => handleRoleToggle(role.value)}
                />
              }
              label={role.label}
            />
          ))}
        </FormGroup>

        {/* Validation Error */}
        {error && (
          <Alert severity="error" sx={{ mt: 2 }} data-testid="role-manager-error">
            {error}
          </Alert>
        )}

        {/* Mutation Error */}
        {updateRolesMutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {t('error.updateFailed')}
          </Alert>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} variant="outlined" data-testid="role-manager-cancel">
          {t('common:actions.cancel')}
        </Button>
        <Button
          onClick={handleSave}
          variant="contained"
          color="primary"
          disabled={updateRolesMutation.isPending}
          data-testid="role-manager-save"
        >
          {updateRolesMutation.isPending ? t('common:actions.saving') : t('common:actions.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default RoleManagerModal;
