/**
 * SpeakerEditModal Component
 *
 * Modal for editing speaker details in the speaker pool.
 * Editable fields: speakerName, company, expertise, assignedOrganizerId, notes, email, phone
 * Non-editable: status (read-only display), sessionId (managed by workflow)
 */

import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Typography,
  IconButton,
  Alert,
  Chip,
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useUpdateSpeakerInPool } from '@/hooks/useSpeakerPool';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect';
import type { SpeakerPoolEntry, UpdateSpeakerPoolRequest } from '@/types/speakerPool.types';

export interface SpeakerEditModalProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
  organizers?: Array<{ id: string; name: string }>;
  onSuccess?: () => void;
}

interface FormErrors {
  speakerName?: string;
  email?: string;
  phone?: string;
}

export const SpeakerEditModal: React.FC<SpeakerEditModalProps> = ({
  open,
  onClose,
  speaker,
  eventCode,
  organizers = [],
  onSuccess,
}) => {
  const { t } = useTranslation('organizer');
  const updateMutation = useUpdateSpeakerInPool();

  // Form state
  const [speakerName, setSpeakerName] = useState('');
  const [company, setCompany] = useState('');
  const [expertise, setExpertise] = useState('');
  const [assignedOrganizerId, setAssignedOrganizerId] = useState<string>('');
  const [notes, setNotes] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});

  // Initialize form when modal opens or speaker changes
  useEffect(() => {
    if (open && speaker) {
      setSpeakerName(speaker.speakerName || '');
      setCompany(speaker.company || '');
      setExpertise(speaker.expertise || '');
      setAssignedOrganizerId(speaker.assignedOrganizerId || '');
      setNotes(speaker.notes || '');
      setEmail(speaker.email || '');
      setPhone(speaker.phone || '');
      setErrors({});
    }
  }, [open, speaker]);

  const validateEmail = (emailValue: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(emailValue);
  };

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {};

    if (!speakerName.trim()) {
      newErrors.speakerName = t(
        'speakerBrainstorm.errors.nameRequired',
        'Speaker name is required'
      );
    }

    if (!email.trim()) {
      newErrors.email = t('speakerBrainstorm.errors.emailRequired', 'Email is required');
    } else if (!validateEmail(email)) {
      newErrors.email = t('speakerBrainstorm.errors.emailInvalid', 'Email must be valid');
    }

    if (!phone.trim()) {
      newErrors.phone = t('speakerBrainstorm.errors.phoneRequired', 'Phone is required');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validateForm() || !speaker) {
      return;
    }

    const request: UpdateSpeakerPoolRequest = {
      speakerName: speakerName.trim(),
      company: company.trim() || undefined,
      expertise: expertise.trim() || undefined,
      assignedOrganizerId: assignedOrganizerId || undefined,
      notes: notes.trim() || undefined,
      email: email.trim(),
      phone: phone.trim(),
    };

    updateMutation.mutate(
      { eventCode, speakerId: speaker.id, request },
      {
        onSuccess: () => {
          onSuccess?.();
          onClose();
        },
      }
    );
  };

  const handleClose = () => {
    if (!updateMutation.isPending) {
      onClose();
    }
  };

  if (!speaker) {
    return null;
  }

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      aria-labelledby="speaker-edit-dialog-title"
    >
      <DialogTitle id="speaker-edit-dialog-title">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            {t('speakerBrainstorm.modal.editTitle', 'Edit Speaker')}
          </Typography>
          <IconButton
            edge="end"
            color="inherit"
            onClick={handleClose}
            aria-label="close"
            disabled={updateMutation.isPending}
          >
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        <Box component="form" noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {/* Status (read-only) */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
            <Typography variant="body2" color="text.secondary">
              {t('speakerBrainstorm.modal.status', 'Status')}:
            </Typography>
            <Chip
              label={speaker.status}
              size="small"
              color={speaker.status === 'IDENTIFIED' ? 'default' : 'primary'}
            />
            <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
              ({t('speakerBrainstorm.modal.statusReadOnly', 'managed by workflow')})
            </Typography>
          </Box>

          {/* Speaker Name */}
          <TextField
            required
            fullWidth
            label={t('speakerBrainstorm.form.speakerName', 'Speaker Name')}
            value={speakerName}
            onChange={(e) => {
              setSpeakerName(e.target.value);
              if (errors.speakerName) {
                setErrors((prev) => ({ ...prev, speakerName: undefined }));
              }
            }}
            error={!!errors.speakerName}
            helperText={errors.speakerName}
            disabled={updateMutation.isPending}
            autoFocus
          />

          {/* Company */}
          <TextField
            fullWidth
            label={t('speakerBrainstorm.form.company', 'Company')}
            value={company}
            onChange={(e) => setCompany(e.target.value)}
            disabled={updateMutation.isPending}
          />

          {/* Expertise */}
          <TextField
            fullWidth
            label={t('speakerBrainstorm.form.expertise', 'Expertise')}
            value={expertise}
            onChange={(e) => setExpertise(e.target.value)}
            disabled={updateMutation.isPending}
          />

          {/* Assigned Organizer */}
          <OrganizerSelect
            value={assignedOrganizerId}
            onChange={(organizerId) => setAssignedOrganizerId(organizerId)}
            organizers={organizers.length > 0 ? organizers : undefined}
            label={t('speakerBrainstorm.form.assignOrganizer', 'Assign to Organizer (Optional)')}
            fullWidth
            disabled={updateMutation.isPending}
            includeUnassigned={true}
            includeAllOption={false}
          />

          {/* Notes */}
          <TextField
            fullWidth
            multiline
            rows={3}
            label={t('speakerBrainstorm.form.notes', 'Notes (Optional)')}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            disabled={updateMutation.isPending}
          />

          {/* Email */}
          <TextField
            required
            fullWidth
            type="email"
            label={t('speakerBrainstorm.form.email', 'Email')}
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              if (errors.email) {
                setErrors((prev) => ({ ...prev, email: undefined }));
              }
            }}
            error={!!errors.email}
            helperText={errors.email}
            disabled={updateMutation.isPending}
          />

          {/* Phone */}
          <TextField
            required
            fullWidth
            label={t('speakerBrainstorm.form.phone', 'Phone')}
            value={phone}
            onChange={(e) => {
              setPhone(e.target.value);
              if (errors.phone) {
                setErrors((prev) => ({ ...prev, phone: undefined }));
              }
            }}
            error={!!errors.phone}
            helperText={errors.phone}
            disabled={updateMutation.isPending}
          />

          {/* Error Alert */}
          {updateMutation.isError && (
            <Alert severity="error">
              {t('speakerBrainstorm.modal.updateError', 'Failed to update speaker')}:{' '}
              {updateMutation.error?.message}
            </Alert>
          )}
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} variant="outlined" disabled={updateMutation.isPending}>
          {t('actions.cancel', 'Cancel')}
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="primary"
          disabled={updateMutation.isPending}
        >
          {updateMutation.isPending
            ? t('speakerBrainstorm.modal.saving', 'Saving...')
            : t('actions.save', 'Save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SpeakerEditModal;
