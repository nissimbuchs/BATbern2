/**
 * EmailVerification Component
 * Story 1.2.4: Email Verification Flow
 * Primary flow: user clicks the verification link from their email.
 * Fallback: manual 6-digit code entry (collapsed by default).
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Box, Typography, Button, Alert, CircularProgress, Collapse } from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { CheckCircle, Email as EmailIcon } from '@mui/icons-material';
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
  const [showManualEntry, setShowManualEntry] = useState(false);
  const hasAutoVerifiedRef = React.useRef(false);

  const email = searchParams.get('email') || '';
  const autoCode = searchParams.get('code');

  const {
    mutate: verify,
    isPending: isLoading,
    error,
  } = useEmailVerification({
    onSuccess: () => {
      setIsVerified(true);
      sessionStorage.setItem('userRole', 'ATTENDEE');
    },
  });
  const { mutate: resend } = useResendVerification();

  const handleVerification = useCallback(
    (verificationCode: string) => {
      verify({ email, code: verificationCode });
    },
    [email, verify]
  );

  // Auto-verification from email link (prevent double execution)
  useEffect(() => {
    if (autoCode && email && !hasAutoVerifiedRef.current) {
      hasAutoVerifiedRef.current = true;
      handleVerification(autoCode);
    }
  }, [autoCode, email, handleVerification]);

  // Redirect countdown - redirect to login after verification
  useEffect(() => {
    if (isVerified && redirectCountdown > 0) {
      const timer = setTimeout(() => setRedirectCountdown(redirectCountdown - 1), 1000);
      return () => clearTimeout(timer);
    } else if (isVerified && redirectCountdown === 0) {
      // Redirect to login page after email verification
      navigate('/auth/login');
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
        <BATbernLoader size={128} />
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
          onClick={() => navigate('/auth/login')}
          sx={{ mb: 2 }}
        >
          {t('verify.goToLogin')}
        </Button>
        <Typography variant="body2" color="text.secondary">
          {t('verify.redirecting', { seconds: redirectCountdown })}
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 500, mx: 'auto', mt: 8 }}>
      {/* Primary: check-your-email messaging */}
      <Box sx={{ textAlign: 'center', mb: 4 }}>
        <EmailIcon sx={{ fontSize: 64, color: 'primary.main', mb: 2 }} />
        <Typography variant="h4" gutterBottom>
          {t('verify.title')}
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 1 }}>
          {t('verify.subtitle')}
        </Typography>
        <Typography variant="h6" sx={{ mb: 2 }}>
          {email}
        </Typography>
        <Typography variant="body1">{t('verify.instruction')}</Typography>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t(`verify.errors.${error.message}`)}
        </Alert>
      )}

      {/* Resend */}
      <Box sx={{ textAlign: 'center', mb: 3 }}>
        <Button onClick={handleResend} disabled={resendCooldown > 0} variant="outlined" fullWidth>
          {resendCooldown > 0
            ? t('verify.resendCooldown', { seconds: resendCooldown })
            : t('verify.resendLink')}
        </Button>
      </Box>

      {/* Fallback: manual code entry */}
      <Box sx={{ textAlign: 'center' }}>
        <Button
          size="small"
          onClick={() => setShowManualEntry((v) => !v)}
          sx={{ color: 'text.secondary' }}
        >
          {showManualEntry ? t('verify.manualHide') : t('verify.manualToggle')}
        </Button>
      </Box>

      <Collapse in={showManualEntry} unmountOnExit>
        <Box sx={{ mt: 2 }}>
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
            sx={{ mt: 3 }}
          >
            {isLoading ? <CircularProgress size={24} /> : t('verify.submitButton')}
          </Button>
        </Box>
      </Collapse>

      <Alert severity="info" sx={{ mt: 4 }}>
        <Typography variant="body2">{t('verify.expirationNotice')}</Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>
          {t('verify.checkSpam')}
        </Typography>
      </Alert>
    </Box>
  );
};

export default EmailVerification;
