/**
 * ForgotPasswordForm Component (TDD - GREEN Phase)
 * Story 1.2.2: Implement Forgot Password Flow
 * Task 7: Enhanced error handling with countdown timer and retry button
 */

import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { Box, TextField, Button, Typography, Link, Alert, CircularProgress } from '@mui/material';
import { ArrowBack, Refresh } from '@mui/icons-material';
import { useForgotPassword, ExtendedError } from '@hooks/useForgotPassword';
import { ForgotPasswordConfirmation } from '../ForgotPasswordConfirmation/ForgotPasswordConfirmation';

interface ForgotPasswordFormData {
  email: string;
}

export const ForgotPasswordForm: React.FC = () => {
  const { t } = useTranslation(['auth', 'common']);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [submittedEmail, setSubmittedEmail] = useState('');
  const [rateLimitCountdown, setRateLimitCountdown] = useState(0);

  const { mutate: forgotPassword, isPending, isError, error, reset } = useForgotPassword();

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
    getValues,
  } = useForm<ForgotPasswordFormData>({
    mode: 'onBlur',
  });

  // Countdown timer for rate limit errors
  useEffect(() => {
    if (rateLimitCountdown > 0) {
      const timer = setTimeout(() => setRateLimitCountdown(rateLimitCountdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [rateLimitCountdown]);

  // Set countdown when rate limit error occurs
  useEffect(() => {
    if (error && (error as ExtendedError).type === 'rateLimitExceeded') {
      const retryAfter = (error as ExtendedError).retryAfter || 60;
      setRateLimitCountdown(retryAfter);
    }
  }, [error]);

  const onSubmit = async (data: ForgotPasswordFormData) => {
    forgotPassword(data.email, {
      onSuccess: () => {
        setSubmittedEmail(data.email);
        setShowConfirmation(true);
      },
    });
  };

  const handleRetry = () => {
    reset(); // Clear error state
    const email = getValues('email');
    if (email) {
      onSubmit({ email });
    }
  };

  // If confirmation screen should be shown, render it instead
  if (showConfirmation) {
    return <ForgotPasswordConfirmation email={submittedEmail} />;
  }

  // Get error info
  const extendedError = error as ExtendedError | undefined;
  const errorType = extendedError?.type;
  const isRateLimitError = errorType === 'rateLimitExceeded';
  const isNetworkError = errorType === 'networkError';

  return (
    <Box sx={{ maxWidth: 400, mx: 'auto', mt: 8 }}>
      <Link
        href="/auth/login"
        sx={{
          display: 'flex',
          alignItems: 'center',
          mb: 2,
          textDecoration: 'none',
          color: 'primary.main',
        }}
      >
        <ArrowBack sx={{ mr: 1 }} />
        {t('auth:forgot.backToLogin')}
      </Link>

      <Typography variant="h4" align="center" gutterBottom>
        {t('auth:forgot.title')}
      </Typography>
      <Typography variant="body2" align="center" color="text.secondary" sx={{ mb: 3 }}>
        {t('auth:forgot.subtitle')}
      </Typography>

      {isError && errorType && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            isNetworkError && (
              <Button color="inherit" size="small" onClick={handleRetry} startIcon={<Refresh />}>
                Retry
              </Button>
            )
          }
        >
          {isRateLimitError ? (
            <Typography variant="body2">
              {t('auth:forgot.errors.rateLimitExceeded', {
                seconds: rateLimitCountdown,
              })}
            </Typography>
          ) : (
            <Typography variant="body2">{t(`auth:forgot.errors.${errorType}`)}</Typography>
          )}
        </Alert>
      )}

      <Box component="form" onSubmit={handleSubmit(onSubmit)}>
        <TextField
          {...register('email', {
            required: t('auth:forgot.errors.emailRequired'),
            pattern: {
              value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
              message: t('auth:forgot.errors.emailInvalid'),
            },
            maxLength: {
              value: 255,
              message: t('auth:forgot.errors.emailTooLong'),
            },
          })}
          label={t('auth:forgot.emailLabel')}
          placeholder={t('auth:forgot.emailPlaceholder')}
          type="email"
          fullWidth
          margin="normal"
          error={!!errors.email}
          helperText={errors.email?.message}
          disabled={isPending}
        />

        <Button
          type="submit"
          fullWidth
          variant="contained"
          size="large"
          disabled={!isValid || isPending || rateLimitCountdown > 0}
          sx={{ mt: 2, mb: 2 }}
          startIcon={isPending ? <CircularProgress size={20} /> : null}
        >
          {isPending
            ? t('common:actions.sending')
            : rateLimitCountdown > 0
              ? t('common:status.wait', { seconds: rateLimitCountdown })
              : t('auth:forgot.submitButton')}
        </Button>

        <Alert severity="info" icon="ℹ️">
          <Typography variant="body2">{t('auth:forgot.linkExpires')}</Typography>
        </Alert>
      </Box>
    </Box>
  );
};
