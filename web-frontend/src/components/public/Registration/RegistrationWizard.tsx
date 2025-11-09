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

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PersonalDetailsStep } from './PersonalDetailsStep';
import { ConfirmRegistrationStep } from './ConfirmRegistrationStep';
import { RegistrationAccordion } from './RegistrationAccordion';
import { Button } from '@/components/public/ui/button';
import { eventApiClient } from '@/services/eventApiClient';
import type { CreateRegistrationRequest } from '@/types/event.types';
import { Loader2 } from 'lucide-react';

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

  // Wizard state
  const [currentStep, setCurrentStep] = useState(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

  // Navigation handlers (Task 7)
  const handleNext = () => {
    setCurrentStep((prev) => Math.min(prev + 1, 2));
  };

  const handleBack = () => {
    setCurrentStep((prev) => Math.max(prev - 1, 1));
    setError(null);
  };

  const handleCancel = () => {
    if (confirm('Are you sure you want to cancel registration?')) {
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
      setError('You must accept the terms and conditions to register.');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const registration = await eventApiClient.createRegistration(eventCode, formData);

      // Success: Redirect to confirmation page
      navigate(`/registration-confirmation/${registration.registrationCode}`);
    } catch (err) {
      // Error handling
      const errorMessage =
        err instanceof Error ? err.message : 'Registration failed. Please try again.';
      setError(errorMessage);
      setIsSubmitting(false);
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
        Edit
      </Button>
    </div>
  );

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
            1. Your Details
          </span>
          <span
            className={`text-sm ${
              currentStep === 2 ? 'text-blue-400 font-medium' : 'text-zinc-500'
            }`}
          >
            2. Confirm Registration
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
          title="Step 1: Your Details"
          summary={step1Summary}
          onToggle={() => setCurrentStep(1)}
        >
          <PersonalDetailsStep formData={formData} setFormData={setFormData} />
        </RegistrationAccordion>

        {/* Step 2: Confirm Registration */}
        <RegistrationAccordion
          isExpanded={currentStep === 2}
          title="Step 2: Confirm Registration"
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
        <Button variant="outline" onClick={handleCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <div className="flex gap-4">
          {currentStep > 1 && (
            <Button variant="outline" onClick={handleBack} disabled={isSubmitting}>
              ← Back
            </Button>
          )}
          {currentStep < 2 ? (
            <Button onClick={handleNext}>Next →</Button>
          ) : (
            <Button
              onClick={handleSubmit}
              disabled={!formData.termsAccepted || isSubmitting}
              className="min-w-[200px]"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Submitting...
                </>
              ) : (
                'Complete Registration'
              )}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};
