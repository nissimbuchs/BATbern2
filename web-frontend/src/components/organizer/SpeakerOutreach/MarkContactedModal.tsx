/**
 * Mark Contacted Modal Component (Story 5.3 - Task 5b)
 *
 * Modal dialog for recording speaker outreach attempts
 * Features:
 * - Contact method selection (email/phone/in-person)
 * - Contact date picker
 * - Notes text area
 * - Form validation
 * - i18n support (German/English)
 */

import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Box,
  IconButton,
  Alert,
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useRecordOutreach } from '../../../hooks/useSpeakerOutreach';
import type { ContactMethod } from '../../../types/speakerOutreach.types';

interface MarkContactedModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  eventCode: string;
  speakerId: string;
  speakerName: string;
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

const MarkContactedModal: React.FC<MarkContactedModalProps> = ({
  open,
  onClose,
  onSuccess,
  eventCode,
  speakerId,
  speakerName,
}) => {
  const { t } = useTranslation('organizer');
  const recordOutreachMutation = useRecordOutreach();

  const initialFormData: FormData = {
    contactMethod: '',
    contactDate: new Date().toISOString().slice(0, 16), // Default to now (datetime-local format)
    notes: '',
  };

  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [errors, setErrors] = useState<FormErrors>({});

  // Reset form when modal opens
  useEffect(() => {
    if (open) {
      setFormData({
        ...initialFormData,
        contactDate: new Date().toISOString().slice(0, 16),
      });
      setErrors({});
    }
  }, [open]);

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
    if (!validateForm()) {
      return;
    }

    try {
      await recordOutreachMutation.mutateAsync({
        eventCode,
        speakerId,
        request: {
          contactMethod: formData.contactMethod as ContactMethod,
          contactDate: new Date(formData.contactDate).toISOString(),
          notes: formData.notes || undefined,
        },
      });

      onSuccess?.();
      onClose();
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

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      data-testid="mark-contacted-modal"
    >
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          {t('speakerOutreach.markContactedModal.title')}
          <IconButton onClick={onClose} size="small" aria-label="close">
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent>
        <Box display="flex" flexDirection="column" gap={2} mt={1}>
          {recordOutreachMutation.isError && (
            <Alert severity="error">{t('speakerOutreach.markContactedModal.error.failed')}</Alert>
          )}

          <TextField label={t('common:role.speaker')} value={speakerName} disabled fullWidth />

          <FormControl fullWidth error={!!errors.contactMethod} required>
            <InputLabel>{t('speakerOutreach.contactMethod')}</InputLabel>
            <Select
              value={formData.contactMethod}
              onChange={(e) => handleContactMethodChange(e.target.value)}
              label={t('speakerOutreach.contactMethod')}
              data-testid="contact-method-select"
            >
              <MenuItem value="email">
                {t('speakerOutreach.markContactedModal.method.email')}
              </MenuItem>
              <MenuItem value="phone">
                {t('speakerOutreach.markContactedModal.method.phone')}
              </MenuItem>
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
            InputLabelProps={{ shrink: true }}
            data-testid="contact-date"
          />

          <TextField
            label={t('common:labels.notes')}
            value={formData.notes}
            onChange={(e) => handleNotesChange(e.target.value)}
            multiline
            rows={4}
            fullWidth
            placeholder={t('speakerOutreach.markContactedModal.notesPlaceholder')}
            data-testid="contact-notes"
          />
        </Box>
      </DialogContent>

      <DialogActions>
        <Button
          onClick={onClose}
          disabled={recordOutreachMutation.isPending}
          data-testid="cancel-button"
        >
          {t('common:actions.cancel')}
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={recordOutreachMutation.isPending}
          data-testid="save-button"
        >
          {recordOutreachMutation.isPending
            ? t('common.saving')
            : t('speakerOutreach.markContacted')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MarkContactedModal;
