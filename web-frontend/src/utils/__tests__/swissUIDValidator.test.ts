/**
 * Swiss UID Validator Tests
 *
 * Tests for Swiss Company UID format validation (CHE-XXX.XXX.XXX)
 */

import { describe, it, expect } from 'vitest';
import {
  SWISS_UID_PATTERN,
  isValidSwissUID,
  formatSwissUID,
  validateSwissUID,
} from '../swissUIDValidator';

describe('swissUIDValidator', () => {
  describe('SWISS_UID_PATTERN', () => {
    it('should_matchValidUID_when_formatCorrect', () => {
      expect(SWISS_UID_PATTERN.test('CHE-123.456.789')).toBe(true);
    });

    it('should_notMatchInvalidUID_when_formatIncorrect', () => {
      expect(SWISS_UID_PATTERN.test('123.456.789')).toBe(false);
      expect(SWISS_UID_PATTERN.test('CHE-12.345.678')).toBe(false);
    });
  });

  describe('isValidSwissUID', () => {
    it('should_returnTrue_when_validUIDProvided', () => {
      expect(isValidSwissUID('CHE-123.456.789')).toBe(true);
      expect(isValidSwissUID('CHE-000.000.000')).toBe(true);
      expect(isValidSwissUID('CHE-999.999.999')).toBe(true);
    });

    it('should_returnFalse_when_nullOrUndefined', () => {
      expect(isValidSwissUID(null)).toBe(false);
      expect(isValidSwissUID(undefined)).toBe(false);
    });

    it('should_returnFalse_when_emptyString', () => {
      expect(isValidSwissUID('')).toBe(false);
      expect(isValidSwissUID('   ')).toBe(false);
    });

    it('should_returnFalse_when_missingCHEPrefix', () => {
      expect(isValidSwissUID('123.456.789')).toBe(false);
      expect(isValidSwissUID('DE-123.456.789')).toBe(false);
    });

    it('should_returnFalse_when_wrongNumberFormat', () => {
      expect(isValidSwissUID('CHE-12.345.678')).toBe(false);
      expect(isValidSwissUID('CHE-1234.567.890')).toBe(false);
      expect(isValidSwissUID('CHE-123-456-789')).toBe(false);
    });

    it('should_returnFalse_when_containsLettersInNumbers', () => {
      expect(isValidSwissUID('CHE-12A.456.789')).toBe(false);
      expect(isValidSwissUID('CHE-123.45B.789')).toBe(false);
    });

    it('should_handleWhitespace_when_validUIDWithSpaces', () => {
      expect(isValidSwissUID('  CHE-123.456.789  ')).toBe(true);
      expect(isValidSwissUID('\tCHE-123.456.789\n')).toBe(true);
    });

    it('should_returnFalse_when_lowercasePrefix', () => {
      expect(isValidSwissUID('che-123.456.789')).toBe(false);
    });
  });

  describe('formatSwissUID', () => {
    it('should_returnFormattedUID_when_validInput', () => {
      expect(formatSwissUID('CHE-123.456.789')).toBe('CHE-123.456.789');
    });

    it('should_returnEmptyString_when_nullOrUndefined', () => {
      expect(formatSwissUID(null)).toBe('');
      expect(formatSwissUID(undefined)).toBe('');
    });

    it('should_returnEmptyString_when_emptyString', () => {
      expect(formatSwissUID('')).toBe('');
      expect(formatSwissUID('   ')).toBe('');
    });

    it('should_returnEmptyString_when_invalidFormat', () => {
      expect(formatSwissUID('123.456.789')).toBe('');
      expect(formatSwissUID('CHE-12.345.678')).toBe('');
      expect(formatSwissUID('invalid')).toBe('');
    });

    it('should_trimWhitespace_when_validUIDWithSpaces', () => {
      expect(formatSwissUID('  CHE-123.456.789  ')).toBe('CHE-123.456.789');
    });

    it('should_convertToUppercase_when_lowercaseInput', () => {
      // formatSwissUID converts to uppercase before validating
      expect(formatSwissUID('che-123.456.789')).toBe('CHE-123.456.789');
    });
  });

  describe('validateSwissUID', () => {
    it('should_returnUndefined_when_validUID', () => {
      expect(validateSwissUID('CHE-123.456.789')).toBeUndefined();
    });

    it('should_returnUndefined_when_emptyOrUndefined', () => {
      // Empty is allowed since it's an optional field
      expect(validateSwissUID('')).toBeUndefined();
      expect(validateSwissUID('   ')).toBeUndefined();
      expect(validateSwissUID(undefined)).toBeUndefined();
    });

    it('should_returnErrorMessage_when_invalidFormat', () => {
      const errorMessage = 'Invalid Swiss UID format. Expected format: CHE-XXX.XXX.XXX';
      expect(validateSwissUID('123.456.789')).toBe(errorMessage);
      expect(validateSwissUID('CHE-12.345.678')).toBe(errorMessage);
      expect(validateSwissUID('invalid')).toBe(errorMessage);
    });

    it('should_returnErrorMessage_when_partialUID', () => {
      const errorMessage = 'Invalid Swiss UID format. Expected format: CHE-XXX.XXX.XXX';
      expect(validateSwissUID('CHE-')).toBe(errorMessage);
      expect(validateSwissUID('CHE-123')).toBe(errorMessage);
      expect(validateSwissUID('CHE-123.456')).toBe(errorMessage);
    });
  });
});
