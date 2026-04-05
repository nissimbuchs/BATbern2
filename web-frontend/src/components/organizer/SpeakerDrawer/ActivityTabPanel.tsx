import React, { useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  Stack,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  TextField,
  Button,
  Alert,
  List,
  ListItem,
  ListItemText,
  Chip,
} from '@mui/material';
import { Email, Phone, Person } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { formatDateTime } from '@/utils/date';
import { useSpeakerOutreachHistory, useRecordOutreach } from '@/hooks/useSpeakerOutreach';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { ContactMethod } from '@/types/speakerOutreach.types';

interface ActivityTabPanelProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
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

const getContactMethodIcon = (method: string): React.ReactElement | null => {
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

export const ActivityTabPanel: React.FC<ActivityTabPanelProps> = ({ speaker, eventCode }) => {
  const { t, i18n } = useTranslation('organizer');
  const fmt = (dateString: string) => formatDateTime(new Date(dateString), i18n.language);

  const {
    data: outreachHistory,
    isLoading,
    isError,
  } = useSpeakerOutreachHistory(eventCode, speaker.id);

  const recordOutreachMutation = useRecordOutreach();

  const showForm = speaker.status === 'IDENTIFIED' || speaker.status === 'CONTACTED';

  const initialFormData: FormData = {
    contactMethod: '',
    contactDate: new Date().toISOString().slice(0, 16),
    notes: '',
  };

  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [errors, setErrors] = useState<FormErrors>({});
  const [prevSpeakerId, setPrevSpeakerId] = useState(speaker.id);

  // Reset form on speaker change using derived state pattern
  if (speaker.id !== prevSpeakerId) {
    setPrevSpeakerId(speaker.id);
    setFormData({
      contactMethod: '',
      contactDate: new Date().toISOString().slice(0, 16),
      notes: '',
    });
    setErrors({});
  }

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
    if (!validateForm()) return;

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
      setFormData({
        ...initialFormData,
        contactDate: new Date().toISOString().slice(0, 16),
      });
    } catch (error) {
      console.error('Failed to record outreach:', error);
    }
  };

  return (
    <Box sx={{ height: '100%', overflow: 'auto', p: 2 }}>
      {/* Mark Contacted Form */}
      {showForm && (
        <Paper sx={{ p: 2, mb: 2 }} elevation={2}>
          <Typography variant="subtitle2" gutterBottom fontWeight="bold">
            {t('speakerOutreach.markContactedModal.title')}
          </Typography>

          <Stack spacing={2} mt={2}>
            {recordOutreachMutation.isError && (
              <Alert severity="error">{t('speakerOutreach.markContactedModal.error.failed')}</Alert>
            )}

            <FormControl fullWidth error={!!errors.contactMethod} required size="small">
              <InputLabel>{t('speakerOutreach.contactMethod')}</InputLabel>
              <Select
                value={formData.contactMethod}
                onChange={(e) => {
                  setFormData({ ...formData, contactMethod: e.target.value as ContactMethod });
                  if (errors.contactMethod) setErrors({ ...errors, contactMethod: undefined });
                }}
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
              onChange={(e) => {
                setFormData({ ...formData, contactDate: e.target.value });
                if (errors.contactDate) setErrors({ ...errors, contactDate: undefined });
              }}
              error={!!errors.contactDate}
              helperText={errors.contactDate}
              required
              fullWidth
              size="small"
              InputLabelProps={{ shrink: true }}
            />

            <TextField
              label={t('common:labels.notes')}
              value={formData.notes}
              onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
              multiline
              rows={3}
              fullWidth
              size="small"
              placeholder={t('speakerOutreach.markContactedModal.notesPlaceholder')}
              inputProps={{ 'data-testid': 'contact-notes-field' }}
            />

            <Stack direction="row" spacing={1} justifyContent="flex-end">
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
      )}

      {/* Contact History */}
      <Typography variant="subtitle2" gutterBottom>
        {t('speakerOutreach.contactHistory')}
      </Typography>

      {isLoading && (
        <Box display="flex" justifyContent="center" p={4}>
          <BATbernLoader size={96} />
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
                  {fmt(attempt.contactDate)}
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
  );
};
