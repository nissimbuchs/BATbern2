/**
 * EventSettingsTab Component (Story 5.6)
 *
 * Event settings, notifications, and danger zone
 */

import React, { useRef, useState } from 'react';
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
  TextField,
  CircularProgress,
} from '@mui/material';
import {
  Notifications as NotificationsIcon,
  Edit as EditIcon,
  Warning as WarningIcon,
  Delete as DeleteIcon,
  Cancel as CancelIcon,
  Person as PersonIcon,
  Group as GroupIcon,
  Image as ImageIcon,
  AddPhotoAlternate as AddPhotoAlternateIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import { useDeleteEvent, useUpdateEvent } from '@/hooks/useEvents';
import { useUploadTeaserImage, useDeleteTeaserImage } from '@/hooks/useEventTeaserImages';
import type { Event, EventDetailUI } from '@/types/event.types';
import type { components } from '@/types/generated/events-api.types';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect/OrganizerSelect';

type TeaserImageItem = components['schemas']['TeaserImageItem'];

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

  // AC6 (Story 10.11): Registration Capacity field
  const [capacityValue, setCapacityValue] = useState<string>(
    event.registrationCapacity?.toString() ?? ''
  );
  const [capacityError, setCapacityError] = useState<string | null>(null);
  const [capacitySuccess, setCapacitySuccess] = useState(false);
  const isArchived = event.workflowState === 'ARCHIVED';

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Teaser images (Story 10.22)
  const teaserImages: TeaserImageItem[] =
    (event as Event & { teaserImages?: TeaserImageItem[] }).teaserImages ?? [];
  const MAX_TEASER_IMAGES = 10;
  const uploadTeaserImageMutation = useUploadTeaserImage(eventCode);
  const deleteTeaserImageMutation = useDeleteTeaserImage(eventCode);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [teaserUploadError, setTeaserUploadError] = useState<string | null>(null);

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

  const handleCapacitySave = async () => {
    setCapacityError(null);
    const parsed = capacityValue.trim() === '' ? null : parseInt(capacityValue, 10);
    if (parsed !== null && (isNaN(parsed) || parsed <= 0)) {
      setCapacityError(t('create.form.capacityPositive', 'Capacity must be a positive number'));
      return;
    }
    try {
      await updateEventMutation.mutateAsync({
        eventCode,
        data: { registrationCapacity: parsed },
      });
      setCapacitySuccess(true);
    } catch (error) {
      setCapacityError(
        error instanceof Error
          ? error.message
          : t('eventPage.settings.capacityUpdateError', 'Failed to update capacity.')
      );
    }
  };

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

  const handleTeaserImageFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';
    setTeaserUploadError(null);
    try {
      await uploadTeaserImageMutation.mutateAsync({
        file,
        request: { contentType: file.type, fileName: file.name },
      });
    } catch (err) {
      setTeaserUploadError(
        err instanceof Error
          ? err.message
          : t('teaserImage.uploadError', 'Upload failed. Please try again.')
      );
    }
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

      {/* Registration Capacity (AC6 — Story 10.11) */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" spacing={1} alignItems="center" mb={2}>
          <GroupIcon color="action" />
          <Typography variant="h6">
            {t('eventPage.settings.capacityLabel', 'Registration Capacity')}
          </Typography>
        </Stack>
        <Divider sx={{ mb: 2 }} />
        <Stack spacing={2}>
          <TextField
            label={t('eventPage.settings.capacityLabel', 'Registration Capacity')}
            helperText={t(
              'eventPage.settings.capacityHelperText',
              'Leave blank for unlimited registrations. Cannot exceed venue capacity.'
            )}
            type="number"
            value={capacityValue}
            onChange={(e) => setCapacityValue(e.target.value)}
            disabled={isArchived || updateEventMutation.isPending}
            inputProps={{ min: 1 }}
            sx={{ maxWidth: 300 }}
            data-testid="registration-capacity-field"
            error={!!capacityError}
          />
          {capacityError && (
            <Alert severity="error" onClose={() => setCapacityError(null)}>
              {capacityError}
            </Alert>
          )}
          <Box>
            <Button
              variant="contained"
              onClick={handleCapacitySave}
              disabled={isArchived || updateEventMutation.isPending}
              data-testid="registration-capacity-save-btn"
            >
              {t('common:actions.save', 'Save')}
            </Button>
          </Box>
        </Stack>
      </Paper>

      <Snackbar
        open={capacitySuccess}
        autoHideDuration={3000}
        onClose={() => setCapacitySuccess(false)}
        message={t('eventPage.settings.capacityUpdated', 'Capacity updated successfully')}
      />

      {/* Teaser Images (Story 10.22) */}
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" spacing={1} alignItems="center" mb={2}>
          <ImageIcon color="action" />
          <Typography variant="h6">{t('teaserImage.title', 'Teaser Images')}</Typography>
          <Chip
            label={`${teaserImages.length} / ${MAX_TEASER_IMAGES}`}
            size="small"
            color={teaserImages.length >= MAX_TEASER_IMAGES ? 'error' : 'default'}
            variant="outlined"
          />
        </Stack>
        <Divider sx={{ mb: 2 }} />

        <Typography variant="body2" color="text.secondary" mb={2}>
          {t(
            'teaserImage.description',
            'Images shown as full-screen slides on the presentation page between topic reveal and agenda preview.'
          )}
        </Typography>

        {teaserUploadError && (
          <Alert severity="error" onClose={() => setTeaserUploadError(null)} sx={{ mb: 2 }}>
            {teaserUploadError}
          </Alert>
        )}

        {/* Gallery */}
        {teaserImages.length > 0 && (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))',
              gap: 1.5,
              mb: 2,
            }}
          >
            {[...teaserImages]
              .sort((a, b) => a.displayOrder - b.displayOrder)
              .map((img) => (
                <Box
                  key={img.id}
                  sx={{
                    position: 'relative',
                    borderRadius: 1,
                    overflow: 'hidden',
                    aspectRatio: '16/9',
                    bgcolor: 'grey.100',
                  }}
                >
                  <img
                    src={img.imageUrl}
                    alt=""
                    style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                  />
                  <IconButton
                    size="small"
                    onClick={() => deleteTeaserImageMutation.mutate(img.id)}
                    disabled={deleteTeaserImageMutation.isPending}
                    sx={{
                      position: 'absolute',
                      top: 4,
                      right: 4,
                      bgcolor: 'rgba(0,0,0,0.55)',
                      color: '#fff',
                      '&:hover': { bgcolor: 'rgba(200,0,0,0.75)' },
                    }}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Box>
              ))}
          </Box>
        )}

        {/* Upload button */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          style={{ display: 'none' }}
          onChange={handleTeaserImageFileChange}
        />
        <Button
          variant="outlined"
          startIcon={
            uploadTeaserImageMutation.isPending ? (
              <CircularProgress size={16} />
            ) : (
              <AddPhotoAlternateIcon />
            )
          }
          disabled={
            isArchived ||
            teaserImages.length >= MAX_TEASER_IMAGES ||
            uploadTeaserImageMutation.isPending
          }
          onClick={() => fileInputRef.current?.click()}
          data-testid="teaser-image-upload-btn"
        >
          {uploadTeaserImageMutation.isPending
            ? t('teaserImage.uploading', 'Uploading...')
            : t('teaserImage.uploadButton', 'Add Teaser Image')}
        </Button>

        {teaserImages.length >= MAX_TEASER_IMAGES && (
          <Typography variant="caption" color="error" sx={{ display: 'block', mt: 1 }}>
            {t('teaserImage.limitReached', 'Maximum of {{max}} teaser images reached.', {
              max: MAX_TEASER_IMAGES,
            })}
          </Typography>
        )}
      </Paper>

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
          <Button onClick={() => setCancelDialogOpen(false)}>{t('common:actions.cancel')}</Button>
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
            {t('common:actions.cancel')}
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
