/**
 * AutoPublishScheduleForm (Story 4.9b)
 *
 * The auto-publish schedule of an event, part of the publishing resource
 * (`GET`/`PATCH /events/{eventCode}/publishing`): whether and how many days before the event the
 * daily job publishes the speakers and the agenda. Without a stored schedule EMS answers the
 * defaults (30 and 14 days, both on). Saving sends only the fields that changed.
 */

import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { usePublishing } from '@/hooks/usePublishing/usePublishing';
import type { PatchPublishingRequest, PublishingStatusResponse } from '@/types/event.types';
import { formatDate } from '@/utils/date/dateFormat';

export interface AutoPublishScheduleFormProps {
  eventCode: string;
}

interface ScheduleDraft {
  phase2Enabled: boolean;
  phase2DaysBeforeEvent: string;
  phase3Enabled: boolean;
  phase3DaysBeforeEvent: string;
}

const MIN_DAYS = 1;
const MAX_DAYS = 365;

const draftOf = (status: PublishingStatusResponse): ScheduleDraft => ({
  phase2Enabled: status.phase2Enabled,
  phase2DaysBeforeEvent: String(status.phase2DaysBeforeEvent),
  phase3Enabled: status.phase3Enabled,
  phase3DaysBeforeEvent: String(status.phase3DaysBeforeEvent),
});

const validDays = (value: string): boolean => {
  const days = Number(value);
  return Number.isInteger(days) && days >= MIN_DAYS && days <= MAX_DAYS;
};

export const AutoPublishScheduleForm: React.FC<AutoPublishScheduleFormProps> = ({ eventCode }) => {
  const { t, i18n } = useTranslation('events');
  const locale = i18n.language === 'de' ? 'de' : 'en';
  const { publishingStatus, updateSchedule, isScheduling } = usePublishing(eventCode);
  const [draft, setDraft] = useState<ScheduleDraft | null>(null);
  const [saved, setSaved] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (publishingStatus) {
      setDraft(draftOf(publishingStatus));
    }
  }, [publishingStatus]);

  const changes = useMemo<PatchPublishingRequest>(() => {
    if (!publishingStatus || !draft) return {};
    const request: PatchPublishingRequest = {};
    if (draft.phase2Enabled !== publishingStatus.phase2Enabled) {
      request.phase2Enabled = draft.phase2Enabled;
    }
    if (Number(draft.phase2DaysBeforeEvent) !== publishingStatus.phase2DaysBeforeEvent) {
      request.phase2DaysBeforeEvent = Number(draft.phase2DaysBeforeEvent);
    }
    if (draft.phase3Enabled !== publishingStatus.phase3Enabled) {
      request.phase3Enabled = draft.phase3Enabled;
    }
    if (Number(draft.phase3DaysBeforeEvent) !== publishingStatus.phase3DaysBeforeEvent) {
      request.phase3DaysBeforeEvent = Number(draft.phase3DaysBeforeEvent);
    }
    return request;
  }, [draft, publishingStatus]);

  if (!publishingStatus || !draft) {
    return null;
  }

  const daysValid =
    validDays(draft.phase2DaysBeforeEvent) && validDays(draft.phase3DaysBeforeEvent);
  const unchanged = Object.keys(changes).length === 0;

  const handleSave = async () => {
    setSaved(false);
    setFailed(false);
    try {
      await updateSchedule(changes);
      setSaved(true);
    } catch {
      setFailed(true);
    }
  };

  const edit = (patch: Partial<ScheduleDraft>) => {
    setSaved(false);
    setDraft({ ...draft, ...patch });
  };

  const phaseRow = (
    phase: 'speakers' | 'agenda',
    enabled: boolean,
    days: string,
    triggerDate: string,
    onEnabled: (value: boolean) => void,
    onDays: (value: string) => void
  ) => (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      alignItems={{ xs: 'flex-start', sm: 'center' }}
      data-testid={`auto-publish-schedule-${phase}`}
    >
      <FormControlLabel
        sx={{ minWidth: 220 }}
        control={
          <Switch
            checked={enabled}
            onChange={(e) => onEnabled(e.target.checked)}
            inputProps={{ 'aria-label': t(`publishing.schedule.${phase}`) }}
          />
        }
        label={t(`publishing.schedule.${phase}`)}
      />
      <TextField
        type="number"
        size="small"
        label={t('publishing.schedule.daysBefore')}
        value={days}
        onChange={(e) => onDays(e.target.value)}
        disabled={!enabled}
        error={!validDays(days)}
        helperText={!validDays(days) ? t('publishing.schedule.invalidDays') : undefined}
        slotProps={{ htmlInput: { min: MIN_DAYS, max: MAX_DAYS } }}
        sx={{ width: 160 }}
      />
      {enabled && (
        <Typography variant="body2" color="text.secondary">
          {t('publishing.schedule.triggerDate', {
            date: formatDate(new Date(triggerDate), locale),
          })}
        </Typography>
      )}
    </Stack>
  );

  return (
    <Box data-testid="auto-publish-schedule">
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('publishing.schedule.description')}
      </Typography>
      <Stack spacing={2}>
        {phaseRow(
          'speakers',
          draft.phase2Enabled,
          draft.phase2DaysBeforeEvent,
          publishingStatus.phase2TriggerDate,
          (value) => edit({ phase2Enabled: value }),
          (value) => edit({ phase2DaysBeforeEvent: value })
        )}
        {phaseRow(
          'agenda',
          draft.phase3Enabled,
          draft.phase3DaysBeforeEvent,
          publishingStatus.phase3TriggerDate,
          (value) => edit({ phase3Enabled: value }),
          (value) => edit({ phase3DaysBeforeEvent: value })
        )}
      </Stack>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mt: 2 }}>
        <Button
          variant="contained"
          size="small"
          onClick={handleSave}
          disabled={unchanged || !daysValid || isScheduling}
          startIcon={isScheduling ? <CircularProgress size={16} /> : undefined}
          data-testid="save-auto-publish-schedule"
        >
          {t('publishing.schedule.save')}
        </Button>
        {saved && (
          <Typography variant="body2" color="success.main" role="status">
            {t('publishing.schedule.saved')}
          </Typography>
        )}
      </Stack>
      {failed && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {t('publishing.schedule.error')}
        </Alert>
      )}
    </Box>
  );
};

export default AutoPublishScheduleForm;
