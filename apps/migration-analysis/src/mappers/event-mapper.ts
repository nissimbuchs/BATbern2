/**
 * Event Mapper - Maps legacy event/topic data to Event Management Service schema
 * Handles German date parsing and event type mapping
 */

import { randomUUID } from 'crypto';
import { LegacyTopic } from '../types/legacy-types';
import { Event, EventType, EventStatus, EventWorkflowState } from '../types/target-types';

/**
 * German month names mapping (lowercase for case-insensitive matching)
 */
const GERMAN_MONTHS: Record<string, number> = {
  'januar': 0,
  'februar': 1,
  'märz': 2,
  'maerz': 2, // Alternative spelling
  'april': 3,
  'mai': 4,
  'juni': 5,
  'juli': 6,
  'august': 7,
  'september': 8,
  'oktober': 9,
  'november': 10,
  'dezember': 11
};

/**
 * Parse German date formats to Date object
 * Handles multiple formats:
 * - "24. Juni 05, 16:00h - 18:30h" → 2005-06-24T16:00:00Z
 * - "Donnerstag, 2. Mai 2024, 16:00 - 18:30 Uhr" → 2024-05-02T16:00:00Z
 * - "2024-05-02" → 2024-05-02T00:00:00Z
 *
 * @param dateString - German date string from legacy system
 * @returns Parsed Date object
 */
export function parseGermanDate(dateString: string): Date {
  // Format 1: ISO date (YYYY-MM-DD)
  const isoMatch = dateString.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (isoMatch) {
    const [, year, month, day] = isoMatch;
    return new Date(parseInt(year), parseInt(month) - 1, parseInt(day));
  }

  // Format 2: German date with full year (DD. Month YYYY, HH:mm)
  // Matches both "DD. Month YYYY, HH:mm" and "DD. Month YYYY, HH:mmh"
  // Uses [a-zA-ZäöüÄÖÜß]+ to match German characters including umlauts
  const fullYearMatch = dateString.match(/(\d{1,2})\.\s+([a-zA-ZäöüÄÖÜß]+)\s+(\d{4}),?\s+(\d{1,2}):(\d{2})/);
  if (fullYearMatch) {
    const [, day, monthName, year, hour, minute] = fullYearMatch;
    const month = GERMAN_MONTHS[monthName.toLowerCase()];
    if (month !== undefined) {
      return new Date(parseInt(year), month, parseInt(day), parseInt(hour), parseInt(minute));
    }
  }

  // Format 3: German date with 2-digit year (DD. Month YY, HH:mmh)
  const shortYearMatch = dateString.match(/(\d{1,2})\.\s+([a-zA-ZäöüÄÖÜß]+)\s+(\d{2}),?\s+(\d{1,2}):(\d{2})/);
  if (shortYearMatch) {
    const [, day, monthName, year, hour, minute] = shortYearMatch;
    const month = GERMAN_MONTHS[monthName.toLowerCase()];
    if (month !== undefined) {
      // Convert 2-digit year to 4-digit (assume 2000s for 00-30, 1900s for 31-99)
      const fullYear = parseInt(year) <= 30 ? 2000 + parseInt(year) : 1900 + parseInt(year);
      return new Date(fullYear, month, parseInt(day), parseInt(hour), parseInt(minute));
    }
  }

  // Fallback: try to parse as-is
  const fallback = new Date(dateString);
  if (!isNaN(fallback.getTime())) {
    return fallback;
  }

  // If all parsing fails, throw error
  throw new Error(`Unable to parse date: ${dateString}`);
}

/**
 * Map legacy event type to EventType enum
 * @param legacyType - Event type from legacy system
 * @returns EventType enum value
 */
export function mapEventType(legacyType: string): EventType {
  switch (legacyType) {
    case 'Abend-BAT':
      return EventType.EVENING;
    case 'Ganztag-BAT':
      return EventType.FULL_DAY;
    case 'Halb-BAT':
      return EventType.AFTERNOON;
    default:
      // Default to EVENING for unknown types
      return EventType.EVENING;
  }
}

/**
 * Generate event code from BAT number
 * Pattern: "BATbern{number}"
 *
 * @param batNumber - BAT event number (1-60)
 * @returns Event code (e.g., "BATbern56")
 */
function generateEventCode(batNumber: number): string {
  return `BATbern${batNumber}`;
}

/**
 * Map legacy event/topic to target Event schema
 * @param legacyEvent - Event data from topics.json
 * @returns Mapped Event entity
 */
export function mapEvent(legacyEvent: LegacyTopic): Event {
  // Parse event date
  const eventDate = parseGermanDate(legacyEvent.datum);

  // Map event type
  const eventType = mapEventType(legacyEvent.eventType);

  // Truncate title to 255 chars if needed
  const title = legacyEvent.topic.length > 255
    ? legacyEvent.topic.substring(0, 255)
    : legacyEvent.topic;

  const event: Event = {
    id: randomUUID(), // Internal database UUID
    eventCode: generateEventCode(legacyEvent.bat), // Public API identifier
    eventNumber: legacyEvent.bat,
    title,
    eventDate,
    eventType,
    status: EventStatus.ARCHIVED, // All historical events are archived
    workflowState: EventWorkflowState.PUBLISHED // All historical events published
    // organizerId intentionally omitted - will be set during migration
  };

  return event;
}
