/**
 * LoginForm Component Implementation
 * Story 1.2.1: AWS Cognito Authentication UI with i18n
 */

import React, { useState, useEffect } from 'react';
import {
  Box,
  TextField,
  Button,
  Typography,
  FormControlLabel,
  Checkbox,
  Alert,
  CircularProgress,
  Link,
  Paper,
  Container,
  IconButton,
  InputAdornment,
} from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@hooks/useAuth';
import { LoginCredentials } from '@/types/auth';

type LoginFormData = {
  email: string;
  password: string;
  rememberMe?: boolean;
};

interface LoginFormProps {
  onSuccess?: () => void;
  onForgotPassword?: () => void;
  onSignUp?: () => void;
}

// Factory function for localized validation schema
const createLoginSchema = (t: (key: string) => string) =>
  z.object({
    email: z
      .string()
      .min(1, t('validation:email.required'))
      .email(t('validation:email.invalid'))
      .max(255, t('validation:email.tooLong')),
    password: z
      .string()
      .min(1, t('validation:password.required'))
      .min(8, t('validation:password.tooShort'))
      .max(128, t('validation:password.tooLong')),
    rememberMe: z.boolean().default(false),
  });

// Map error codes to i18n keys
const getErrorTranslationKey = (errorCode?: string): string => {
  const errorMap: Record<string, string> = {
    INVALID_CREDENTIALS: 'auth:errors.invalidCredentials',
    EMAIL_NOT_VERIFIED: 'auth:errors.emailNotVerified',
    ACCOUNT_LOCKED: 'auth:errors.accountLocked',
    NETWORK_ERROR: 'auth:errors.networkError',
  };
  return errorMap[errorCode || ''] || 'auth:errors.unknownError';
};

export const LoginForm: React.FC<LoginFormProps> = ({ onSuccess, onForgotPassword, onSignUp }) => {
  const { t } = useTranslation(['auth', 'validation']);
  const { signIn, isLoading, error, clearError } = useAuth();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const {
    control,
    handleSubmit,
    formState: { errors, isValid },
    watch,
    reset,
  } = useForm<LoginFormData>({
    resolver: zodResolver(createLoginSchema(t)),
    defaultValues: {
      email: '',
      password: '',
      rememberMe: false,
    },
    mode: 'onBlur',
  });

  // Clear errors when user starts typing
  const watchedEmail = watch('email');
  const watchedPassword = watch('password');

  useEffect(() => {
    if (error || submitError) {
      clearError();
      setSubmitError(null);
    }
  }, [watchedEmail, watchedPassword, error, submitError, clearError]);

  const onSubmit = async (data: LoginFormData) => {
    try {
      setSubmitError(null);

      const credentials: LoginCredentials = {
        email: data.email,
        password: data.password,
        rememberMe: data.rememberMe,
      };

      const success = await signIn(credentials);

      if (success) {
        reset();
        onSuccess?.();
      }
    } catch (err) {
      setSubmitError(t('auth:errors.unknownError'));
    }
  };

  const handleForgotPassword = () => {
    onForgotPassword?.();
  };

  const handleSignUp = () => {
    onSignUp?.();
  };

  // Get localized error message
  const displayError = error ? t(getErrorTranslationKey(error.code)) : submitError;

  return (
    <Container maxWidth="sm">
      <Paper elevation={3} sx={{ p: 4, mt: 4 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <Typography component="h1" variant="h4" gutterBottom>
            {t('auth:login.title')}
          </Typography>

          <Typography variant="body2" color="text.secondary" align="center" sx={{ mb: 3 }}>
            {t('auth:login.subtitle')}
          </Typography>

          {displayError && (
            <Alert severity="error" sx={{ width: '100%', mb: 2 }}>
              {displayError}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit(onSubmit)} sx={{ width: '100%' }}>
            <Controller
              name="email"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  label={t('auth:login.emailLabel')}
                  placeholder={t('auth:login.emailPlaceholder')}
                  type="email"
                  autoComplete="email username"
                  autoFocus
                  margin="normal"
                  error={!!errors.email}
                  helperText={errors.email?.message}
                  disabled={isLoading}
                />
              )}
            />

            <Controller
              name="password"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  label={t('auth:login.passwordLabel')}
                  placeholder={t('auth:login.passwordPlaceholder')}
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  margin="normal"
                  error={!!errors.password}
                  helperText={errors.password?.message}
                  disabled={isLoading}
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          aria-label={
                            showPassword
                              ? t('auth:login.hidePassword')
                              : t('auth:login.showPassword')
                          }
                          onClick={() => setShowPassword(!showPassword)}
                          edge="end"
                        >
                          {showPassword ? <VisibilityOff /> : <Visibility />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                />
              )}
            />

            <Controller
              name="rememberMe"
              control={control}
              render={({ field: { value, onChange, ...field } }) => (
                <FormControlLabel
                  control={
                    <Checkbox
                      {...field}
                      checked={value}
                      onChange={(e) => onChange(e.target.checked)}
                      disabled={isLoading}
                    />
                  }
                  label={t('auth:login.rememberMe')}
                  sx={{ mt: 1 }}
                />
              )}
            />

            <Button
              type="submit"
              fullWidth
              variant="contained"
              sx={{ mt: 3, mb: 2 }}
              disabled={isLoading || !isValid}
              startIcon={isLoading ? <CircularProgress size={20} /> : null}
            >
              {t('auth:login.signInButton')}
            </Button>

            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2 }}>
              <Link
                component="button"
                variant="body2"
                type="button"
                onClick={handleForgotPassword}
                disabled={isLoading}
                sx={{ textDecoration: 'none' }}
              >
                {t('auth:login.forgotPassword')}
              </Link>

              <Box sx={{ textAlign: 'right' }}>
                <Typography variant="body2" component="span" color="text.secondary">
                  {t('auth:login.noAccount')}{' '}
                </Typography>
                <Link
                  component="button"
                  variant="body2"
                  type="button"
                  onClick={handleSignUp}
                  disabled={isLoading}
                  sx={{ textDecoration: 'none' }}
                >
                  {t('auth:login.createAccount')}
                </Link>
              </Box>
            </Box>
          </Box>
        </Box>
      </Paper>
    </Container>
  );
};
