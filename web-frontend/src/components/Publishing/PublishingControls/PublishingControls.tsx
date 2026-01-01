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
} from '@mui/material';
import {
  Error as ErrorIcon,
  Schedule as ScheduleIcon,
  Email as EmailIcon,
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
  const { publishPhase, scheduleAutoPublish, isPublishing, isScheduling } =
    usePublishing(eventCode);

  const [publishingMode, setPublishingMode] = useState<PublishingMode>('progressive');
  const [notifySubscribers, setNotifySubscribers] = useState(true);
  const [showScheduleDialog, setShowScheduleDialog] = useState(false);
  const [showNewsletterPreview, setShowNewsletterPreview] = useState(false);
  const [scheduledDate, setScheduledDate] = useState('');
  const [isAnnouncingPublish, setIsAnnouncingPublish] = useState(false);

  const hasValidationErrors = validationErrors.length > 0;

  const handlePublish = async () => {
    setIsAnnouncingPublish(true);
    await publishPhase(currentPhase, {
      mode: publishingMode,
      notifySubscribers,
    });
    // Reset announcement after a brief delay
    setTimeout(() => setIsAnnouncingPublish(false), 100);
  };

  const handleScheduleConfirm = async () => {
    await scheduleAutoPublish(currentPhase, {
      scheduledDate,
      notifySubscribers,
    });
    setShowScheduleDialog(false);
    setScheduledDate('');
  };

  const getPublishButtonLabel = () => {
    const phaseLabel = currentPhase.charAt(0).toUpperCase() + currentPhase.slice(1);
    return t('publishing.controls.publishPhase', { phase: phaseLabel });
  };

  return (
    <Box>
      {/* Screen reader announcement for publish action */}
      {isAnnouncingPublish && (
        <div role="status" aria-live="polite" style={{ position: 'absolute', left: '-10000px' }}>
          {t('publishing.controls.accessibility.publishingPhase', { phase: currentPhase })}
        </div>
      )}

      {/* Validation Errors */}
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

      {/* Action Buttons */}
      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <Button
          variant="contained"
          color="primary"
          onClick={handlePublish}
          disabled={isPublishing || hasValidationErrors}
          startIcon={isPublishing ? <CircularProgress size={20} /> : null}
          aria-label={getPublishButtonLabel()}
        >
          {isPublishing ? (
            <span data-testid="publishing-loading-text">{t('publishing.controls.publishing')}</span>
          ) : (
            getPublishButtonLabel()
          )}
        </Button>

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
