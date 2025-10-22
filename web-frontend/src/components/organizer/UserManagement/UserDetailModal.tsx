import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Avatar,
  Chip,
  Grid,
  Divider,
  IconButton,
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types/user.types';

interface UserDetailModalProps {
  user: User | null;
  open: boolean;
  onClose: () => void;
}

const UserDetailModal: React.FC<UserDetailModalProps> = ({ user, open, onClose }) => {
  const { t } = useTranslation('userManagement');

  if (!user) {
    return null;
  }

  const fullName = `${user.firstName} ${user.lastName}`;

  // Role badge configuration
  const roleConfig: Record<
    string,
    { label: string; color: 'primary' | 'secondary' | 'success' | 'info' }
  > = {
    ORGANIZER: { label: t('filters.role.organizer'), color: 'primary' },
    SPEAKER: { label: t('filters.role.speaker'), color: 'secondary' },
    PARTNER: { label: t('filters.role.partner'), color: 'success' },
    ATTENDEE: { label: t('filters.role.attendee'), color: 'info' },
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      aria-labelledby="user-detail-dialog-title"
    >
      <DialogTitle id="user-detail-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">{t('modal.userDetail.title')}</Typography>
          <IconButton edge="end" color="inherit" onClick={onClose} aria-label="close">
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        <Grid container spacing={3}>
          {/* Profile Section */}
          <Grid item xs={12}>
            <Box display="flex" alignItems="center" gap={2}>
              <Avatar src={user.profilePictureUrl} alt={fullName} sx={{ width: 80, height: 80 }}>
                {user.firstName[0]}
                {user.lastName[0]}
              </Avatar>
              <Box>
                <Typography variant="h5">{fullName}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {user.email}
                </Typography>
                <Box mt={1}>
                  <Chip
                    label={
                      user.active ? t('filters.status.active') : t('filters.status.inactive')
                    }
                    color={user.active ? 'success' : 'default'}
                    size="small"
                  />
                </Box>
              </Box>
            </Box>
          </Grid>

          <Grid item xs={12}>
            <Divider />
          </Grid>

          {/* Roles Section */}
          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {t('table.headers.roles')}
            </Typography>
            <Box display="flex" gap={1} flexWrap="wrap">
              {user.roles.map((role) => (
                <Chip
                  key={role}
                  label={roleConfig[role]?.label || role}
                  color={roleConfig[role]?.color || 'default'}
                />
              ))}
            </Box>
          </Grid>

          {/* Company Section */}
          {user.company && (
            <Grid item xs={12} sm={6}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('table.headers.company')}
              </Typography>
              <Typography variant="body1">{user.company.name}</Typography>
              {user.company.website && (
                <Typography variant="body2" color="text.secondary">
                  {user.company.website}
                </Typography>
              )}
            </Grid>
          )}

          {/* Additional Info Section */}
          <Grid item xs={12}>
            <Divider />
          </Grid>

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {t('modal.userDetail.createdAt')}
            </Typography>
            <Typography variant="body2">{new Date(user.createdAt).toLocaleDateString()}</Typography>
          </Grid>

          <Grid item xs={12} sm={6}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {t('modal.userDetail.updatedAt')}
            </Typography>
            <Typography variant="body2">{new Date(user.updatedAt).toLocaleDateString()}</Typography>
          </Grid>
        </Grid>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} variant="outlined">
          {t('actions.close')}
        </Button>
        <Button variant="contained" color="primary">
          {t('actions.edit')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UserDetailModal;
