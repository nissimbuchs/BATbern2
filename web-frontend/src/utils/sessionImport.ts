/**
 * Session Import Utilities
 *
 * Functions for parsing, validating, and transforming legacy session JSON
 * (sessions.json) into API-compatible format for batch import.
 */

import type {
  LegacySession,
  SessionImportCandidate,
  BatchImportSessionRequest,
} from '@/types/sessionImport.types';

/**
 * Parses sessions.json content and validates structure
 *
 * @param jsonContent - Raw JSON file content
 * @returns Array of parsed legacy sessions
 * @throws Error if JSON is invalid or structure is unexpected
 */
export function parseSessionsJson(jsonContent: string): LegacySession[] {
  try {
    const parsed = JSON.parse(jsonContent);

    // Sessions.json is an array of session objects
    if (!Array.isArray(parsed)) {
      throw new Error('sessions.json must be an array');
    }

    // Validate each session has required fields
    const sessions = parsed.filter((session) => {
      if (typeof session !== 'object' || session === null) {
        return false;
      }
      if (typeof session.bat !== 'number' || !session.title) {
        return false;
      }
      return true;
    });

    if (sessions.length === 0) {
      throw new Error('No valid sessions found in file');
    }

    return sessions as LegacySession[];
  } catch (error) {
    if (error instanceof SyntaxError) {
      throw new Error(`Invalid JSON format: ${error.message}`);
    }
    throw error;
  }
}

/**
 * Transforms legacy sessions into import candidates
 *
 * @param sessions - Array of legacy sessions from sessions.json
 * @returns Array of import candidates ready for API submission
 */
export function createSessionImportCandidates(sessions: LegacySession[]): SessionImportCandidate[] {
  return sessions.map((session) => {
    const apiPayload: BatchImportSessionRequest = {
      title: session.title,
      abstract: session.abstract || '',
      pdf: session.pdf,
      materialUrl: session.materialUrl, // From generate-staging-sessions-json.js
      bat: session.bat,
      referenten: session.referenten,
      authoren: session.authoren,
    };

    const eventCode = `BATbern${session.bat}`;
    const speakersCount = session.referenten?.length || 0;

    return {
      source: session,
      apiPayload,
      importStatus: 'pending',
      eventCode,
      speakersCount,
    };
  });
}

/**
 * Filters sessions by event number
 *
 * @param sessions - Array of sessions
 * @param eventNumber - Event number to filter by
 * @returns Filtered array of sessions
 */
export function filterSessionsByEvent(
  sessions: LegacySession[],
  eventNumber: number
): LegacySession[] {
  return sessions.filter((session) => session.bat === eventNumber);
}

/**
 * Groups sessions by event number
 *
 * @param sessions - Array of sessions
 * @returns Map of event number to sessions
 */
export function groupSessionsByEvent(sessions: LegacySession[]): Map<number, LegacySession[]> {
  const grouped = new Map<number, LegacySession[]>();

  for (const session of sessions) {
    const existing = grouped.get(session.bat) || [];
    existing.push(session);
    grouped.set(session.bat, existing);
  }

  return grouped;
}

/**
 * Validates that all sessions reference valid event numbers
 *
 * @param sessions - Array of sessions
 * @param validEventNumbers - Set of valid event numbers
 * @returns Object with valid and invalid sessions
 */
export function validateSessionEventReferences(
  sessions: LegacySession[],
  validEventNumbers: Set<number>
): {
  valid: LegacySession[];
  invalid: LegacySession[];
} {
  const valid: LegacySession[] = [];
  const invalid: LegacySession[] = [];

  for (const session of sessions) {
    if (validEventNumbers.has(session.bat)) {
      valid.push(session);
    } else {
      invalid.push(session);
    }
  }

  return { valid, invalid };
}
