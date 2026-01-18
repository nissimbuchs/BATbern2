/**
 * Send Invitation Modal Component - Story 6.3
 *
 * Modal dialog for organizers to send speaker invitations
 * Features:
 * - Personal message textarea with placeholder
 * - Expiration days selector
 * - Speaker info display
 * - Form validation (email required)
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
  Typography,
  Chip,
} from '@mui/material';
import { Close as CloseIcon, Email as EmailIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSendInvitation } from '@/hooks/useSendInvitation';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

interface SendInvitationModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  eventCode: string;
  speaker: SpeakerPoolEntry | null;
}

interface FormData {
  personalMessage: string;
  expirationDays: number;
}

const EXPIRATION_OPTIONS = [7, 14, 21, 30];

const SendInvitationModal: React.FC<SendInvitationModalProps> = ({
  open,
  onClose,
  onSuccess,
  eventCode,
  speaker,
}) => {
  const { t } = useTranslation(['organizer', 'common']);
  const sendInvitationMutation = useSendInvitation();

  const initialFormData: FormData = {
    personalMessage: '',
    expirationDays: 14,
  };

  const [formData, setFormData] = useState<FormData>(initialFormData);

  // Reset form when modal opens
  useEffect(() => {
    if (open) {
      setFormData(initialFormData);
    }
  }, [open]);

  const handleSubmit = async () => {
    // Use speaker pool ID (speaker.id) instead of username
    // This allows sending invitations to speakers without user accounts
    if (!speaker?.id) {
      return;
    }

    try {
      await sendInvitationMutation.mutateAsync({
        eventCode,
        request: {
          speakerPoolId: speaker.id,
          personalMessage: formData.personalMessage || undefined,
          expirationDays: formData.expirationDays,
        },
      });

      onSuccess?.();
      onClose();
    } catch (error) {
      console.error('Failed to send invitation:', error);
    }
  };

  const handlePersonalMessageChange = (value: string) => {
    setFormData({ ...formData, personalMessage: value });
  };

  const handleExpirationChange = (value: number) => {
    setFormData({ ...formData, expirationDays: value });
  };

  // Check if speaker has email (required for invitation)
  const hasEmail = speaker?.email && speaker.email.trim() !== '';

  // Check if speaker has a valid ID (always true for pool entries)
  const hasSpeakerId = !!speaker?.id;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      data-testid="send-invitation-modal"
    >
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          {t('organizer:sendInvitation.title')}
          <IconButton onClick={onClose} size="small" aria-label="close">
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent>
        <Box display="flex" flexDirection="column" gap={2} mt={1}>
          {sendInvitationMutation.isError && (
            <Alert severity="error">{t('organizer:sendInvitation.error.failed')}</Alert>
          )}

          {!hasEmail && (
            <Alert severity="warning">{t('organizer:sendInvitation.error.noEmail')}</Alert>
          )}

          {/* Speaker Info */}
          <Box sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {t('organizer:sendInvitation.speakerInfo')}
            </Typography>
            <Typography variant="h6">{speaker?.speakerName}</Typography>
            {speaker?.company && (
              <Typography variant="body2" color="text.secondary">
                {speaker.company}
              </Typography>
            )}
            {hasEmail && (
              <Box display="flex" alignItems="center" gap={1} mt={1}>
                <EmailIcon fontSize="small" color="action" />
                <Typography variant="body2">{speaker?.email}</Typography>
              </Box>
            )}
            <Box mt={1}>
              <Chip label={speaker?.status} size="small" color="default" />
            </Box>
          </Box>

          {/* Personal Message */}
          <TextField
            label={t('organizer:sendInvitation.personalMessage')}
            value={formData.personalMessage}
            onChange={(e) => handlePersonalMessageChange(e.target.value)}
            multiline
            rows={4}
            fullWidth
            placeholder={t('organizer:sendInvitation.personalMessagePlaceholder')}
            helperText={t('organizer:sendInvitation.personalMessageHelp')}
            data-testid="personal-message"
          />

          {/* Expiration Days */}
          <FormControl fullWidth>
            <InputLabel>{t('organizer:sendInvitation.expirationDays')}</InputLabel>
            <Select
              value={formData.expirationDays}
              onChange={(e) => handleExpirationChange(e.target.value as number)}
              label={t('organizer:sendInvitation.expirationDays')}
              data-testid="expiration-days-select"
            >
              {EXPIRATION_OPTIONS.map((days) => (
                <MenuItem key={days} value={days}>
                  {t('organizer:sendInvitation.expirationOption', { days })}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={sendInvitationMutation.isPending}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={sendInvitationMutation.isPending || !hasEmail || !hasSpeakerId}
          data-testid="send-invitation-button"
        >
          {sendInvitationMutation.isPending
            ? t('common:actions.sending')
            : t('organizer:sendInvitation.send')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SendInvitationModal;
