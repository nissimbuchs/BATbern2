/**
 * ForgotPasswordConfirmation Component (TDD - GREEN Phase)
 * Story 1.2.2: Implement Forgot Password Flow
 */

import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Box, Typography, Button, Alert, Snackbar } from '@mui/material';
import { CheckCircle } from '@mui/icons-material';
import { useResendResetLink } from '@hooks/useResendResetLink';

interface ForgotPasswordConfirmationProps {
  email: string;
}

export const ForgotPasswordConfirmation: React.FC<ForgotPasswordConfirmationProps> = ({
  email,
}) => {
  const { t } = useTranslation(['auth', 'common']);
  const [cooldown, setCooldown] = useState(0);
  const [showSuccessToast, setShowSuccessToast] = useState(false);
  const { mutate: resendLink, isLoading } = useResendResetLink();

  useEffect(() => {
    if (cooldown > 0) {
      const timer = setTimeout(() => setCooldown(cooldown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [cooldown]);

  const handleResend = () => {
    resendLink(email, {
      onSuccess: () => {
        setCooldown(60); // 60-second cooldown
        setShowSuccessToast(true);
      },
    });
  };

  // Mask email for display (u***@example.com)
  const maskEmail = (email: string) => {
    const [local, domain] = email.split('@');
    if (local.length === 1) {
      return `${local}@${domain}`;
    }
    return `${local[0]}${'*'.repeat(local.length - 1)}@${domain}`;
  };

  return (
    <Box sx={{ maxWidth: 400, mx: 'auto', mt: 8, textAlign: 'center' }}>
      <CheckCircle sx={{ fontSize: 64, color: 'success.main', mb: 2 }} />

      <Typography variant="h4" gutterBottom>
        {t('auth:forgot.confirmTitle')}
      </Typography>

      <Typography variant="body1" sx={{ mb: 2 }}>
        {t('auth:forgot.confirmMessage')}
      </Typography>

      <Typography variant="h6" sx={{ mb: 3, fontWeight: 'bold' }}>
        {maskEmail(email)}
      </Typography>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {t('auth:forgot.confirmInstructions')}
      </Typography>

      <Button variant="contained" fullWidth href="/auth/login" sx={{ mb: 2 }}>
        {t('auth:forgot.backToLogin')}
      </Button>

      <Box sx={{ mt: 3 }}>
        <Typography variant="body2" color="text.secondary">
          {t('auth:forgot.didntReceive')}
        </Typography>
        <Button onClick={handleResend} disabled={cooldown > 0 || isLoading} sx={{ mt: 1 }}>
          {cooldown > 0
            ? t('common:status.wait', { seconds: cooldown })
            : t('auth:forgot.resendButton')}
        </Button>
      </Box>

      <Alert severity="info" sx={{ mt: 3 }}>
        {t('auth:forgot.checkSpam')}
      </Alert>

      {/* Success toast notification */}
      <Snackbar
        open={showSuccessToast}
        autoHideDuration={3000}
        onClose={() => setShowSuccessToast(false)}
        message={t('auth:forgot.resendSuccess')}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      />
    </Box>
  );
};
