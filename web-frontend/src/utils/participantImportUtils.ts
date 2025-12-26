/**
 * Utility functions for parsing and converting participant CSV data
 * for batch import feature
 */

import Papa from 'papaparse';
import type {
  SourceParticipant,
  BatchRegistrationRequest,
  BatchRegistrationItem,
} from '../types/participantImport.types';

/**
 * Parse participant CSV file
 *
 * @param fileContent - Raw CSV file content as string
 * @returns Array of parsed participants
 * @throws Error if CSV structure is invalid or parsing fails
 */
export function parseParticipantCsv(fileContent: string): SourceParticipant[] {
  // Remove BOM character if present (common in UTF-8 files from Excel)
  const cleanContent = fileContent.replace(/^\uFEFF/, '');

  const { data, errors } = Papa.parse<SourceParticipant>(cleanContent, {
    header: true,
    skipEmptyLines: true,
    transformHeader: (header) => header.trim(),
  });

  if (errors.length > 0) {
    throw new Error(`CSV parsing errors: ${errors.map((e) => e.message).join(', ')}`);
  }

  // Validate structure (should have at least the required metadata columns)
  if (data.length > 0) {
    const columns = Object.keys(data[0]);
    const requiredColumns = ['Name', 'FirstName', 'LastName', 'BestMail', 'companyKey'];
    const missingColumns = requiredColumns.filter((col) => !columns.includes(col));

    if (missingColumns.length > 0) {
      throw new Error(`Missing required columns: ${missingColumns.join(', ')}`);
    }
  }

  return data;
}

/**
 * Normalize name for username generation
 *
 * Handles German characters (ä→ae, ö→oe, ü→ue, ß→ss)
 * Removes all non-alphanumeric characters
 *
 * @param str - Name string to normalize
 * @returns Normalized lowercase string
 */
function normalizeName(str: string): string {
  return str
    .toLowerCase()
    .replace(/ä/g, 'ae')
    .replace(/ö/g, 'oe')
    .replace(/ü/g, 'ue')
    .replace(/ß/g, 'ss')
    .replace(/[^a-z0-9]/g, '');
}

/**
 * Construct username from first name and last name
 *
 * Format: firstname.lastname (e.g., "john.doe")
 * Handles German characters (ä→ae, ö→oe, ü→ue, ß→ss)
 *
 * @param firstName - Participant's first name
 * @param lastName - Participant's last name
 * @returns Constructed username
 * @throws Error if both firstName and lastName are empty or normalize to empty strings
 */
export function constructUsername(firstName: string, lastName: string): string {
  const normalizedFirst = normalizeName(firstName || '');
  const normalizedLast = normalizeName(lastName || '');

  // Validate: at least one name component must be non-empty
  if (!normalizedFirst && !normalizedLast) {
    throw new Error('Cannot construct username: both first name and last name are empty');
  }

  // Use "unknown" as fallback if one name is empty
  const userFirst = normalizedFirst || 'unknown';
  const userLast = normalizedLast || 'unknown';

  return `${userFirst}.${userLast}`;
}

/**
 * Generate synthetic email for participants without email
 *
 * Format: firstname.lastname@batbern.ch
 * Handles German characters (ä→ae, ö→oe, ü→ue, ß→ss)
 *
 * @param firstName - Participant's first name
 * @param lastName - Participant's last name
 * @returns Synthetic email address
 * @throws Error if both firstName and lastName are empty or normalize to empty strings
 */
export function generateSyntheticEmail(firstName: string, lastName: string): string {
  const username = constructUsername(firstName, lastName);
  return `${username}@batbern.ch`;
}

/**
 * Extract event participation from CSV row
 *
 * Returns array of event codes where participant attended
 * Columns 1-57 represent events: "1" = attended, "" = not attended
 *
 * @param participant - Source participant from CSV
 * @returns Array of event codes (e.g., ["BATbern2", "BATbern25"])
 */
export function extractEventParticipation(participant: SourceParticipant): string[] {
  const eventCodes: string[] = [];

  for (let i = 1; i <= 57; i++) {
    const columnValue = participant[i.toString()];
    if (columnValue === '1') {
      eventCodes.push(`BATbern${i}`);
    }
  }

  return eventCodes;
}

/**
 * Convert source participant to batch registration request
 *
 * Creates API request payload from parsed CSV participant
 * Generates synthetic email if BestMail is missing
 *
 * @param participant - Source participant from CSV
 * @returns Batch registration request for API
 */
export function convertParticipantToRegistrationRequest(
  participant: SourceParticipant
): BatchRegistrationRequest {
  // Use provided email or generate synthetic one
  const email =
    participant.BestMail?.trim() ||
    generateSyntheticEmail(participant.FirstName, participant.LastName);

  const eventCodes = extractEventParticipation(participant);

  const registrations: BatchRegistrationItem[] = eventCodes.map((eventCode) => ({
    eventCode,
    status: 'ATTENDED',
  }));

  return {
    participantEmail: email,
    firstName: participant.FirstName,
    lastName: participant.LastName,
    companyId: participant.companyKey?.trim() || undefined, // Story 3.2: Include company from CSV
    registrations,
  };
}
