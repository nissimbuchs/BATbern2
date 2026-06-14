/**
 * EventInfoTab (Epic 14, Story 14.F.2) — the "Info" sub-tab of Details.
 *
 * In-tab editable event identity (replaces the Phase-A read-only summary +
 * edit-modal): theme image (replace via FileUpload / ✨ AI-generate), Title,
 * Description (+ ✨ AI-generate), the selected Topic chip + "Change topic", and
 * When & where. Reuses the shared zod schema + `useUpdateEvent` (recompose, not
 * rewrite; NFR9) — saves changed fields on an explicit Save. "Change topic" is
 * an interim route nav; Story 14.F.3 swaps it for the focused overlay.
 */

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useForm, Controller, type Resolver } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  IconButton,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  AutoAwesome,
  Topic as TopicIcon,
  Delete as DeleteIcon,
  PhotoCamera as ReplaceIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useUpdateEvent } from '@/hooks/useEvents';
import { useFeatureFlags } from '@/hooks/useFeatureFlags';
import { useFileUpload } from '@/hooks/useFileUpload/useFileUpload';
import { topicService } from '@/services/topicService';
import type { Topic } from '@/types/topic.types';
import type { Event, EventDetailUI, EventUI } from '@/types/event.types';
import { EventTypeSelector } from '@/components/organizer/EventTypeSelector/EventTypeSelector';
import { AiAssistDrawer } from './AiAssistDrawer';
import { TopicSelectionOverlay } from './topicOverlay/TopicSelectionOverlay';
import {
  createEventSchema,
  normalizeEventType,
  transformDatesForApi,
  getChangedFields,
  type EventFormData,
  type PartialEventFormData,
} from '@/components/organizer/EventManagement/eventFormSchema';

interface EventInfoTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

/** Date-time ISO → YYYY-MM-DD for date inputs. */
function toDateInput(value?: string): string {
  if (!value) return '';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? '' : d.toISOString().slice(0, 10);
}

export const EventInfoTab: React.FC<EventInfoTabProps> = ({ event, eventCode }) => {
  const { t } = useTranslation('events');
  const { aiContentEnabled } = useFeatureFlags();
  const updateEvent = useUpdateEvent();

  const [aiDrawerOpen, setAiDrawerOpen] = useState(false);
  const [topicOverlayOpen, setTopicOverlayOpen] = useState(false);
  const [themeImageUploadId, setThemeImageUploadId] = useState<string | undefined>();
  const [topic, setTopic] = useState<Topic | null>(null);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Theme image: a custom full-width preview with overlay actions (AI / replace /
  // delete), driven by the shared presigned-upload hook.
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [imageRemoved, setImageRemoved] = useState(false);
  const { uploadFile, isUploading } = useFileUpload({
    uploadEndpoint: '/logos/presigned-url',
    maxFileSize: 5 * 1024 * 1024,
    allowedTypes: ['image/png', 'image/jpeg', 'image/svg+xml'],
    onUploadSuccess: (data) => {
      setThemeImageUploadId(data.uploadId);
      setPreviewUrl(data.tempFileUrl ?? null);
      setImageRemoved(false);
      setSaved(false);
    },
    onUploadError: (err) => setSaveError(err.message),
  });
  const shownImage = imageRemoved ? null : (previewUrl ?? event.themeImageUrl ?? null);

  const ev = event as EventUI;
  const initialValues = useMemo<EventFormData>(
    () => ({
      eventNumber: ev.eventNumber ?? 0,
      title: event.title ?? '',
      description: event.description ?? '',
      date: toDateInput(ev.date),
      registrationDeadline: toDateInput(ev.registrationDeadline),
      venueName: ev.venueName ?? '',
      venueAddress: ev.venueAddress ?? '',
      venueCapacity: ev.venueCapacity ?? 0,
      workflowState: ev.workflowState,
      eventType: normalizeEventType(ev.eventType),
    }),
    [event, ev]
  );

  const schema = useMemo(() => createEventSchema(t), [t]);
  const {
    control,
    handleSubmit,
    reset,
    setValue,
    formState: { errors, isDirty },
  } = useForm<EventFormData>({
    // z.coerce.number() infers `unknown` for inputs, so the resolver type needs a cast
    // (same pattern as EventForm).
    resolver: zodResolver(schema) as unknown as Resolver<EventFormData>,
    defaultValues: initialValues,
    mode: 'onBlur',
  });

  useEffect(() => {
    reset(initialValues);
  }, [initialValues, reset]);

  // Resolve the selected topic for the chip.
  useEffect(() => {
    const code = event.topicCode;
    if (!code) {
      setTopic(null);
      return;
    }
    topicService
      .getTopicById(code)
      .then(setTopic)
      .catch(() => setTopic(null));
  }, [event.topicCode]);

  const onSave = handleSubmit(async (data) => {
    setSaved(false);
    setSaveError(null);
    const changed: PartialEventFormData = getChangedFields(data, initialValues);
    const payload = transformDatesForApi(changed);
    if (imageRemoved) {
      // The PATCH endpoint clears the theme image when themeImageUploadId is blank.
      (payload as Record<string, unknown>).themeImageUploadId = '';
    } else if (themeImageUploadId) {
      (payload as Record<string, unknown>).themeImageUploadId = themeImageUploadId;
    }
    if (Object.keys(payload).length === 0) return;
    try {
      await updateEvent.mutateAsync({ eventCode, data: payload });
      setThemeImageUploadId(undefined);
      setImageRemoved(false);
      setSaved(true);
    } catch (e) {
      setSaveError(
        e instanceof Error ? e.message : t('errors.saveFailed', 'Could not save. Please try again.')
      );
    }
  });

  const dirty = isDirty || !!themeImageUploadId || imageRemoved;

  const cardSx = { p: 2 } as const;

  return (
    <Stack spacing={2} data-testid="event-info-tab">
      {/* Two columns (per prototype #p-details): identity on the left, topic + when/where on the right. */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
          gap: 2,
          alignItems: 'start',
        }}
      >
        {/* LEFT column */}
        <Stack spacing={2}>
          {/* Theme image card — full-width banner with overlay actions */}
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Box sx={{ position: 'relative' }} data-testid="info-theme-image">
              {shownImage ? (
                <Box
                  component="img"
                  src={shownImage}
                  alt={t('form.themeImageAlt', 'Event theme image')}
                  sx={{ width: '100%', height: 180, objectFit: 'cover', display: 'block' }}
                />
              ) : (
                <Box
                  onClick={() => fileInputRef.current?.click()}
                  sx={{
                    width: '100%',
                    height: 180,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    bgcolor: 'action.hover',
                    cursor: 'pointer',
                  }}
                >
                  <Typography variant="body2" color="text.secondary">
                    {isUploading
                      ? t('eventPage.details.uploadingImage', 'Uploading…')
                      : t('eventPage.details.uploadImage', 'Click to upload a theme image')}
                  </Typography>
                </Box>
              )}

              {/* Top-right: replace */}
              <Tooltip title={t('eventPage.details.replaceImage', 'Replace image')}>
                <span style={{ position: 'absolute', top: 8, right: 8 }}>
                  <IconButton
                    size="small"
                    sx={{ bgcolor: 'background.paper', '&:hover': { bgcolor: 'background.paper' } }}
                    onClick={() => fileInputRef.current?.click()}
                    disabled={isUploading}
                    data-testid="info-replace-theme"
                  >
                    <ReplaceIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>

              {/* Bottom-left: AI-generate */}
              {aiContentEnabled && (
                <Tooltip title={t('eventPage.details.aiGenerateImage', '✨ Generate (AI)')}>
                  <IconButton
                    size="small"
                    sx={{
                      position: 'absolute',
                      bottom: 8,
                      left: 8,
                      bgcolor: 'background.paper',
                      '&:hover': { bgcolor: 'background.paper' },
                    }}
                    onClick={() => setAiDrawerOpen(true)}
                    data-testid="info-ai-theme"
                  >
                    <AutoAwesome fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}

              {/* Bottom-right: delete badge */}
              {shownImage && (
                <Tooltip title={t('form.removeThemeImage', 'Remove image')}>
                  <IconButton
                    size="small"
                    color="error"
                    sx={{
                      position: 'absolute',
                      bottom: 8,
                      right: 8,
                      bgcolor: 'background.paper',
                      '&:hover': { bgcolor: 'background.paper' },
                    }}
                    onClick={() => {
                      setImageRemoved(true);
                      setPreviewUrl(null);
                      setThemeImageUploadId('');
                      setSaved(false);
                    }}
                    data-testid="info-delete-theme"
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}

              <input
                ref={fileInputRef}
                type="file"
                hidden
                accept="image/png,image/jpeg,image/svg+xml"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) void uploadFile(file);
                  e.target.value = '';
                }}
              />
            </Box>
          </Paper>

          {/* Event details card */}
          <Paper variant="outlined" sx={cardSx}>
            <Typography variant="subtitle1" gutterBottom>
              {t('eventPage.details.heading', 'Event details')}
            </Typography>
            <Stack spacing={2}>
              <Controller
                name="title"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('form.title', 'Title')}
                    fullWidth
                    error={!!errors.title}
                    helperText={errors.title?.message}
                    inputProps={{ 'data-testid': 'info-title-field' }}
                  />
                )}
              />
              <Controller
                name="description"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('form.description', 'Description')}
                    fullWidth
                    multiline
                    minRows={4}
                    error={!!errors.description}
                    helperText={errors.description?.message}
                    inputProps={{ 'data-testid': 'info-description-field' }}
                  />
                )}
              />
              {aiContentEnabled && (
                <Box>
                  <Button
                    size="small"
                    startIcon={<AutoAwesome />}
                    onClick={() => setAiDrawerOpen(true)}
                    data-testid="info-ai-assist"
                  >
                    {t(
                      'eventPage.details.aiGenerateDescription',
                      '✨ Generate description with AI'
                    )}
                  </Button>
                </Box>
              )}
            </Stack>
          </Paper>
        </Stack>

        {/* RIGHT column */}
        <Stack spacing={2}>
          {/* Topic card */}
          <Paper variant="outlined" sx={cardSx}>
            <Typography variant="subtitle1" gutterBottom>
              {t('eventPage.overview.selectedTopic', 'Selected Topic')}
            </Typography>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
              {topic || event.topicCode ? (
                <Chip
                  icon={<TopicIcon />}
                  label={topic?.title ?? event.topicCode}
                  color="primary"
                  variant="outlined"
                />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  {t('eventPage.details.noTopic', 'No topic selected yet')}
                </Typography>
              )}
              <Button
                size="small"
                variant="outlined"
                onClick={() => setTopicOverlayOpen(true)}
                data-testid="info-change-topic"
              >
                {t('eventPage.details.changeTopic', 'Change topic')}
              </Button>
            </Stack>
          </Paper>

          {/* When & where card */}
          <Paper variant="outlined" sx={cardSx}>
            <Typography variant="subtitle1" gutterBottom>
              {t('eventPage.details.whenWhere', 'When & where')}
            </Typography>
            <Stack spacing={2}>
              {/* Date + registration deadline on one line */}
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                <Controller
                  name="date"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      type="date"
                      label={t('form.eventDate', 'Event date')}
                      InputLabelProps={{ shrink: true }}
                      error={!!errors.date}
                      helperText={errors.date?.message}
                      fullWidth
                      inputProps={{ 'data-testid': 'info-date-field' }}
                    />
                  )}
                />
                <Controller
                  name="registrationDeadline"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      type="date"
                      label={t('form.registrationDeadline', 'Registration deadline')}
                      InputLabelProps={{ shrink: true }}
                      error={!!errors.registrationDeadline}
                      helperText={errors.registrationDeadline?.message}
                      fullWidth
                      inputProps={{ 'data-testid': 'info-deadline-field' }}
                    />
                  )}
                />
              </Stack>

              {/* Event type + capacity on one line */}
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="flex-start">
                <Box sx={{ flex: 1, width: '100%' }}>
                  <Controller
                    name="eventType"
                    control={control}
                    render={({ field }) => (
                      <EventTypeSelector
                        value={normalizeEventType(field.value)}
                        onChange={field.onChange}
                      />
                    )}
                  />
                </Box>
                <Controller
                  name="venueCapacity"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      type="number"
                      label={t('form.capacity', 'Venue capacity')}
                      error={!!errors.venueCapacity}
                      helperText={errors.venueCapacity?.message}
                      fullWidth
                      inputProps={{ 'data-testid': 'info-capacity-field' }}
                    />
                  )}
                />
              </Stack>

              <Controller
                name="venueName"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('form.venue', 'Venue name')}
                    error={!!errors.venueName}
                    helperText={errors.venueName?.message}
                    fullWidth
                    inputProps={{ 'data-testid': 'info-venue-name-field' }}
                  />
                )}
              />
              <Controller
                name="venueAddress"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('form.venueAddress', 'Venue address')}
                    error={!!errors.venueAddress}
                    helperText={errors.venueAddress?.message}
                    fullWidth
                    inputProps={{ 'data-testid': 'info-venue-address-field' }}
                  />
                )}
              />
            </Stack>
          </Paper>
        </Stack>
      </Box>

      {saveError && (
        <Alert severity="error" data-testid="info-save-error">
          {saveError}
        </Alert>
      )}
      {saved && (
        <Alert severity="success" onClose={() => setSaved(false)} data-testid="info-saved">
          {t('eventPage.details.saved', 'Changes saved')}
        </Alert>
      )}

      <Box>
        <Button
          variant="contained"
          onClick={onSave}
          disabled={!dirty || updateEvent.isPending}
          startIcon={updateEvent.isPending ? <CircularProgress size={16} /> : undefined}
          data-testid="info-save-button"
        >
          {t('eventPage.details.save', 'Save changes')}
        </Button>
      </Box>

      <AiAssistDrawer
        eventCode={eventCode}
        open={aiDrawerOpen}
        onClose={() => setAiDrawerOpen(false)}
        onDescriptionGenerated={(text: string) =>
          setValue('description', text, { shouldDirty: true })
        }
        onImageGenerated={() => setAiDrawerOpen(false)}
      />

      <TopicSelectionOverlay
        open={topicOverlayOpen}
        eventCode={eventCode}
        currentTopicCode={event.topicCode ?? undefined}
        onClose={() => setTopicOverlayOpen(false)}
      />
    </Stack>
  );
};

export default EventInfoTab;
