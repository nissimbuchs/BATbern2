/**
 * TypeScript types for participant batch import feature
 */

import type { components } from './generated/events-api.types';

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
 * Re-export API types for convenience
 */
export type BatchRegistrationRequest = components['schemas']['BatchRegistrationRequest'];
export type BatchRegistrationResponse = components['schemas']['BatchRegistrationResponse'];
export type BatchRegistrationItem = components['schemas']['BatchRegistrationItem'];
export type FailedRegistration = components['schemas']['FailedRegistration'];
