/**
 * Password Strength Utility Tests
 * Story 1.2.3: Implement Account Creation Flow - Task 2 (RED Phase)
 * TDD: Write tests first before implementation
 */

import { describe, test, expect } from 'vitest';
import { checkPasswordRequirements, calculatePasswordStrength } from './passwordStrength';

describe('checkPasswordRequirements', () => {
  test('should_returnAllFalse_when_emptyPassword', () => {
    const requirements = checkPasswordRequirements('');

    expect(requirements.minLength).toBe(false);
    expect(requirements.hasUppercase).toBe(false);
    expect(requirements.hasLowercase).toBe(false);
    expect(requirements.hasNumber).toBe(false);
  });

  test('should_detectMinLength_when_passwordHas8Chars', () => {
    const requirements = checkPasswordRequirements('12345678');

    expect(requirements.minLength).toBe(true);
  });

  test('should_returnFalseMinLength_when_passwordHas7Chars', () => {
    const requirements = checkPasswordRequirements('1234567');

    expect(requirements.minLength).toBe(false);
  });

  test('should_detectUppercase_when_passwordHasCapitalLetter', () => {
    const requirements = checkPasswordRequirements('Password');

    expect(requirements.hasUppercase).toBe(true);
  });

  test('should_returnFalseUppercase_when_noCapitalLetter', () => {
    const requirements = checkPasswordRequirements('password');

    expect(requirements.hasUppercase).toBe(false);
  });

  test('should_detectLowercase_when_passwordHasLowercaseLetter', () => {
    const requirements = checkPasswordRequirements('Password');

    expect(requirements.hasLowercase).toBe(true);
  });

  test('should_returnFalseLowercase_when_noLowercaseLetter', () => {
    const requirements = checkPasswordRequirements('PASSWORD');

    expect(requirements.hasLowercase).toBe(false);
  });

  test('should_detectNumber_when_passwordHasDigit', () => {
    const requirements = checkPasswordRequirements('Password1');

    expect(requirements.hasNumber).toBe(true);
  });

  test('should_returnFalseNumber_when_noDigit', () => {
    const requirements = checkPasswordRequirements('Password');

    expect(requirements.hasNumber).toBe(false);
  });

  test('should_returnAllTrue_when_passwordMeetsAllRequirements', () => {
    const requirements = checkPasswordRequirements('Password123');

    expect(requirements.minLength).toBe(true);
    expect(requirements.hasUppercase).toBe(true);
    expect(requirements.hasLowercase).toBe(true);
    expect(requirements.hasNumber).toBe(true);
  });
});

describe('calculatePasswordStrength', () => {
  test('should_returnWeak_when_onlyLowercase', () => {
    const strength = calculatePasswordStrength('password');

    expect(strength).toBe('weak');
  });

  test('should_returnWeak_when_shortPassword', () => {
    const strength = calculatePasswordStrength('Pass1');

    expect(strength).toBe('weak');
  });

  test('should_returnWeak_when_meets3Requirements', () => {
    const strength = calculatePasswordStrength('password123'); // has: minLength, lowercase, number

    expect(strength).toBe('weak');
  });

  test('should_returnMedium_when_meets4Requirements', () => {
    const strength = calculatePasswordStrength('Password123'); // has: minLength, uppercase, lowercase, number

    expect(strength).toBe('medium');
  });

  test('should_returnStrong_when_meetsAllRequirements', () => {
    const strength = calculatePasswordStrength('Password123!'); // has: minLength, uppercase, lowercase, number, special

    expect(strength).toBe('strong');
  });

  test('should_returnWeak_when_emptyPassword', () => {
    const strength = calculatePasswordStrength('');

    expect(strength).toBe('weak');
  });
});
