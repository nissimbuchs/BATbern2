/**
 * Password Strength Utility
 * Story 1.2.2a: Reset Password Confirmation
 *
 * Reusable utility for calculating password strength
 * Used in registration and password reset flows
 */

export type PasswordStrength = 'weak' | 'medium' | 'strong';

export interface PasswordStrengthResult {
  strength: PasswordStrength | null;
  score: number;
}

/**
 * Calculate password strength based on character types and length
 *
 * Algorithm:
 * - Weak: 1-2 character types (e.g., only lowercase + numbers)
 * - Medium: 3 character types (e.g., lower + upper + number)
 * - Strong: 4 character types or 3 types with special char and length >= 12
 *
 * @param password - The password to evaluate
 * @returns PasswordStrengthResult with strength level and numeric score
 */
export function calculatePasswordStrength(password: string): PasswordStrengthResult {
  if (!password) {
    return { strength: null, score: 0 };
  }

  const hasLower = /[a-z]/.test(password);
  const hasUpper = /[A-Z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSpecial = /[^A-Za-z0-9]/.test(password);

  // Count character type diversity
  const charTypeCount = [hasLower, hasUpper, hasNumber, hasSpecial].filter(Boolean).length;

  // Calculate strength score (0-6 scale)
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (hasLower) score++;
  if (hasUpper) score++;
  if (hasNumber) score++;
  if (hasSpecial) score++;

  // Categorize strength
  let strength: PasswordStrength;
  if (charTypeCount <= 2) {
    strength = 'weak';
  } else if (charTypeCount === 3 || (charTypeCount === 4 && password.length < 12)) {
    strength = 'medium';
  } else {
    strength = 'strong';
  }

  return { strength, score };
}

/**
 * Get visual representation of password strength for progress bars
 *
 * @param strength - The password strength level
 * @returns Percentage value (0-100) for visual indicators
 */
export function getPasswordStrengthValue(strength: PasswordStrength | null): number {
  switch (strength) {
    case 'weak':
      return 33;
    case 'medium':
      return 66;
    case 'strong':
      return 100;
    default:
      return 0;
  }
}

/**
 * Get Material-UI color for password strength indicator
 *
 * @param strength - The password strength level
 * @returns MUI color name ('error' | 'warning' | 'success' | 'inherit')
 */
export function getPasswordStrengthColor(
  strength: PasswordStrength | null
): 'error' | 'warning' | 'success' | 'inherit' {
  switch (strength) {
    case 'weak':
      return 'error';
    case 'medium':
      return 'warning';
    case 'strong':
      return 'success';
    default:
      return 'inherit';
  }
}
