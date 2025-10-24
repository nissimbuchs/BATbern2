/**
 * EmailVerification Component
 * Story 1.2.4: Email Verification Flow
 * Allows users to verify their email with a 6-digit code
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchAuthSession } from 'aws-amplify/auth';
import { Box, Typography, Button, Alert, CircularProgress } from '@mui/material';
import { CheckCircle } from '@mui/icons-material';
import { CodeInput } from '../CodeInput/CodeInput';
import { useEmailVerification, useResendVerification } from '@/hooks/useEmailVerification';

export const EmailVerification: React.FC = () => {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [code, setCode] = useState('');
  const [isVerified, setIsVerified] = useState(false);
  const [redirectCountdown, setRedirectCountdown] = useState(3);
  const [resendCooldown, setResendCooldown] = useState(0);

  const email = searchParams.get('email') || '';
  const autoCode = searchParams.get('code');

  const { mutate: verify, isPending: isLoading, error } = useEmailVerification();
  const { mutate: resend } = useResendVerification();

  const handleVerification = useCallback(
    async (verificationCode: string) => {
      verify(
        { email, code: verificationCode },
        {
          onSuccess: async () => {
            setIsVerified(true);

            try {
              const session = await fetchAuthSession({ forceRefresh: true });
              const role =
                (session.tokens?.idToken?.payload['cognito:groups'] as string[])?.[0] || 'ATTENDEE';
              sessionStorage.setItem('userRole', role);
            } catch (err) {
              console.error('[EmailVerification] Failed to fetch role:', err);
              sessionStorage.setItem('userRole', 'ATTENDEE');
            }
          },
        }
      );
    },
    [email, verify]
  );

  // Auto-verification from email link
  useEffect(() => {
    if (autoCode && email) {
      handleVerification(autoCode);
    }
  }, [autoCode, email, handleVerification]);

  // Redirect countdown
  useEffect(() => {
    if (isVerified && redirectCountdown > 0) {
      const timer = setTimeout(() => setRedirectCountdown(redirectCountdown - 1), 1000);
      return () => clearTimeout(timer);
    } else if (isVerified && redirectCountdown === 0) {
      const role = sessionStorage.getItem('userRole') || 'ATTENDEE';
      navigate(`/${role.toLowerCase()}/dashboard`);
    }
  }, [isVerified, redirectCountdown, navigate]);

  // Resend cooldown
  useEffect(() => {
    if (resendCooldown > 0) {
      const timer = setTimeout(() => setResendCooldown(resendCooldown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [resendCooldown]);

  const handleResend = () => {
    resend(email, {
      onSuccess: () => {
        setResendCooldown(60);
      },
    });
  };

  if (autoCode && isLoading) {
    return (
      <Box sx={{ maxWidth: 400, mx: 'auto', mt: 8, textAlign: 'center' }}>
        <CircularProgress size={64} />
        <Typography variant="h5" sx={{ mt: 2 }}>
          {t('verify.verifying')}
        </Typography>
      </Box>
    );
  }

  if (isVerified) {
    return (
      <Box sx={{ maxWidth: 400, mx: 'auto', mt: 8, textAlign: 'center' }}>
        <CheckCircle sx={{ fontSize: 64, color: 'success.main', mb: 2 }} />
        <Typography variant="h4" gutterBottom>
          {t('verify.successTitle')}
        </Typography>
        <Typography variant="body1" sx={{ mb: 3 }}>
          {t('verify.successMessage')}
        </Typography>
        <Button
          variant="contained"
          fullWidth
          onClick={() => {
            const role = sessionStorage.getItem('userRole') || 'ATTENDEE';
            navigate(`/${role.toLowerCase()}/dashboard`);
          }}
          sx={{ mb: 2 }}
        >
          {t('verify.goToDashboard')}
        </Button>
        <Typography variant="body2" color="text.secondary">
          {t('verify.redirecting', { seconds: redirectCountdown })}
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 500, mx: 'auto', mt: 8 }}>
      <Typography variant="h4" align="center" gutterBottom>
        {t('verify.title')}
      </Typography>
      <Typography variant="body2" align="center" color="text.secondary" sx={{ mb: 1 }}>
        {t('verify.subtitle')}
      </Typography>
      <Typography variant="h6" align="center" sx={{ mb: 3 }}>
        {email}
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t(`verify.errors.${error.message}`)}
        </Alert>
      )}

      <Typography variant="body2" align="center" sx={{ mb: 2 }}>
        {t('verify.codeLabel')}
      </Typography>

      <CodeInput
        length={6}
        onComplete={handleVerification}
        onCodeChange={setCode}
        error={!!error}
      />

      <Button
        variant="contained"
        fullWidth
        size="large"
        onClick={() => handleVerification(code)}
        disabled={code.length !== 6 || isLoading}
        sx={{ mt: 3, mb: 2 }}
      >
        {isLoading ? <CircularProgress size={24} /> : t('verify.submitButton')}
      </Button>

      <Box sx={{ textAlign: 'center', mt: 3 }}>
        <Button onClick={handleResend} disabled={resendCooldown > 0} sx={{ mt: 1 }}>
          {resendCooldown > 0
            ? t('verify.resendCooldown', { seconds: resendCooldown })
            : t('verify.resendLink')}
        </Button>
      </Box>

      <Alert severity="info" sx={{ mt: 3 }}>
        <Typography variant="body2">{t('verify.expirationNotice')}</Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>
          {t('verify.checkSpam')}
        </Typography>
      </Alert>
    </Box>
  );
};

export default EmailVerification;
