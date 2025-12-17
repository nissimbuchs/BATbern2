/**
 * Event Batch Import Types
 *
 * Types for importing historical events from legacy JSON format (topics.json + sessions.json)
 * into the new Events API.
 *
 * Used by EventBatchImportModal and useEventBatchImport hook.
 */

import type { components } from '@/types/generated/events-api.types';

type CreateEventRequest = components['schemas']['CreateEventRequest'];

/**
 * Source event format from legacy topics.json (after Phase 0 pre-processing)
 *
 * File location: apps/BATspa-old/src/api/topics.json
 *
 * Enhanced with parsedDate, description, and moderator fields during Phase 0 pre-processing.
 * Category field added for topic management integration (Story 5.2).
 */
export interface LegacyEvent {
  /** Event number (e.g., 1, 2, 3...) */
  bat: number;
  /** Event title (German) */
  topic: string;
  /** Event topic category (e.g., "Artificial Intelligence & Machine Learning", "Cloud & Infrastructure") */
  category: string;
  /** Original German date string (e.g., "24. Juni 05, 16:00h - 18:30h") - kept for reference */
  datum: string;
  /** Parsed ISO 8601 datetime (added in Phase 0) - e.g., "2005-06-24T16:00:00+02:00" */
  parsedDate: string;
  /** AI-generated event description (added in Phase 0) combining all session abstracts */
  description: string;
  /** Event moderator full name (added in Phase 0) - e.g., "Thomas Goetz", "Nissim Buchs" */
  moderator: string;
  /** Legacy event type classification */
  eventType: 'Abend-BAT' | 'Ganztages-BAT' | 'Nachmittags-BAT';
  /** Optional: Planned event number */
  planned?: number;
  /** Optional: Next event reference */
  next?: number;
}

/**
 * Session data for organizer extraction
 *
 * File location: apps/BATspa-old/src/api/sessions.json
 *
 * Two types of sessions:
 * 1. Simple sessions (Moderation, Programmheft) - Has authoren field for moderator name
 * 2. Detailed sessions (Presentations) - Has abstract and referenten fields
 */
export interface LegacySession {
  /** Event number this session belongs to */
  bat: number;
  /** Session title */
  title: string;
  /** PDF filename (optional) */
  pdf?: string;
  /** Moderator name for "Moderation" sessions (Type 1) - e.g., "Thomas Goetz" */
  authoren?: string;
  /** Session abstract for detailed sessions (Type 2) - used for description generation */
  abstract?: string;
  /** Speaker details for detailed sessions (Type 2) */
  referenten?: Array<{
    name: string;
    bio?: string;
    company?: string;
    portrait?: string;
  }>;
}

/**
 * Import status for each event during batch import
 */
export type ImportStatus = 'pending' | 'importing' | 'success' | 'error' | 'skipped';

/**
 * Import mode - determines whether to create new events or update existing ones
 */
export type ImportMode = 'create' | 'update';

/**
 * Fields that can be selectively updated during batch import
 */
export interface UpdateFieldSelection {
  /** Update event title */
  title: boolean;
  /** Update event description */
  description: boolean;
  /** Update event topic/category */
  topic: boolean;
  /** Update event date */
  date: boolean;
  /** Update venue information */
  venue: boolean;
  /** Update organizer */
  organizer: boolean;
}

/**
 * Candidate for import with transformation and status tracking
 */
export interface EventImportCandidate {
  /** Original source data from topics.json */
  source: LegacyEvent;
  /** Transformed payload for CreateEventRequest API */
  apiPayload: CreateEventRequest;
  /** Current import status */
  importStatus: ImportStatus;
  /** Error message if import failed */
  errorMessage?: string;
  /** Topic category from source data (for topic assignment) */
  topicCategory?: string;
  /** Whether this event already exists (for update mode) */
  existsInDatabase?: boolean;
}

/**
 * Result of a batch import operation
 */
export interface EventBatchImportResult {
  /** Total events processed */
  total: number;
  /** Successfully imported */
  success: number;
  /** Failed to import */
  failed: number;
  /** Skipped (already exists) */
  skipped: number;
}

/**
 * Organizer mapping: Event number → Username
 *
 * Built from sessions.json by finding "Moderation" sessions
 * and transforming moderator names to username format.
 */
export interface OrganizerMap {
  [eventNumber: number]: string;
}

/**
 * Props for the batch import modal component
 */
export interface EventBatchImportModalProps {
  /** Whether the modal is open */
  open: boolean;
  /** Callback when modal is closed */
  onClose: () => void;
  /** Callback when import is complete */
  onImportComplete?: (result: EventBatchImportResult) => void;
  /** Import mode - create new or update existing events */
  mode?: ImportMode;
}
