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

import React, { useEffect, useMemo, useState } from 'react';
import { useForm, Controller, type Resolver } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { AutoAwesome, Topic as TopicIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useUpdateEvent } from '@/hooks/useEvents';
import { useFeatureFlags } from '@/hooks/useFeatureFlags';
import { topicService } from '@/services/topicService';
import type { Topic } from '@/types/topic.types';
import type { Event, EventDetailUI, EventUI } from '@/types/event.types';
import { FileUpload } from '@/components/shared/FileUpload/FileUpload';
import { EventTypeSelector } from '@/components/organizer/EventTypeSelector/EventTypeSelector';
import { AiAssistDrawer } from './AiAssistDrawer';
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
  const navigate = useNavigate();
  const { aiContentEnabled } = useFeatureFlags();
  const updateEvent = useUpdateEvent();

  const [aiDrawerOpen, setAiDrawerOpen] = useState(false);
  const [themeImageUploadId, setThemeImageUploadId] = useState<string | undefined>();
  const [topic, setTopic] = useState<Topic | null>(null);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

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
    if (themeImageUploadId) {
      (payload as Record<string, unknown>).themeImageUploadId = themeImageUploadId;
    }
    if (Object.keys(payload).length === 0) return;
    try {
      await updateEvent.mutateAsync({ eventCode, data: payload });
      setThemeImageUploadId(undefined);
      setSaved(true);
    } catch (e) {
      setSaveError(
        e instanceof Error ? e.message : t('errors.saveFailed', 'Could not save. Please try again.')
      );
    }
  });

  const dirty = isDirty || !!themeImageUploadId;

  return (
    <Stack spacing={3} data-testid="event-info-tab">
      {/* Theme image */}
      <Box>
        <Typography variant="subtitle2" gutterBottom>
          {t('form.themeImage', 'Theme image')}
        </Typography>
        <FileUpload
          currentFileUrl={event.themeImageUrl ?? undefined}
          onUploadSuccess={(data) => {
            setThemeImageUploadId(data.uploadId);
            setSaved(false);
          }}
          onFileRemove={() => setThemeImageUploadId('')}
          maxFileSize={5 * 1024 * 1024}
          allowedTypes={['image/png', 'image/jpeg', 'image/svg+xml']}
          altText={t('form.themeImageAlt', 'Event theme image')}
          removeButtonLabel={t('form.removeThemeImage', 'Remove image')}
        />
        {aiContentEnabled && event.topicCode && (
          <Button
            size="small"
            startIcon={<AutoAwesome />}
            onClick={() => setAiDrawerOpen(true)}
            sx={{ mt: 1 }}
            data-testid="info-ai-assist"
          >
            {t('eventPage.details.aiAssist', '✨ AI-generate')}
          </Button>
        )}
      </Box>

      {/* Title */}
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

      {/* Description */}
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

      {/* Topic */}
      <Box>
        <Typography variant="subtitle2" gutterBottom>
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
            onClick={() => navigate(`/organizer/topics?eventCode=${eventCode}`)}
            data-testid="info-change-topic"
          >
            {t('eventPage.details.changeTopic', 'Change topic')}
          </Button>
        </Stack>
      </Box>

      {/* When & where */}
      <Typography variant="subtitle2">
        {t('eventPage.details.whenWhere', 'When & where')}
      </Typography>
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

      <Controller
        name="eventType"
        control={control}
        render={({ field }) => (
          <EventTypeSelector value={normalizeEventType(field.value)} onChange={field.onChange} />
        )}
      />

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
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
          name="venueCapacity"
          control={control}
          render={({ field }) => (
            <TextField
              {...field}
              type="number"
              label={t('form.capacity', 'Venue capacity')}
              error={!!errors.venueCapacity}
              helperText={errors.venueCapacity?.message}
              sx={{ maxWidth: { sm: 200 } }}
              fullWidth
              inputProps={{ 'data-testid': 'info-capacity-field' }}
            />
          )}
        />
      </Stack>

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
    </Stack>
  );
};

export default EventInfoTab;
