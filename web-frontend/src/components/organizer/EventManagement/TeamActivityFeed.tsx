/**
 * TeamActivityFeed Component (Enhanced with Notifications)
 *
 * Story BAT-7 - Notifications API Consolidation
 * AC: Display EventBridge-triggered notifications for organizers
 *
 * Displays notifications from Event Management Service:
 * - EventBridge-triggered notifications (TASK_ASSIGNED, DEADLINE_WARNING, etc.)
 * - Mark as read functionality
 * - Real-time updates via React Query
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
  Badge,
  Tooltip,
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Description as DescriptionIcon,
  CheckCircle as CheckCircleIcon,
  People as PeopleIcon,
  Settings as SettingsIcon,
  Notifications as NotificationsIcon,
  Assignment as AssignmentIcon,
  Warning as WarningIcon,
  Event as EventIcon,
  DoneAll as DoneAllIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { formatDistanceToNow } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import type { Notification, NotificationType } from '@/types/notification';
import { useMarkAsRead, useBatchMarkAsRead, useDeleteNotification } from '@/hooks/useNotifications';

interface TeamActivityFeedProps {
  notifications: Notification[];
  totalNotifications?: number;
  isLoading?: boolean;
  onReload?: () => void;
  limit?: number;
}

// Notification type to icon mapping
const getNotificationIcon = (type: NotificationType) => {
  switch (type) {
    // Event notifications
    case 'EVENT_PUBLISHED':
    case 'EVENT_STATUS_CHANGED':
      return <EventIcon />;
    // Speaker workflow notifications
    case 'SPEAKER_INVITED':
    case 'SPEAKER_ACCEPTED':
    case 'SPEAKER_DECLINED':
    case 'SPEAKER_CONFIRMED':
      return <PeopleIcon />;
    case 'CONTENT_SUBMITTED':
    case 'QUALITY_REVIEW_PENDING':
    case 'QUALITY_REVIEW_APPROVED':
    case 'QUALITY_REVIEW_REQUIRES_CHANGES':
      return <DescriptionIcon />;
    case 'SLOT_ASSIGNED':
      return <CheckCircleIcon />;
    // Task notifications
    case 'TASK_ASSIGNED':
      return <AssignmentIcon />;
    case 'TASK_DEADLINE_WARNING':
    case 'DEADLINE_WARNING':
      return <WarningIcon />;
    // Other notifications
    case 'OVERFLOW_DETECTED':
    case 'VOTING_REQUIRED':
      return <NotificationsIcon />;
    default:
      return <SettingsIcon />;
  }
};

// Notification type to color mapping
const getNotificationColor = (
  type: NotificationType,
  priority: string
): 'primary' | 'success' | 'info' | 'warning' | 'error' | 'default' => {
  // Priority overrides type-based color for urgent notifications
  if (priority === 'URGENT') return 'error';
  if (priority === 'HIGH') return 'warning';

  switch (type) {
    // Event notifications
    case 'EVENT_PUBLISHED':
    case 'EVENT_STATUS_CHANGED':
      return 'success';
    // Speaker workflow
    case 'SPEAKER_INVITED':
    case 'SPEAKER_ACCEPTED':
    case 'SPEAKER_CONFIRMED':
      return 'primary';
    case 'SPEAKER_DECLINED':
      return 'warning';
    // Content/Quality
    case 'CONTENT_SUBMITTED':
    case 'QUALITY_REVIEW_APPROVED':
      return 'success';
    case 'QUALITY_REVIEW_REQUIRES_CHANGES':
      return 'warning';
    case 'QUALITY_REVIEW_PENDING':
      return 'info';
    // Tasks
    case 'TASK_ASSIGNED':
      return 'primary';
    case 'TASK_DEADLINE_WARNING':
    case 'DEADLINE_WARNING':
      return 'warning';
    // Critical
    case 'OVERFLOW_DETECTED':
    case 'VOTING_REQUIRED':
      return 'error';
    default:
      return 'default';
  }
};

export const TeamActivityFeed: React.FC<TeamActivityFeedProps> = ({
  notifications,
  totalNotifications,
  isLoading = false,
  onReload,
  limit,
}) => {
  const { t, i18n } = useTranslation('common');
  const locale = i18n.language === 'de' ? de : enUS;
  const navigate = useNavigate();
  const markAsReadMutation = useMarkAsRead();
  const batchMarkAsReadMutation = useBatchMarkAsRead();
  const deleteNotificationMutation = useDeleteNotification();

  const displayedNotifications = limit ? notifications.slice(0, limit) : notifications;
  const hasMore = limit && notifications.length > limit;
  const unreadCount = notifications.filter((n) => !n.isRead).length;
  const totalCount = totalNotifications ?? notifications.length;

  const handleMarkAsRead = async (notificationId: string) => {
    try {
      await markAsReadMutation.mutateAsync(notificationId);
    } catch (error) {
      console.error('Failed to mark notification as read:', error);
    }
  };

  const handleMarkAllAsRead = async () => {
    const unreadIds = notifications.filter((n) => !n.isRead).map((n) => n.id);
    if (unreadIds.length === 0) return;
    try {
      await batchMarkAsReadMutation.mutateAsync(unreadIds);
    } catch (error) {
      console.error('Failed to mark all notifications as read:', error);
    }
  };

  const handleDelete = async (notificationId: string) => {
    try {
      await deleteNotificationMutation.mutateAsync(notificationId);
    } catch (error) {
      console.error('Failed to delete notification:', error);
    }
  };

  // Loading state
  if (isLoading) {
    return (
      <Box>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">{t('notifications.title')}</Typography>
          <CircularProgress size={20} />
        </Stack>
        <List>
          {[1, 2, 3, 4, 5].map((index) => (
            <ListItem key={index} data-testid={`skeleton-notification-${index}`}>
              <Skeleton variant="rectangular" width="100%" height={60} />
            </ListItem>
          ))}
        </List>
      </Box>
    );
  }

  // Empty state
  if (notifications.length === 0) {
    return (
      <Box textAlign="center" py={4}>
        <NotificationsIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 2 }} />
        <Typography variant="h6" gutterBottom>
          {t('notifications.title')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('notifications.noNotifications')}
        </Typography>
      </Box>
    );
  }

  return (
    <Box aria-label={t('notifications.notificationFeed')}>
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="h6">{t('notifications.title')}</Typography>
          <Badge badgeContent={unreadCount} color="error">
            <Chip
              label={totalCount}
              size="small"
              aria-label={`${totalCount} total notifications`}
            />
          </Badge>
        </Stack>

        <Stack direction="row" spacing={0.5} alignItems="center">
          {unreadCount > 0 && (
            <Tooltip title={t('notifications.markAllAsRead')}>
              <IconButton
                onClick={handleMarkAllAsRead}
                disabled={batchMarkAsReadMutation.isPending}
                size="small"
                aria-label={t('notifications.markAllAsRead')}
              >
                <DoneAllIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          <Tooltip title={t('notifications.reload')}>
            <IconButton
              onClick={onReload}
              disabled={isLoading}
              size="small"
              aria-label={t('notifications.reload')}
            >
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Stack>
      </Stack>

      {/* Last updated timestamp */}
      <Typography variant="caption" color="text.secondary" display="block" mb={1}>
        {t('notifications.lastUpdated')}:{' '}
        {formatDistanceToNow(new Date(), { locale, addSuffix: true })}
      </Typography>

      {/* Notification list */}
      <List data-testid="team-activity-feed">
        {displayedNotifications.map((notification) => {
          const relativeTime = formatDistanceToNow(new Date(notification.createdAt), {
            locale,
            addSuffix: true,
          });

          const color = getNotificationColor(notification.notificationType, notification.priority);
          const isUnread = !notification.isRead;

          return (
            <ListItem
              key={notification.id}
              data-testid={`notification-item-${notification.id}`}
              sx={{
                py: 1.5,
                bgcolor: isUnread ? 'action.hover' : 'transparent',
                borderLeft: isUnread ? `4px solid` : 'none',
                borderColor: `${color}.main`,
              }}
              secondaryAction={
                <Stack direction="row" spacing={0}>
                  {isUnread && (
                    <Tooltip title={t('notifications.markAsRead')}>
                      <IconButton
                        size="small"
                        onClick={() => handleMarkAsRead(notification.id)}
                        disabled={markAsReadMutation.isPending}
                      >
                        <DoneAllIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Tooltip title={t('notifications.delete')}>
                    <IconButton
                      size="small"
                      onClick={() => handleDelete(notification.id)}
                      disabled={deleteNotificationMutation.isPending}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
              }
            >
              <ListItemAvatar>
                <Avatar
                  sx={{
                    bgcolor: `${color}.light`,
                    color: `${color}.dark`,
                  }}
                >
                  {getNotificationIcon(notification.notificationType)}
                </Avatar>
              </ListItemAvatar>

              <ListItemText
                primaryTypographyProps={{ component: 'div' }}
                secondaryTypographyProps={{ component: 'div' }}
                primary={
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="body2" fontWeight={isUnread ? 'bold' : 'normal'}>
                      {notification.subject}
                    </Typography>
                    {notification.eventCode && (
                      <Chip label={notification.eventCode} size="small" variant="outlined" />
                    )}
                    {notification.priority === 'URGENT' && (
                      <Chip label={t('notifications.priority.urgent')} size="small" color="error" />
                    )}
                    {notification.priority === 'HIGH' && (
                      <Chip label={t('notifications.priority.high')} size="small" color="warning" />
                    )}
                  </Stack>
                }
                secondary={
                  <Stack spacing={0.5} mt={0.5}>
                    <Typography variant="caption" color="text.secondary">
                      {notification.body.length > 80
                        ? `${notification.body.substring(0, 80)}...`
                        : notification.body}
                    </Typography>
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
          <Button
            variant="outlined"
            size="small"
            onClick={() => navigate('/organizer/notifications')}
          >
            {t('notifications.viewAll')} ({totalCount})
          </Button>
        </Box>
      )}
    </Box>
  );
};
