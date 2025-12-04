/**
 * Speaker Batch Import Types
 *
 * Types for importing speakers from legacy JSON format (speakers.json)
 * into the new User API as SPEAKER role users.
 *
 * Used by SpeakerBatchImportModal and useSpeakerBatchImport hook.
 */

import type { CreateUserFormData } from '@/types/user.types';

/**
 * Source speaker format from legacy speakers.json
 *
 * File location: apps/BATspa-old/src/api/speakers.json
 */
export interface LegacySpeaker {
  /** Speaker ID (e.g., "adrian.gschwend", "thomas.goetz") */
  id: string;
  /** Full name (e.g., "Adrian Gschwend", "Dr. Thomas Goetz") */
  name: string;
  /** Company ID reference (e.g., "bfh", "mobiliar") - can be null */
  companyId: string | null;
  /** Portrait filename (e.g., "adrian.gschwend.jpg") - can be null */
  portrait: string | null;
  /** Biography text - can be null */
  bio: string | null;
  /** Portrait URL for import (added by update-speaker-portrait-urls.js) */
  portraitUrl?: string;
}

/**
 * Import status for each speaker during batch import
 */
export type ImportStatus = 'pending' | 'importing' | 'success' | 'updated' | 'error' | 'skipped';

/**
 * Existing user data for comparison during import
 */
export interface ExistingUserData {
  username: string;
  firstName: string;
  lastName: string;
  bio?: string;
  companyId?: string;
  profilePictureUrl?: string;
}

/**
 * Candidate for import with transformation and status tracking
 */
export interface SpeakerImportCandidate {
  /** Original source data from speakers.json */
  source: LegacySpeaker;
  /** Transformed payload for CreateUserRequest API */
  apiPayload: CreateUserFormData;
  /** Current import status */
  importStatus: ImportStatus;
  /** Error message if import failed */
  errorMessage?: string;
  /** Existing user data if user already exists (for update comparison) */
  existingUser?: ExistingUserData;
  /** Portrait URL for upload (from source.portraitUrl or localhost server) */
  portraitUrl?: string;
  /** Whether any fields need updating */
  hasChanges?: boolean;
  /** List of fields that will be updated */
  changedFields?: string[];
}

/**
 * Result of a batch import operation
 */
export interface SpeakerBatchImportResult {
  /** Total speakers processed */
  total: number;
  /** Successfully created (new users) */
  success: number;
  /** Successfully updated (existing users) */
  updated: number;
  /** Failed to import */
  failed: number;
  /** Skipped (already exists, no changes) */
  skipped: number;
}

/**
 * Props for the batch import modal component
 */
export interface SpeakerBatchImportModalProps {
  /** Whether the modal is open */
  open: boolean;
  /** Callback when modal is closed */
  onClose: () => void;
  /** Callback when import is complete */
  onImportComplete?: (result: SpeakerBatchImportResult) => void;
}
