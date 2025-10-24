/**
 * RegistrationStep2 Component
 * Story 1.2.3: Implement Account Creation Flow - Task 7 (GREEN Phase)
 * TDD: Implementation after tests written
 */

import React from 'react';
import { useTranslation } from 'react-i18next';
import { useFormContext, Controller } from 'react-hook-form';
import {
  Box,
  Typography,
  Button,
  Checkbox,
  FormControlLabel,
  FormHelperText,
  Link,
  Alert,
  Paper,
  Stack,
} from '@mui/material';
import { Info } from '@mui/icons-material';

export interface RegistrationStep2Props {
  onBack: () => void;
  onSubmit: () => void;
  isLoading: boolean;
  error: Error | null;
}

export const RegistrationStep2: React.FC<RegistrationStep2Props> = ({
  onBack,
  onSubmit,
  isLoading,
  error,
}) => {
  const { t } = useTranslation('auth');
  const {
    watch,
    control,
    formState: { errors },
    trigger,
  } = useFormContext();

  const fullName = watch('fullName');
  const email = watch('email');
  const agreedToTerms = watch('agreedToTerms');

  const handleSubmit = async () => {
    // Validate terms agreement
    const isValid = await trigger('agreedToTerms');
    if (isValid && agreedToTerms) {
      onSubmit();
    }
  };

  // Map Cognito error codes to localized messages
  const getErrorMessage = (error: Error): string => {
    const errorCode = error.message;

    // Map Cognito error codes to translation keys
    const errorMap: Record<string, string> = {
      UsernameExistsException: t('register.errors.usernameExists'),
      InvalidPasswordException: t('register.errors.passwordWeak'),
      InvalidParameterException: t('register.errors.invalidEmail'),
      LimitExceededException: t('register.errors.rateLimitExceeded'),
      TooManyRequestsException: t('register.errors.rateLimitExceeded'),
      NetworkError: t('register.errors.networkError'),
      SIGNUP_FAILED: t('register.errors.signupFailed'),
    };

    return errorMap[errorCode] || t('register.errors.signupFailed');
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        {t('register.step2.title')}
      </Typography>

      {/* Information Summary */}
      <Paper variant="outlined" sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" gutterBottom>
            {t('register.step2.reviewTitle')}
          </Typography>
          <Button variant="text" onClick={onBack} size="small">
            {t('register.step2.editButton')}
          </Button>
        </Box>

        <Stack spacing={1}>
          <Box>
            <Typography variant="body2" color="text.secondary">
              {t('register.step2.nameLabel')}
            </Typography>
            <Typography variant="body1">{fullName}</Typography>
          </Box>
          <Box>
            <Typography variant="body2" color="text.secondary">
              {t('register.step2.emailLabel')}
            </Typography>
            <Typography variant="body1">{email}</Typography>
          </Box>
        </Stack>
      </Paper>

      {/* Terms and Conditions Checkbox */}
      <Controller
        name="agreedToTerms"
        control={control}
        rules={{
          required: t('register.errors.termsRequired'),
        }}
        render={({ field }) => (
          <FormControlLabel
            control={<Checkbox {...field} checked={field.value} />}
            label={
              <Typography variant="body2">
                {t('register.step2.termsLabel').split('Terms of Service')[0]}
                <Link href="/terms" target="_blank" rel="noopener noreferrer">
                  Terms of Service
                </Link>
                {' and '}
                <Link href="/privacy" target="_blank" rel="noopener noreferrer">
                  Privacy Policy
                </Link>
              </Typography>
            }
            sx={{ mb: 2, alignItems: 'flex-start' }}
          />
        )}
      />
      {errors.agreedToTerms && (
        <FormHelperText error sx={{ mt: -1, mb: 2 }}>
          {errors.agreedToTerms.message as string}
        </FormHelperText>
      )}

      {/* Newsletter Opt-in Checkbox */}
      <Controller
        name="newsletterOptIn"
        control={control}
        render={({ field }) => (
          <FormControlLabel
            control={<Checkbox {...field} checked={field.value} />}
            label={<Typography variant="body2">{t('register.step2.newsletterLabel')}</Typography>}
            sx={{ mb: 3 }}
          />
        )}
      />

      {/* Verification Notice */}
      <Alert icon={<Info />} severity="info" sx={{ mb: 3 }}>
        {t('register.step2.verificationNotice', { email })}
      </Alert>

      {/* Error Display */}
      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {getErrorMessage(error)}
        </Alert>
      )}

      {/* Action Buttons */}
      <Box sx={{ display: 'flex', gap: 2 }}>
        <Button variant="outlined" onClick={onBack} fullWidth disabled={isLoading}>
          {t('register.step2.backButton')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          fullWidth
          disabled={isLoading}
          size="large"
        >
          {isLoading ? t('register.step2.submittingButton') : t('register.step2.submitButton')}
        </Button>
      </Box>
    </Box>
  );
};

export default RegistrationStep2;
