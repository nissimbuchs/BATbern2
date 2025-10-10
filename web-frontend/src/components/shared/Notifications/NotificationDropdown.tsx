/**
 * NotificationDropdown Component
 * Displays user notifications with manual reload, mark as read, and delete functionality
 * Story 1.17 AC4 - Notification System
 * Task 13b: Wrapped with React.memo() for performance optimization
 */

import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Menu,
  List,
  ListItem,
  ListItemText,
  IconButton,
  Button,
  Typography,
  Box,
  CircularProgress,
  Alert,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Delete as DeleteIcon,
  PriorityHigh as PriorityHighIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n/config';
import { Notification } from '../../../types/notification';
import { formatDateTime } from '../../../utils/date';

export interface NotificationDropdownProps {
  notifications: Notification[];
  unreadCount: number;
  onMarkAsRead: (ids: string[] | 'all') => void;
  onDelete: (id: string) => void;
  onReload: () => void;
  anchorEl: HTMLElement | null;
  open: boolean;
  onClose: () => void;
  isLoading?: boolean;
  error?: string;
}

export const NotificationDropdown = React.memo<NotificationDropdownProps>(
  ({
    notifications,
    unreadCount,
    onMarkAsRead,
    onDelete,
    onReload,
    anchorEl,
    open,
    onClose,
    isLoading = false,
    error,
  }) => {
    const { t } = useTranslation('common');
    const [deleteConfirmation, setDeleteConfirmation] = useState<{
      open: boolean;
      notificationId: string | null;
    }>({ open: false, notificationId: null });

    const handleNotificationClick = (notification: Notification) => {
      if (!notification.isRead) {
        onMarkAsRead([notification.id]);
      }
    };

    const handleDeleteClick = (notificationId: string, event: React.MouseEvent) => {
      event.stopPropagation();
      setDeleteConfirmation({ open: true, notificationId });
    };

    const handleDeleteConfirm = () => {
      if (deleteConfirmation.notificationId) {
        onDelete(deleteConfirmation.notificationId);
        setDeleteConfirmation({ open: false, notificationId: null });
      }
    };

    const handleDeleteCancel = () => {
      setDeleteConfirmation({ open: false, notificationId: null });
    };

    const handleMarkAllAsRead = () => {
      onMarkAsRead('all');
    };

    const renderNotificationItem = (notification: Notification) => {
      const itemContent = (
        <ListItem
          key={notification.id}
          className={!notification.isRead ? 'unread' : ''}
          sx={{
            position: 'relative',
            '&.unread': {
              backgroundColor: 'action.hover',
              borderLeft: 3,
              borderColor: 'primary.main',
            },
            '&:hover': {
              backgroundColor: 'action.selected',
            },
          }}
          secondaryAction={
            <IconButton
              edge="end"
              aria-label="delete notification"
              onClick={(e) => handleDeleteClick(notification.id, e)}
              size="small"
            >
              <DeleteIcon />
            </IconButton>
          }
        >
          <Box sx={{ width: '100%', pr: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              {notification.priority === 'URGENT' && (
                <PriorityHighIcon
                  data-testid="urgent-priority-icon"
                  color="error"
                  fontSize="small"
                />
              )}
              <Typography variant="subtitle2" component="div">
                {notification.title}
              </Typography>
            </Box>
            <ListItemText
              secondary={
                <>
                  <Typography variant="body2" color="text.secondary">
                    {notification.message}
                  </Typography>
                  <Typography
                    variant="caption"
                    color="text.disabled"
                    sx={{ display: 'block', mt: 0.5 }}
                  >
                    {formatDateTime(new Date(notification.timestamp), i18n.language)}
                  </Typography>
                </>
              }
              sx={{ mt: 0.5 }}
            />
          </Box>
        </ListItem>
      );

      if (notification.actionUrl) {
        return (
          <Link
            key={notification.id}
            to={notification.actionUrl}
            style={{ textDecoration: 'none', color: 'inherit' }}
            onClick={() => handleNotificationClick(notification)}
          >
            {itemContent}
          </Link>
        );
      }

      return (
        <div
          key={notification.id}
          onClick={() => handleNotificationClick(notification)}
          style={{ cursor: 'pointer' }}
        >
          {itemContent}
        </div>
      );
    };

    return (
      <>
        <Menu
          anchorEl={anchorEl}
          open={open}
          onClose={onClose}
          PaperProps={{
            sx: {
              width: 400,
              maxHeight: 500,
            },
          }}
          MenuListProps={{
            'aria-label': 'Notifications menu',
            role: 'menu',
          }}
        >
          {/* Header with reload button and mark all as read */}
          <Box
            sx={{
              p: 2,
              pb: 1,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <Typography variant="h6">{t('notifications.title', 'Notifications')}</Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              {unreadCount > 0 && (
                <Button size="small" onClick={handleMarkAllAsRead} variant="text">
                  {t('notifications.markAllAsRead', 'Mark all as read')}
                </Button>
              )}
              <IconButton onClick={onReload} disabled={isLoading} aria-label="reload" size="small">
                <RefreshIcon />
              </IconButton>
            </Box>
          </Box>

          {/* Screen reader announcement for unread count */}
          <Box
            role="status"
            sx={{ position: 'absolute', left: -9999, width: 1, height: 1, overflow: 'hidden' }}
          >
            {unreadCount} unread notifications
          </Box>

          <Divider />

          {/* Loading state */}
          {isLoading && (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
              <CircularProgress role="progressbar" size={32} />
            </Box>
          )}

          {/* Error state */}
          {error && !isLoading && (
            <Box sx={{ p: 2 }}>
              <Alert severity="error" sx={{ mb: 1 }}>
                {error}
              </Alert>
              <Button fullWidth variant="outlined" onClick={onReload} aria-label="retry">
                {t('notifications.retry', 'Retry')}
              </Button>
            </Box>
          )}

          {/* Empty state */}
          {!isLoading && !error && notifications.length === 0 && (
            <Box sx={{ p: 4, textAlign: 'center' }}>
              <Typography variant="body2" color="text.secondary">
                {t('notifications.empty', 'No notifications')}
              </Typography>
            </Box>
          )}

          {/* Notification list */}
          {!isLoading && !error && notifications.length > 0 && (
            <List sx={{ p: 0, maxHeight: 400, overflow: 'auto' }}>
              {notifications.map(renderNotificationItem)}
            </List>
          )}
        </Menu>

        {/* Delete confirmation dialog */}
        <Dialog
          open={deleteConfirmation.open}
          onClose={handleDeleteCancel}
          aria-labelledby="delete-confirmation-dialog"
        >
          <DialogTitle id="delete-confirmation-dialog">
            {t('notifications.deleteConfirmation.title', 'Delete Notification')}
          </DialogTitle>
          <DialogContent>
            <DialogContentText>
              {t(
                'notifications.deleteConfirmation.message',
                'Are you sure you want to delete this notification?'
              )}
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleDeleteCancel} aria-label="cancel">
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button onClick={handleDeleteConfirm} color="error" autoFocus>
              {t('common.delete', 'Delete')}
            </Button>
          </DialogActions>
        </Dialog>
      </>
    );
  }
);
