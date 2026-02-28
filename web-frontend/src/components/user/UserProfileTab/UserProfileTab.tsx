/**
 * UserProfileTab Component
 * Story 2.6: User Account Management Frontend
 */

import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Box, Button, Divider, Paper, TextField, Typography } from '@mui/material';
import ProfileHeader from '../ProfileHeader/ProfileHeader';
import { CompanyAutocomplete } from '@/components/organizer/PartnerManagement/CompanyAutocomplete';
import WatchPairingSection from '@/features/profile/WatchPairingSection';
import type { User, UserActivity } from '@/types/userAccount.types';
import type { components } from '@/types/generated/company-api.types';
import {
  useUpdateUserProfile,
  useUploadProfilePicture,
  useRemoveProfilePicture,
} from '@/hooks/useUserAccount/useUserAccount';

type Company = components['schemas']['CompanyResponse'];

interface UserProfileTabProps {
  user: User;
  activity: UserActivity[];
}

const UserProfileTab: React.FC<UserProfileTabProps> = ({ user, activity }) => {
  const { t } = useTranslation(['userManagement', 'common']);
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState<{
    firstName: string;
    lastName: string;
    bio: string;
    company: Company | null;
  }>({
    firstName: user.firstName,
    lastName: user.lastName,
    bio: user.bio || '',
    company: user.company
      ? {
          name: user.company.name,
          displayName: user.company.name,
          isVerified: false,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }
      : null,
  });

  // Sync form data when user prop changes (e.g., after save)
  useEffect(() => {
    setFormData({
      firstName: user.firstName,
      lastName: user.lastName,
      bio: user.bio || '',
      company: user.company
        ? {
            name: user.company.name,
            displayName: user.company.name,
            isVerified: false,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }
        : null,
    });
  }, [user]);

  const updateProfileMutation = useUpdateUserProfile();
  const uploadPhotoMutation = useUploadProfilePicture();
  const removePhotoMutation = useRemoveProfilePicture();

  const handlePhotoUpload = async (file: File) => {
    try {
      await uploadPhotoMutation.mutateAsync({ file });
    } catch (error) {
      console.error('Photo upload failed:', error);
    }
  };

  const handlePhotoRemove = async () => {
    if (window.confirm(t('profile.removePhotoConfirm'))) {
      try {
        await removePhotoMutation.mutateAsync();
      } catch (error) {
        console.error('Photo removal failed:', error);
      }
    }
  };

  const handleSave = async () => {
    try {
      await updateProfileMutation.mutateAsync({
        firstName: formData.firstName,
        lastName: formData.lastName,
        bio: formData.bio,
        companyId: formData.company?.name || undefined,
      });
      setIsEditing(false);
    } catch (error) {
      console.error('Profile update failed:', error);
    }
  };

  const handleCancel = () => {
    setFormData({
      firstName: user.firstName,
      lastName: user.lastName,
      bio: user.bio || '',
      company: user.company
        ? {
            name: user.company.name,
            displayName: user.company.name,
            isVerified: false,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }
        : null,
    });
    setIsEditing(false);
  };

  const handleCompanyChange = (company: Company | null) => {
    setFormData((prev) => ({ ...prev, company }));
  };

  const bioCharCount = formData.bio.length;
  const bioMaxLength = 2000;

  return (
    <Box data-testid="user-profile-tab">
      <ProfileHeader
        user={user}
        onPhotoUpload={handlePhotoUpload}
        onPhotoRemove={handlePhotoRemove}
      />

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">{t('profile.personalInfo')}</Typography>
          {!isEditing && (
            <Button
              variant="outlined"
              onClick={() => setIsEditing(true)}
              data-testid="edit-profile-button"
            >
              {t('profile.editProfile')}
            </Button>
          )}
        </Box>

        <Divider sx={{ mb: 2 }} />

        {isEditing ? (
          <Box component="form" noValidate autoComplete="off">
            <TextField
              fullWidth
              label={t('profile.firstName')}
              value={formData.firstName}
              onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
              margin="normal"
              data-testid="first-name-field"
            />
            <TextField
              fullWidth
              label={t('profile.lastName')}
              value={formData.lastName}
              onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
              margin="normal"
              data-testid="last-name-field"
            />
            <Box sx={{ mt: 2 }}>
              <CompanyAutocomplete
                value={formData.company}
                onChange={handleCompanyChange}
                label={t('common:labels.company')}
              />
            </Box>
            <TextField
              fullWidth
              label={t('profile.bio')}
              value={formData.bio}
              onChange={(e) => setFormData({ ...formData, bio: e.target.value })}
              margin="normal"
              multiline
              rows={4}
              inputProps={{ maxLength: bioMaxLength }}
              helperText={
                <span data-testid="bio-char-counter">
                  {bioCharCount}/{bioMaxLength}
                </span>
              }
              error={bioCharCount > bioMaxLength}
              data-testid="bio-field"
            />
            {bioCharCount > bioMaxLength && (
              <Typography color="error" variant="caption" data-testid="bio-validation-error">
                {t('profile.bioValidation', { max: bioMaxLength })}
              </Typography>
            )}
            <Box sx={{ mt: 2, display: 'flex', gap: 2 }}>
              <Button variant="contained" onClick={handleSave} data-testid="save-profile-button">
                {t('profile.saveChanges')}
              </Button>
              <Button variant="outlined" onClick={handleCancel} data-testid="cancel-edit-button">
                {t('common:actions.cancel')}
              </Button>
            </Box>
          </Box>
        ) : (
          <Box>
            <Typography variant="body1">
              <strong>{t('profile.labelName')}</strong> {user.firstName} {user.lastName}
            </Typography>
            <Typography variant="body1" sx={{ mt: 1 }}>
              <strong>{t('profile.labelEmail')}</strong> {user.email}
            </Typography>
            {user.company && (
              <Typography variant="body1" sx={{ mt: 1 }}>
                <strong>{t('profile.labelCompany')}</strong> {user.company.name}
              </Typography>
            )}
            {user.bio && (
              <Typography variant="body1" sx={{ mt: 1 }}>
                <strong>{t('profile.labelBio')}</strong> {user.bio}
              </Typography>
            )}
          </Box>
        )}
      </Paper>

      {/* Apple Watch Pairing (Organizers only) */}
      {user.roles.includes('ORGANIZER') && (
        <Paper sx={{ p: 3, mb: 3 }}>
          <WatchPairingSection username={user.username} />
        </Paper>
      )}

      {/* Activity Timeline */}
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>
          {t('profile.recentActivity')}
        </Typography>
        <Divider sx={{ mb: 2 }} />
        {activity.length > 0 ? (
          activity.slice(0, 5).map((item) => (
            <Box key={item.id} sx={{ mb: 2 }} data-testid="activity-item">
              <Typography variant="body2">{item.description}</Typography>
              <Typography variant="caption" color="text.secondary" data-testid="activity-timestamp">
                {new Date(item.timestamp).toLocaleString()}
              </Typography>
            </Box>
          ))
        ) : (
          <Typography variant="body2" color="text.secondary">
            {t('profile.noActivity')}
          </Typography>
        )}
        {activity.length > 5 && (
          <Button size="small" data-testid="view-all-activities-link">
            {t('profile.viewAll')}
          </Button>
        )}
      </Paper>
    </Box>
  );
};

export default UserProfileTab;
