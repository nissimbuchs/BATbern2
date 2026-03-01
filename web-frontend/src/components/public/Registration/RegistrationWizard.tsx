/**
 * RegistrationWizard Component (Story 4.1.5 - Tasks 4, 7, 8, 9)
 *
 * Main 2-step registration wizard container.
 * Reusable component that works in both inline (HeroSection) and dedicated page contexts.
 *
 * Features:
 * - 2-step accordion-style wizard (Personal Details → Confirm)
 * - react-hook-form + zod validation
 * - Progress indicator
 * - Navigation with state preservation
 * - API integration for registration submission
 */

import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { PersonalDetailsStep, type PersonalDetailsStepRef } from './PersonalDetailsStep';
import { ConfirmRegistrationStep } from './ConfirmRegistrationStep';
import { RegistrationAccordion } from './RegistrationAccordion';
import { Button } from '@/components/public/ui/button';
import { eventApiClient } from '@/services/eventApiClient';
import { useMyRegistration } from '@/hooks/useMyRegistration';
import { useAuth } from '@/hooks/useAuth/useAuth';
import { useUserProfile } from '@/hooks/useUserProfile/useUserProfile';
import type { CreateRegistrationRequest } from '@/types/event.types';
import { Loader2, CheckCircle2, Mail, ArrowLeft, AlertCircle } from 'lucide-react';

export interface RegistrationWizardProps {
  /** Event code for registration */
  eventCode: string;
  /** Optional cancel handler (for inline mode to collapse wizard) */
  onCancel?: () => void;
  /** Whether wizard is displayed inline (vs dedicated page) */
  inline?: boolean;
}

/**
 * RegistrationWizard - 2-Step Registration Flow
 *
 * Step 1: Personal Details (firstName, lastName, email, company, role)
 * Step 2: Confirm Registration (review, preferences, special requests, terms)
 *
 * Works in both inline (hero section) and dedicated page contexts.
 */
export const RegistrationWizard = ({
  eventCode,
  onCancel,
  inline = false,
}: RegistrationWizardProps) => {
  const navigate = useNavigate();
  const { t } = useTranslation(['registration', 'common']);
  const queryClient = useQueryClient();
  const step1Ref = useRef<PersonalDetailsStepRef>(null);

  // AC6: Check if authenticated user already has a registration (Story 10.10, T11)
  const { data: myRegistration, isLoading: isRegistrationLoading } = useMyRegistration(eventCode);

  // Pre-fill form from user profile when authenticated
  const { isAuthenticated } = useAuth();
  const { userProfile } = useUserProfile({ enabled: isAuthenticated });

  // Wizard state
  const [currentStep, setCurrentStep] = useState(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [registrationSuccess, setRegistrationSuccess] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  // Form data state
  const [formData, setFormData] = useState<CreateRegistrationRequest>({
    firstName: '',
    lastName: '',
    email: '',
    company: '',
    role: '',
    termsAccepted: false,
    communicationPreferences: {
      newsletterSubscribed: false,
      eventReminders: true,
    },
    specialRequests: '',
  });

  // Pre-fill form fields from user profile once resolved (only fills still-empty fields)
  useEffect(() => {
    if (!userProfile || !step1Ref.current) return;
    step1Ref.current.prefill({
      firstName: userProfile.firstName,
      lastName: userProfile.lastName,
      email: userProfile.email,
    });
  }, [userProfile]);

  // Navigation handlers (Task 7)
  const handleNext = async () => {
    if (currentStep === 1) {
      // Validate Step 1 before proceeding
      const isValid = await step1Ref.current?.validateAndSync();
      if (!isValid) {
        setError(t('wizard.errors.fillRequired'));
        return;
      }
      setError(null);
    }
    setCurrentStep((prev) => Math.min(prev + 1, 2));
  };

  const handleBack = () => {
    setCurrentStep((prev) => Math.max(prev - 1, 1));
    setError(null);
  };

  // Track wizard bottom when step changes (similar to HeroSection)
  useEffect(() => {
    if (currentStep !== 2 || !inline) return;

    let animationFrame: number;
    let startTime: number | null = null;
    const animationDuration = 500; // Accordion animation duration

    const trackWizardBottom = (timestamp: number) => {
      if (!startTime) startTime = timestamp;
      const elapsed = timestamp - startTime;

      const wizardSection = document.getElementById('registration-wizard-section');
      if (wizardSection && elapsed < animationDuration) {
        const rect = wizardSection.getBoundingClientRect();
        // Scroll so bottom of wizard aligns with bottom of viewport
        const scrollTarget = window.pageYOffset + rect.bottom - window.innerHeight;
        window.scrollTo({ top: scrollTarget, behavior: 'instant' });

        animationFrame = requestAnimationFrame(trackWizardBottom);
      }
    };

    animationFrame = requestAnimationFrame(trackWizardBottom);

    return () => {
      if (animationFrame) {
        cancelAnimationFrame(animationFrame);
      }
    };
  }, [currentStep, inline]);

  const handleCancel = () => {
    if (confirm(t('wizard.cancelConfirm'))) {
      if (onCancel) {
        // Inline mode: collapse wizard
        onCancel();
      } else {
        // Dedicated page: navigate back to event
        navigate(`/`);
      }
    }
  };

  // Form submission (Task 8)
  const handleSubmit = async () => {
    // Validate terms accepted
    if (!formData.termsAccepted) {
      setError(t('wizard.errors.acceptTerms'));
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const response = await eventApiClient.createRegistration(eventCode, formData);

      // Store registration in sessionStorage to show "already registered" on homepage
      sessionStorage.setItem(
        'pendingRegistration',
        JSON.stringify({
          email: response.email,
          eventCode: eventCode,
          timestamp: Date.now(),
          expiresAt: Date.now() + 48 * 60 * 60 * 1000, // 48 hours (matches token validity)
        })
      );

      // Success: Show success message inline (Story 4.1.5c)
      setRegisteredEmail(response.email);
      setRegistrationSuccess(true);
      setIsSubmitting(false);
      // AC7: Invalidate my-registration cache so banner/guard reflect new status immediately
      queryClient.invalidateQueries({ queryKey: ['my-registration', eventCode] });
    } catch (err) {
      // Handle duplicate registration (409 Conflict)
      // Backend returns 409 only for confirmed/cancelled registrations
      // For pending registrations, backend returns 200 OK (reuses existing)
      if (err instanceof Error && err.message.includes('already registered')) {
        // User is already confirmed - show error message
        setError(
          t('public.registration.errors.alreadyRegistered', {
            defaultValue: 'You are already registered for this event.',
          })
        );
        setIsSubmitting(false);
      } else {
        // Other errors: show error message
        const errorMessage = err instanceof Error ? err.message : t('wizard.errors.failed');
        setError(errorMessage);
        setIsSubmitting(false);
      }
    }
  };

  // Step 1 summary for accordion
  const step1Summary = formData.firstName && formData.lastName && (
    <div className="flex items-center justify-between">
      <span>
        {formData.firstName} {formData.lastName} • {formData.email}
      </span>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => setCurrentStep(1)}
        className="text-blue-400 hover:text-blue-300"
      >
        {t('wizard.buttons.edit')}
      </Button>
    </div>
  );

  // AC6: Registration Wizard guard (Story 10.10, T11)
  // When the authenticated user already has a non-null registration:
  // - REGISTERED / CONFIRMED / WAITLIST → show guard with "Go back" button
  // - CANCELLED → show guard with "Register again" button (backend T4.6 allows re-registration)
  if (!isRegistrationLoading && myRegistration != null) {
    const isCancelled = myRegistration.status === 'CANCELLED';
    const formattedDate = myRegistration.registrationDate
      ? new Date(myRegistration.registrationDate).toLocaleDateString()
      : null;

    return (
      <div
        className={`w-full ${inline ? 'max-w-4xl mx-auto' : ''}`}
        data-testid="registration-status-guard"
      >
        <div className="text-center mb-6">
          <AlertCircle className="h-12 w-12 text-amber-400 mx-auto mb-4" />
          <h2 className="text-2xl font-light mb-2">
            {t('registrationStatusGuard.alreadyRegistered')}
          </h2>
          {formattedDate && (
            <p className="text-sm text-zinc-400">
              {myRegistration.status} · {formattedDate}
            </p>
          )}
        </div>
        <div className="flex justify-center gap-4">
          {isCancelled ? (
            // CANCELLED: allow re-registration (backend deletes old record and creates new)
            // Note: we can't reset myRegistration client-side, but the guard will dismiss
            // once the query is invalidated after successful submission.
            <Button
              onClick={() => {
                // Optimistically mark as not-registered so the wizard shows immediately.
                // setQueryData (not removeQueries) avoids a re-fetch that would re-populate
                // the guard before the user submits. The cache is properly invalidated on
                // successful submission via queryClient.invalidateQueries in the success block.
                queryClient.setQueryData(['my-registration', eventCode], null);
              }}
              data-testid="registration-guard-register-again-btn"
            >
              {t('registrationStatusGuard.registerAgain')}
            </Button>
          ) : (
            <Button
              variant="outline"
              onClick={onCancel ?? (() => navigate('/'))}
              data-testid="registration-guard-go-back-btn"
            >
              {t('registrationStatusGuard.goBack')}
            </Button>
          )}
        </div>
      </div>
    );
  }

  // Success view
  if (registrationSuccess) {
    return (
      <div className={`w-full ${inline ? 'max-w-4xl mx-auto' : ''}`}>
        <div className="text-center">
          <CheckCircle2 className="h-16 w-16 text-green-400 mx-auto mb-4" />
          <h2 className="text-3xl font-light mb-2">{t('success.title')}</h2>
          <p className="text-xl text-zinc-400 mb-8">{t('success.subtitle')}</p>
        </div>

        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 mb-6">
          <div className="flex items-start gap-3">
            <Mail className="h-6 w-6 text-blue-400 flex-shrink-0 mt-1" />
            <div className="flex-1">
              <h3 className="text-lg font-medium mb-2">{t('success.emailSent')}</h3>
              {registeredEmail && (
                <p className="text-zinc-300 mb-3">
                  {t('success.emailSentTo')}{' '}
                  <span className="font-mono text-blue-400">{registeredEmail}</span>
                </p>
              )}
              <p className="text-sm text-zinc-400 mb-4">
                {t('success.clickLink')}{' '}
                <span className="text-zinc-300 font-medium">{t('success.validFor')}</span>
                {t('success.valid')}
              </p>
              <div className="bg-zinc-800/50 border border-zinc-700 rounded p-3 mt-4">
                <p className="text-sm font-medium text-zinc-300 mb-1">
                  {t('success.didntReceive')}
                </p>
                <ul className="text-sm text-zinc-400 space-y-1 ml-4">
                  <li>• {t('success.checkSpam')}</li>
                  <li>• {t('success.checkEmail')}</li>
                  <li>• {t('success.waitMinutes')}</li>
                </ul>
              </div>
            </div>
          </div>
        </div>

        <div className="flex justify-center gap-4">
          {onCancel && (
            <Button variant="outline" onClick={onCancel}>
              <ArrowLeft className="h-4 w-4 mr-2" />
              {t('success.close')}
            </Button>
          )}
          {!inline && (
            <Button variant="outline" onClick={() => navigate('/')}>
              <ArrowLeft className="h-4 w-4 mr-2" />
              {t('navigation.home')}
            </Button>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className={`w-full ${inline ? 'max-w-4xl mx-auto' : ''}`}>
      {/* Progress Indicator (Task 9) */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-2">
          <span
            className={`text-sm ${
              currentStep === 1 ? 'text-blue-400 font-medium' : 'text-zinc-500'
            }`}
          >
            {t('wizard.steps.step1Progress')}
          </span>
          <span
            className={`text-sm ${
              currentStep === 2 ? 'text-blue-400 font-medium' : 'text-zinc-500'
            }`}
          >
            {t('wizard.steps.step2Progress')}
          </span>
        </div>
        <div className="h-2 bg-zinc-800 rounded-full overflow-hidden">
          <div
            className="h-full bg-blue-400 transition-all duration-300 ease-in-out"
            style={{ width: `${(currentStep / 2) * 100}%` }}
          />
        </div>
      </div>

      {/* Accordion Steps */}
      <div className="space-y-4">
        {/* Step 1: Personal Details */}
        <RegistrationAccordion
          isExpanded={currentStep === 1}
          title={t('wizard.steps.step1Title')}
          summary={step1Summary}
          onToggle={() => {
            if (currentStep !== 1) {
              setCurrentStep(1);
              setError(null);
            }
          }}
        >
          <PersonalDetailsStep ref={step1Ref} formData={formData} setFormData={setFormData} />
        </RegistrationAccordion>

        {/* Step 2: Confirm Registration */}
        <RegistrationAccordion
          isExpanded={currentStep === 2}
          title={t('wizard.steps.step2Title')}
          onToggle={() => {
            // Step 2 can only be accessed by clicking Next from Step 1
            // Clicking the header when collapsed should not expand it
            if (currentStep === 2) {
              // Do nothing - user must use Back button
            }
          }}
        >
          <ConfirmRegistrationStep
            formData={formData}
            setFormData={setFormData}
            onEdit={() => setCurrentStep(1)}
          />
        </RegistrationAccordion>
      </div>

      {/* Error Message */}
      {error && (
        <div className="mt-4 p-4 bg-red-900/20 border border-red-800 rounded-lg">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      {/* Navigation Buttons */}
      <div className="mt-8 flex justify-between">
        <Button
          variant="outline"
          onClick={handleCancel}
          disabled={isSubmitting}
          data-testid="registration-wizard-cancel-btn"
        >
          {t('wizard.buttons.cancel')}
        </Button>
        <div className="flex gap-4">
          {currentStep > 1 && (
            <Button
              variant="outline"
              onClick={handleBack}
              disabled={isSubmitting}
              data-testid="registration-wizard-back-btn"
            >
              {t('wizard.buttons.back')}
            </Button>
          )}
          {currentStep < 2 ? (
            <Button onClick={handleNext} data-testid="registration-wizard-next-btn">
              {t('wizard.buttons.next')}
            </Button>
          ) : (
            <Button
              onClick={handleSubmit}
              disabled={!formData.termsAccepted || isSubmitting}
              className="min-w-[200px]"
              data-testid="registration-wizard-submit-btn"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t('wizard.buttons.submitting')}
                </>
              ) : (
                t('wizard.buttons.complete')
              )}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};
