/**
 * UserCreateEditModal Component
 * Unified modal for creating and editing users
 * Features:
 * - Create new users with email, name, roles, company, and bio
 * - Edit existing users (update name, bio, company)
 * - Company autocomplete with search
 */

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
  Avatar,
  Badge,
  Tooltip,
  CircularProgress,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import {
  Close as CloseIcon,
  PhotoCamera as PhotoCameraIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useCreateUser, useUpdateUser } from '../../../hooks/useUserManagement';
import { CompanyAutocomplete } from '../PartnerManagement/CompanyAutocomplete';
import { uploadProfilePictureForUser } from '@/services/api/userAccountApi';
import apiClient from '@/services/api/apiClient';
import type { Role, User } from '../../../types/user.types';
import type { components } from '@/types/generated/company-api.types';

type Company = components['schemas']['CompanyResponse'];

interface UserCreateEditModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: (user?: User) => void;
  user?: User | null; // If provided, edit mode; otherwise create mode
}

interface FormData {
  firstName: string;
  lastName: string;
  email: string;
  bio: string;
  company: Company | null;
  initialRoles: Role[];
}

interface FormErrors {
  firstName?: string;
  lastName?: string;
  email?: string;
  bio?: string;
  company?: string;
  initialRoles?: string;
}

const UserCreateEditModal: React.FC<UserCreateEditModalProps> = ({
  open,
  onClose,
  onSuccess,
  user,
}) => {
  const { t } = useTranslation('userManagement');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const createUserMutation = useCreateUser();
  const updateUserMutation = useUpdateUser();

  const isEditMode = !!user;

  const initialFormData: FormData = {
    firstName: '',
    lastName: '',
    email: '',
    bio: '',
    company: null,
    initialRoles: [],
  };

  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [errors, setErrors] = useState<FormErrors>({});
  const [profilePictureUrl, setProfilePictureUrl] = useState<string | undefined>(undefined);
  const [isUploadingPhoto, setIsUploadingPhoto] = useState(false);

  // Initialize form when modal opens or user changes
  useEffect(() => {
    if (open) {
      if (user) {
        // Edit mode - populate with user data
        setFormData({
          firstName: user.firstName || '',
          lastName: user.lastName || '',
          email: user.email || '',
          bio: user.bio || '',
          company: user.companyId
            ? {
                name: user.company?.name || user.companyId,
                displayName: user.company?.displayName || user.company?.name || user.companyId,
                industry: user.company?.industry || undefined,
                website: user.company?.website || undefined,
                isVerified: false, // User.company doesn't include this, default to false
                createdAt: new Date().toISOString(), // Required field, use current time as placeholder
                updatedAt: new Date().toISOString(), // Required field, use current time as placeholder
                logo: undefined, // User.company type doesn't include logo
              }
            : null,
          initialRoles: (user.roles as Role[]) || [],
        });
        setProfilePictureUrl(user.profilePictureUrl || undefined);
      } else {
        // Create mode - reset form
        setFormData(initialFormData);
        setProfilePictureUrl(undefined);
      }
      setErrors({});
    }
  }, [open, user]);

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

    if (!isEditMode) {
      // Email required only for create mode
      if (!formData.email.trim()) {
        newErrors.email = t('error.required');
      } else if (!validateEmail(formData.email)) {
        newErrors.email = t('error.email.invalid');
      }

      // Roles required only for create mode
      if (formData.initialRoles.length === 0) {
        newErrors.initialRoles = t('modal.createUser.selectRoles');
      }
    }

    // Bio validation (optional, but check max length if provided)
    if (formData.bio && formData.bio.length > 2000) {
      newErrors.bio = t('error.bioTooLong', 'Bio must be less than 2000 characters');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleInputChange =
    (field: keyof Omit<FormData, 'company' | 'initialRoles'>) =>
    (event: React.ChangeEvent<HTMLInputElement>) => {
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

  const handleCompanyChange = (company: Company | null) => {
    setFormData((prev) => ({
      ...prev,
      company,
    }));

    if (errors.company) {
      setErrors((prev) => ({
        ...prev,
        company: undefined,
      }));
    }
  };

  const handlePhotoUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !isEditMode || !user) return;

    setIsUploadingPhoto(true);
    try {
      // Use admin endpoint to upload for the specific user (user.id is the username)
      const newUrl = await uploadProfilePictureForUser(user.id, file);
      setProfilePictureUrl(newUrl);
    } catch (error) {
      console.error('Photo upload failed:', error);
    } finally {
      setIsUploadingPhoto(false);
    }
  };

  const handlePhotoRemove = async () => {
    if (!isEditMode || !user) return;

    setIsUploadingPhoto(true);
    try {
      // Use admin endpoint to remove for the specific user
      await apiClient.delete(`/users/${user.id}/picture`);
      setProfilePictureUrl(undefined);
    } catch (error) {
      console.error('Photo removal failed:', error);
    } finally {
      setIsUploadingPhoto(false);
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
      let createdOrUpdatedUser: User | undefined;

      if (isEditMode && user) {
        // Edit mode - update user
        createdOrUpdatedUser = await updateUserMutation.mutateAsync({
          username: user.id,
          data: {
            firstName: formData.firstName,
            lastName: formData.lastName,
            bio: formData.bio || undefined,
            companyId: formData.company?.name || undefined,
          },
        });
      } else {
        // Create mode - create new user
        createdOrUpdatedUser = await createUserMutation.mutateAsync({
          firstName: formData.firstName,
          lastName: formData.lastName,
          email: formData.email,
          bio: formData.bio || undefined,
          companyId: formData.company?.name || undefined,
          initialRoles: formData.initialRoles,
        });
      }

      onSuccess?.(createdOrUpdatedUser);
      onClose();
    } catch (error) {
      // Error handling is done by React Query
      console.error(isEditMode ? 'Failed to update user:' : 'Failed to create user:', error);
    }
  };

  const roleOptions: { value: Role; label: string }[] = [
    { value: 'ORGANIZER', label: t('common:role.organizer') },
    { value: 'SPEAKER', label: t('common:role.speaker') },
    { value: 'PARTNER', label: t('common:role.partner') },
    { value: 'ATTENDEE', label: t('common:role.attendee') },
  ];

  const mutation = isEditMode ? updateUserMutation : createUserMutation;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      fullScreen={isMobile}
      aria-labelledby="user-dialog-title"
    >
      <DialogTitle id="user-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            {isEditMode ? t('modal.editUser.title', 'Edit User') : t('modal.createUser.title')}
          </Typography>
          <IconButton
            edge="end"
            color="inherit"
            onClick={onClose}
            aria-label={t('common:actions.close')}
          >
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        <Box component="form" noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {/* Profile Picture and Name Fields */}
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
            {/* Profile Picture (only in edit mode) */}
            {isEditMode && (
              <Box sx={{ position: 'relative', flexShrink: 0 }}>
                <Badge
                  overlap="circular"
                  anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                  badgeContent={
                    <>
                      <input
                        accept="image/jpeg,image/png"
                        style={{ display: 'none' }}
                        id="modal-profile-photo-upload"
                        type="file"
                        onChange={handlePhotoUpload}
                        disabled={isUploadingPhoto}
                      />
                      <label htmlFor="modal-profile-photo-upload">
                        <Tooltip title={t('modal.editUser.uploadPhoto', 'Upload Photo')}>
                          <IconButton
                            aria-label={t('modal.editUser.uploadPhotoAria')}
                            component="span"
                            size="small"
                            disabled={isUploadingPhoto}
                            sx={{
                              bgcolor: 'primary.main',
                              color: 'white',
                              '&:hover': { bgcolor: 'primary.dark' },
                              '&:disabled': { bgcolor: 'grey.400' },
                            }}
                          >
                            {isUploadingPhoto ? (
                              <CircularProgress size={16} color="inherit" />
                            ) : (
                              <PhotoCameraIcon fontSize="small" />
                            )}
                          </IconButton>
                        </Tooltip>
                      </label>
                    </>
                  }
                >
                  <Avatar
                    src={profilePictureUrl}
                    sx={{ width: 80, height: 80, fontSize: '1.5rem' }}
                  >
                    {formData.firstName[0] || ''}
                    {formData.lastName[0] || ''}
                  </Avatar>
                </Badge>

                {profilePictureUrl && (
                  <Tooltip title={t('modal.editUser.removePhoto', 'Remove Photo')}>
                    <IconButton
                      aria-label={t('modal.editUser.removePhotoAria')}
                      size="small"
                      onClick={handlePhotoRemove}
                      disabled={isUploadingPhoto}
                      sx={{
                        position: 'absolute',
                        top: -8,
                        right: -8,
                        bgcolor: 'error.main',
                        color: 'white',
                        '&:hover': { bgcolor: 'error.dark' },
                        '&:disabled': { bgcolor: 'grey.400' },
                      }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>
            )}

            {/* Name Fields */}
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
              {/* First Name */}
              <TextField
                name="firstName"
                data-testid="user-create-firstName"
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
                name="lastName"
                data-testid="user-create-lastName"
                label={t('modal.createUser.lastName')}
                value={formData.lastName}
                onChange={handleInputChange('lastName')}
                error={!!errors.lastName}
                helperText={errors.lastName}
                required
                fullWidth
              />
            </Box>
          </Box>

          {/* Email (only in create mode) */}
          {!isEditMode && (
            <TextField
              name="email"
              data-testid="user-create-email"
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
          )}

          {/* Company Autocomplete */}
          <CompanyAutocomplete
            value={formData.company}
            onChange={handleCompanyChange}
            error={errors.company}
            label={t('modal.createUser.company', 'Company')}
          />

          {/* Bio */}
          <TextField
            label={t('modal.createUser.bio', 'Bio')}
            value={formData.bio}
            onChange={handleInputChange('bio')}
            error={!!errors.bio}
            helperText={errors.bio || t('modal.createUser.bioHelper', 'Max 2000 characters')}
            multiline
            rows={4}
            fullWidth
          />

          {/* Roles (only in create mode) */}
          {!isEditMode && (
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
                        data-testid={`user-create-role-${role.value}`}
                        value={role.value}
                        checked={formData.initialRoles.includes(role.value)}
                        onChange={() => handleRoleToggle(role.value)}
                      />
                    }
                    label={role.label}
                  />
                ))}
              </FormGroup>
              {errors.initialRoles && (
                <Typography variant="caption" color="error" data-testid="user-create-role-error">
                  {errors.initialRoles}
                </Typography>
              )}
            </Box>
          )}

          {/* Mutation Error */}
          {mutation.isError && (
            <Alert severity="error">
              {isEditMode ? t('error.updateFailed') : t('error.createFailed')}
            </Alert>
          )}
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} variant="outlined" data-testid="user-create-cancel">
          {t('common:actions.cancel')}
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="primary"
          disabled={mutation.isPending}
          data-testid="user-create-submit"
        >
          {mutation.isPending
            ? isEditMode
              ? t('modal.editUser.saving', 'Saving...')
              : t('modal.createUser.creating')
            : isEditMode
              ? t('common:actions.save')
              : t('modal.createUser.title')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UserCreateEditModal;
