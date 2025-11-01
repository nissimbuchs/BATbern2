/**
 * TeamActivityFeed Component
 *
 * Story 2.5.3 - Task 8b (GREEN Phase)
 * AC: 1 (Event Dashboard Display)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Displays team activity feed with manual reload
 */

import React from 'react';
import {
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Avatar,
  Typography,
  Box,
  Button,
  IconButton,
  Stack,
  Skeleton,
  Chip,
  CircularProgress,
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Person as PersonIcon,
  Description as DescriptionIcon,
  CheckCircle as CheckCircleIcon,
  People as PeopleIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { formatDistanceToNow } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import type { TeamActivity } from '@/types/event.types';

interface TeamActivityFeedProps {
  activities: TeamActivity[];
  isLoading?: boolean;
  onReload?: () => void;
  limit?: number;
}

// Activity type to icon mapping
const getActivityIcon = (type: string) => {
  switch (type) {
    case 'speaker_assigned':
      return <PeopleIcon />;
    case 'materials_uploaded':
      return <DescriptionIcon />;
    case 'workflow_advanced':
      return <CheckCircleIcon />;
    case 'speaker_invited':
      return <PersonIcon />;
    default:
      return <SettingsIcon />;
  }
};

// Activity type to color mapping
const getActivityColor = (type: string): 'primary' | 'success' | 'info' | 'default' => {
  switch (type) {
    case 'speaker_assigned':
    case 'speaker_invited':
      return 'primary';
    case 'workflow_advanced':
      return 'success';
    case 'materials_uploaded':
      return 'info';
    default:
      return 'default';
  }
};

export const TeamActivityFeed: React.FC<TeamActivityFeedProps> = ({
  activities,
  isLoading = false,
  onReload,
  limit,
}) => {
  const { t, i18n } = useTranslation('events');
  const locale = i18n.language === 'de' ? de : enUS;

  const displayedActivities = limit ? activities.slice(0, limit) : activities;
  const hasMore = limit && activities.length > limit;

  // Loading state
  if (isLoading) {
    return (
      <Box>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">{t('dashboard.teamActivity')}</Typography>
          <CircularProgress size={20} />
        </Stack>
        <List>
          {[1, 2, 3, 4, 5].map((index) => (
            <ListItem key={index} data-testid={`skeleton-activity-${index}`}>
              <Skeleton variant="rectangular" width="100%" height={60} />
            </ListItem>
          ))}
        </List>
      </Box>
    );
  }

  // Empty state
  if (activities.length === 0) {
    return (
      <Box textAlign="center" py={4}>
        <Typography variant="h6" gutterBottom>
          {t('dashboard.teamActivity')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('dashboard.noRecentActivity')}
        </Typography>
      </Box>
    );
  }

  return (
    <Box aria-label="Team activity feed">
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="h6">{t('dashboard.teamActivity')}</Typography>
          <Chip
            label={t('dashboard.activityCount', { count: activities.length })}
            size="small"
            aria-label={`${activities.length} activities`}
          />
        </Stack>

        <IconButton
          onClick={onReload}
          disabled={isLoading}
          size="small"
          aria-label="Reload activity feed"
        >
          <RefreshIcon />
        </IconButton>
      </Stack>

      {/* Last updated timestamp */}
      <Typography variant="caption" color="text.secondary" display="block" mb={1}>
        {t('dashboard.lastUpdated')}: {formatDistanceToNow(new Date(), { locale, addSuffix: true })}
      </Typography>

      {/* Activity list */}
      <List>
        {/* Today group header */}
        <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1 }}>
          {t('dashboard.today')}
        </Typography>

        {displayedActivities.map((activity) => {
          const isSystemActivity = activity.actorUsername === 'System';
          const relativeTime = formatDistanceToNow(new Date(activity.timestamp), {
            locale,
            addSuffix: true,
          });

          return (
            <ListItem
              key={activity.id}
              data-testid={`activity-item-${activity.id}`}
              className={isSystemActivity ? 'system-activity' : ''}
              sx={{ py: 1.5 }}
            >
              <ListItemAvatar>
                <Avatar
                  sx={{
                    bgcolor: isSystemActivity
                      ? 'grey.400'
                      : `${getActivityColor(activity.type)}.light`,
                  }}
                >
                  {getActivityIcon(activity.type)}
                </Avatar>
              </ListItemAvatar>

              <ListItemText
                primaryTypographyProps={{ component: 'div' }}
                secondaryTypographyProps={{ component: 'div' }}
                primary={
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="body2" fontWeight="bold">
                      {activity.actorName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {t(`dashboard.activityAction.${activity.type}`)}
                    </Typography>
                    <Chip label={activity.eventCode} size="small" variant="outlined" />
                  </Stack>
                }
                secondary={
                  <Stack spacing={0.5} mt={0.5}>
                    <Typography variant="caption">{activity.targetDescription}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {relativeTime}
                    </Typography>
                  </Stack>
                }
              />
            </ListItem>
          );
        })}
      </List>

      {/* View All button */}
      {hasMore && (
        <Box textAlign="center" mt={2}>
          <Button variant="outlined" size="small">
            {t('dashboard.viewAll')}
          </Button>
        </Box>
      )}
    </Box>
  );
};
