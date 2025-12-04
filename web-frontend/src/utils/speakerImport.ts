/**
 * Speaker Import Utilities
 *
 * Functions for parsing, validating, and transforming legacy speaker JSON
 * into API-compatible format for batch import as SPEAKER role users.
 */

import type { CreateUserFormData } from '@/types/user.types';
import type {
  LegacySpeaker,
  SpeakerImportCandidate,
  ExistingUserData,
} from '@/types/speakerImport.types';

/**
 * Extracts first and last name from a full name string.
 * Handles titles like "Dr.", "Prof.", etc.
 *
 * @example
 * parseFullName("Adrian Gschwend") // => { firstName: "Adrian", lastName: "Gschwend" }
 * parseFullName("Dr. Thomas Goetz") // => { firstName: "Thomas", lastName: "Goetz" }
 * parseFullName("Andreas R. Lehmann (Swisscom)") // => { firstName: "Andreas R.", lastName: "Lehmann" }
 */
export function parseFullName(fullName: string): { firstName: string; lastName: string } {
  // Remove content in parentheses (e.g., "(Swisscom)")
  const cleanName = fullName.replace(/\s*\([^)]*\)\s*/g, '').trim();

  // Remove common titles
  const withoutTitles = cleanName.replace(/^(Dr\.|Prof\.|Dipl\.|Ing\.|lic\.)\s*/gi, '').trim();

  const parts = withoutTitles.split(/\s+/);

  if (parts.length === 1) {
    return { firstName: parts[0], lastName: '' };
  }

  // Last part is the last name, everything else is first name
  const lastName = parts.pop() || '';
  const firstName = parts.join(' ');

  return { firstName, lastName };
}

/**
 * Generates a synthetic email from speaker ID.
 * Format: id@batbern.ch (for import purposes, to be updated later)
 *
 * @example
 * generateSyntheticEmail("adrian.gschwend") // => "adrian.gschwend@batbern.ch"
 */
export function generateSyntheticEmail(speakerId: string): string {
  // Clean the ID to be email-safe
  const cleanId = speakerId.toLowerCase().replace(/[^a-z0-9.-]/g, '.');

  return `${cleanId}@batbern.ch`;
}

/**
 * Transforms a source speaker object to API CreateUserFormData format
 *
 * CompanyId handling:
 * - If a companyIdMap is provided, maps legacy IDs to database IDs
 * - Otherwise, passes through the source companyId directly (assumes IDs match)
 */
export function transformSpeakerForApi(
  source: LegacySpeaker,
  companyIdMap?: Map<string, string>
): CreateUserFormData {
  const { firstName, lastName } = parseFullName(source.name);

  // Map legacy companyId to actual database ID if mapping provided
  // Otherwise pass through directly (assumes IDs match)
  let resolvedCompanyId: string | undefined;
  if (source.companyId) {
    if (companyIdMap) {
      resolvedCompanyId = companyIdMap.get(source.companyId.toLowerCase());
    } else {
      // Pass through directly - assume legacy IDs match database IDs
      resolvedCompanyId = source.companyId;
    }
  }

  return {
    firstName: firstName || source.id.split('.')[0] || 'Unknown',
    lastName: lastName || source.id.split('.').slice(1).join(' ') || 'Speaker',
    email: generateSyntheticEmail(source.id),
    bio: source.bio || undefined,
    companyId: resolvedCompanyId,
    initialRoles: ['SPEAKER'],
  };
}

/**
 * Parses JSON content and validates it as an array of source speakers
 *
 * @throws Error if JSON is invalid or doesn't contain speaker array
 */
export function parseSpeakersJson(jsonContent: string): LegacySpeaker[] {
  let parsed: unknown;

  try {
    parsed = JSON.parse(jsonContent);
  } catch (error) {
    throw new Error(`Invalid JSON: ${error instanceof Error ? error.message : 'Parse error'}`);
  }

  if (!Array.isArray(parsed)) {
    throw new Error('JSON must be an array of speakers');
  }

  // Validate each item has required fields
  const speakers: LegacySpeaker[] = [];
  for (let i = 0; i < parsed.length; i++) {
    const item = parsed[i];
    if (typeof item !== 'object' || item === null) {
      throw new Error(`Item at index ${i} is not an object`);
    }

    if (typeof item.id !== 'string' || !item.id) {
      throw new Error(`Item at index ${i} is missing required 'id' field`);
    }

    if (typeof item.name !== 'string' || !item.name) {
      throw new Error(`Item at index ${i} is missing required 'name' field`);
    }

    // Cast to LegacySpeaker - we've validated the minimum required fields
    speakers.push(item as LegacySpeaker);
  }

  return speakers;
}

/**
 * Creates import candidates from source speakers
 */
export function createImportCandidates(sources: LegacySpeaker[]): SpeakerImportCandidate[] {
  return sources.map((source) => ({
    source,
    apiPayload: transformSpeakerForApi(source),
    importStatus: 'pending' as const,
    // Include portraitUrl if present in source (added by update-speaker-portrait-urls.js)
    portraitUrl: source.portraitUrl,
  }));
}

/**
 * Checks if a username already exists in the provided list
 */
export function isDuplicateUsername(username: string, existingUsernames: string[]): boolean {
  return existingUsernames.some((existing) => existing.toLowerCase() === username.toLowerCase());
}

/**
 * Compares import data with existing user data and returns list of changed fields
 */
export function detectChanges(
  apiPayload: CreateUserFormData,
  existingUser: ExistingUserData,
  hasPortraitUrl: boolean
): string[] {
  const changes: string[] = [];

  // Compare text fields (normalize empty strings and undefined)
  const normalize = (val: string | undefined | null): string => (val || '').trim();

  if (normalize(apiPayload.firstName) !== normalize(existingUser.firstName)) {
    changes.push('firstName');
  }
  if (normalize(apiPayload.lastName) !== normalize(existingUser.lastName)) {
    changes.push('lastName');
  }
  if (normalize(apiPayload.bio) !== normalize(existingUser.bio)) {
    changes.push('bio');
  }

  // For companyId: only compare if import has a value
  // If import has no companyId (undefined), don't treat it as a change - keep existing
  // Use case-insensitive comparison since IDs may differ in case
  if (apiPayload.companyId) {
    const importCompanyId = normalize(apiPayload.companyId).toLowerCase();
    const existingCompanyId = normalize(existingUser.companyId).toLowerCase();
    if (importCompanyId !== existingCompanyId) {
      changes.push('companyId');
    }
  }

  // Check if portrait needs upload (has URL to upload and user doesn't have one)
  if (hasPortraitUrl && !existingUser.profilePictureUrl) {
    changes.push('portrait');
  }

  return changes;
}
