/**
 * ResendConfirmationAction Component
 * Issue #1141: a user who self-registered but never confirmed their email address
 * (Cognito UNCONFIRMED) was stuck on login, forgot-password and sign-up. This single
 * action, shared by all three screens, sends a fresh confirmation link (Cognito
 * resendSignUpCode via the email-verification service) and then opens the
 * verification page for that address.
 */

import React from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Box, Button, CircularProgress, Typography } from '@mui/material';
import { useResendVerification } from '@/hooks/useEmailVerification';

export interface ResendConfirmationActionProps {
  /** The address the user entered; the action is disabled while it is empty. */
  email: string;
}

export const ResendConfirmationAction: React.FC<ResendConfirmationActionProps> = ({ email }) => {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const { mutate: resend, isPending, error } = useResendVerification();
  const trimmedEmail = email.trim();

  const handleClick = () => {
    resend(trimmedEmail, {
      onSuccess: () => {
        navigate(`/auth/verify-email?email=${encodeURIComponent(trimmedEmail)}`);
      },
    });
  };

  return (
    <Box sx={{ mt: 1 }}>
      <Button
        variant="outlined"
        size="small"
        color="inherit"
        onClick={handleClick}
        disabled={!trimmedEmail || isPending}
        startIcon={isPending ? <CircularProgress size={16} /> : null}
      >
        {t('resendConfirmation.button')}
      </Button>
      {error && (
        <Typography variant="body2" role="status" sx={{ mt: 1 }}>
          {t(`verify.errors.${error.message}`, t('verify.errors.networkError'))}
        </Typography>
      )}
    </Box>
  );
};

export default ResendConfirmationAction;
