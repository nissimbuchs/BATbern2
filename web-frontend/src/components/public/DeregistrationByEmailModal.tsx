/**
 * DeregistrationByEmailModal (Story 10.12 — AC9)
 *
 * Modal shown from HomePage and RegistrationWizard status guard.
 * User enters email → always shows "check your inbox" (anti-enumeration).
 */

import React, { useState } from 'react';
import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useDeregistrationByEmail } from '@/hooks/useDeregistration';

interface DeregistrationByEmailModalProps {
  open: boolean;
  onClose: () => void;
  eventCode: string;
}

export const DeregistrationByEmailModal: React.FC<DeregistrationByEmailModalProps> = ({
  open,
  onClose,
  eventCode,
}) => {
  const { t } = useTranslation(['registration', 'common']);
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const mutation = useDeregistrationByEmail();

  const validateEmail = (value: string): boolean => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  };

  const handleSubmit = () => {
    if (!validateEmail(email)) {
      setEmailError(t('deregistration.modal.emailError'));
      return;
    }
    setEmailError('');
    mutation.mutate(
      { email, eventCode },
      {
        onSettled: () => {
          // Always show success regardless of result (anti-enumeration)
          setSubmitted(true);
        },
      }
    );
  };

  const handleClose = () => {
    // Reset state on close
    setEmail('');
    setEmailError('');
    setSubmitted(false);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('deregistration.modal.title')}</DialogTitle>
      <DialogContent>
        {submitted ? (
          <Typography variant="body2" sx={{ mt: 1 }}>
            {t('deregistration.modal.successMessage')}
          </Typography>
        ) : (
          <>
            <Typography variant="body2" sx={{ mb: 2, mt: 1 }}>
              {t('deregistration.modal.body')}
            </Typography>
            <TextField
              autoFocus
              label={t('deregistration.modal.emailLabel')}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={Boolean(emailError)}
              helperText={emailError}
              fullWidth
              disabled={mutation.isPending}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSubmit();
              }}
            />
          </>
        )}
      </DialogContent>
      <DialogActions>
        {submitted ? (
          <Button onClick={handleClose}>{t('common:actions.close')}</Button>
        ) : (
          <>
            <Button onClick={handleClose} disabled={mutation.isPending}>
              {t('common:actions.close')}
            </Button>
            <Button
              onClick={handleSubmit}
              variant="contained"
              color="error"
              disabled={mutation.isPending || !email}
              startIcon={mutation.isPending ? <CircularProgress size={16} /> : undefined}
            >
              {t('deregistration.modal.submitButton')}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
};
