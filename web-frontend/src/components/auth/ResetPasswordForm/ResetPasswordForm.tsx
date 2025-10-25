/**
 * ResetPasswordForm Component (TDD - GREEN Phase)
 * Story 1.2.2a: Reset Password Confirmation
 *
 * Implements password reset confirmation with code verification
 * Following patterns from Story 1.2.1 (i18n Foundation)
 */

import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
  TextField,
  Button,
  Typography,
  Link,
  Alert,
  CircularProgress,
  IconButton,
  InputAdornment,
  LinearProgress,
} from '@mui/material';
import { ArrowBack, Visibility, VisibilityOff } from '@mui/icons-material';
import { useResetPassword, type ResetPasswordError } from '@/hooks/useResetPassword';
import { authService } from '@/services/auth/authService';
import {
  calculatePasswordStrength,
  getPasswordStrengthColor,
  getPasswordStrengthValue,
  type PasswordStrength,
} from '@/utils/passwordStrength';

interface ResetPasswordFormData {
  code: string;
  newPassword: string;
  confirmPassword: string;
}

export const ResetPasswordForm: React.FC = () => {
  const { t } = useTranslation(['auth', 'validation']);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const email = searchParams.get('email') || '';
  const codeFromUrl = searchParams.get('code') || '';

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [passwordStrength, setPasswordStrength] = useState<PasswordStrength | null>(null);
  const [isAutoLoggingIn, setIsAutoLoggingIn] = useState(false);

  const { mutate: resetPassword, isPending, isError, isSuccess, error } = useResetPassword();

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isValid },
  } = useForm<ResetPasswordFormData>({
    mode: 'onChange',
    defaultValues: {
      code: codeFromUrl,
    },
  });

  const newPassword = watch('newPassword');

  // Calculate password strength using utility
  useEffect(() => {
    const { strength } = calculatePasswordStrength(newPassword);
    setPasswordStrength(strength);
  }, [newPassword]);

  const onSubmit = async (data: ResetPasswordFormData) => {
    resetPassword(
      {
        email,
        code: data.code,
        newPassword: data.newPassword,
      },
      {
        onSuccess: async () => {
          // Attempt auto-login after successful password reset
          try {
            setIsAutoLoggingIn(true);

            const signInResult = await authService.signIn({
              email,
              password: data.newPassword,
              rememberMe: false, // Don't persist session for auto-login
            });

            if (signInResult.success && signInResult.user) {
              // Auto-login successful - redirect to dashboard after short delay
              setTimeout(() => {
                navigate('/dashboard', { replace: true });
              }, 1500);
            } else {
              // Auto-login failed - redirect to login with success message
              setTimeout(() => {
                navigate('/login', {
                  state: { message: t('auth:reset.successMessage') },
                });
              }, 2000);
            }
          } catch (error) {
            // Auto-login failed - redirect to login with success message
            console.error('[ResetPasswordForm] Auto-login error:', error);
            setTimeout(() => {
              navigate('/login', {
                state: { message: t('auth:reset.successMessage') },
              });
            }, 2000);
          } finally {
            setIsAutoLoggingIn(false);
          }
        },
      }
    );
  };

  const handleRequestNewCode = () => {
    navigate('/auth/forgot-password');
  };

  // Show success state
  if (isSuccess) {
    return (
      <Box sx={{ maxWidth: 400, mx: 'auto', mt: 8, textAlign: 'center' }}>
        <Alert severity="success" sx={{ mb: 3 }}>
          {t('auth:reset.successMessage')}
        </Alert>
        <Typography variant="body2" color="text.secondary">
          {isAutoLoggingIn
            ? t('auth:reset.loggingIn', 'Logging you in...')
            : t('auth:reset.redirecting')}
        </Typography>
        <CircularProgress size={24} sx={{ mt: 2 }} />
      </Box>
    );
  }

  // Get error info
  const resetPasswordError = error as ResetPasswordError | undefined;
  const errorType = resetPasswordError?.type;

  const getErrorMessage = () => {
    if (!errorType) return null;

    switch (errorType) {
      case 'invalidCode':
        return t('auth:reset.errors.invalidCode');
      case 'expiredCode':
        return t('auth:reset.errors.expiredCode');
      case 'invalidPassword':
        return t('auth:reset.errors.invalidPassword');
      case 'rateLimitExceeded':
        return t('auth:reset.errors.rateLimitExceeded');
      default:
        return t('auth:reset.errors.networkError');
    }
  };

  return (
    <Box sx={{ maxWidth: 400, mx: 'auto', mt: 8 }}>
      <Link
        href="/login"
        sx={{
          display: 'flex',
          alignItems: 'center',
          mb: 2,
          textDecoration: 'none',
          color: 'primary.main',
        }}
      >
        <ArrowBack sx={{ mr: 1 }} />
        {t('auth:reset.backToLogin')}
      </Link>

      <Typography variant="h4" align="center" gutterBottom>
        {t('auth:reset.title')}
      </Typography>
      <Typography variant="body2" align="center" color="text.secondary" sx={{ mb: 3 }}>
        {t('auth:reset.subtitle')}
      </Typography>

      {isError && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            errorType === 'expiredCode' ? (
              <Button color="inherit" size="small" onClick={handleRequestNewCode}>
                {t('auth:reset.requestNewCode')}
              </Button>
            ) : undefined
          }
        >
          {getErrorMessage()}
        </Alert>
      )}

      <Box component="form" onSubmit={handleSubmit(onSubmit)}>
        <TextField
          label={t('auth:reset.emailLabel')}
          type="email"
          fullWidth
          margin="normal"
          value={email}
          disabled
          InputProps={{
            readOnly: true,
          }}
        />

        <TextField
          {...register('code', {
            required: t('auth:reset.errors.invalidCode'),
            pattern: {
              value: /^\d{6}$/,
              message: t('auth:reset.errors.invalidCode'),
            },
          })}
          label={t('auth:reset.codeLabel')}
          type="text"
          fullWidth
          margin="normal"
          error={!!errors.code}
          helperText={errors.code?.message}
          disabled={isPending}
          inputProps={{
            maxLength: 6,
            inputMode: 'numeric',
            pattern: '[0-9]*',
          }}
        />

        <TextField
          {...register('newPassword', {
            required: t('auth:reset.errors.invalidPassword'),
            minLength: {
              value: 8,
              message: t('auth:reset.errors.invalidPassword'),
            },
            pattern: {
              value: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/,
              message: t('auth:reset.errors.invalidPassword'),
            },
          })}
          label={t('auth:reset.newPasswordLabel')}
          type={showPassword ? 'text' : 'password'}
          fullWidth
          margin="normal"
          error={!!errors.newPassword}
          helperText={errors.newPassword?.message}
          disabled={isPending}
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword(!showPassword)}
                  edge="end"
                >
                  {showPassword ? <VisibilityOff /> : <Visibility />}
                </IconButton>
              </InputAdornment>
            ),
          }}
        />

        {newPassword && passwordStrength && (
          <Box sx={{ mt: 1, mb: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
              <Typography variant="caption" color="text.secondary">
                Password strength:
              </Typography>
              <Typography
                variant="caption"
                color={`${getPasswordStrengthColor(passwordStrength)}.main`}
              >
                {t(`auth:reset.passwordStrength.${passwordStrength}`)}
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={getPasswordStrengthValue(passwordStrength)}
              color={getPasswordStrengthColor(passwordStrength)}
              sx={{ height: 6, borderRadius: 1 }}
            />
          </Box>
        )}

        <TextField
          {...register('confirmPassword', {
            required: t('auth:reset.errors.passwordMismatch'),
            validate: (value) => value === newPassword || t('auth:reset.errors.passwordMismatch'),
          })}
          label={t('auth:reset.confirmPasswordLabel')}
          type={showConfirmPassword ? 'text' : 'password'}
          fullWidth
          margin="normal"
          error={!!errors.confirmPassword}
          helperText={errors.confirmPassword?.message}
          disabled={isPending}
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  edge="end"
                >
                  {showConfirmPassword ? <VisibilityOff /> : <Visibility />}
                </IconButton>
              </InputAdornment>
            ),
          }}
        />

        <Alert severity="info" icon="ℹ️" sx={{ mt: 2, mb: 2 }}>
          <Typography variant="body2">{t('auth:reset.passwordRequirements')}</Typography>
        </Alert>

        <Button
          type="submit"
          fullWidth
          variant="contained"
          size="large"
          disabled={isPending || !isValid}
          sx={{ mt: 2, mb: 2 }}
          startIcon={isPending ? <CircularProgress size={20} /> : null}
        >
          {isPending ? t('common:actions.loading') : t('auth:reset.submitButton')}
        </Button>
      </Box>
    </Box>
  );
};
