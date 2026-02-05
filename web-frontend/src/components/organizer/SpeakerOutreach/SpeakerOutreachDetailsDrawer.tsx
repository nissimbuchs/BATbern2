/**
 * Speaker Outreach Details Drawer Component (Story 5.6 Enhanced)
 *
 * Displays detailed outreach history for a selected speaker
 * Features:
 * - Slide-in drawer from right side
 * - Speaker information summary
 * - Mark contacted form (for IDENTIFIED/CONTACTED speakers)
 * - Chronological list of outreach attempts
 * - Contact method, date, and notes for each attempt
 * - Empty state when no outreach history exists
 */

import React, { useState, useEffect } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  List,
  ListItem,
  ListItemText,
  Chip,
  CircularProgress,
  Alert,
  Paper,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Button,
  Stack,
  Snackbar,
} from '@mui/material';
import { Close as CloseIcon, Email, Phone, Person, Send as SendIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerOutreachHistory, useRecordOutreach } from '../../../hooks/useSpeakerOutreach';
import { useSendInvitation } from '../../../hooks/useSpeakerPool';
import type { SpeakerPoolEntry } from '../../../types/speakerPool.types';
import type { ContactMethod } from '../../../types/speakerOutreach.types';

interface SpeakerOutreachDetailsDrawerProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
  showMarkContactedForm?: boolean; // Show form for IDENTIFIED/CONTACTED speakers
  onOpenContentSubmission?: (speaker: SpeakerPoolEntry) => void; // Callback for ACCEPTED speakers
  onOpenQualityReview?: (speaker: SpeakerPoolEntry) => void; // Callback for CONTENT_SUBMITTED speakers
}

interface FormData {
  contactMethod: ContactMethod | '';
  contactDate: string;
  notes: string;
}

interface FormErrors {
  contactMethod?: string;
  contactDate?: string;
}

const SpeakerOutreachDetailsDrawer: React.FC<SpeakerOutreachDetailsDrawerProps> = ({
  open,
  onClose,
  speaker,
  eventCode,
  showMarkContactedForm = false,
  onOpenContentSubmission,
  onOpenQualityReview,
}) => {
  const { t } = useTranslation('organizer');

  const {
    data: outreachHistory,
    isLoading,
    isError,
  } = useSpeakerOutreachHistory(eventCode, speaker?.id || '');

  const recordOutreachMutation = useRecordOutreach();
  const sendInvitationMutation = useSendInvitation(eventCode);

  // Snackbar state for invitation feedback
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarSeverity, setSnackbarSeverity] = useState<'success' | 'error'>('success');

  // Handle sending invitation
  const handleSendInvitation = async () => {
    if (!speaker) return;

    // Use locally saved email if speaker doesn't have one in the database
    const effectiveEmail = speaker.email || (emailSaved ? emailInput : undefined);

    // Default response deadline: 30 days from now
    const defaultDeadline = new Date();
    defaultDeadline.setDate(defaultDeadline.getDate() + 30);
    const responseDeadline = defaultDeadline.toISOString().split('T')[0]; // YYYY-MM-DD format

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

  const handleSnackbarClose = () => {
    setSnackbarOpen(false);
  };

  // Show Send Invitation button only for IDENTIFIED speakers
  const canSendInvitation = speaker?.status === 'IDENTIFIED';
  const hasEmail = !!speaker?.email;

  // Email input state (AC4: for speakers without email)
  const [emailInput, setEmailInput] = useState('');
  const [emailError, setEmailError] = useState('');
  const [emailTouched, setEmailTouched] = useState(false);
  const [emailSaved, setEmailSaved] = useState(false); // Track when email has been locally saved

  // Email validation
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

  // Reset email input when drawer opens/closes or speaker changes
  useEffect(() => {
    if (open && speaker) {
      setEmailInput('');
      setEmailError('');
      setEmailTouched(false);
      setEmailSaved(false);
    }
  }, [open, speaker?.id]);

  // Show email input only for IDENTIFIED speakers without email
  const showEmailInput = canSendInvitation && !hasEmail;
  const isEmailValid = emailInput && validateEmail(emailInput);

  // Form state for marking contacted
  const initialFormData: FormData = {
    contactMethod: '',
    contactDate: new Date().toISOString().slice(0, 16),
    notes: '',
  };

  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [errors, setErrors] = useState<FormErrors>({});
  const [showForm, setShowForm] = useState(false);

  // Reset form when drawer opens/closes or speaker changes
  useEffect(() => {
    if (open && speaker) {
      // Auto-show form for IDENTIFIED or CONTACTED speakers if prop is true
      const shouldShowForm =
        showMarkContactedForm &&
        (speaker.status === 'IDENTIFIED' || speaker.status === 'CONTACTED');
      setShowForm(shouldShowForm);
      setFormData({
        ...initialFormData,
        contactDate: new Date().toISOString().slice(0, 16),
      });
      setErrors({});
    } else {
      setShowForm(false);
    }
  }, [open, speaker, showMarkContactedForm]);

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {};

    if (!formData.contactMethod) {
      newErrors.contactMethod = t('speakerOutreach.markContactedModal.error.methodRequired');
    }

    if (!formData.contactDate) {
      newErrors.contactDate = t('speakerOutreach.markContactedModal.error.dateRequired');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validateForm() || !speaker) {
      return;
    }

    try {
      await recordOutreachMutation.mutateAsync({
        eventCode,
        speakerId: speaker.id,
        request: {
          contactMethod: formData.contactMethod as ContactMethod,
          contactDate: new Date(formData.contactDate).toISOString(),
          notes: formData.notes || undefined,
        },
      });

      // Reset form and hide it
      setFormData({
        ...initialFormData,
        contactDate: new Date().toISOString().slice(0, 16),
      });
      setShowForm(false);
    } catch (error) {
      console.error('Failed to record outreach:', error);
    }
  };

  const handleContactMethodChange = (value: string) => {
    setFormData({ ...formData, contactMethod: value as ContactMethod });
    if (errors.contactMethod) {
      setErrors({ ...errors, contactMethod: undefined });
    }
  };

  const handleContactDateChange = (value: string) => {
    setFormData({ ...formData, contactDate: value });
    if (errors.contactDate) {
      setErrors({ ...errors, contactDate: undefined });
    }
  };

  const handleNotesChange = (value: string) => {
    setFormData({ ...formData, notes: value });
  };

  const getContactMethodIcon = (method: string) => {
    switch (method) {
      case 'email':
        return <Email fontSize="small" />;
      case 'phone':
        return <Phone fontSize="small" />;
      case 'in_person':
        return <Person fontSize="small" />;
      default:
        return null;
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat('de-CH', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: { width: { xs: '100%', sm: 500 } },
      }}
    >
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Header */}
        <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">{t('speakerOutreach.outreachDetails')}</Typography>
          <IconButton onClick={onClose} size="small">
            <CloseIcon />
          </IconButton>
        </Box>

        <Divider />

        {/* Speaker Info */}
        {speaker && (
          <Paper sx={{ m: 2, p: 2 }} elevation={1}>
            <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
              {speaker.speakerName}
            </Typography>
            {speaker.company && (
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {speaker.company}
              </Typography>
            )}
            {speaker.expertise && (
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {t('speakerBrainstorm.form.expertise')}: {speaker.expertise}
              </Typography>
            )}
            {speaker.email && (
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {speaker.email}
              </Typography>
            )}
            <Box mt={1}>
              <Chip
                label={speaker.status}
                size="small"
                color={
                  speaker.status === 'ACCEPTED'
                    ? 'success'
                    : speaker.status === 'DECLINED'
                      ? 'error'
                      : speaker.status === 'INVITED'
                        ? 'info'
                        : speaker.status === 'CONTACTED'
                          ? 'warning'
                          : 'default'
                }
              />
              {speaker.isTentative && (
                <Chip label={t('speakers.tentative')} size="small" color="warning" sx={{ ml: 1 }} />
              )}
            </Box>

            {/* Response Details - Story 6.2a */}
            {/* Show for any speaker who has accepted (acceptedAt is set), not just current ACCEPTED status */}
            {speaker.acceptedAt && (
              <Box mt={2} sx={{ bgcolor: 'success.light', p: 1.5, borderRadius: 1, opacity: 0.9 }}>
                <Typography variant="subtitle2" color="success.dark" gutterBottom>
                  {t('speakers.responseDetails')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('speakers.acceptedAt')}: {formatDate(speaker.acceptedAt)}
                </Typography>
                {speaker.preferredTimeSlot && (
                  <Typography variant="body2" color="text.secondary">
                    {t('speakers.preferredTimeSlot')}: {speaker.preferredTimeSlot}
                  </Typography>
                )}
                {speaker.travelRequirements && (
                  <Typography variant="body2" color="text.secondary">
                    {t('speakers.travelRequirements')}: {speaker.travelRequirements}
                  </Typography>
                )}
                {speaker.technicalRequirements && (
                  <Typography variant="body2" color="text.secondary">
                    {t('speakers.technicalRequirements')}: {speaker.technicalRequirements}
                  </Typography>
                )}
                {speaker.initialPresentationTitle && (
                  <Typography variant="body2" color="text.secondary">
                    {t('speakers.initialTitle')}: {speaker.initialPresentationTitle}
                  </Typography>
                )}
                {speaker.preferenceComments && (
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ mt: 1, fontStyle: 'italic' }}
                  >
                    {t('speakers.comments')}: {speaker.preferenceComments}
                  </Typography>
                )}
              </Box>
            )}

            {speaker.status === 'DECLINED' && speaker.declineReason && (
              <Box mt={2} sx={{ bgcolor: 'error.light', p: 1.5, borderRadius: 1, opacity: 0.9 }}>
                <Typography variant="subtitle2" color="error.dark" gutterBottom>
                  {t('speakers.declineDetails')}
                </Typography>
                {speaker.declinedAt && (
                  <Typography variant="body2" color="text.secondary">
                    {t('speakers.declinedAt')}: {formatDate(speaker.declinedAt)}
                  </Typography>
                )}
                <Typography variant="body2" color="text.secondary">
                  {t('speakers.declineReason')}: {speaker.declineReason}
                </Typography>
              </Box>
            )}

            {speaker.isTentative && speaker.tentativeReason && (
              <Box mt={2} sx={{ bgcolor: 'warning.light', p: 1.5, borderRadius: 1, opacity: 0.9 }}>
                <Typography variant="subtitle2" color="warning.dark" gutterBottom>
                  {t('speakers.tentativeDetails')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('speakers.tentativeReason')}: {speaker.tentativeReason}
                </Typography>
              </Box>
            )}

            {/* Revision Needed Feedback (when content was rejected) */}
            {speaker.contentStatus === 'REVISION_NEEDED' && speaker.notes && (
              <Box mt={2} sx={{ bgcolor: 'error.light', p: 1.5, borderRadius: 1, opacity: 0.9 }}>
                <Typography variant="subtitle2" color="error.dark" gutterBottom>
                  {t('speakers.revisionRequested', 'Revision Requested')}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
                  {speaker.notes}
                </Typography>
              </Box>
            )}

            {/* Story 6.3: Submitted Content Display */}
            {speaker.submittedTitle && (
              <Box mt={2} sx={{ bgcolor: 'success.light', p: 1.5, borderRadius: 1, opacity: 0.9 }}>
                <Typography variant="subtitle2" color="success.dark" gutterBottom>
                  {t('speakers.submittedContent', 'Submitted Content')}
                  {speaker.contentStatus && (
                    <Chip
                      label={speaker.contentStatus}
                      size="small"
                      color={
                        speaker.contentStatus === 'APPROVED'
                          ? 'success'
                          : speaker.contentStatus === 'SUBMITTED'
                            ? 'info'
                            : 'warning'
                      }
                      sx={{ ml: 1 }}
                    />
                  )}
                </Typography>
                <Typography variant="body2" fontWeight="medium" gutterBottom>
                  {speaker.submittedTitle}
                </Typography>
                {speaker.submittedAbstract && (
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{
                      mt: 1,
                      whiteSpace: 'pre-wrap',
                      maxHeight: 150,
                      overflow: 'auto',
                    }}
                  >
                    {speaker.submittedAbstract}
                  </Typography>
                )}
                {speaker.contentSubmittedAt && (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ mt: 1, display: 'block' }}
                  >
                    {t('speakers.submittedAt', 'Submitted')}:{' '}
                    {formatDate(speaker.contentSubmittedAt)}
                  </Typography>
                )}
              </Box>
            )}

            {/* Email Input for Speakers without Email (AC4) */}
            {showEmailInput && (
              <Box mt={2}>
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
                    placeholder="speaker@example.com"
                  />
                  <Button
                    variant="outlined"
                    size="small"
                    disabled={!isEmailValid}
                    onClick={() => {
                      // Mark email as saved locally - enables Send Invitation button
                      // Note: Full backend support for updating speaker email is pending
                      setEmailSaved(true);
                      setSnackbarMessage(t('speakers.emailSaved'));
                      setSnackbarSeverity('success');
                      setSnackbarOpen(true);
                    }}
                  >
                    {t('speakers.saveEmail')}
                  </Button>
                </Stack>
              </Box>
            )}

            {/* Send Invitation Button (Story 6.1c) */}
            {canSendInvitation && (
              <Box mt={2}>
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
              </Box>
            )}

            {/* Content Submission Button for ACCEPTED speakers */}
            {speaker.status === 'ACCEPTED' && onOpenContentSubmission && (
              <Box mt={2}>
                <Button
                  variant="contained"
                  color="success"
                  onClick={() => onOpenContentSubmission(speaker)}
                  fullWidth
                >
                  {t('speakers.submitContent', 'Submit Content')}
                </Button>
              </Box>
            )}

            {/* Quality Review Button for CONTENT_SUBMITTED speakers */}
            {speaker.status === 'CONTENT_SUBMITTED' && onOpenQualityReview && (
              <Box mt={2}>
                <Button
                  variant="contained"
                  color="warning"
                  onClick={() => onOpenQualityReview(speaker)}
                  fullWidth
                >
                  {t('speakers.reviewContent', 'Review Content')}
                </Button>
              </Box>
            )}
          </Paper>
        )}

        <Divider />

        {/* Mark Contacted Form (for IDENTIFIED/CONTACTED speakers) */}
        {showForm && speaker && (
          <Box sx={{ p: 2 }}>
            <Paper sx={{ p: 2 }} elevation={2}>
              <Typography variant="subtitle2" gutterBottom fontWeight="bold">
                {t('speakerOutreach.markContactedModal.title')}
              </Typography>

              <Stack spacing={2} mt={2}>
                {recordOutreachMutation.isError && (
                  <Alert severity="error">
                    {t('speakerOutreach.markContactedModal.error.failed')}
                  </Alert>
                )}

                <FormControl fullWidth error={!!errors.contactMethod} required size="small">
                  <InputLabel>{t('speakerOutreach.contactMethod')}</InputLabel>
                  <Select
                    value={formData.contactMethod}
                    onChange={(e) => handleContactMethodChange(e.target.value)}
                    label={t('speakerOutreach.contactMethod')}
                    data-testid="contact-method-select"
                  >
                    <MenuItem value="email" data-testid="contact-method-email">
                      {t('speakerOutreach.markContactedModal.method.email')}
                    </MenuItem>
                    <MenuItem value="phone" data-testid="contact-method-phone">
                      {t('speakerOutreach.markContactedModal.method.phone')}
                    </MenuItem>
                    <MenuItem value="in_person" data-testid="contact-method-in_person">
                      {t('speakerOutreach.markContactedModal.method.inPerson')}
                    </MenuItem>
                  </Select>
                  {errors.contactMethod && (
                    <Box color="error.main" fontSize="0.75rem" mt={0.5}>
                      {errors.contactMethod}
                    </Box>
                  )}
                </FormControl>

                <TextField
                  label={t('speakerOutreach.contactDate')}
                  type="datetime-local"
                  value={formData.contactDate}
                  onChange={(e) => handleContactDateChange(e.target.value)}
                  error={!!errors.contactDate}
                  helperText={errors.contactDate}
                  required
                  fullWidth
                  size="small"
                  InputLabelProps={{ shrink: true }}
                />

                <TextField
                  label={t('speakerOutreach.notes')}
                  value={formData.notes}
                  onChange={(e) => handleNotesChange(e.target.value)}
                  multiline
                  rows={3}
                  fullWidth
                  size="small"
                  placeholder={t('speakerOutreach.markContactedModal.notesPlaceholder')}
                  inputProps={{ 'data-testid': 'contact-notes-field' }}
                />

                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button size="small" onClick={() => setShowForm(false)}>
                    {t('common:actions.cancel')}
                  </Button>
                  <Button
                    size="small"
                    variant="contained"
                    onClick={handleSubmit}
                    disabled={recordOutreachMutation.isPending}
                    data-testid="mark-contacted-button"
                  >
                    {recordOutreachMutation.isPending
                      ? t('common.saving')
                      : t('speakerOutreach.markContacted')}
                  </Button>
                </Stack>
              </Stack>
            </Paper>
          </Box>
        )}

        {showForm && <Divider />}

        {/* Outreach History */}
        <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            {t('speakerOutreach.contactHistory')}
          </Typography>

          {isLoading && (
            <Box display="flex" justifyContent="center" p={4}>
              <CircularProgress />
            </Box>
          )}

          {isError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {t('speakerOutreach.error.loadHistory')}
            </Alert>
          )}

          {!isLoading && !isError && (!outreachHistory || outreachHistory.length === 0) && (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <Typography variant="body2" color="text.secondary">
                {t('speakerOutreach.noContactHistory')}
              </Typography>
            </Box>
          )}

          {!isLoading && !isError && outreachHistory && outreachHistory.length > 0 && (
            <List>
              {outreachHistory.map((attempt, index) => (
                <ListItem
                  key={index}
                  sx={{
                    flexDirection: 'column',
                    alignItems: 'flex-start',
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    mb: 2,
                  }}
                >
                  <Box display="flex" alignItems="center" gap={1} mb={1}>
                    {getContactMethodIcon(attempt.contactMethod)}
                    <Chip
                      label={t(
                        `speakerOutreach.markContactedModal.method.${attempt.contactMethod === 'in_person' ? 'inPerson' : attempt.contactMethod}`
                      )}
                      size="small"
                      variant="outlined"
                    />
                    <Typography variant="caption" color="text.secondary">
                      {formatDate(attempt.contactDate)}
                    </Typography>
                  </Box>

                  {attempt.notes && (
                    <ListItemText
                      secondary={attempt.notes}
                      secondaryTypographyProps={{
                        sx: {
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-word',
                        },
                      }}
                    />
                  )}

                  {attempt.organizerUsername && (
                    <Typography variant="caption" color="text.secondary" mt={0.5}>
                      {t('speakerOutreach.contactedBy')}: {attempt.organizerUsername}
                    </Typography>
                  )}
                </ListItem>
              ))}
            </List>
          )}
        </Box>
      </Box>

      {/* Invitation Feedback Snackbar */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={6000}
        onClose={handleSnackbarClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={handleSnackbarClose} severity={snackbarSeverity} sx={{ width: '100%' }}>
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Drawer>
  );
};

export default SpeakerOutreachDetailsDrawer;
