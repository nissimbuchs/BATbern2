/**
 * ProfileHeader Component
 * Story 2.6: User Account Management Frontend
 * Displays profile photo, name, email, company, roles, member since
 */

import React from 'react';
import { Box, Avatar, Typography, Chip, IconButton, Stack, Badge, Tooltip } from '@mui/material';
import {
  PhotoCamera as PhotoCameraIcon,
  Delete as DeleteIcon,
  CheckCircle as CheckCircleIcon,
} from '@mui/icons-material';
import { format } from 'date-fns';
import type { User } from '@/types/userAccount.types';

interface ProfileHeaderProps {
  user: User;
  onPhotoUpload: (file: File) => void;
  onPhotoRemove: () => void;
}

const roleIcons: Record<string, string> = {
  ORGANIZER: '🎯',
  SPEAKER: '🎤',
  PARTNER: '👔',
  ATTENDEE: '👤',
};

const ProfileHeader: React.FC<ProfileHeaderProps> = ({ user, onPhotoUpload, onPhotoRemove }) => {
  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      onPhotoUpload(file);
    }
  };

  const formatMemberSince = (dateString: string): string => {
    try {
      return format(new Date(dateString), 'MMMM yyyy');
    } catch {
      return dateString;
    }
  };

  const getInitials = (firstName: string, lastName: string): string => {
    return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
  };

  return (
    <Box sx={{ mb: 4 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} alignItems="center">
        {/* Profile Photo */}
        <Box sx={{ position: 'relative' }}>
          <Badge
            overlap="circular"
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            badgeContent={
              <>
                <input
                  accept="image/jpeg,image/png"
                  style={{ display: 'none' }}
                  id="profile-photo-upload"
                  type="file"
                  onChange={handleFileSelect}
                />
                <label htmlFor="profile-photo-upload">
                  <Tooltip title="Upload Photo">
                    <IconButton
                      aria-label="Upload profile photo"
                      component="span"
                      size="small"
                      sx={{
                        bgcolor: 'primary.main',
                        color: 'white',
                        '&:hover': { bgcolor: 'primary.dark' },
                      }}
                      data-testid="upload-photo-button"
                    >
                      <PhotoCameraIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </label>
              </>
            }
          >
            <Avatar
              src={user.profilePictureUrl}
              alt={`${user.firstName} ${user.lastName}`}
              sx={{ width: 120, height: 120, fontSize: '2rem' }}
              data-testid="profile-photo"
            >
              {!user.profilePictureUrl && getInitials(user.firstName, user.lastName)}
            </Avatar>
          </Badge>

          {user.profilePictureUrl && (
            <Tooltip title="Remove Photo">
              <IconButton
                aria-label="Remove profile photo"
                size="small"
                onClick={onPhotoRemove}
                sx={{
                  position: 'absolute',
                  top: -8,
                  right: -8,
                  bgcolor: 'error.main',
                  color: 'white',
                  '&:hover': { bgcolor: 'error.dark' },
                }}
                data-testid="remove-photo-button"
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Box>

        {/* User Info */}
        <Box sx={{ flex: 1, textAlign: { xs: 'center', sm: 'left' } }}>
          {/* Name */}
          <Typography variant="h4" component="h1" gutterBottom data-testid="user-name">
            {user.firstName} {user.lastName}
          </Typography>

          {/* Email with Verified Badge */}
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              mb: 1,
              justifyContent: { xs: 'center', sm: 'flex-start' },
            }}
          >
            <Typography variant="body1" color="text.secondary" data-testid="user-email">
              {user.email}
            </Typography>
            {user.emailVerified && (
              <Chip
                icon={<CheckCircleIcon />}
                label="Verified"
                size="small"
                color="success"
                variant="outlined"
                data-testid="email-verified-badge"
              />
            )}
          </Box>

          {/* Company */}
          {user.company && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ mb: 1 }}
              data-testid="user-company"
            >
              {user.company.name}
            </Typography>
          )}

          {/* Role Badges */}
          <Stack
            direction="row"
            spacing={1}
            sx={{ mb: 1, justifyContent: { xs: 'center', sm: 'flex-start' }, flexWrap: 'wrap' }}
          >
            {user.roles.map((role) => (
              <Chip
                key={role}
                label={`${roleIcons[role] || ''} ${role.charAt(0) + role.slice(1).toLowerCase()}`}
                size="small"
                variant="filled"
                color="primary"
                data-testid="role-badge"
              />
            ))}
          </Stack>

          {/* Member Since */}
          <Typography variant="caption" color="text.secondary" data-testid="member-since">
            Member since {formatMemberSince(user.memberSince)}
          </Typography>
        </Box>
      </Stack>
    </Box>
  );
};

export default ProfileHeader;
