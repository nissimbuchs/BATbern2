/**
 * RegistrationWizard Component
 * Story 1.2.3: Implement Account Creation Flow - Task 9 (GREEN Phase)
 * TDD: Implementation after tests written
 */

import React from 'react';
import { useForm, FormProvider } from 'react-hook-form';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Stepper, Step, StepLabel, Typography, Container } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { RegistrationStep1 } from '../RegistrationStep1';
import { RegistrationStep2 } from '../RegistrationStep2';
import { useRegistration } from '@/hooks/useRegistration';

export const RegistrationWizard: React.FC = () => {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const currentStep = parseInt(searchParams.get('step') || '1', 10);

  // Story 6.3: Pre-fill email from URL query parameter (speaker invitation flow)
  const prefilledEmail = searchParams.get('email') || '';

  const methods = useForm({
    defaultValues: {
      fullName: '',
      email: prefilledEmail,
      password: '',
      confirmPassword: '',
      agreedToTerms: false,
      newsletterOptIn: false,
    },
    mode: 'onBlur',
  });

  const { mutate: register, isPending, error } = useRegistration();

  const handleStepChange = (step: number) => {
    setSearchParams({ step: step.toString() });
  };

  const handleSubmit = () => {
    const formData = methods.getValues();

    register(formData, {
      onSuccess: (response) => {
        // Navigate to email verification screen
        navigate(`/auth/verify-email?email=${encodeURIComponent(response.email)}`);
      },
    });
  };

  const steps = [t('register.step1.title'), t('register.step2.title')];

  return (
    <Container maxWidth="sm" sx={{ mt: 4, mb: 4 }}>
      <Typography variant="h4" align="center" gutterBottom>
        {t('register.title')}
      </Typography>
      <Typography variant="subtitle1" align="center" color="text.secondary" gutterBottom>
        {t('register.subtitle')}
      </Typography>

      <Stepper activeStep={currentStep - 1} sx={{ my: 4 }}>
        {steps.map((_, index) => (
          <Step key={index}>
            <StepLabel>{`Step ${index + 1}`}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <FormProvider {...methods}>
        {currentStep === 1 && <RegistrationStep1 onContinue={() => handleStepChange(2)} />}
        {currentStep === 2 && (
          <RegistrationStep2
            onBack={() => handleStepChange(1)}
            onSubmit={handleSubmit}
            isLoading={isPending}
            error={error}
          />
        )}
      </FormProvider>
    </Container>
  );
};

export default RegistrationWizard;
