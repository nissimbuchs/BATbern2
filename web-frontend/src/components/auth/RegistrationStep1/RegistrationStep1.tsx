/**
 * RegistrationStep1 Component
 * Story 1.2.3: Implement Account Creation Flow - Task 5 (GREEN Phase)
 *
 * Step 1 of registration wizard: Personal Information
 * - Full Name, Email, Password, Confirm Password fields
 * - Real-time password strength indicator
 * - Password requirement checklist
 * - Form validation with localized error messages
 */

import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useFormContext } from 'react-hook-form';
import {
  Box,
  TextField,
  IconButton,
  InputAdornment,
  Typography,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  LinearProgress,
  Button,
} from '@mui/material';
import { Visibility, VisibilityOff, CheckCircle, RadioButtonUnchecked } from '@mui/icons-material';
import * as passwordStrength from '../../../utils/passwordStrength/passwordStrength';

const { checkPasswordRequirements, calculatePasswordStrength } = passwordStrength;
type PasswordStrength = passwordStrength.PasswordStrength;

interface RegistrationStep1Props {
  onContinue: () => void;
}

export const RegistrationStep1: React.FC<RegistrationStep1Props> = ({ onContinue }) => {
  const { t } = useTranslation('auth');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const {
    register,
    watch,
    formState: { errors },
    trigger,
  } = useFormContext();

  const password = watch('password', '');
  const requirements = checkPasswordRequirements(password);
  const strength = calculatePasswordStrength(password);

  const handleContinue = async () => {
    const isValid = await trigger(['fullName', 'email', 'password', 'confirmPassword']);
    if (isValid) {
      onContinue();
    }
  };

  const getStrengthColor = (currentStrength: PasswordStrength): 'error' | 'warning' | 'success' => {
    switch (currentStrength) {
      case 'weak':
        return 'error';
      case 'medium':
        return 'warning';
      case 'strong':
        return 'success';
    }
  };

  const getStrengthValue = (currentStrength: PasswordStrength): number => {
    switch (currentStrength) {
      case 'weak':
        return 33;
      case 'medium':
        return 66;
      case 'strong':
        return 100;
    }
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        {t('register.step1.title')}
      </Typography>

      <TextField
        {...register('fullName', {
          required: t('register.errors.fullNameRequired'),
          minLength: {
            value: 2,
            message: t('register.errors.fullNameTooShort'),
          },
          maxLength: {
            value: 100,
            message: t('register.errors.fullNameTooLong'),
          },
          pattern: {
            value: /^[a-zA-ZÄäÖöÜüß\s-]+$/,
            message: t('register.errors.fullNameInvalid'),
          },
        })}
        label={t('register.step1.fullNameLabel')}
        placeholder={t('register.step1.fullNamePlaceholder')}
        fullWidth
        margin="normal"
        error={!!errors.fullName}
        helperText={errors.fullName?.message as string}
      />

      <TextField
        {...register('email', {
          required: t('register.errors.emailRequired'),
          pattern: {
            value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
            message: t('register.errors.emailInvalid'),
          },
          maxLength: {
            value: 255,
            message: t('register.errors.emailTooLong'),
          },
        })}
        label={t('register.step1.emailLabel')}
        placeholder={t('register.step1.emailPlaceholder')}
        type="email"
        fullWidth
        margin="normal"
        error={!!errors.email}
        helperText={errors.email?.message as string}
      />

      <TextField
        {...register('password', {
          required: t('register.errors.passwordRequired'),
          minLength: {
            value: 8,
            message: t('register.errors.passwordTooShort'),
          },
          maxLength: {
            value: 128,
            message: t('register.errors.passwordTooLong'),
          },
        })}
        label={t('register.step1.passwordLabel')}
        placeholder={t('register.step1.passwordPlaceholder')}
        type={showPassword ? 'text' : 'password'}
        fullWidth
        margin="normal"
        error={!!errors.password}
        helperText={errors.password?.message as string}
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                onClick={() => setShowPassword(!showPassword)}
                edge="end"
                aria-label={t(
                  showPassword ? 'registration.hidePassword' : 'registration.showPassword'
                )}
              >
                {showPassword ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          ),
        }}
      />

      {password && (
        <Box sx={{ mt: 1, mb: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
            <Typography variant="body2">{t(`register.passwordStrength.${strength}`)}</Typography>
            <LinearProgress
              variant="determinate"
              value={getStrengthValue(strength)}
              color={getStrengthColor(strength)}
              sx={{ flex: 1 }}
            />
          </Box>

          <Typography variant="caption" color="text.secondary">
            {t('register.passwordRequirements.title')}
          </Typography>
          <List dense>
            <ListItem>
              <ListItemIcon>
                {requirements.minLength ? (
                  <CheckCircle color="success" fontSize="small" />
                ) : (
                  <RadioButtonUnchecked fontSize="small" />
                )}
              </ListItemIcon>
              <ListItemText
                primary={t('register.passwordRequirements.minLength')}
                primaryTypographyProps={{ variant: 'body2' }}
              />
            </ListItem>
            <ListItem>
              <ListItemIcon>
                {requirements.hasUppercase && requirements.hasLowercase ? (
                  <CheckCircle color="success" fontSize="small" />
                ) : (
                  <RadioButtonUnchecked fontSize="small" />
                )}
              </ListItemIcon>
              <ListItemText
                primary={t('register.passwordRequirements.hasUppercase')}
                primaryTypographyProps={{ variant: 'body2' }}
              />
            </ListItem>
            <ListItem>
              <ListItemIcon>
                {requirements.hasNumber ? (
                  <CheckCircle color="success" fontSize="small" />
                ) : (
                  <RadioButtonUnchecked fontSize="small" />
                )}
              </ListItemIcon>
              <ListItemText
                primary={t('register.passwordRequirements.hasNumber')}
                primaryTypographyProps={{ variant: 'body2' }}
              />
            </ListItem>
          </List>
        </Box>
      )}

      <TextField
        {...register('confirmPassword', {
          required: t('register.errors.passwordRequired'),
          validate: (value) => value === password || t('register.errors.passwordMismatch'),
        })}
        label={t('register.step1.confirmPasswordLabel')}
        placeholder={t('register.step1.confirmPasswordPlaceholder')}
        type={showConfirmPassword ? 'text' : 'password'}
        fullWidth
        margin="normal"
        error={!!errors.confirmPassword}
        helperText={errors.confirmPassword?.message as string}
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                edge="end"
                aria-label={t(
                  showConfirmPassword ? 'registration.hidePassword' : 'registration.showPassword'
                )}
              >
                {showConfirmPassword ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          ),
        }}
      />

      <Button variant="contained" fullWidth size="large" onClick={handleContinue} sx={{ mt: 3 }}>
        {t('register.step1.continueButton')}
      </Button>
    </Box>
  );
};
