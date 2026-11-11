/**
 * TypeScript types for participant batch import feature
 */

import type { components } from './generated/event-registrations-api.types';

/**
 * Source participant data structure from CSV
 * Represents a row from the anmeldungen.csv file
 */
export interface SourceParticipant {
  /** Full name (often "FirstName,FirstName,LastName" format) */
  Name: string;
  /** First name */
  FirstName: string;
  /** Last name */
  LastName: string;
  /** Email address (may be empty for ~15% of participants) */
  BestMail: string;
  /** Company identifier (not used for import) */
  companyKey: string;
  /** Event columns 1-57: "1" = attended, "" = not attended */
  [key: string]: string;
}

/**
 * Import status for each participant
 */
export type ImportStatus = 'pending' | 'importing' | 'success' | 'error' | 'skipped';

/**
 * Participant candidate for batch import
 * Represents a parsed participant ready for import with UI state
 */
export interface ParticipantImportCandidate {
  /** First name */
  firstName: string;
  /** Last name */
  lastName: string;
  /** Email address (real or synthetic) */
  email: string;
  /** Constructed username (firstname.lastname) */
  username: string;
  /** Number of events participant attended */
  eventCount: number;
  /** Array of event codes participant attended (e.g., ["BATbern1", "BATbern25"]) */
  eventCodes?: string[];
  /** True if email is synthetic (generated @batbern.ch) */
  isSyntheticEmail: boolean;
  /** True if user already exists in the system */
  isExisting?: boolean;
  /** Current import status */
  importStatus: ImportStatus;
  /** Error message if import failed */
  errorMessage?: string;
}

/**
 * Result summary for batch import operation
 */
export interface ParticipantBatchImportResult {
  /** Total participants processed */
  total: number;
  /** Successfully imported */
  success: number;
  /** Failed to import */
  failed: number;
  /** Skipped (e.g., duplicates, validation errors) */
  skipped: number;
}

/**
 * Props for ParticipantBatchImportModal component
 */
export interface ParticipantBatchImportModalProps {
  /** Whether modal is open */
  open: boolean;
  /** Callback when modal should close */
  onClose: () => void;
  /** Optional callback after successful import */
  onImportComplete?: (result: ParticipantBatchImportResult) => void;
}

/**
 * CSV parsing result
 */
export interface CsvParseResult {
  /** Parsed participants */
  participants: SourceParticipant[];
  /** Validation errors */
  errors: string[];
}

/**
 * One CSV person ready to import: the frontend model the import hook regroups per event (Story
 * 4.10c). Not a wire shape: the API takes the people of one event at a time
 * (`ParticipantImportPerson`, `POST /events/{eventCode}/participants`).
 */
export interface ParticipantImportRow {
  /** Real or synthetic (`@batbern.ch`) address */
  email: string;
  firstName: string;
  lastName: string;
  /** Company identifier (company name); optional */
  companyId?: string;
  /** The events the person attended (e.g. ["BATbern1", "BATbern25"]) */
  eventCodes: string[];
}

export type ParticipantImportPerson = components['schemas']['ParticipantImportPerson'];
export type ParticipantImportResult = components['schemas']['ParticipantImportResult'];
