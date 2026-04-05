import React, { useState } from 'react';
import {
  Box,
  Typography,
  Button,
  Stack,
  CircularProgress,
  Snackbar,
  Alert,
  TextField,
} from '@mui/material';
import { Send as SendIcon, NotificationsActive } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSendInvitation, useSendReminder } from '@/hooks/useSpeakerPool';
import { AssignedOrganizerField } from './AssignedOrganizerField';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

interface OverviewTabPanelProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
  isMobile: boolean;
  onOpenContentSubmission: () => void;
  onOpenQualityReview: () => void;
}

export const OverviewTabPanel: React.FC<OverviewTabPanelProps> = ({
  speaker,
  eventCode,
  isMobile,
  onOpenContentSubmission,
  onOpenQualityReview,
}) => {
  const { t } = useTranslation('organizer');
  const sendInvitationMutation = useSendInvitation(eventCode);
  const sendReminderMutation = useSendReminder(eventCode);

  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarSeverity, setSnackbarSeverity] = useState<'success' | 'error'>('success');

  // Email input state for IDENTIFIED speakers without email
  const [emailInput, setEmailInput] = useState('');
  const [emailError, setEmailError] = useState('');
  const [emailTouched, setEmailTouched] = useState(false);
  const [emailSaved, setEmailSaved] = useState(false);

  const validateEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const handleEmailChange = (value: string) => {
    setEmailInput(value);
    if (emailTouched && value && !validateEmail(value)) {
      setEmailError(t('speakers.invalidEmail'));
    } else {
      setEmailError('');
    }
  };

  const handleEmailBlur = () => {
    setEmailTouched(true);
    if (emailInput && !validateEmail(emailInput)) {
      setEmailError(t('speakers.invalidEmail'));
    }
  };

  const canSendInvitation = speaker.status === 'IDENTIFIED';
  const hasEmail = !!speaker.email;
  const showEmailInput = canSendInvitation && !hasEmail;
  const isEmailValid = emailInput && validateEmail(emailInput);

  const handleSendInvitation = async () => {
    const effectiveEmail = speaker.email || (emailSaved ? emailInput : undefined);
    const defaultDeadline = new Date();
    defaultDeadline.setDate(defaultDeadline.getDate() + 30);
    const responseDeadline = defaultDeadline.toISOString().split('T')[0];

    try {
      const result = await sendInvitationMutation.mutateAsync({
        username: speaker.id,
        options: {
          responseDeadline,
          ...(effectiveEmail && !speaker.email ? { email: effectiveEmail } : {}),
        },
      });
      setSnackbarMessage(t('speakers.invitationSent', { email: result.email || effectiveEmail }));
      setSnackbarSeverity('success');
      setSnackbarOpen(true);
    } catch {
      setSnackbarMessage(t('speakers.invitationFailed'));
      setSnackbarSeverity('error');
      setSnackbarOpen(true);
    }
  };

  const handleSendReminder = async () => {
    const reminderType = speaker.status === 'INVITED' ? 'RESPONSE' : 'CONTENT';
    try {
      const result = await sendReminderMutation.mutateAsync({
        speakerPoolId: speaker.id,
        request: { reminderType },
      });
      setSnackbarMessage(t('speakers.reminderSent', { email: result.emailAddress }));
      setSnackbarSeverity('success');
      setSnackbarOpen(true);
    } catch {
      setSnackbarMessage(t('speakers.reminderFailed'));
      setSnackbarSeverity('error');
      setSnackbarOpen(true);
    }
  };

  const actionButtons = () => {
    switch (speaker.status) {
      case 'IDENTIFIED':
        return (
          <Stack spacing={2}>
            {showEmailInput && (
              <Stack spacing={1}>
                <TextField
                  label={t('speakers.email')}
                  value={emailInput}
                  onChange={(e) => handleEmailChange(e.target.value)}
                  onBlur={handleEmailBlur}
                  error={!!emailError}
                  helperText={emailError || t('speakers.emailRequired')}
                  fullWidth
                  size="small"
                  type="email"
                  placeholder={t('speakerOutreach.emailPlaceholder')}
                />
                <Button
                  variant="outlined"
                  size="small"
                  disabled={!isEmailValid}
                  onClick={() => {
                    setEmailSaved(true);
                    setSnackbarMessage(t('speakers.emailSaved'));
                    setSnackbarSeverity('success');
                    setSnackbarOpen(true);
                  }}
                >
                  {t('speakers.saveEmail')}
                </Button>
              </Stack>
            )}
            <Button
              variant="contained"
              color="primary"
              startIcon={
                sendInvitationMutation.isPending ? (
                  <CircularProgress size={16} color="inherit" />
                ) : (
                  <SendIcon />
                )
              }
              onClick={handleSendInvitation}
              disabled={sendInvitationMutation.isPending || (!hasEmail && !emailSaved)}
              fullWidth
            >
              {sendInvitationMutation.isPending
                ? t('speakers.sending')
                : t('speakers.sendInvitation')}
            </Button>
          </Stack>
        );

      case 'INVITED':
        return (
          <Button
            variant="outlined"
            color="info"
            startIcon={
              sendReminderMutation.isPending ? (
                <CircularProgress size={16} color="inherit" />
              ) : (
                <NotificationsActive />
              )
            }
            onClick={handleSendReminder}
            disabled={sendReminderMutation.isPending}
            fullWidth
          >
            {sendReminderMutation.isPending
              ? t('speakers.sendingReminder')
              : t('speakers.sendReminder')}
          </Button>
        );

      case 'ACCEPTED':
        return (
          <Button variant="contained" color="success" onClick={onOpenContentSubmission} fullWidth>
            {t('speakers.submitContent', 'Submit Content')}
          </Button>
        );

      case 'CONTENT_SUBMITTED':
        return (
          <Button variant="contained" color="warning" onClick={onOpenQualityReview} fullWidth>
            {t('speakers.reviewContent', 'Review Content')}
          </Button>
        );

      default:
        return null;
    }
  };

  const buttons = actionButtons();

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box sx={{ flex: 1, p: 2 }}>
        <Stack spacing={3}>
          {/* Assigned Organizer */}
          <AssignedOrganizerField speaker={speaker} eventCode={eventCode} />

          {/* Quick Info */}
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              {t('speakerOutreach.outreachDetails')}
            </Typography>
            {speaker.email && (
              <Typography variant="body2" color="text.secondary">
                {speaker.email}
              </Typography>
            )}
            {speaker.expertise && (
              <Typography variant="body2" color="text.secondary">
                {t('speakerBrainstorm.form.expertise')}: {speaker.expertise}
              </Typography>
            )}
            {speaker.invitedAt && (
              <Typography variant="body2" color="text.secondary">
                {t('speakers.invite')}: {new Date(speaker.invitedAt).toLocaleDateString('de-CH')}
              </Typography>
            )}
            {speaker.responseDeadline && (
              <Typography variant="body2" color="text.secondary">
                Deadline: {new Date(speaker.responseDeadline).toLocaleDateString('de-CH')}
              </Typography>
            )}
          </Box>

          {/* Action Buttons (non-mobile: inline) */}
          {!isMobile && buttons}
        </Stack>
      </Box>

      {/* Mobile: sticky action footer */}
      {isMobile && buttons && (
        <Box
          sx={{
            position: 'sticky',
            bottom: 0,
            bgcolor: 'background.paper',
            p: 2,
            borderTop: 1,
            borderColor: 'divider',
          }}
        >
          {buttons}
        </Box>
      )}

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={6000}
        onClose={() => setSnackbarOpen(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbarOpen(false)}
          severity={snackbarSeverity}
          sx={{ width: '100%' }}
        >
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
};
