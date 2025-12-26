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
    return `Publish ${phaseLabel}`;
  };

  return (
    <Box>
      {/* Screen reader announcement for publish action */}
      {isAnnouncingPublish && (
        <div role="status" aria-live="polite" style={{ position: 'absolute', left: '-10000px' }}>
          Publishing {currentPhase}
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
            Validation Errors:
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
        <FormLabel component="legend" id="publishing-mode-label" aria-label="Publishing Mode">
          Mode
        </FormLabel>
        <RadioGroup
          aria-labelledby="publishing-mode-label"
          value={publishingMode}
          onChange={(e) => setPublishingMode(e.target.value as PublishingMode)}
          row
        >
          <FormControlLabel value="draft" control={<Radio />} label="Draft" />
          <FormControlLabel value="progressive" control={<Radio />} label="Progressive" />
          <FormControlLabel value="complete" control={<Radio />} label="Complete" />
        </RadioGroup>
      </FormControl>

      {/* Newsletter Notification Toggle */}
      <FormControlLabel
        control={
          <Checkbox
            checked={notifySubscribers}
            onChange={(e) => setNotifySubscribers(e.target.checked)}
            inputProps={{ 'aria-label': 'Notify subscribers when publishing' }}
          />
        }
        label="Notify subscribers"
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
            <span data-testid="publishing-loading-text">Publishing...</span>
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
          Schedule Publish
        </Button>

        <Button
          variant="outlined"
          startIcon={<EmailIcon />}
          onClick={() => setShowNewsletterPreview(true)}
        >
          Preview Newsletter
        </Button>
      </Box>

      {/* Schedule Publish Dialog */}
      <Dialog open={showScheduleDialog} onClose={() => setShowScheduleDialog(false)}>
        <DialogTitle>Schedule Publishing</DialogTitle>
        <DialogContent>
          <TextField
            inputProps={{ 'data-testid': 'schedule-datetime-picker' }}
            label="Schedule Date & Time"
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
          <Button onClick={() => setShowScheduleDialog(false)}>Cancel</Button>
          <Button onClick={handleScheduleConfirm} variant="contained">
            Confirm Schedule
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
        <DialogTitle>Newsletter Preview</DialogTitle>
        <DialogContent data-testid="newsletter-preview-modal">
          <Typography variant="h6" gutterBottom>
            Subject: New {currentPhase} published for {eventCode}
          </Typography>
          <Box sx={{ mt: 2, p: 2, bgcolor: 'grey.100', borderRadius: 1 }}>
            <Typography variant="body1">
              Newsletter content preview for {currentPhase} phase...
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowNewsletterPreview(false)}>Close</Button>
          <Button variant="contained">Send Test Email</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
