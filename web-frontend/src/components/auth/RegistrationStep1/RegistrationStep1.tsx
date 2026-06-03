/**
 * RegistrationStep1 Component
 * Story 1.2.3: Implement Account Creation Flow - Task 5 (GREEN Phase)
 * Story 12.6a: split single Full Name field into Given/Family name (no more fragile split heuristic)
 *
 * Step 1 of registration wizard: Personal Information
 * - Given name, Family name, Email, Password, Confirm Password fields
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

// Story 12.6a: locales whose convention is family-name-first. The data mapping
// (firstName=given, lastName=family) never changes — only the visual field order
// flips for these locales. Currently only Japanese; extend the set as needed.
const FAMILY_NAME_FIRST_LOCALES = new Set(['ja']);

interface RegistrationStep1Props {
  onContinue: () => void;
}

export const RegistrationStep1: React.FC<RegistrationStep1Props> = ({ onContinue }) => {
  const { t, i18n } = useTranslation('auth');
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
    const isValid = await trigger([
      'firstName',
      'lastName',
      'email',
      'password',
      'confirmPassword',
    ]);
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

  // Story 12.6a: two name fields. register names stay firstName/lastName (matches
  // authService.signUp + the DB columns) while labels say Given/Family (order-neutral).
  const givenNameField = (
    <TextField
      key="firstName"
      {...register('firstName', {
        required: t('register.errors.givenNameRequired'),
        minLength: {
          value: 2,
          message: t('register.errors.givenNameTooShort'),
        },
        maxLength: {
          value: 100,
          message: t('register.errors.givenNameTooLong'),
        },
        pattern: {
          // Accept any Unicode letter (handles é, è, à, ç, ñ, ş, ø, ł, …) plus
          // whitespace, dot, apostrophe, and hyphen. The backend slug service
          // normalises diacritics for the generated username; rejecting them
          // here used to silently strip required form fields (2026-05-18
          // incident: "René Strauss" / "Renée Gressly").
          value: /^[\p{L}\s.'-]+$/u,
          message: t('register.errors.givenNameInvalid'),
        },
      })}
      label={t('register.step1.givenNameLabel')}
      placeholder={t('register.step1.givenNamePlaceholder')}
      fullWidth
      margin="normal"
      error={!!errors.firstName}
      helperText={errors.firstName?.message as string}
    />
  );

  const familyNameField = (
    <TextField
      key="lastName"
      {...register('lastName', {
        required: t('register.errors.familyNameRequired'),
        minLength: {
          value: 2,
          message: t('register.errors.familyNameTooShort'),
        },
        maxLength: {
          value: 100,
          message: t('register.errors.familyNameTooLong'),
        },
        pattern: {
          // Accept any Unicode letter (handles é, è, à, ç, ñ, ş, ø, ł, …) plus
          // whitespace, dot, apostrophe, and hyphen. The backend slug service
          // normalises diacritics for the generated username; rejecting them
          // here used to silently strip required form fields (2026-05-18
          // incident: "René Strauss" / "Renée Gressly").
          value: /^[\p{L}\s.'-]+$/u,
          message: t('register.errors.familyNameInvalid'),
        },
      })}
      label={t('register.step1.familyNameLabel')}
      placeholder={t('register.step1.familyNamePlaceholder')}
      fullWidth
      margin="normal"
      error={!!errors.lastName}
      helperText={errors.lastName?.message as string}
    />
  );

  const familyNameFirst = FAMILY_NAME_FIRST_LOCALES.has(i18n.language);

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        {t('register.step1.title')}
      </Typography>

      {familyNameFirst ? (
        <>
          {familyNameField}
          {givenNameField}
        </>
      ) : (
        <>
          {givenNameField}
          {familyNameField}
        </>
      )}

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
          validate: (value) =>
            checkPasswordRequirements(value).hasSpecialChar ||
            t('register.errors.passwordMissingSymbol'),
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
            <ListItem>
              <ListItemIcon>
                {requirements.hasSpecialChar ? (
                  <CheckCircle color="success" fontSize="small" />
                ) : (
                  <RadioButtonUnchecked fontSize="small" />
                )}
              </ListItemIcon>
              <ListItemText
                primary={t('register.passwordRequirements.hasSpecialChar')}
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
