/**
 * Swiss UID Validator
 *
 * Validates Swiss Company UID format (CHE-XXX.XXX.XXX)
 * Based on Swiss Federal Register requirements
 */

export const SWISS_UID_PATTERN = /^CHE-\d{3}\.\d{3}\.\d{3}$/;

/**
 * Validates Swiss UID format
 * @param uid - The UID string to validate
 * @returns true if valid format, false otherwise
 */
export function isValidSwissUID(uid: string | undefined | null): boolean {
  if (!uid) return false;
  return SWISS_UID_PATTERN.test(uid.trim());
}

/**
 * Formats a Swiss UID string (removes extra spaces, converts to uppercase)
 * @param uid - The raw UID input
 * @returns Formatted UID or empty string if invalid
 */
export function formatSwissUID(uid: string | undefined | null): string {
  if (!uid) return '';
  const cleaned = uid.trim().toUpperCase();
  return isValidSwissUID(cleaned) ? cleaned : '';
}

/**
 * Validates and provides error message for Swiss UID
 * @param uid - The UID to validate
 * @returns Error message if invalid, undefined if valid
 */
export function validateSwissUID(uid: string | undefined): string | undefined {
  if (!uid || uid.trim() === '') {
    return undefined; // Optional field
  }

  if (!isValidSwissUID(uid)) {
    return 'Invalid Swiss UID format. Expected format: CHE-XXX.XXX.XXX';
  }

  return undefined;
}
