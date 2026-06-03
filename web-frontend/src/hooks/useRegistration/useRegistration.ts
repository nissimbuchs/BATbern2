/**
 * useRegistration Hook
 * Story 1.2.3: Implement Account Creation Flow - Task 10 (GREEN Phase)
 * Handles user registration via AWS Cognito SignUp
 */

import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { authService } from '@/services/auth/authService';

export interface RegistrationFormData {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
  agreedToTerms: boolean;
  newsletterOptIn: boolean;
}

export interface RegistrationResult {
  email: string;
  requiresConfirmation: boolean;
}

export const useRegistration = () => {
  const { i18n } = useTranslation();

  return useMutation({
    mutationFn: async (data: RegistrationFormData): Promise<RegistrationResult> => {
      // Story 12.6a: names are captured in two clean fields — no more fragile
      // fullName.split(/\s+/) heuristic that mis-split multi-word given names
      // ("Anna Maria") and compound surnames ("von der Berg").
      const firstName = data.firstName.trim();
      const lastName = data.lastName.trim();

      // Call existing authService.signUp method (using existing SignUpData interface)
      const result = await authService.signUp({
        email: data.email,
        password: data.password,
        confirmPassword: data.password, // Already validated in form
        firstName,
        lastName,
        role: 'attendee', // FR22: All self-registered users are ATTENDEE (lowercase per UserRole type)
        companyId: '', // Not needed for self-registration
        acceptTerms: data.agreedToTerms,
        language: i18n.language, // User's selected language
        newsletterOptIn: data.newsletterOptIn,
      });

      if (!result.success) {
        throw new Error(result.error?.code || 'SIGNUP_FAILED');
      }

      // PostConfirmation Lambda (Story 1.2.5) creates database record automatically
      return {
        email: data.email,
        requiresConfirmation: result.requiresConfirmation,
      };
    },
  });
};
