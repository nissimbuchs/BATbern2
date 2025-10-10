/**
 * Date Formatting Utility Tests
 * Story 1.17, Task 2a: TDD for date/time formatting with date-fns locales
 */

import { describe, test, expect } from 'vitest';
import {
  formatDate,
  formatTime,
  formatDateTime,
  formatRelativeTime,
  parseDate,
} from './dateFormat';

describe('Date Formatting Utilities with i18n', () => {
  describe('formatDate', () => {
    test('should_formatDate_when_germanLocaleUsed', () => {
      const date = new Date('2024-03-15T14:30:00Z');
      const result = formatDate(date, 'de');

      // Expected: "15. März 2024" or "15.03.2024" depending on format
      expect(result).toMatch(/15.*März.*2024|15\.03\.2024/);
    });

    test('should_formatDate_when_englishLocaleUsed', () => {
      const date = new Date('2024-03-15T14:30:00Z');
      const result = formatDate(date, 'en');

      // Expected: "March 15, 2024" or "3/15/2024" depending on format
      expect(result).toMatch(/March 15, 2024|3\/15\/2024/);
    });

    test('should_formatDateWithCustomFormat_when_formatStringProvided', () => {
      const date = new Date('2024-03-15T14:30:00Z');
      const result = formatDate(date, 'de', 'dd.MM.yyyy');

      expect(result).toBe('15.03.2024');
    });

    test('should_throwError_when_invalidDateProvided', () => {
      const invalidDate = new Date('invalid');

      expect(() => formatDate(invalidDate, 'de')).toThrow();
    });
  });

  describe('formatTime', () => {
    test('should_formatTime24Hour_when_germanLocaleUsed', () => {
      const date = new Date(2024, 2, 15, 14, 30, 0); // Local date: March 15, 2024, 14:30
      const result = formatTime(date, 'de');

      // Expected: "14:30" (24-hour format for German)
      expect(result).toBe('14:30');
    });

    test('should_formatTime12Hour_when_englishLocaleUsed', () => {
      const date = new Date(2024, 2, 15, 14, 30, 0); // Local date: March 15, 2024, 14:30
      const result = formatTime(date, 'en');

      // Expected: "2:30 PM" (12-hour format for English)
      expect(result).toBe('2:30 PM');
    });

    test('should_formatTimeWithSeconds_when_includeSecondsTrue', () => {
      const date = new Date(2024, 2, 15, 14, 30, 45); // Local date with seconds
      const result = formatTime(date, 'de', true);

      // Expected: "14:30:45"
      expect(result).toBe('14:30:45');
    });
  });

  describe('formatDateTime', () => {
    test('should_formatDateAndTime_when_germanLocaleUsed', () => {
      const date = new Date(2024, 2, 15, 14, 30, 0); // Local date
      const result = formatDateTime(date, 'de');

      // Expected: "15. März 2024, 14:30"
      expect(result).toBe('15. März 2024, 14:30');
    });

    test('should_formatDateAndTime_when_englishLocaleUsed', () => {
      const date = new Date(2024, 2, 15, 14, 30, 0); // Local date
      const result = formatDateTime(date, 'en');

      // Expected: "March 15, 2024, 2:30 PM"
      expect(result).toBe('March 15, 2024, 2:30 PM');
    });
  });

  describe('formatRelativeTime', () => {
    test('should_returnRelativeTime_when_dateInPast', () => {
      const now = new Date(2024, 2, 15, 14, 30, 0);
      const pastDate = new Date(2024, 2, 15, 14, 0, 0); // 30 minutes earlier
      const result = formatRelativeTime(pastDate, 'de', now);

      // Expected: "vor 30 Minuten"
      expect(result).toBe('vor 30 Minuten');
    });

    test('should_returnRelativeTime_when_dateInFuture', () => {
      const now = new Date(2024, 2, 15, 14, 30, 0);
      const futureDate = new Date(2024, 2, 15, 15, 0, 0); // 30 minutes later
      const result = formatRelativeTime(futureDate, 'de', now);

      // Expected: "in 30 Minuten"
      expect(result).toBe('in 30 Minuten');
    });

    test('should_returnToday_when_dateIsToday', () => {
      const now = new Date(2024, 2, 15, 14, 30, 0);
      const today = new Date(2024, 2, 15, 10, 0, 0); // 4.5 hours earlier (rounds to 5)
      const result = formatRelativeTime(today, 'de', now);

      // Expected: "vor etwa 5 Stunden" (date-fns rounds 4.5 hours to 5)
      expect(result).toBe('vor etwa 5 Stunden');
    });
  });

  describe('parseDate', () => {
    test('should_parseISOString_when_validISOStringProvided', () => {
      const isoString = '2024-03-15T14:30:00Z';
      const result = parseDate(isoString);

      expect(result).toBeInstanceOf(Date);
      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(2); // March is month 2 (0-indexed)
      expect(result.getDate()).toBe(15);
    });

    test('should_parseDateString_when_customFormatProvided', () => {
      const dateString = '15.03.2024';
      const result = parseDate(dateString, 'dd.MM.yyyy');

      expect(result).toBeInstanceOf(Date);
      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(2);
      expect(result.getDate()).toBe(15);
    });

    test('should_throwError_when_invalidDateStringProvided', () => {
      const invalidString = 'not-a-date';

      expect(() => parseDate(invalidString)).toThrow();
    });
  });

  describe('Locale fallback behavior', () => {
    test('should_useDefaultLocale_when_unsupportedLocaleProvided', () => {
      const date = new Date('2024-03-15T14:30:00Z');
      // @ts-expect-error Testing invalid locale
      const result = formatDate(date, 'xx');

      // Should fall back to German (de) as default
      expect(result).toBeDefined();
      expect(typeof result).toBe('string');
    });
  });
});
