/**
 * EventForm Component
 *
 * Modal form for creating and editing events
 * - Create and edit modes with pre-filled data
 * - Form validation (date 30+ days future, deadline 7+ days before event, capacity positive)
 * - Auto-save functionality (5-second debounce, always enabled, NOT configurable)
 * - Unsaved changes warning
 * - Draft save support for create mode
 * - Partial update support (PATCH for changed fields only)
 * - Role-based access control (organizer-only editing)
 *
 * Story: 2.5.3 - Event Management Frontend
 * Acceptance Criteria: AC3 (Create), AC4 (Edit), AC20 (Auto-Save)
 * Wireframe: docs/wireframes/story-1.16-event-detail-edit.md v1.1
 */

import React, { useEffect, useState, useCallback } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormHelperText,
  Alert,
  Box,
  Typography,
  Chip,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { eventApiClient } from '@/services/eventApiClient';
import type { Event, EventUI, CreateEventRequest, PatchEventRequest } from '@/types/event.types';
import { useDebounce } from '@/hooks/useDebounce';

// Validation schema factory function (needs t function for translations)
const createEventSchema = (t: (key: string) => string) =>
  z
    .object({
      title: z
        .string()
        .min(1, t('validation.titleRequired'))
        .min(10, t('validation.titleMinLength')),
      description: z.string().min(1, t('validation.descriptionRequired')),
      date: z
        .string()
        .min(1, t('validation.eventDateRequired'))
        .refine(
          (val) => {
            const date = new Date(val);
            const minDate = new Date();
            minDate.setDate(minDate.getDate() + 30);
            return date >= minDate;
          },
          { message: t('validation.eventDateFuture') }
        ),
      registrationDeadline: z.string().optional().or(z.literal('')),
      venueName: z.string().min(1, t('validation.venueNameRequired')),
      venueAddress: z.string().min(1, t('validation.venueAddressRequired')),
      venueCapacity: z.number().positive(t('validation.capacityPositive')),
      // UI-only fields (will be stored in metadata)
      theme: z.string().optional().or(z.literal('')),
      eventType: z.enum(['full_day', 'afternoon', 'evening']).optional(),
    })
    .refine(
      (data) => {
        if (!data.registrationDeadline) return true;
        const eventDate = new Date(data.date);
        const deadline = new Date(data.registrationDeadline);
        const minDeadline = new Date(eventDate);
        minDeadline.setDate(minDeadline.getDate() - 7);
        return deadline <= minDeadline;
      },
      {
        message: t('validation.registrationDeadline'),
        path: ['registrationDeadline'],
      }
    );

type EventFormData = z.infer<ReturnType<typeof createEventSchema>>;

// Type for partial updates (all fields optional)
type PartialEventFormData = {
  [K in keyof EventFormData]?: EventFormData[K];
};

interface EventFormProps {
  open: boolean;
  mode: 'create' | 'edit';
  event?: Event;
  onClose: () => void;
  onSuccess?: () => void;
}

export const EventForm: React.FC<EventFormProps> = ({ open, mode, event, onClose, onSuccess }) => {
  const { t } = useTranslation('events');
  const { user } = useAuth();
  const [apiError, setApiError] = useState<string | null>(null);
  const [autoSaveStatus, setAutoSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>(
    'idle'
  );
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const [showUnsavedWarning, setShowUnsavedWarning] = useState(false);
  const [initialFormData, setInitialFormData] = useState<PartialEventFormData>({});

  // Check role-based access control
  const hasEditPermission = user?.role === 'organizer';

  // Create validation schema with translations
  const eventSchema = createEventSchema(t);

  const form = useForm<EventFormData>({
    resolver: zodResolver(eventSchema),
    mode: 'onBlur',
    defaultValues: event
      ? {
          title: event.title,
          description: event.description || '',
          date: event.date.split('T')[0],
          registrationDeadline: event.registrationDeadline?.split('T')[0] || '',
          venueName: event.venueName || '',
          venueAddress: event.venueAddress || '',
          venueCapacity: event.venueCapacity || 200,
          theme: (event as EventUI).theme || '',
          eventType: (event as EventUI).eventType || 'full_day',
        }
      : {
          title: '',
          description: '',
          date: '',
          registrationDeadline: '',
          venueName: '',
          venueAddress: '',
          venueCapacity: 200,
          theme: '',
          eventType: 'full_day',
        },
  });

  const {
    control,
    handleSubmit,
    formState: { errors, isDirty },
    watch,
    getValues,
  } = form;

  // Store initial form data for partial update comparison
  useEffect(() => {
    if (event) {
      setInitialFormData({
        title: event.title,
        description: event.description || '',
        date: event.date.split('T')[0],
        registrationDeadline: event.registrationDeadline?.split('T')[0] || '',
        venueName: event.venueName || '',
        venueAddress: event.venueAddress || '',
        venueCapacity: event.venueCapacity || 200,
        theme: (event as EventUI).theme || '',
        eventType: (event as EventUI).eventType || 'full_day',
      });
    }
  }, [event]);

  // Get changed fields for partial update (PATCH)
  const getChangedFields = useCallback(
    (currentData: PartialEventFormData): PartialEventFormData => {
      const changedFields: Record<string, unknown> = {};
      (Object.keys(currentData) as Array<keyof EventFormData>).forEach((key) => {
        if (currentData[key] !== initialFormData[key]) {
          changedFields[key] = currentData[key];
        }
      });
      return changedFields as PartialEventFormData;
    },
    [initialFormData]
  );

  // Auto-save functionality (5-second debounce, always enabled)
  const formValues = watch();
  const debouncedFormValues = useDebounce(formValues, 5000);

  useEffect(() => {
    if (mode === 'edit' && isDirty && event && hasEditPermission) {
      const changedFields = getChangedFields(debouncedFormValues);
      if (Object.keys(changedFields).length > 0) {
        handleAutoSave(changedFields);
      }
    }
  }, [debouncedFormValues, mode, isDirty, event, hasEditPermission]);

  const handleAutoSave = async (changedFields: PartialEventFormData) => {
    if (!event) return;

    setAutoSaveStatus('saving');
    setApiError(null);

    try {
      // Convert form data to API request format (PatchEventRequest)
      await eventApiClient.patchEvent(event.eventCode, changedFields as PatchEventRequest);
      setAutoSaveStatus('saved');
      setLastSavedAt(new Date());
      setInitialFormData(getValues()); // Update initial data after successful save
    } catch (error: unknown) {
      setAutoSaveStatus('error');
      const err = error as { response?: { status: number } };
      if (err.response?.status === 409) {
        setApiError(t('autoSave.conflict'));
      } else {
        setApiError(t('autoSave.failed'));
      }
    }
  };

  const handleCreate = async (data: EventFormData, isDraft = false) => {
    setApiError(null);

    if (!user?.username) {
      setApiError('User not authenticated');
      return;
    }

    try {
      // IMPORTANT: Backend expects organizerUsername (String) per Story 1.16.2
      // OpenAPI spec updated to use username as public identifier
      const createData: CreateEventRequest = {
        title: data.title,
        eventNumber: 0, // Backend will auto-generate the event number
        date: new Date(data.date).toISOString(),
        registrationDeadline: data.registrationDeadline
          ? new Date(data.registrationDeadline).toISOString()
          : '',
        venueName: data.venueName,
        venueAddress: data.venueAddress,
        venueCapacity: data.venueCapacity,
        status: isDraft ? 'planning' : 'topic_defined',
        organizerUsername: user.username, // Use username (e.g., "john.doe")
        currentAttendeeCount: 0,
        description: data.description || undefined,
        metadata:
          data.theme || data.eventType
            ? JSON.stringify({ theme: data.theme, eventType: data.eventType })
            : undefined,
      };

      await eventApiClient.createEvent(createData);
      onSuccess?.();
      onClose();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      setApiError(err.response?.data?.message || t('errors.saveFailed'));
    }
  };

  const handleUpdate = async (data: EventFormData) => {
    if (!event) return;

    setApiError(null);

    try {
      const changedFields = getChangedFields(data);
      if (Object.keys(changedFields).length === 0) {
        onClose();
        return;
      }

      await eventApiClient.patchEvent(event.eventCode, changedFields as PatchEventRequest);
      onSuccess?.();
      onClose();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      setApiError(err.response?.data?.message || t('errors.saveFailed'));
    }
  };

  const handleSaveClick = handleSubmit((data: EventFormData) => {
    if (mode === 'create') {
      handleCreate(data, false);
    } else {
      handleUpdate(data);
    }
  });

  const handleSaveDraftClick = () => {
    const data = getValues();
    handleCreate(data, true);
  };

  const handleCloseClick = () => {
    if (isDirty && mode === 'edit') {
      setShowUnsavedWarning(true);
    } else {
      onClose();
    }
  };

  const handleConfirmClose = () => {
    setShowUnsavedWarning(false);
    onClose();
  };

  const handleCancelClose = () => {
    setShowUnsavedWarning(false);
  };

  if (!hasEditPermission) {
    return (
      <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
        <DialogContent>
          <Alert severity="error">{t('errors.unauthorized')}</Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{t('form.cancel')}</Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <>
      <Dialog open={open} onClose={handleCloseClick} maxWidth="md" fullWidth>
        <DialogTitle>{mode === 'create' ? t('form.createEvent') : t('form.editEvent')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            {apiError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {apiError}
              </Alert>
            )}

            {mode === 'edit' && (
              <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  {t('autoSave.enabled')}
                </Typography>
                {autoSaveStatus === 'saving' && (
                  <Chip label={t('autoSave.saving')} size="small" color="info" />
                )}
                {autoSaveStatus === 'saved' && lastSavedAt && (
                  <Chip
                    label={t('autoSave.saved', {
                      time: lastSavedAt.toLocaleTimeString(),
                    })}
                    size="small"
                    color="success"
                  />
                )}
                {autoSaveStatus === 'error' && (
                  <Chip label={t('autoSave.failed')} size="small" color="error" />
                )}
              </Box>
            )}

            <Controller
              name="title"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t('form.title')}
                  fullWidth
                  error={!!errors.title}
                  helperText={errors.title?.message}
                  margin="normal"
                />
              )}
            />

            <Controller
              name="description"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t('form.description')}
                  fullWidth
                  multiline
                  rows={4}
                  error={!!errors.description}
                  helperText={errors.description?.message}
                  margin="normal"
                />
              )}
            />

            <Controller
              name="date"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t('form.eventDate')}
                  type="date"
                  fullWidth
                  error={!!errors.date}
                  helperText={errors.date?.message}
                  margin="normal"
                  InputLabelProps={{ shrink: true }}
                />
              )}
            />

            <Controller
              name="registrationDeadline"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t('form.registrationDeadline')}
                  type="date"
                  fullWidth
                  error={!!errors.registrationDeadline}
                  helperText={errors.registrationDeadline?.message}
                  margin="normal"
                  InputLabelProps={{ shrink: true }}
                />
              )}
            />

            <Controller
              name="eventType"
              control={control}
              render={({ field }) => (
                <FormControl fullWidth margin="normal" error={!!errors.eventType}>
                  <InputLabel>{t('form.eventType')}</InputLabel>
                  <Select {...field} label={t('form.eventType')}>
                    <MenuItem value="full_day">{t('form.eventTypes.fullDay')}</MenuItem>
                    <MenuItem value="afternoon">{t('form.eventTypes.afternoon')}</MenuItem>
                    <MenuItem value="evening">{t('form.eventTypes.evening')}</MenuItem>
                  </Select>
                  {errors.eventType && <FormHelperText>{errors.eventType.message}</FormHelperText>}
                </FormControl>
              )}
            />

            <Controller
              name="venueCapacity"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t('form.capacity')}
                  type="number"
                  fullWidth
                  error={!!errors.venueCapacity}
                  helperText={errors.venueCapacity?.message}
                  margin="normal"
                />
              )}
            />

            <Controller
              name="venueName"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t('form.venue')}
                  fullWidth
                  margin="normal"
                  placeholder="e.g., Kornhausforum Bern"
                />
              )}
            />

            <Controller
              name="venueAddress"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t('form.venueAddress')}
                  fullWidth
                  margin="normal"
                  placeholder="e.g., Kornhausplatz 18, 3011 Bern"
                />
              )}
            />

            <Controller
              name="theme"
              control={control}
              render={({ field }) => (
                <TextField {...field} label={t('form.theme')} fullWidth margin="normal" />
              )}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseClick}>{t('form.cancel')}</Button>
          {mode === 'create' && (
            <Button onClick={handleSaveDraftClick} color="secondary">
              {t('form.saveDraft')}
            </Button>
          )}
          <Button onClick={handleSaveClick} variant="contained" color="primary">
            {mode === 'create' ? t('form.saveCreate') : t('form.save')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Unsaved Changes Warning Dialog */}
      <Dialog open={showUnsavedWarning} onClose={handleCancelClose}>
        <DialogTitle>{t('confirmations.unsavedChanges')}</DialogTitle>
        <DialogContent>
          <Typography>{t('confirmations.unsavedChangesMessage')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCancelClose}>{t('confirmations.cancel')}</Button>
          <Button onClick={handleConfirmClose} color="error" variant="contained">
            {t('confirmations.confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
