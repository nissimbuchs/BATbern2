/**
 * Session Batch Import Types
 *
 * Types for importing historical sessions from legacy JSON format (sessions.json)
 * into the Event Management Service.
 *
 * Used by SessionBatchImportModal and useSessionBatchImport hook.
 */

/**
 * Source session format from legacy sessions.json
 *
 * File location: apps/BATspa-old/src/api/sessions.json
 *
 * Two types of sessions:
 * 1. Simple sessions (Moderation, Programmheft) - Has authoren field for moderator name
 * 2. Detailed sessions (Presentations) - Has abstract and referenten fields
 */
export interface LegacySession {
  /** Event number this session belongs to (links to BATbern{bat}) */
  bat: number;
  /** Session title */
  title: string;
  /** Session abstract/description */
  abstract: string;
  /** PDF filename (optional) - will be appended to description */
  pdf?: string;
  /** Moderator name for "Moderation" sessions - e.g., "Thomas Goetz" */
  authoren?: string;
  /** Speaker details for detailed sessions */
  referenten?: LegacySpeaker[];
}

/**
 * Speaker data from legacy sessions.json
 */
export interface LegacySpeaker {
  /** Speaker full name */
  name: string;
  /** Speaker bio */
  bio?: string;
  /** Company identifier */
  company?: string;
  /** Portrait filename */
  portrait?: string;
  /** Speaker ID - maps to username in user_profiles */
  speakerId?: string;
}

/**
 * Request payload for batch import API
 * Matches BatchImportSessionRequest.java DTO
 */
export interface BatchImportSessionRequest {
  /** Session title */
  title: string;
  /** Session description (from abstract field) */
  description: string;
  /** PDF filename for reference */
  pdf?: string;
  /** Event number (bat field) */
  bat: number;
  /** List of speakers */
  referenten?: LegacySpeaker[];
  /** Moderator names */
  authoren?: string;
}

/**
 * Import status for each session during batch import
 */
export type SessionImportStatus = 'pending' | 'importing' | 'success' | 'error' | 'skipped';

/**
 * Candidate for import with transformation and status tracking
 */
export interface SessionImportCandidate {
  /** Original source data from sessions.json */
  source: LegacySession;
  /** Transformed payload for batch import API */
  apiPayload: BatchImportSessionRequest;
  /** Current import status */
  importStatus: SessionImportStatus;
  /** Error message if import failed */
  errorMessage?: string;
  /** Event code for display (e.g., "BATbern142") */
  eventCode: string;
  /** Number of speakers */
  speakersCount: number;
}

/**
 * Result of a batch import operation
 * Matches BatchImportSessionResult.java DTO
 */
export interface SessionBatchImportResult {
  /** Total sessions processed */
  totalProcessed: number;
  /** Successfully created */
  successfullyCreated: number;
  /** Skipped (duplicates) */
  skipped: number;
  /** Failed to import */
  failed: number;
  /** Detailed results per session */
  details: SessionImportDetail[];
}

/**
 * Detail for a single session import result
 * Matches SessionImportDetail.java DTO
 */
export interface SessionImportDetail {
  /** Session title */
  title: string;
  /** Import status */
  status: 'success' | 'skipped' | 'failed';
  /** Status message */
  message: string;
  /** Generated session slug (null if failed) */
  sessionSlug?: string;
}

/**
 * Props for the session batch import modal component
 */
export interface SessionBatchImportModalProps {
  /** Whether the modal is open */
  open: boolean;
  /** Callback when modal is closed */
  onClose: () => void;
  /** Callback when import is complete */
  onImportComplete?: (result: SessionBatchImportResult) => void;
}
