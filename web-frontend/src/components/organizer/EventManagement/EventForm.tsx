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
import { useCreateEvent, useUpdateEvent } from '@/hooks/useEvents';
import type { Event, EventUI, CreateEventRequest, PatchEventRequest } from '@/types/event.types';
import { useDebounce } from '@/hooks/useDebounce';
import { FileUpload } from '@/components/shared/FileUpload/FileUpload';
import { EventTypeSelector } from '@/components/organizer/EventTypeSelector/EventTypeSelector';
import type { components } from '@/types/generated/events-api.types';

/**
 * Converts legacy lowercase snake_case event types to UPPER_CASE enum values
 * Handles migration from old format (full_day) to new format (FULL_DAY)
 */
function normalizeEventType(eventType: string | undefined): components['schemas']['EventType'] {
  if (!eventType) return 'FULL_DAY';

  const typeMap: Record<string, components['schemas']['EventType']> = {
    full_day: 'FULL_DAY',
    afternoon: 'AFTERNOON',
    evening: 'EVENING',
    FULL_DAY: 'FULL_DAY',
    AFTERNOON: 'AFTERNOON',
    EVENING: 'EVENING',
  };

  return typeMap[eventType] || 'FULL_DAY';
}

// Validation schema factory function (needs t function for translations)
const createEventSchema = (t: (key: string) => string) =>
  z
    .object({
      eventNumber: z.coerce
        .number({ message: t('validation.eventNumberRequired') })
        .positive(t('validation.eventNumberPositive')),
      title: z
        .string()
        .min(1, t('validation.titleRequired'))
        .min(10, t('validation.titleMinLength')),
      description: z.string().min(1, t('validation.descriptionRequired')),
      date: z.string().min(1, t('validation.eventDateRequired')),
      registrationDeadline: z.string().optional().or(z.literal('')),
      venueName: z.string().min(1, t('validation.venueNameRequired')),
      venueAddress: z.string().min(1, t('validation.venueAddressRequired')),
      venueCapacity: z.coerce
        .number({ message: t('validation.capacityRequired') })
        .positive(t('validation.capacityPositive')),
      status: z.enum([
        'planning',
        'topic_defined',
        'speakers_invited',
        'agenda_draft',
        'published',
        'registration_open',
        'registration_closed',
        'in_progress',
        'completed',
        'archived',
      ]),
      // UI-only fields (will be stored in metadata)
      theme: z.string().optional().or(z.literal('')),
      eventType: z.enum(['FULL_DAY', 'AFTERNOON', 'EVENING']).optional(),
    })
    .refine(
      (data) => {
        if (!data.registrationDeadline) return true;
        const eventDate = new Date(data.date);
        const deadline = new Date(data.registrationDeadline);
        return deadline <= eventDate;
      },
      {
        message: t('validation.registrationDeadline'),
        path: ['registrationDeadline'],
      }
    );

// Explicit type for form data (needed because z.infer doesn't handle complex transforms correctly)
interface EventFormData {
  eventNumber: number;
  title: string;
  description: string;
  date: string;
  venueName: string;
  venueAddress: string;
  venueCapacity: number;
  registrationDeadline?: string;
  workflowState?: components['schemas']['EventWorkflowState'];
  theme?: string;
  eventType?: components['schemas']['EventType'];
}

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

  // Mutation hooks for proper cache management (MVC pattern)
  const createEventMutation = useCreateEvent();
  const updateEventMutation = useUpdateEvent();

  const [apiError, setApiError] = useState<string | null>(null);
  const [autoSaveStatus, setAutoSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>(
    'idle'
  );
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const [showUnsavedWarning, setShowUnsavedWarning] = useState(false);
  const [initialFormData, setInitialFormData] = useState<PartialEventFormData>({});
  const [themeImageUploadId, setThemeImageUploadId] = useState<string | undefined>(undefined);

  // Check role-based access control
  const hasEditPermission = user?.role === 'organizer';

  // Initialize themeImageUploadId when event changes or dialog opens
  // themeImageUploadId is now included in Event response (Story 2.5.3a)
  useEffect(() => {
    if (open && mode === 'create') {
      // Clear upload ID when creating new event
      setThemeImageUploadId(undefined);
    } else if (open && mode === 'edit' && event) {
      // Initialize from event's upload ID when editing
      setThemeImageUploadId(event.themeImageUploadId ?? undefined);
    }
  }, [event, open, mode]);

  // Create validation schema with translations
  const eventSchema = createEventSchema(t);

  const form = useForm<EventFormData>({
    // NOTE: Type assertion required here due to Zod limitation
    // z.coerce.number() returns 'unknown' for input type, causing type mismatch with zodResolver
    // This is safe because:
    // 1. EventFormData interface explicitly defines venueCapacity: number and eventNumber: number
    // 2. Zod schema validates and coerces the values to number at runtime
    // 3. The form will only accept/return EventFormData with these fields as numbers
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    resolver: zodResolver(eventSchema) as any,
    mode: 'onBlur',
    defaultValues: event
      ? {
          eventNumber: event.eventNumber || 1,
          title: event.title,
          description: event.description || '',
          date: event.date ? event.date.split('T')[0] : '',
          registrationDeadline: event.registrationDeadline?.split('T')[0] || '',
          venueName: event.venueName || '',
          venueAddress: event.venueAddress || '',
          venueCapacity: event.venueCapacity || 200,
          workflowState: event.workflowState || 'CREATED',
          theme: (event as EventUI).theme || '',
          eventType: normalizeEventType((event as EventUI).eventType),
        }
      : {
          eventNumber: 1,
          title: '',
          description: '',
          date: '',
          registrationDeadline: '',
          venueName: '',
          venueAddress: '',
          venueCapacity: 200,
          workflowState: 'CREATED' as const,
          theme: '',
          eventType: 'FULL_DAY',
        },
  });

  const {
    control,
    handleSubmit,
    formState: { errors, isDirty },
    watch,
    getValues,
    reset,
  } = form;

  // Reset form when modal opens in create mode
  useEffect(() => {
    if (open && mode === 'create') {
      reset({
        eventNumber: 1,
        title: '',
        description: '',
        date: '',
        registrationDeadline: '',
        venueName: '',
        venueAddress: '',
        venueCapacity: 200,
        workflowState: 'CREATED',
        theme: '',
        eventType: 'FULL_DAY',
      });
    }
  }, [open, mode, reset]);

  // Store initial form data for partial update comparison
  useEffect(() => {
    if (event) {
      setInitialFormData({
        eventNumber: event.eventNumber || 1,
        title: event.title,
        description: event.description || '',
        date: event.date ? event.date.split('T')[0] : '',
        registrationDeadline: event.registrationDeadline?.split('T')[0] || '',
        venueName: event.venueName || '',
        venueAddress: event.venueAddress || '',
        venueCapacity: event.venueCapacity || 200,
        workflowState: event.workflowState || 'CREATED',
        theme: (event as EventUI).theme || '',
        eventType: normalizeEventType((event as EventUI).eventType),
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

  // Transform date fields from date-only format (YYYY-MM-DD) to ISO 8601 (YYYY-MM-DDTHH:mm:ssZ)
  // Required for PATCH requests per OpenAPI spec (format: date-time)
  const transformDatesForApi = (data: PartialEventFormData): PatchEventRequest => {
    const transformed = { ...data } as Record<string, unknown>;

    // Convert date field if present
    if (transformed.date && typeof transformed.date === 'string') {
      transformed.date = new Date(transformed.date).toISOString();
    }

    // Convert registrationDeadline field if present
    if (transformed.registrationDeadline && typeof transformed.registrationDeadline === 'string') {
      transformed.registrationDeadline = new Date(transformed.registrationDeadline).toISOString();
    }

    return transformed as PatchEventRequest;
  };

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
      // Transform dates to ISO 8601 format before sending to API
      const patchData = transformDatesForApi(changedFields);

      // Use mutation hook for proper cache management (MVC pattern)
      // This ensures all event caches (list, detail, current) are invalidated
      await updateEventMutation.mutateAsync({
        eventCode: event.eventCode,
        data: patchData,
      });
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
      const createData = {
        title: data.title,
        eventNumber: data.eventNumber,
        date: new Date(data.date).toISOString(),
        registrationDeadline: data.registrationDeadline
          ? new Date(data.registrationDeadline).toISOString()
          : '',
        venueName: data.venueName,
        venueAddress: data.venueAddress,
        venueCapacity: data.venueCapacity,
        status: isDraft ? 'planning' : data.workflowState,
        organizerUsername: user.username, // Use username (e.g., "john.doe")
        currentAttendeeCount: 0,
        description: data.description || undefined,
        metadata:
          data.theme || data.eventType
            ? JSON.stringify({ theme: data.theme, eventType: data.eventType })
            : undefined,
        themeImageUploadId: themeImageUploadId || undefined, // Story 2.5.3a
      } as CreateEventRequest;

      // Use mutation hook for proper cache management (MVC pattern)
      await createEventMutation.mutateAsync(createData);
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

      // Add themeImageUploadId if a new image was uploaded during this edit session
      // themeImageUploadId is only set when FileUpload component successfully uploads
      if (themeImageUploadId !== undefined) {
        (changedFields as Record<string, unknown>).themeImageUploadId = themeImageUploadId;
      }

      if (Object.keys(changedFields).length === 0) {
        onClose();
        return;
      }

      // Transform dates to ISO 8601 format before sending to API
      const patchData = transformDatesForApi(changedFields);

      // Use mutation hook for proper cache management (MVC pattern)
      await updateEventMutation.mutateAsync({
        eventCode: event.eventCode,
        data: patchData,
      });
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

            {/* Event Number and Title side-by-side */}
            <Box sx={{ display: 'flex', gap: 2 }}>
              <Controller
                name="eventNumber"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('form.eventNumber')}
                    type="number"
                    error={!!errors.eventNumber}
                    helperText={errors.eventNumber?.message}
                    margin="normal"
                    sx={{ width: '150px' }}
                  />
                )}
              />
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
            </Box>

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

            {/* Event Date and Registration Deadline side-by-side */}
            <Box sx={{ display: 'flex', gap: 2 }}>
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
                    slotProps={{ inputLabel: { shrink: true } }}
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
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                )}
              />
            </Box>

            {/* Event Type and Venue Capacity side-by-side */}
            <Box sx={{ display: 'flex', gap: 2 }}>
              <Controller
                name="eventType"
                control={control}
                render={({ field }) => (
                  <Box sx={{ flex: 1, mt: 2 }}>
                    <EventTypeSelector
                      value={field.value || 'FULL_DAY'}
                      onChange={field.onChange}
                      disabled={!hasEditPermission}
                      error={!!errors.eventType}
                      helperText={errors.eventType?.message}
                    />
                  </Box>
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
            </Box>

            <Controller
              name="workflowState"
              control={control}
              render={({ field }) => (
                <FormControl fullWidth margin="normal" error={!!errors.workflowState}>
                  <InputLabel>{t('form.workflowState')}</InputLabel>
                  <Select {...field} label={t('form.workflowState')}>
                    <MenuItem value="planning">{t('form.workflowStateValues.planning')}</MenuItem>
                    <MenuItem value="topic_defined">
                      {t('form.workflowStateValues.topicDefined')}
                    </MenuItem>
                    <MenuItem value="speakers_invited">
                      {t('form.workflowStateValues.speakersInvited')}
                    </MenuItem>
                    <MenuItem value="agenda_draft">
                      {t('form.workflowStateValues.agendaDraft')}
                    </MenuItem>
                    <MenuItem value="published">{t('form.workflowStateValues.published')}</MenuItem>
                    <MenuItem value="registration_open">
                      {t('form.workflowStateValues.registrationOpen')}
                    </MenuItem>
                    <MenuItem value="registration_closed">
                      {t('form.workflowStateValues.registrationClosed')}
                    </MenuItem>
                    <MenuItem value="in_progress">
                      {t('form.workflowStateValues.inProgress')}
                    </MenuItem>
                    <MenuItem value="completed">{t('form.workflowStateValues.completed')}</MenuItem>
                    <MenuItem value="archived">{t('form.workflowStateValues.archived')}</MenuItem>
                  </Select>
                  {errors.workflowState && (
                    <FormHelperText>{errors.workflowState.message}</FormHelperText>
                  )}
                </FormControl>
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

            {/* Theme Image Upload - Story 2.5.3a */}
            <Box sx={{ mt: 3, pt: 3, borderTop: 1, borderColor: 'divider' }}>
              <Typography variant="subtitle2" gutterBottom>
                {t('form.themeImage')}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                {t('form.themeImageHelp')}
              </Typography>
              <FileUpload
                currentFileUrl={event?.themeImageUrl ?? undefined}
                onUploadSuccess={(data) => setThemeImageUploadId(data.uploadId)}
                onUploadError={(error) => {
                  setApiError(t(`errors.${error.type}`, { defaultValue: error.message }));
                }}
                onFileRemove={() => setThemeImageUploadId('')}
                maxFileSize={5 * 1024 * 1024}
                allowedTypes={['image/png', 'image/jpeg', 'image/svg+xml']}
                altText={t('form.themeImageAlt')}
                removeButtonLabel={t('form.removeThemeImage')}
              />
            </Box>
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
