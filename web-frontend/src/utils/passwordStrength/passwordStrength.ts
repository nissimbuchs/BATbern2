/**
 * Password Strength Utility
 * Story 1.2.3: Implement Account Creation Flow - Task 3 (GREEN Phase)
 *
 * Provides password requirement checking and strength calculation
 * following AWS Cognito password policy requirements.
 */

export interface PasswordRequirements {
  minLength: boolean;
  hasUppercase: boolean;
  hasLowercase: boolean;
  hasNumber: boolean;
  hasSpecialChar?: boolean;
}

export type PasswordStrength = 'weak' | 'medium' | 'strong';

/**
 * Check if password meets individual requirements
 * @param password - The password to check
 * @returns Object with boolean flags for each requirement
 */
export const checkPasswordRequirements = (password: string): PasswordRequirements => {
  return {
    minLength: password.length >= 8,
    hasUppercase: /[A-Z]/.test(password),
    hasLowercase: /[a-z]/.test(password),
    hasNumber: /[0-9]/.test(password),
    hasSpecialChar: /[!@#$%^&*(),.?":{}|<>]/.test(password),
  };
};

/**
 * Calculate overall password strength based on requirements met
 * @param password - The password to evaluate
 * @returns Password strength level (weak, medium, strong)
 */
export const calculatePasswordStrength = (password: string): PasswordStrength => {
  const requirements = checkPasswordRequirements(password);

  // Count how many requirements are met
  const score = [
    requirements.minLength,
    requirements.hasUppercase,
    requirements.hasLowercase,
    requirements.hasNumber,
    requirements.hasSpecialChar,
  ].filter(Boolean).length;

  // Weak: 0-3 requirements met
  if (score <= 3) return 'weak';

  // Medium: 4 requirements met
  if (score === 4) return 'medium';

  // Strong: All 5 requirements met
  return 'strong';
};
