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
} from '@mui/material';
import { Close as CloseIcon, Email, Phone, Person, Edit as EditIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerOutreachHistory, useRecordOutreach } from '../../../hooks/useSpeakerOutreach';
import type { SpeakerPoolEntry } from '../../../types/speakerPool.types';
import type { ContactMethod } from '../../../types/speakerOutreach.types';
import { SpeakerEditModal } from '../../SpeakerBrainstormingPanel/SpeakerEditModal';

interface SpeakerOutreachDetailsDrawerProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
  showMarkContactedForm?: boolean; // Show form for IDENTIFIED/CONTACTED speakers
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
}) => {
  const { t } = useTranslation('organizer');

  const {
    data: outreachHistory,
    isLoading,
    isError,
  } = useSpeakerOutreachHistory(eventCode, speaker?.id || '');

  const recordOutreachMutation = useRecordOutreach();

  // Form state for marking contacted
  const initialFormData: FormData = {
    contactMethod: '',
    contactDate: new Date().toISOString().slice(0, 16),
    notes: '',
  };

  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [errors, setErrors] = useState<FormErrors>({});
  const [showForm, setShowForm] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);

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
            <Box display="flex" justifyContent="space-between" alignItems="flex-start">
              <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
                {speaker.speakerName}
              </Typography>
              <IconButton
                size="small"
                onClick={() => setEditModalOpen(true)}
                title={t('speakerBrainstorm.pool.edit', 'Edit speaker')}
              >
                <EditIcon fontSize="small" />
              </IconButton>
            </Box>
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
              <Box display="flex" alignItems="center" gap={1} mt={1}>
                <Email fontSize="small" color="action" />
                <Typography variant="body2" color="text.secondary">
                  {speaker.email}
                </Typography>
              </Box>
            )}
            {speaker.phone && (
              <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                <Phone fontSize="small" color="action" />
                <Typography variant="body2" color="text.secondary">
                  {speaker.phone}
                </Typography>
              </Box>
            )}
            <Box mt={1}>
              <Chip
                label={speaker.status}
                size="small"
                color={speaker.status === 'CONTACTED' ? 'success' : 'default'}
              />
            </Box>
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
                  >
                    {speaker.email && (
                      <MenuItem value="email">
                        {t('speakerOutreach.markContactedModal.method.email')}
                      </MenuItem>
                    )}
                    {speaker.phone && (
                      <MenuItem value="phone">
                        {t('speakerOutreach.markContactedModal.method.phone')}
                      </MenuItem>
                    )}
                    <MenuItem value="in_person">
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

      {/* Edit Speaker Modal */}
      <SpeakerEditModal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        speaker={speaker}
        eventCode={eventCode}
        onSuccess={() => {
          // Close drawer after successful edit so user sees fresh data when reopening
          onClose();
        }}
      />
    </Drawer>
  );
};

export default SpeakerOutreachDetailsDrawer;
