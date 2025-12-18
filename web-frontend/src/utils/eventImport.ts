/**
 * Event Import Utilities
 *
 * Functions for parsing, validating, and transforming legacy event JSON
 * (topics.json + sessions.json) into API-compatible format for batch import.
 */

import type { components } from '@/types/generated/events-api.types';
import type {
  LegacyEvent,
  LegacySession,
  EventImportCandidate,
  OrganizerMap,
} from '@/types/eventImport.types';

type CreateEventRequest = components['schemas']['CreateEventRequest'];

/**
 * Transforms a moderator full name to username format.
 *
 * Removes company suffixes, extracts first and last name,
 * removes middle initials, and converts to lowercase with dot separator.
 *
 * @example
 * transformNameToUsername("Thomas Goetz") // => "thomas.goetz"
 * transformNameToUsername("Nissim J. Buchs, RTC AG") // => "nissim.buchs"
 * transformNameToUsername("Sara Kim") // => "sara.kim"
 */
export function transformNameToUsername(fullName: string): string {
  // Remove company suffix (everything after comma)
  const nameOnly = fullName.split(',')[0].trim();

  // Split into parts
  const parts = nameOnly.split(/\s+/).filter((p) => p.length > 0);

  if (parts.length === 0) {
    return '';
  }

  // If only one part, return it
  if (parts.length === 1) {
    return parts[0].toLowerCase();
  }

  // Extract first name (first part) and last name (last part)
  // Skip middle initials (single letter parts with optional dot)
  const firstName = parts[0];
  const lastName = parts[parts.length - 1];

  // Join with dot and convert to lowercase
  return `${firstName}.${lastName}`.toLowerCase();
}

/**
 * Builds organizer map from sessions data.
 *
 * Finds "Moderation" sessions for each event and extracts moderator name
 * from the authoren field. Transforms name to username format.
 *
 * @param sessions - Array of legacy sessions
 * @returns Map of event number to organizer username
 *
 * @example
 * buildOrganizerMap(sessions)
 * // => { 11: "thomas.goetz", 14: "thomas.goetz", ... }
 */
export function buildOrganizerMap(sessions: LegacySession[]): OrganizerMap {
  const map: OrganizerMap = {};

  // Group sessions by event number
  for (const session of sessions) {
    // Look for "Moderation" sessions
    if (session.title === 'Moderation' && session.authoren) {
      const username = transformNameToUsername(session.authoren);
      if (username) {
        map[session.bat] = username;
      }
    }
  }

  return map;
}

/**
 * Determines venue based on event number.
 *
 * Smart defaults:
 * - Events 1-9: Kursaal Bern
 * - Events 10+: Zentrum Paul Klee
 * - Default capacity: 200
 *
 * @param eventNumber - Event number (bat field)
 * @returns Venue details with name, address, and capacity
 */
export function determineVenue(eventNumber: number): {
  name: string;
  address: string;
  capacity: number;
} {
  if (eventNumber >= 1 && eventNumber <= 9) {
    return {
      name: 'Kursaal Bern',
      address: 'Kornhausstrasse 3, 3013 Bern',
      capacity: 50,
    };
  }

  // Events 10 and above
  return {
    name: 'Zentrum Paul Klee, Bern',
    address: 'Monument im Fruchtland 3, 3006 Bern',
    capacity: 200,
  };
}

/**
 * Calculates registration deadline from event date.
 *
 * Subtracts 1 day from event date and sets time to 23:59:59 UTC.
 *
 * @param eventDate - ISO 8601 event date string
 * @returns ISO 8601 registration deadline string in UTC
 *
 * @example
 * calculateRegistrationDeadline("2005-06-24T16:00:00+02:00")
 * // => "2005-06-23T23:59:59.000Z"
 */
export function calculateRegistrationDeadline(eventDate: string): string {
  const date = new Date(eventDate);
  date.setUTCDate(date.getUTCDate() - 1);
  date.setUTCHours(23, 59, 59, 0);
  return date.toISOString();
}

/**
 * Transforms a legacy event to API CreateEventRequest format.
 *
 * Maps all fields including smart defaults for venue, registration deadline,
 * and status. Stores legacy metadata in metadata field as JSON.
 * Transforms moderator name to username format.
 *
 * @param event - Legacy event from topics.json (with moderator field)
 * @returns CreateEventRequest payload for API
 */
export function transformEventForApi(event: LegacyEvent): CreateEventRequest {
  // Transform moderator name to username
  const organizerUsername = transformNameToUsername(event.moderator);
  const venue = determineVenue(event.bat);
  const registrationDeadline = calculateRegistrationDeadline(event.parsedDate);

  // Store legacy data in metadata for reference
  const metadata = {
    legacyEventType: event.eventType,
    legacyDatum: event.datum,
    legacyBatNumber: event.bat,
  };

  return {
    title: event.topic.trim(),
    eventNumber: event.bat,
    date: event.parsedDate,
    registrationDeadline,
    venueName: venue.name,
    venueAddress: venue.address,
    venueCapacity: venue.capacity,
    status: 'archived', // All historical events are archived
    organizerUsername,
    currentAttendeeCount: 0,
    description: event.description,
    metadata: JSON.stringify(metadata),
    eventType: 'AFTERNOON', // Default for historical events
  };
}

/**
 * Parses topics.json content and validates structure.
 *
 * Validates that each event has required fields after Phase 0 pre-processing
 * (bat, topic, datum, parsedDate, description, eventType).
 *
 * @param jsonContent - Raw JSON string from topics.json
 * @returns Array of validated LegacyEvent objects
 * @throws Error if JSON is invalid or events are missing required fields
 */
export function parseEventsJson(jsonContent: string): LegacyEvent[] {
  let parsed: unknown;

  try {
    parsed = JSON.parse(jsonContent);
  } catch (error) {
    throw new Error(`Invalid JSON: ${error instanceof Error ? error.message : 'Parse error'}`);
  }

  if (!Array.isArray(parsed)) {
    throw new Error('JSON must be an array of events');
  }

  const events: LegacyEvent[] = [];
  for (let i = 0; i < parsed.length; i++) {
    const item = parsed[i];

    if (typeof item !== 'object' || item === null) {
      throw new Error(`Event at index ${i} is not an object`);
    }

    // Validate required fields
    if (typeof item.bat !== 'number') {
      throw new Error(`Event at index ${i} is missing required 'bat' field (number)`);
    }

    if (typeof item.topic !== 'string' || !item.topic) {
      throw new Error(`Event at index ${i} is missing required 'topic' field (string)`);
    }

    if (typeof item.datum !== 'string') {
      throw new Error(`Event at index ${i} is missing required 'datum' field (string)`);
    }

    if (typeof item.parsedDate !== 'string' || !item.parsedDate) {
      throw new Error(
        `Event at index ${i} is missing required 'parsedDate' field (Phase 0 preprocessing required)`
      );
    }

    if (typeof item.description !== 'string' || !item.description) {
      throw new Error(
        `Event at index ${i} is missing required 'description' field (Phase 0 preprocessing required)`
      );
    }

    if (typeof item.eventType !== 'string') {
      throw new Error(`Event at index ${i} is missing required 'eventType' field (string)`);
    }

    if (typeof item.moderator !== 'string' || !item.moderator) {
      throw new Error(
        `Event at index ${i} is missing required 'moderator' field (Phase 0 preprocessing required)`
      );
    }

    if (typeof item.category !== 'string' || !item.category) {
      throw new Error(
        `Event at index ${i} is missing required 'category' field (topic categorization required)`
      );
    }

    // Validate parsedDate is valid ISO 8601
    const parsedDate = new Date(item.parsedDate);
    if (isNaN(parsedDate.getTime())) {
      throw new Error(`Event at index ${i} has invalid 'parsedDate' format (must be ISO 8601)`);
    }

    events.push(item as LegacyEvent);
  }

  return events;
}

/**
 * Parses sessions.json content and validates structure.
 *
 * Validates that each session has required fields (bat, title).
 *
 * @param jsonContent - Raw JSON string from sessions.json
 * @returns Array of validated LegacySession objects
 * @throws Error if JSON is invalid or sessions are missing required fields
 */
export function parseSessionsJson(jsonContent: string): LegacySession[] {
  let parsed: unknown;

  try {
    parsed = JSON.parse(jsonContent);
  } catch (error) {
    throw new Error(`Invalid JSON: ${error instanceof Error ? error.message : 'Parse error'}`);
  }

  if (!Array.isArray(parsed)) {
    throw new Error('JSON must be an array of sessions');
  }

  const sessions: LegacySession[] = [];
  for (let i = 0; i < parsed.length; i++) {
    const item = parsed[i];

    if (typeof item !== 'object' || item === null) {
      throw new Error(`Session at index ${i} is not an object`);
    }

    if (typeof item.bat !== 'number') {
      throw new Error(`Session at index ${i} is missing required 'bat' field (number)`);
    }

    if (typeof item.title !== 'string' || !item.title) {
      throw new Error(`Session at index ${i} is missing required 'title' field (string)`);
    }

    sessions.push(item as LegacySession);
  }

  return sessions;
}

/**
 * Creates import candidates from source events.
 *
 * Transforms each legacy event to API format.
 * Moderator is extracted from the event.moderator field and transformed to username.
 * Category is preserved for topic assignment during import.
 *
 * @param events - Array of legacy events from topics.json
 * @returns Array of import candidates ready for processing
 */
export function createImportCandidates(events: LegacyEvent[]): EventImportCandidate[] {
  return events.map((event) => ({
    source: event,
    apiPayload: transformEventForApi(event),
    importStatus: 'pending' as const,
    topicCategory: event.category, // Preserve category for topic assignment
  }));
}

/**
 * Checks if an event number already exists in the provided list.
 *
 * @param eventNumber - Event number to check
 * @param existingNumbers - Set of existing event numbers
 * @returns True if event number exists, false otherwise
 */
export function isDuplicateEventNumber(eventNumber: number, existingNumbers: Set<number>): boolean {
  return existingNumbers.has(eventNumber);
}

/**
 * Builds partial payload for event update based on field selection.
 *
 * Only includes fields that are selected in the fieldSelection object.
 * Used for batch update mode to selectively update specific fields.
 *
 * @param candidate - Event import candidate with source data and API payload
 * @param fieldSelection - Object indicating which fields to include in update
 * @returns Partial update payload with only selected fields
 *
 * @example
 * ```typescript
 * const payload = buildPartialPayload(candidate, {
 *   title: true,
 *   description: true,
 *   topic: false,  // Not included
 *   date: false,   // Not included
 *   venue: false,  // Not included
 *   organizer: false // Not included
 * });
 * // => { title: "...", description: "..." }
 * ```
 */
export function buildPartialPayload(
  candidate: EventImportCandidate,
  fieldSelection: import('@/types/eventImport.types').UpdateFieldSelection
): Partial<CreateEventRequest> {
  const payload = candidate.apiPayload;
  const partial: Partial<CreateEventRequest> = {};

  // Only include selected fields
  if (fieldSelection.title) {
    partial.title = payload.title;
  }

  if (fieldSelection.description) {
    partial.description = payload.description;
  }

  // Note: topic assignment is handled separately via assignTopicToEvent
  // fieldSelection.topic controls whether to assign/update topic

  if (fieldSelection.date) {
    partial.date = payload.date;
    partial.registrationDeadline = payload.registrationDeadline;
  }

  if (fieldSelection.venue) {
    partial.venueName = payload.venueName;
    partial.venueAddress = payload.venueAddress;
    partial.venueCapacity = payload.venueCapacity;
  }

  if (fieldSelection.organizer) {
    partial.organizerUsername = payload.organizerUsername;
  }

  return partial;
}
