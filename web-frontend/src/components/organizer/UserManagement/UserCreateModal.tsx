import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
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
import { useCreateUser } from '../../../hooks/useUserManagement';
import type { Role } from '../../../types/user.types';

interface UserCreateModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

interface FormData {
  firstName: string;
  lastName: string;
  email: string;
  initialRoles: Role[];
}

interface FormErrors {
  firstName?: string;
  lastName?: string;
  email?: string;
  initialRoles?: string;
}

const UserCreateModal: React.FC<UserCreateModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslation('userManagement');
  const createUserMutation = useCreateUser();

  const initialFormData: FormData = {
    firstName: '',
    lastName: '',
    email: '',
    initialRoles: [],
  };

  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [errors, setErrors] = useState<FormErrors>({});

  // Reset form when modal opens
  useEffect(() => {
    if (open) {
      setFormData(initialFormData);
      setErrors({});
    }
  }, [open]);

  const validateEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {};

    if (!formData.firstName.trim()) {
      newErrors.firstName = t('error.required');
    }

    if (!formData.lastName.trim()) {
      newErrors.lastName = t('error.required');
    }

    if (!formData.email.trim()) {
      newErrors.email = t('error.required');
    } else if (!validateEmail(formData.email)) {
      newErrors.email = t('error.email.invalid');
    }

    if (formData.initialRoles.length === 0) {
      newErrors.initialRoles = t('modal.createUser.selectRoles');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleInputChange =
    (field: keyof FormData) => (event: React.ChangeEvent<HTMLInputElement>) => {
      setFormData((prev) => ({
        ...prev,
        [field]: event.target.value,
      }));

      // Clear error for this field when user starts typing
      if (errors[field]) {
        setErrors((prev) => ({
          ...prev,
          [field]: undefined,
        }));
      }
    };

  const handleRoleToggle = (role: Role) => {
    setFormData((prev) => {
      const initialRoles = prev.initialRoles.includes(role)
        ? prev.initialRoles.filter((r) => r !== role)
        : [...prev.initialRoles, role];

      return { ...prev, initialRoles };
    });

    // Clear roles error when user selects a role
    if (errors.initialRoles) {
      setErrors((prev) => ({
        ...prev,
        initialRoles: undefined,
      }));
    }
  };

  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    try {
      await createUserMutation.mutateAsync({
        firstName: formData.firstName,
        lastName: formData.lastName,
        email: formData.email,
        initialRoles: formData.initialRoles,
      });

      onSuccess?.();
      onClose();
    } catch (error) {
      // Error handling is done by React Query
      console.error('Failed to create user:', error);
    }
  };

  const roleOptions: { value: Role; label: string }[] = [
    { value: 'ORGANIZER', label: t('filters.role.organizer') },
    { value: 'SPEAKER', label: t('filters.role.speaker') },
    { value: 'PARTNER', label: t('filters.role.partner') },
    { value: 'ATTENDEE', label: t('filters.role.attendee') },
  ];

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      aria-labelledby="create-user-dialog-title"
    >
      <DialogTitle id="create-user-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">{t('modal.createUser.title')}</Typography>
          <IconButton edge="end" color="inherit" onClick={onClose} aria-label="close">
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        <Box component="form" noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {/* First Name */}
          <TextField
            label={t('modal.createUser.firstName')}
            value={formData.firstName}
            onChange={handleInputChange('firstName')}
            error={!!errors.firstName}
            helperText={errors.firstName}
            required
            fullWidth
            autoFocus
          />

          {/* Last Name */}
          <TextField
            label={t('modal.createUser.lastName')}
            value={formData.lastName}
            onChange={handleInputChange('lastName')}
            error={!!errors.lastName}
            helperText={errors.lastName}
            required
            fullWidth
          />

          {/* Email */}
          <TextField
            label={t('modal.createUser.email')}
            type="email"
            value={formData.email}
            onChange={handleInputChange('email')}
            onBlur={() => {
              if (formData.email && !validateEmail(formData.email)) {
                setErrors((prev) => ({
                  ...prev,
                  email: t('error.email.invalid'),
                }));
              }
            }}
            error={!!errors.email}
            helperText={errors.email}
            required
            fullWidth
          />

          {/* Roles */}
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              {t('modal.createUser.roles')} *
            </Typography>
            <FormGroup>
              {roleOptions.map((role) => (
                <FormControlLabel
                  key={role.value}
                  control={
                    <Checkbox
                      checked={formData.initialRoles.includes(role.value)}
                      onChange={() => handleRoleToggle(role.value)}
                    />
                  }
                  label={role.label}
                />
              ))}
            </FormGroup>
            {errors.initialRoles && (
              <Typography variant="caption" color="error">
                {errors.initialRoles}
              </Typography>
            )}
          </Box>

          {/* Mutation Error */}
          {createUserMutation.isError && <Alert severity="error">{t('error.createFailed')}</Alert>}
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} variant="outlined">
          {t('actions.cancel')}
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="primary"
          disabled={createUserMutation.isPending}
        >
          {createUserMutation.isPending
            ? t('modal.createUser.creating')
            : t('modal.createUser.title')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UserCreateModal;
