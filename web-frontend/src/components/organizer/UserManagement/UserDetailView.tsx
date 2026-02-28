/**
 * User Detail View - Full page view for user details
 * Converted from UserDetailModal to match CompanyDetailView pattern
 */

import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Tabs,
  Tab,
  Chip,
  Avatar,
  Divider,
  Stack,
  Grid,
  Paper,
  BottomNavigation,
  BottomNavigationAction,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
  Person as OverviewIcon,
  Event as EventIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import type { User } from '../../../types/user.types';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs/Breadcrumbs';
import { EventsParticipatedTable } from './EventsParticipatedTable';
import { useCompany } from '@/hooks/useCompany/useCompany';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`user-tabpanel-${index}`}
      aria-labelledby={`user-tab-${index}`}
    >
      <Box sx={{ pt: 3 }}>{children}</Box>
    </div>
  );
}

interface UserDetailViewProps {
  user: User;
  onBack: () => void;
  onEdit?: (user: User) => void;
  onDelete?: (user: User) => void;
  canEdit?: boolean;
  canDelete?: boolean;
}

export const UserDetailView: React.FC<UserDetailViewProps> = ({
  user,
  onEdit,
  onDelete,
  canEdit = true,
  canDelete = false,
}) => {
  const { t } = useTranslation('userManagement');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const location = useLocation();
  const locationState = location.state as { from?: string; fromLabel?: string } | null;
  const backPath = locationState?.from ?? '/organizer/users';
  const backLabel = locationState?.fromLabel ?? t('title');
  const [activeTab, setActiveTab] = useState(0);

  // Fetch company with logo if user has a company
  const { data: companyWithLogo } = useCompany(user.company?.name || '', { expand: ['logo'] });

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const fullName = `${user.firstName} ${user.lastName}`;

  // Role badge configuration
  const roleConfig: Record<
    string,
    { label: string; color: 'primary' | 'secondary' | 'success' | 'info' }
  > = {
    ORGANIZER: { label: t('common:role.organizer'), color: 'primary' },
    SPEAKER: { label: t('common:role.speaker'), color: 'secondary' },
    PARTNER: { label: t('common:role.partner'), color: 'success' },
    ATTENDEE: { label: t('common:role.attendee'), color: 'info' },
  };

  return (
    <Box sx={{ p: 3, pb: 8 }} data-testid="user-detail-view">
      {/* Breadcrumbs */}
      <Breadcrumbs items={[{ label: backLabel, path: backPath }, { label: fullName }]} />

      {/* Action buttons */}
      {(canEdit || canDelete) && (
        <Box sx={{ mb: 3, display: 'flex', justifyContent: 'flex-end' }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            {canDelete && onDelete && (
              <Button
                variant="outlined"
                color="error"
                startIcon={<DeleteIcon />}
                onClick={() => onDelete(user)}
              >
                {t('common:actions.delete')}
              </Button>
            )}
            {canEdit && onEdit && (
              <Button variant="contained" startIcon={<EditIcon />} onClick={() => onEdit(user)}>
                {t('common:actions.edit')}
              </Button>
            )}
          </Stack>
        </Box>
      )}

      {/* User Profile Card */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={3}>
            {/* Profile Section */}
            <Grid size={12}>
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

            <Grid size={12}>
              <Divider />
            </Grid>

            {/* Roles Section */}
            <Grid size={{ xs: 12, sm: 6 }}>
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
              <Grid size={{ xs: 12, sm: 6 }}>
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  {t('table.headers.company')}
                </Typography>
                {companyWithLogo?.logo?.url ? (
                  <Box
                    component="img"
                    src={companyWithLogo.logo.url}
                    alt={user.company.name}
                    sx={{
                      maxHeight: 64,
                      maxWidth: 120,
                      objectFit: 'contain',
                      mb: 1,
                    }}
                  />
                ) : (
                  <Typography variant="body1">
                    {user.company.displayName || user.company.name}
                  </Typography>
                )}
                {user.company.website && (
                  <Typography variant="body2" color="text.secondary">
                    {user.company.website}
                  </Typography>
                )}
              </Grid>
            )}

            {/* Bio Section */}
            {user.bio && (
              <Grid size={12}>
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  {t('modal.userDetail.bio', 'Bio')}
                </Typography>
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                  {user.bio}
                </Typography>
              </Grid>
            )}

            {/* Additional Info Section */}
            <Grid size={12}>
              <Divider />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('modal.userDetail.createdAt')}
              </Typography>
              <Typography variant="body2">
                {new Date(user.createdAt).toLocaleDateString()}
              </Typography>
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('modal.userDetail.updatedAt')}
              </Typography>
              <Typography variant="body2">
                {new Date(user.updatedAt).toLocaleDateString()}
              </Typography>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Top Tabs — desktop only */}
      {!isMobile && (
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
          <Tabs value={activeTab} onChange={handleTabChange} aria-label="user detail tabs">
            <Tab
              label={t('common:labels.overview')}
              id="user-tab-0"
              aria-controls="user-tabpanel-0"
            />
            <Tab
              label={t('userDetail.tabs.eventsParticipated')}
              id="user-tab-1"
              aria-controls="user-tabpanel-1"
            />
          </Tabs>
        </Box>
      )}

      {/* Tab Panels */}
      <TabPanel value={activeTab} index={0}>
        <Typography variant="body1" paragraph>
          {t('userDetail.overviewText')}
        </Typography>
      </TabPanel>

      <TabPanel value={activeTab} index={1}>
        <EventsParticipatedTable userId={user.id} />
      </TabPanel>

      {/* Bottom Navigation — mobile only */}
      <Paper
        sx={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          zIndex: 1100,
          display: { xs: 'block', md: 'none' },
        }}
        elevation={3}
      >
        <BottomNavigation value={activeTab} onChange={(_, v) => setActiveTab(v)}>
          <BottomNavigationAction icon={<OverviewIcon />} sx={{ minWidth: 0, flex: 1 }} />
          <BottomNavigationAction icon={<EventIcon />} sx={{ minWidth: 0, flex: 1 }} />
        </BottomNavigation>
      </Paper>
    </Box>
  );
};
