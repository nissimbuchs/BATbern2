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
  Checkbox,
  FormControlLabel,
  Tabs,
  Tab,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { useCreateEvent, useUpdateEvent } from '@/hooks/useEvents';
import type { Event, EventUI, CreateEventRequest, PatchEventRequest } from '@/types/event.types';
import { useDebounce } from '@/hooks/useDebounce';
import { FileUpload } from '@/components/shared/FileUpload/FileUpload';
import { EventTypeSelector } from '@/components/organizer/EventTypeSelector/EventTypeSelector';
import type { components } from '@/types/generated/events-api.types';
import { workflowService } from '@/services/workflowService';
import { useQueryClient } from '@tanstack/react-query';
import { EventTasksTab } from '../Tasks/EventTasksTab';
import type { EventTaskResponse } from '@/services/taskService';

// mapWorkflowStateToStatus function removed - no longer needed
// Backend now uses workflowState directly (Phase 1-2 migration complete)

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
      workflowState: z.enum([
        'CREATED',
        'TOPIC_SELECTION',
        'SPEAKER_BRAINSTORMING',
        'SPEAKER_OUTREACH',
        'SPEAKER_CONFIRMATION',
        'CONTENT_COLLECTION',
        'QUALITY_REVIEW',
        'THRESHOLD_CHECK',
        'OVERFLOW_MANAGEMENT',
        'SLOT_ASSIGNMENT',
        'AGENDA_PUBLISHED',
        'AGENDA_FINALIZED',
        'NEWSLETTER_SENT',
        'EVENT_READY',
        'PARTNER_MEETING_COMPLETE',
        'ARCHIVED',
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
  const queryClient = useQueryClient();

  const [apiError, setApiError] = useState<string | null>(null);
  const [autoSaveStatus, setAutoSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>(
    'idle'
  );
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const [showUnsavedWarning, setShowUnsavedWarning] = useState(false);
  const [initialFormData, setInitialFormData] = useState<PartialEventFormData>({});
  const [themeImageUploadId, setThemeImageUploadId] = useState<string | undefined>(undefined);
  const [overrideValidation, setOverrideValidation] = useState(false);
  const [overrideReason, setOverrideReason] = useState('');
  const [currentTab, setCurrentTab] = useState(0);
  const [selectedTemplates, setSelectedTemplates] = useState<string[]>([]);
  const [templateAssignees, setTemplateAssignees] = useState<Record<string, string>>({});
  const [customTasks, setCustomTasks] = useState<EventTaskResponse[]>([]);

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
      // CRITICAL: Exclude workflowState from auto-save - workflow transitions are critical operations
      // that should ONLY happen on explicit user action (Save button), not via auto-save.
      // Auto-saving workflow transitions causes:
      // 1. Race conditions with manual save → optimistic locking conflicts (500 errors)
      // 2. Unintended state transitions without user confirmation
      // 3. Loss of override checkbox state context
      const patchFields = { ...changedFields };
      delete (patchFields as Partial<Record<string, unknown>>).workflowState;

      // Only proceed if there are fields to update (besides workflowState)
      if (Object.keys(patchFields).length === 0) {
        // No fields to auto-save (only workflowState changed, which we exclude)
        setAutoSaveStatus('saved');
        return;
      }

      // Update event fields via PATCH API
      const patchData = transformDatesForApi(patchFields);
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
      const createData: CreateEventRequest = {
        title: data.title,
        eventNumber: data.eventNumber,
        date: new Date(data.date).toISOString(),
        registrationDeadline: data.registrationDeadline
          ? new Date(data.registrationDeadline).toISOString()
          : '',
        venueName: data.venueName,
        venueAddress: data.venueAddress,
        venueCapacity: data.venueCapacity,
        workflowState: isDraft ? 'CREATED' : data.workflowState,
        eventType: data.eventType || 'FULL_DAY', // Required field with default
        organizerUsername: user.username, // Use username (e.g., "john.doe")
        currentAttendeeCount: 0,
        description: data.description || undefined,
        metadata: data.theme ? JSON.stringify({ theme: data.theme }) : undefined,
        themeImageUploadId: themeImageUploadId || undefined, // Story 2.5.3a
      };

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

      // Check if workflowState changed - handle separately via workflow transition API
      const workflowStateChanged = 'workflowState' in changedFields;
      const newWorkflowState = changedFields.workflowState as
        | components['schemas']['EventWorkflowState']
        | undefined;

      // Remove workflowState from patch data - it goes through separate API
      const patchFields = { ...changedFields };
      delete (patchFields as Partial<Record<string, unknown>>).workflowState;

      // Update event fields (if any changed besides workflowState)
      if (Object.keys(patchFields).length > 0) {
        const patchData = transformDatesForApi(patchFields);
        await updateEventMutation.mutateAsync({
          eventCode: event.eventCode,
          data: patchData,
        });
      }

      // Handle workflow state transition separately via workflow transition API
      if (workflowStateChanged && newWorkflowState) {
        await workflowService.transitionWorkflowState(
          event.eventCode,
          newWorkflowState,
          overrideValidation,
          overrideReason || undefined
        );

        // Invalidate React Query caches to reflect workflow state change in UI
        queryClient.invalidateQueries({ queryKey: ['events'] }); // List caches
        queryClient.invalidateQueries({ queryKey: ['event', event.eventCode] }); // Detail caches
        queryClient.invalidateQueries({ queryKey: ['eventWorkflow', event.eventCode] }); // Workflow cache
        queryClient.invalidateQueries({ queryKey: ['events', 'current'] }); // Current event cache
      }

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

        {/* Tabs - Story 5.5 Phase 6 */}
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={currentTab} onChange={(_, newValue) => setCurrentTab(newValue)}>
            <Tab label={t('tabs.info', 'Info')} />
            <Tab label={t('tabs.tasks', 'Tasks')} disabled={mode === 'create'} />
          </Tabs>
        </Box>

        <DialogContent>
          {/* Tab Panel 0: Info (Event Form) */}
          {currentTab === 0 && (
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
                    <InputLabel>{t('form.status')}</InputLabel>
                    <Select {...field} label={t('form.status')}>
                      <MenuItem value="CREATED">{t('workflow.states.created')}</MenuItem>
                      <MenuItem value="TOPIC_SELECTION">
                        {t('workflow.states.topic_selection')}
                      </MenuItem>
                      <MenuItem value="SPEAKER_BRAINSTORMING">
                        {t('workflow.states.speaker_brainstorming')}
                      </MenuItem>
                      <MenuItem value="SPEAKER_OUTREACH">
                        {t('workflow.states.speaker_outreach')}
                      </MenuItem>
                      <MenuItem value="SPEAKER_CONFIRMATION">
                        {t('workflow.states.speaker_confirmation')}
                      </MenuItem>
                      <MenuItem value="CONTENT_COLLECTION">
                        {t('workflow.states.content_collection')}
                      </MenuItem>
                      <MenuItem value="QUALITY_REVIEW">
                        {t('workflow.states.quality_review')}
                      </MenuItem>
                      <MenuItem value="THRESHOLD_CHECK">
                        {t('workflow.states.threshold_check')}
                      </MenuItem>
                      <MenuItem value="OVERFLOW_MANAGEMENT">
                        {t('workflow.states.overflow_management')}
                      </MenuItem>
                      <MenuItem value="SLOT_ASSIGNMENT">
                        {t('workflow.states.slot_assignment')}
                      </MenuItem>
                      <MenuItem value="AGENDA_PUBLISHED">
                        {t('workflow.states.agenda_published')}
                      </MenuItem>
                      <MenuItem value="AGENDA_FINALIZED">
                        {t('workflow.states.agenda_finalized')}
                      </MenuItem>
                      <MenuItem value="NEWSLETTER_SENT">
                        {t('workflow.states.newsletter_sent')}
                      </MenuItem>
                      <MenuItem value="EVENT_READY">{t('workflow.states.event_ready')}</MenuItem>
                      <MenuItem value="PARTNER_MEETING_COMPLETE">
                        {t('workflow.states.partner_meeting_complete')}
                      </MenuItem>
                      <MenuItem value="ARCHIVED">{t('workflow.states.archived')}</MenuItem>
                    </Select>
                    {errors.workflowState && (
                      <FormHelperText>{errors.workflowState.message}</FormHelperText>
                    )}
                  </FormControl>
                )}
              />

              {/* Override Workflow Validation Checkbox */}
              <Box
                sx={{
                  mt: 2,
                  p: 2,
                  bgcolor: 'warning.light',
                  borderRadius: 1,
                  border: '1px solid',
                  borderColor: 'warning.main',
                }}
              >
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={overrideValidation}
                      onChange={(e) => setOverrideValidation(e.target.checked)}
                    />
                  }
                  label="Override workflow validation (allows any state transition)"
                />

                {overrideValidation && (
                  <TextField
                    fullWidth
                    multiline
                    rows={2}
                    label="Override Reason (Optional)"
                    value={overrideReason}
                    onChange={(e) => setOverrideReason(e.target.value)}
                    placeholder="Why are you overriding workflow validation?"
                    sx={{ mt: 1 }}
                    helperText="This will be logged for audit purposes"
                  />
                )}
              </Box>

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
          )}

          {/* Tab Panel 1: Tasks (EventTasksTab) - Story 5.5 Phase 6 */}
          {currentTab === 1 && mode === 'edit' && (
            <Box sx={{ pt: 2 }}>
              <EventTasksTab
                eventId={event?.eventCode || null}
                organizerUsername={user?.username || ''}
                selectedTemplates={selectedTemplates}
                templateAssignees={templateAssignees}
                customTasks={customTasks}
                onTemplateToggle={(templateId, checked) => {
                  if (checked) {
                    setSelectedTemplates([...selectedTemplates, templateId]);
                  } else {
                    setSelectedTemplates(selectedTemplates.filter((id) => id !== templateId));
                  }
                }}
                onAssigneeChange={(templateId, assignee) => {
                  setTemplateAssignees({ ...templateAssignees, [templateId]: assignee });
                }}
                onAddCustomTask={() => {
                  // Custom task modal handled internally by EventTasksTab
                }}
                onEditCustomTask={(task) => {
                  // Edit modal handled internally by EventTasksTab
                  console.log('Edit task:', task);
                }}
                onDeleteCustomTask={(taskId) => {
                  setCustomTasks(customTasks.filter((t) => t.id !== taskId));
                }}
              />
            </Box>
          )}
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
