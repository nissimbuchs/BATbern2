import React from 'react';
import {
  Box,
  Container,
  Typography,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Avatar,
  IconButton,
  Stack,
  Chip,
  Tooltip,
  Paper,
  Button,
  Skeleton,
  Divider,
} from '@mui/material';
import {
  DoneAll as DoneAllIcon,
  Delete as DeleteIcon,
  Notifications as NotificationsIcon,
} from '@mui/icons-material';
import { formatDistanceToNow } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import {
  useNotifications,
  useMarkAsRead,
  useBatchMarkAsRead,
  useDeleteNotification,
} from '@/hooks/useNotifications';

const NotificationsPage: React.FC = () => {
  const { t, i18n } = useTranslation('common');
  const locale = i18n.language === 'de' ? de : enUS;
  const { user } = useAuth();

  const { data, isLoading } = useNotifications(
    { username: user?.username || '', status: 'ALL' },
    { page: 1, limit: 50 }
  );

  const markAsReadMutation = useMarkAsRead();
  const batchMarkAsReadMutation = useBatchMarkAsRead();
  const deleteNotificationMutation = useDeleteNotification();

  const notifications = data?.data || [];
  const unreadIds = notifications.filter((n) => !n.isRead).map((n) => n.id);

  const handleMarkAllAsRead = async () => {
    if (unreadIds.length === 0) return;
    await batchMarkAsReadMutation.mutateAsync(unreadIds);
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          {t('notifications.title')}
        </Typography>
        {unreadIds.length > 0 && (
          <Button
            variant="outlined"
            startIcon={<DoneAllIcon />}
            onClick={handleMarkAllAsRead}
            disabled={batchMarkAsReadMutation.isPending}
          >
            {t('notifications.markAllAsRead')} ({unreadIds.length})
          </Button>
        )}
      </Stack>

      <Paper>
        {isLoading && (
          <List>
            {[1, 2, 3, 4, 5].map((i) => (
              <ListItem key={i}>
                <Skeleton variant="rectangular" width="100%" height={60} />
              </ListItem>
            ))}
          </List>
        )}

        {!isLoading && notifications.length === 0 && (
          <Box textAlign="center" py={8}>
            <NotificationsIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              {t('notifications.empty')}
            </Typography>
          </Box>
        )}

        {!isLoading && notifications.length > 0 && (
          <List disablePadding>
            {notifications.map((notification, idx) => {
              const isUnread = !notification.isRead;
              const relativeTime = formatDistanceToNow(new Date(notification.createdAt), {
                locale,
                addSuffix: true,
              });

              return (
                <React.Fragment key={notification.id}>
                  {idx > 0 && <Divider component="li" />}
                  <ListItem
                    sx={{
                      py: 1.5,
                      bgcolor: isUnread ? 'action.hover' : 'transparent',
                      borderLeft: isUnread ? '4px solid' : 'none',
                      borderColor: 'primary.main',
                    }}
                    secondaryAction={
                      <Stack direction="row" spacing={0}>
                        {isUnread && (
                          <Tooltip title={t('notifications.markAsRead')}>
                            <IconButton
                              size="small"
                              onClick={() => markAsReadMutation.mutateAsync(notification.id)}
                              disabled={markAsReadMutation.isPending}
                            >
                              <DoneAllIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                        <Tooltip title={t('notifications.delete')}>
                          <IconButton
                            size="small"
                            onClick={() => deleteNotificationMutation.mutateAsync(notification.id)}
                            disabled={deleteNotificationMutation.isPending}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Stack>
                    }
                  >
                    <ListItemAvatar>
                      <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.dark' }}>
                        <NotificationsIcon />
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
                            <Chip label="URGENT" size="small" color="error" />
                          )}
                        </Stack>
                      }
                      secondary={
                        <Stack spacing={0.5} mt={0.5}>
                          <Typography variant="caption" color="text.secondary">
                            {notification.body}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {relativeTime}
                          </Typography>
                        </Stack>
                      }
                    />
                  </ListItem>
                </React.Fragment>
              );
            })}
          </List>
        )}
      </Paper>
    </Container>
  );
};

export default NotificationsPage;
