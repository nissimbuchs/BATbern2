import React, { useState } from 'react';
import {
  Box,
  Button,
  Checkbox,
  FormControl,
  FormControlLabel,
  FormLabel,
  Radio,
  RadioGroup,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Typography,
  CircularProgress,
  Stack,
  Tooltip,
} from '@mui/material';
import {
  Error as ErrorIcon,
  Schedule as ScheduleIcon,
  Email as EmailIcon,
  CheckCircle as CheckIcon,
  Topic as TopicIcon,
  People as PeopleIcon,
  EventNote as AgendaIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePublishing } from '@/hooks/usePublishing/usePublishing';
import { PublishingPhase, PublishingMode, PublishValidationError } from '@/types/event.types';

export interface PublishingControlsProps {
  eventCode: string;
  currentPhase: PublishingPhase;
  validationErrors?: PublishValidationError[];
}

export const PublishingControls: React.FC<PublishingControlsProps> = ({
  eventCode,
  currentPhase,
  validationErrors = [],
}) => {
  const { t } = useTranslation('events');
  const {
    publishPhase,
    scheduleAutoPublish,
    isPublishing,
    isScheduling,
    publishingStatus,
  } = usePublishing(eventCode);

  const [publishingMode, setPublishingMode] = useState<PublishingMode>('progressive');
  const [notifySubscribers, setNotifySubscribers] = useState(true);
  const [showScheduleDialog, setShowScheduleDialog] = useState(false);
  const [showNewsletterPreview, setShowNewsletterPreview] = useState(false);
  const [scheduledDate, setScheduledDate] = useState('');
  const [isAnnouncingPublish, setIsAnnouncingPublish] = useState(false);
  const [publishingPhase, setPublishingPhase] = useState<PublishingPhase | null>(null);

  const hasValidationErrors = validationErrors.length > 0;

  // Get published phases from status
  const publishedPhases = publishingStatus?.publishedPhases || [];

  // Check if a phase is already published
  const isPhasePublished = (phase: PublishingPhase): boolean => {
    return publishedPhases.includes(phase);
  };

  // Check if a phase can be published (validation passes and prerequisites met)
  const canPublishPhase = (phase: PublishingPhase): boolean => {
    if (!publishingStatus) return false;

    switch (phase) {
      case 'topic':
        return publishingStatus.topic?.isValid === true;
      case 'speakers':
        // Speakers requires topic to be published first (progressive mode)
        return (
          publishingStatus.speakers?.isValid === true &&
          (isPhasePublished('topic') || publishingMode !== 'progressive')
        );
      case 'agenda':
        // Agenda requires speakers to be published first (progressive mode)
        return (
          publishingStatus.sessions?.isValid === true &&
          (isPhasePublished('speakers') || publishingMode !== 'progressive')
        );
      default:
        return false;
    }
  };

  // Get validation message for a phase
  const getPhaseValidationMessage = (phase: PublishingPhase): string | null => {
    if (!publishingStatus) return null;

    switch (phase) {
      case 'topic':
        if (!publishingStatus.topic?.isValid) {
          return publishingStatus.topic?.errors?.[0] || t('publishing.controls.topicNotReady');
        }
        break;
      case 'speakers':
        if (!isPhasePublished('topic') && publishingMode === 'progressive') {
          return t('publishing.controls.publishTopicFirst');
        }
        if (!publishingStatus.speakers?.isValid) {
          return publishingStatus.speakers?.errors?.[0] || t('publishing.controls.speakersNotReady');
        }
        break;
      case 'agenda':
        if (!isPhasePublished('speakers') && publishingMode === 'progressive') {
          return t('publishing.controls.publishSpeakersFirst');
        }
        if (!publishingStatus.sessions?.isValid) {
          const { assignedCount = 0, totalCount = 0 } = publishingStatus.sessions || {};
          return t('publishing.controls.agendaNotReady', { assigned: assignedCount, total: totalCount });
        }
        break;
    }
    return null;
  };

  const handlePublish = async (phase: PublishingPhase) => {
    setIsAnnouncingPublish(true);
    setPublishingPhase(phase);
    await publishPhase(phase, {
      mode: publishingMode,
      notifySubscribers,
    });
    // Reset announcement after a brief delay
    setTimeout(() => {
      setIsAnnouncingPublish(false);
      setPublishingPhase(null);
    }, 100);
  };

  const handleScheduleConfirm = async () => {
    await scheduleAutoPublish(currentPhase, {
      scheduledDate,
      notifySubscribers,
    });
    setShowScheduleDialog(false);
    setScheduledDate('');
  };

  const getPhaseIcon = (phase: PublishingPhase) => {
    switch (phase) {
      case 'topic':
        return <TopicIcon />;
      case 'speakers':
        return <PeopleIcon />;
      case 'agenda':
        return <AgendaIcon />;
    }
  };

  const getPhaseLabel = (phase: PublishingPhase): string => {
    return t(`publishing.controls.phase.${phase}`);
  };

  return (
    <Box>
      {/* Screen reader announcement for publish action */}
      {isAnnouncingPublish && publishingPhase && (
        <div role="status" aria-live="polite" style={{ position: 'absolute', left: '-10000px' }}>
          {t('publishing.controls.accessibility.publishingPhase', { phase: publishingPhase })}
        </div>
      )}

      {/* Validation Errors from API */}
      {hasValidationErrors && (
        <Alert
          severity="error"
          icon={<ErrorIcon data-testid="validation-error-icon" />}
          sx={{ mb: 2 }}
        >
          <Typography variant="subtitle2" gutterBottom>
            {t('publishing.controls.validationErrors')}
          </Typography>
          {validationErrors.map((error, index) => (
            <Typography key={index} variant="body2">
              • {error.message}
            </Typography>
          ))}
        </Alert>
      )}

      {/* Mode Selection */}
      <FormControl component="fieldset" sx={{ mb: 3 }}>
        <FormLabel
          component="legend"
          id="publishing-mode-label"
          aria-label={t('publishing.controls.accessibility.publishingMode')}
        >
          {t('publishing.controls.mode')}
        </FormLabel>
        <RadioGroup
          aria-labelledby="publishing-mode-label"
          value={publishingMode}
          onChange={(e) => setPublishingMode(e.target.value as PublishingMode)}
          row
        >
          <FormControlLabel
            value="draft"
            control={<Radio />}
            label={t('publishing.controls.modeDraft')}
          />
          <FormControlLabel
            value="progressive"
            control={<Radio />}
            label={t('publishing.controls.modeProgressive')}
          />
          <FormControlLabel
            value="complete"
            control={<Radio />}
            label={t('publishing.controls.modeComplete')}
          />
        </RadioGroup>
      </FormControl>

      {/* Newsletter Notification Toggle */}
      <FormControlLabel
        control={
          <Checkbox
            checked={notifySubscribers}
            onChange={(e) => setNotifySubscribers(e.target.checked)}
            inputProps={{
              'aria-label': t('publishing.controls.accessibility.notifySubscribers'),
            }}
          />
        }
        label={t('publishing.controls.notifySubscribers')}
        sx={{ mb: 2, display: 'block' }}
      />

      {/* Phase Publishing Buttons */}
      <Typography variant="subtitle2" gutterBottom sx={{ mt: 2 }}>
        {t('publishing.controls.publishPhases')}
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 3 }} flexWrap="wrap" useFlexGap>
        {(['topic', 'speakers', 'agenda'] as PublishingPhase[]).map((phase) => {
          const isPublished = isPhasePublished(phase);
          const canPublish = canPublishPhase(phase);
          const validationMessage = getPhaseValidationMessage(phase);
          const isCurrentlyPublishing = isPublishing && publishingPhase === phase;

          return (
            <Tooltip
              key={phase}
              title={isPublished ? t('publishing.controls.alreadyPublished') : validationMessage || ''}
              arrow
            >
              <span>
                <Button
                  variant={isPublished ? 'outlined' : 'contained'}
                  color={isPublished ? 'success' : 'primary'}
                  onClick={() => handlePublish(phase)}
                  disabled={isPublishing || isPublished || !canPublish}
                  startIcon={
                    isCurrentlyPublishing ? (
                      <CircularProgress size={20} />
                    ) : isPublished ? (
                      <CheckIcon />
                    ) : (
                      getPhaseIcon(phase)
                    )
                  }
                  data-testid={`publish-${phase}-button`}
                  aria-label={t('publishing.controls.publishPhase', { phase: getPhaseLabel(phase) })}
                  sx={{ minWidth: 160 }}
                >
                  {isPublished
                    ? t('publishing.controls.published', { phase: getPhaseLabel(phase) })
                    : isCurrentlyPublishing
                      ? t('publishing.controls.publishing')
                      : t('publishing.controls.publishPhase', { phase: getPhaseLabel(phase) })}
                </Button>
              </span>
            </Tooltip>
          );
        })}
      </Stack>

      {/* Secondary Actions */}
      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <Button
          variant="outlined"
          startIcon={<ScheduleIcon />}
          onClick={() => setShowScheduleDialog(true)}
          disabled={isScheduling}
        >
          {t('publishing.controls.schedulePublish')}
        </Button>

        <Button
          variant="outlined"
          startIcon={<EmailIcon />}
          onClick={() => setShowNewsletterPreview(true)}
        >
          {t('publishing.controls.previewNewsletter')}
        </Button>
      </Box>

      {/* Schedule Publish Dialog */}
      <Dialog open={showScheduleDialog} onClose={() => setShowScheduleDialog(false)}>
        <DialogTitle>{t('publishing.controls.scheduleDialog.title')}</DialogTitle>
        <DialogContent>
          <TextField
            inputProps={{ 'data-testid': 'schedule-datetime-picker' }}
            label={t('publishing.controls.scheduleDialog.dateLabel')}
            type="datetime-local"
            value={scheduledDate}
            onChange={(e) => setScheduledDate(e.target.value)}
            fullWidth
            sx={{ mt: 2 }}
            InputLabelProps={{
              shrink: true,
            }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowScheduleDialog(false)}>
            {t('publishing.controls.scheduleDialog.cancel')}
          </Button>
          <Button onClick={handleScheduleConfirm} variant="contained">
            {t('publishing.controls.scheduleDialog.confirm')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Newsletter Preview Modal */}
      <Dialog
        open={showNewsletterPreview}
        onClose={() => setShowNewsletterPreview(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>{t('publishing.controls.newsletterDialog.title')}</DialogTitle>
        <DialogContent data-testid="newsletter-preview-modal">
          <Typography variant="h6" gutterBottom>
            {t('publishing.controls.newsletterDialog.subject', {
              phase: currentPhase,
              eventCode,
            })}
          </Typography>
          <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
            <Typography variant="body1">
              {t('publishing.controls.newsletterDialog.contentPreview', { phase: currentPhase })}
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowNewsletterPreview(false)}>
            {t('publishing.controls.newsletterDialog.close')}
          </Button>
          <Button variant="contained">{t('publishing.controls.newsletterDialog.sendTest')}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
