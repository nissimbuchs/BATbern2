/**
 * Date Formatting Utilities
 * Story 1.17, Task 2b: Date/time formatting with date-fns locales
 *
 * `locale` is the UI language (i18next code); the date-fns locale comes from `dateFnsLocale`, the
 * same rule as `intlLocale` (#1222 item 6). English and the German family (de, gsw-BE, rm) keep
 * their explicit formats, every other language its date-fns long format.
 */

import { format, formatDistance, formatDistanceToNow, parseISO, parse, isValid } from 'date-fns';
import { type Locale } from 'date-fns/locale';
import { dateFnsLocale } from './dateFnsLocale';
import { primaryLanguage } from './intlLocale';

function getLocale(locale?: string | null): Locale {
  return dateFnsLocale(locale);
}

type Family = 'en' | 'de' | 'other';

function family(locale: string): Family {
  const primary = primaryLanguage(locale);
  if (primary === 'en') return 'en';
  if (getLocale(locale).code === 'de') return 'de';
  return 'other';
}

/**
 * Format a date according to locale
 *
 * @param date - Date to format
 * @param locale - Locale code ('de' or 'en')
 * @param formatString - Optional custom format string (date-fns format)
 * @returns Formatted date string
 * @throws Error if date is invalid
 *
 * @example
 * formatDate(new Date('2024-03-15'), 'de') // "15. März 2024"
 * formatDate(new Date('2024-03-15'), 'en') // "March 15, 2024"
 * formatDate(new Date('2024-03-15'), 'de', 'dd.MM.yyyy') // "15.03.2024"
 */
export function formatDate(date: Date, locale: string, formatString?: string): string {
  if (!isValid(date)) {
    throw new Error('Invalid date provided');
  }

  const localeObj = getLocale(locale);

  // Default format strings per locale
  const defaultFormat = { en: 'MMMM d, yyyy', de: 'd. MMMM yyyy', other: 'PPP' }[family(locale)];

  return format(date, formatString || defaultFormat, { locale: localeObj });
}

/**
 * Format a time according to locale
 *
 * @param date - Date to format
 * @param locale - Locale code ('de' or 'en')
 * @param includeSeconds - Whether to include seconds
 * @returns Formatted time string
 * @throws Error if date is invalid
 *
 * @example
 * formatTime(new Date('2024-03-15T14:30:00'), 'de') // "14:30"
 * formatTime(new Date('2024-03-15T14:30:00'), 'en') // "2:30 PM"
 */
export function formatTime(date: Date, locale: string, includeSeconds = false): string {
  if (!isValid(date)) {
    throw new Error('Invalid date provided');
  }

  const localeObj = getLocale(locale);

  // German uses 24-hour format, English uses 12-hour format
  const timeFormat = {
    en: includeSeconds ? 'h:mm:ss a' : 'h:mm a',
    de: includeSeconds ? 'HH:mm:ss' : 'HH:mm',
    other: includeSeconds ? 'pp' : 'p',
  }[family(locale)];

  return format(date, timeFormat, { locale: localeObj });
}

/**
 * Format a date and time according to locale
 *
 * @param date - Date to format
 * @param locale - Locale code ('de' or 'en')
 * @returns Formatted date and time string
 * @throws Error if date is invalid
 *
 * @example
 * formatDateTime(new Date('2024-03-15T14:30:00'), 'de') // "15. März 2024, 14:30"
 * formatDateTime(new Date('2024-03-15T14:30:00'), 'en') // "March 15, 2024, 2:30 PM"
 */
export function formatDateTime(date: Date, locale: string): string {
  if (!isValid(date)) {
    throw new Error('Invalid date provided');
  }

  const localeObj = getLocale(locale);

  // Combined date and time format
  const dateTimeFormat = {
    en: 'MMMM d, yyyy, h:mm a',
    de: 'd. MMMM yyyy, HH:mm',
    other: 'PPP, p',
  }[family(locale)];

  return format(date, dateTimeFormat, { locale: localeObj });
}

/**
 * Format a relative time (e.g., "2 hours ago", "in 3 days")
 *
 * @param date - Date to format
 * @param locale - Locale code ('de' or 'en')
 * @param baseDate - Optional base date for comparison (defaults to now)
 * @returns Relative time string
 * @throws Error if date is invalid
 *
 * @example
 * formatRelativeTime(new Date('2024-03-15T12:00:00'), 'de', new Date('2024-03-15T14:30:00'))
 * // "vor 2 Stunden" (2 hours ago)
 */
export function formatRelativeTime(date: Date, locale: string, baseDate?: Date): string {
  if (!isValid(date)) {
    throw new Error('Invalid date provided');
  }

  const localeObj = getLocale(locale);

  // If baseDate is provided, calculate the distance between dates
  if (baseDate) {
    return formatDistance(date, baseDate, {
      locale: localeObj,
      addSuffix: true,
      includeSeconds: false,
    });
  }

  return formatDistanceToNow(date, {
    locale: localeObj,
    addSuffix: true,
    includeSeconds: false,
  });
}

/**
 * Parse a date string into a Date object
 *
 * @param dateString - Date string to parse
 * @param formatString - Optional format string for parsing (defaults to ISO 8601)
 * @returns Parsed Date object
 * @throws Error if date string is invalid
 *
 * @example
 * parseDate('2024-03-15T14:30:00Z') // Date object
 * parseDate('15.03.2024', 'dd.MM.yyyy') // Date object
 */
export function parseDate(dateString: string, formatString?: string): Date {
  let parsedDate: Date;

  if (formatString) {
    parsedDate = parse(dateString, formatString, new Date());
  } else {
    parsedDate = parseISO(dateString);
  }

  if (!isValid(parsedDate)) {
    throw new Error(`Invalid date string: ${dateString}`);
  }

  return parsedDate;
}
