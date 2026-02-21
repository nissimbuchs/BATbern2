/**
 * EventSettingsTab Component (Story 5.6)
 *
 * Event settings, notifications, and danger zone
 */

import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  Stack,
  Divider,
  Switch,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  Alert,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  IconButton,
  Snackbar,
} from '@mui/material';
import {
  Notifications as NotificationsIcon,
  Edit as EditIcon,
  Warning as WarningIcon,
  Delete as DeleteIcon,
  Cancel as CancelIcon,
  Person as PersonIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import { useDeleteEvent, useUpdateEvent } from '@/hooks/useEvents';
import type { Event, EventDetailUI } from '@/types/event.types';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect/OrganizerSelect';

interface EventSettingsTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

interface NotificationRule {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  scheduledDate?: string;
}

export const EventSettingsTab: React.FC<EventSettingsTabProps> = ({ event, eventCode }) => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();
  const deleteEventMutation = useDeleteEvent();

  const updateEventMutation = useUpdateEvent();
  const [selectedOrganizer, setSelectedOrganizer] = useState(event.organizerUsername);
  const [moderatorUpdateError, setModeratorUpdateError] = useState<string | null>(null);
  const [moderatorUpdateSuccess, setModeratorUpdateSuccess] = useState(false);

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // ⚠️ MOCK DATA - Notification rules (backend integration pending)
  const [notifications, setNotifications] = useState<NotificationRule[]>([
    {
      id: '1',
      name: t('eventPage.settings.speakerReminders', 'Speaker deadline reminders'),
      description: t('eventPage.settings.speakerRemindersDesc', '3 days before deadline'),
      enabled: true,
    },
    {
      id: '2',
      name: t('eventPage.settings.registrationEmails', 'Registration confirmation emails'),
      description: t('eventPage.settings.registrationEmailsDesc', 'Immediate on registration'),
      enabled: true,
    },
    {
      id: '3',
      name: t('eventPage.settings.agendaDistribution', 'Final agenda distribution'),
      description: t('eventPage.settings.agendaDistributionDesc', 'Scheduled for event day -14'),
      enabled: true,
      scheduledDate: '2025-03-01',
    },
    {
      id: '4',
      name: t('eventPage.settings.checkInReminders', 'Event day check-in reminders'),
      description: t('eventPage.settings.checkInRemindersDesc', 'Morning of event'),
      enabled: true,
      scheduledDate: '2025-03-15',
    },
  ]);

  const handleOrganizerChange = async (newOrganizer: string) => {
    const previous = selectedOrganizer;
    setSelectedOrganizer(newOrganizer);
    setModeratorUpdateError(null);
    try {
      await updateEventMutation.mutateAsync({
        eventCode,
        data: { organizerUsername: newOrganizer },
      });
      setModeratorUpdateSuccess(true);
    } catch (error) {
      setSelectedOrganizer(previous);
      setModeratorUpdateError(
        error instanceof Error
          ? error.message
          : t('eventPage.settings.moderatorUpdateError', 'Failed to update moderator.')
      );
    }
  };

  const handleToggleNotification = (id: string) => {
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, enabled: !n.enabled } : n)));
  };

  const handleDeleteEvent = async () => {
    try {
      setDeleteError(null);
      await deleteEventMutation.mutateAsync(eventCode);
      setDeleteDialogOpen(false);
      navigate('/organizer/events');
    } catch (error) {
      console.error('Failed to delete event:', error);
      setDeleteError(
        error instanceof Error
          ? error.message
          : t('eventPage.settings.deleteError', 'Failed to delete event. Please try again.')
      );
    }
  };

  const handleCancelEvent = () => {
    console.log('Cancel event:', eventCode);
    setCancelDialogOpen(false);
  };

  return (
    <Stack spacing={3}>
      {/* Event Moderator */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" spacing={1} alignItems="center" mb={2}>
          <PersonIcon color="action" />
          <Typography variant="h6">
            {t('eventPage.settings.moderator', 'Event Moderator')}
          </Typography>
        </Stack>
        <Divider sx={{ mb: 2 }} />
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {t(
              'eventPage.settings.moderatorDesc',
              'The organizer responsible for moderating this event.'
            )}
          </Typography>
          <OrganizerSelect
            value={selectedOrganizer}
            onChange={handleOrganizerChange}
            label={t('eventPage.settings.moderatorLabel', 'Moderator')}
            includeUnassigned={false}
            includeAllOption={false}
            disabled={updateEventMutation.isPending}
            size="medium"
            sx={{ maxWidth: 400 }}
            data-testid="moderator-select"
          />
          {moderatorUpdateError && (
            <Alert severity="error" onClose={() => setModeratorUpdateError(null)}>
              {moderatorUpdateError}
            </Alert>
          )}
        </Stack>
      </Paper>

      <Snackbar
        open={moderatorUpdateSuccess}
        autoHideDuration={3000}
        onClose={() => setModeratorUpdateSuccess(false)}
        message={t('eventPage.settings.moderatorUpdated', 'Moderator updated successfully')}
      />

      {/* Notifications */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <NotificationsIcon color="action" />
            <Typography variant="h6">
              {t('eventPage.settings.notifications', 'Notifications')}
            </Typography>
            <Chip label="MOCK DATA" size="small" color="warning" variant="outlined" />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {notifications.filter((n) => n.enabled).length}{' '}
            {t('eventPage.settings.activeAutomations', 'active automations')}
          </Typography>
        </Stack>
        <Divider sx={{ mb: 2 }} />
        <Alert severity="info" sx={{ mb: 2 }}>
          ⚠️ This is mock notification data. Backend integration pending.
        </Alert>

        <List disablePadding>
          {notifications.map((rule) => (
            <ListItem
              key={rule.id}
              sx={{
                borderBottom: '1px solid',
                borderColor: 'divider',
                py: 1.5,
              }}
            >
              <ListItemText
                primary={rule.name}
                secondary={
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="caption" color="text.secondary">
                      {rule.description}
                    </Typography>
                    {rule.scheduledDate && (
                      <Typography variant="caption" color="primary">
                        • {format(new Date(rule.scheduledDate), 'MMM d, yyyy')}
                      </Typography>
                    )}
                  </Stack>
                }
              />
              <ListItemSecondaryAction>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Switch
                    checked={rule.enabled}
                    onChange={() => handleToggleNotification(rule.id)}
                    size="small"
                  />
                  <IconButton size="small">
                    <EditIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </ListItemSecondaryAction>
            </ListItem>
          ))}
        </List>

        <Button variant="text" sx={{ mt: 2 }}>
          {t('eventPage.settings.manageAll', 'Manage All Notifications')}
        </Button>
      </Paper>

      {/* Danger Zone */}
      <Paper sx={{ p: 3, borderColor: 'error.main', borderWidth: 1, borderStyle: 'solid' }}>
        <Stack direction="row" spacing={1} alignItems="center" mb={2}>
          <WarningIcon color="error" />
          <Typography variant="h6" color="error">
            {t('eventPage.settings.dangerZone', 'Danger Zone')}
          </Typography>
        </Stack>
        <Divider sx={{ mb: 2 }} />

        <Alert severity="warning" sx={{ mb: 3 }}>
          {t(
            'eventPage.settings.dangerWarning',
            'These actions are irreversible. Please proceed with caution.'
          )}
        </Alert>

        <Stack spacing={2}>
          {/* Cancel Event */}
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'stretch', sm: 'center' }}
            spacing={2}
            sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 1 }}
          >
            <Box>
              <Typography variant="body1" fontWeight="medium">
                {t('eventPage.settings.cancelEvent', 'Cancel Event')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {t(
                  'eventPage.settings.cancelEventDesc',
                  'Cancels the event and notifies all registrants'
                )}
              </Typography>
            </Box>
            <Button
              variant="outlined"
              color="warning"
              startIcon={<CancelIcon />}
              onClick={() => setCancelDialogOpen(true)}
            >
              {t('eventPage.settings.cancelEvent', 'Cancel Event')}
            </Button>
          </Stack>

          {/* Delete Event */}
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'stretch', sm: 'center' }}
            spacing={2}
            sx={{ p: 2, bgcolor: 'error.lighter', borderRadius: 1 }}
          >
            <Box>
              <Typography variant="body1" fontWeight="medium">
                {t('eventPage.settings.deleteEvent', 'Delete Event')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {t(
                  'eventPage.settings.deleteEventDesc',
                  'Permanently removes event (only if no registrations)'
                )}
              </Typography>
            </Box>
            <Button
              variant="contained"
              color="error"
              startIcon={<DeleteIcon />}
              onClick={() => setDeleteDialogOpen(true)}
              disabled={(event.currentAttendeeCount || 0) > 0}
            >
              {t('eventPage.settings.deleteEvent', 'Delete Event')}
            </Button>
          </Stack>

          {(event.currentAttendeeCount || 0) > 0 && (
            <Alert severity="info">
              {t(
                'eventPage.settings.cannotDelete',
                'Cannot delete event with registrations. Cancel the event instead.'
              )}
            </Alert>
          )}
        </Stack>
      </Paper>

      {/* Cancel Confirmation Dialog */}
      <Dialog open={cancelDialogOpen} onClose={() => setCancelDialogOpen(false)}>
        <DialogTitle>{t('eventPage.settings.confirmCancel', 'Cancel Event?')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t(
              'eventPage.settings.cancelConfirmText',
              'This will cancel the event and notify all {count} registered attendees. This action cannot be undone.'
            ).replace('{count}', String(event.currentAttendeeCount || 0))}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCancelDialogOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
          <Button onClick={handleCancelEvent} color="warning" variant="contained">
            {t('eventPage.settings.confirmCancelBtn', 'Yes, Cancel Event')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteDialogOpen}
        onClose={() => !deleteEventMutation.isPending && setDeleteDialogOpen(false)}
      >
        <DialogTitle color="error">
          {t('eventPage.settings.confirmDelete', 'Delete Event?')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t(
              'eventPage.settings.deleteConfirmText',
              'This will permanently delete the event "{title}". This action cannot be undone.'
            ).replace('{title}', event.title)}
          </DialogContentText>
          {deleteError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {deleteError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setDeleteDialogOpen(false)}
            disabled={deleteEventMutation.isPending}
          >
            {t('common.cancel', 'Cancel')}
          </Button>
          <Button
            onClick={handleDeleteEvent}
            color="error"
            variant="contained"
            disabled={deleteEventMutation.isPending}
          >
            {deleteEventMutation.isPending
              ? t('common.deleting', 'Deleting...')
              : t('eventPage.settings.confirmDeleteBtn', 'Yes, Delete Event')}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};

export default EventSettingsTab;
