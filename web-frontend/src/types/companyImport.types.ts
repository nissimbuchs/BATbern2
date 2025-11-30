/**
 * Company Batch Import Types
 *
 * Types for importing companies from legacy JSON format into the new API.
 * Used by CompanyBatchImportModal and useCompanyBatchImport hook.
 */

import type { components } from '@/types/generated/company-api.types';

type CreateCompanyRequest = components['schemas']['CreateCompanyRequest'];

/**
 * Source company format from legacy JSON file (apps/BATspa-old/src/api/companies.json)
 */
export interface SourceCompany {
  /** Short identifier (e.g., "sbb", "mobiliar", "isc-ejpd") */
  id: string;
  /** Full company name (e.g., "SBB CFF FFS") */
  displayName: string;
  /** Logo filename (e.g., "sbb.jpg") - can be null */
  logo: string | null;
  /** Website URL (e.g., "https://www.sbb.ch") - can be null */
  url: string | null;
  /** Number of speakers from this company */
  speakerCount: number;
  /** Absolute path to local logo file - can be null */
  logoFilePath: string | null;
  /** Data quality status */
  status: 'complete' | 'needs_logo' | 'pending_url' | 'pending_logo';
  /** Optional flag indicating logo availability */
  has_logo?: boolean;
  /** Optional notes about the company */
  note?: string;
  /** External logo URL (alternative to local file) */
  logoUrl?: string;
  /** Industry category (e.g., "Transportation", "Financial Services") */
  industry?: string;
}

/**
 * Import status for each company during batch import
 */
export type ImportStatus = 'pending' | 'importing' | 'success' | 'updated' | 'error' | 'skipped';

/**
 * Candidate for import with transformation and status tracking
 */
export interface ImportCandidate {
  /** Original source data */
  source: SourceCompany;
  /** Transformed payload for API */
  apiPayload: CreateCompanyRequest;
  /** Logo URL for preview (logoUrl from source, or null) */
  logoUrl: string | null;
  /** Current import status */
  importStatus: ImportStatus;
  /** Error message if import failed */
  errorMessage?: string;
  /** Existing company name if it already exists (for update operations) */
  existingCompanyName?: string;
}

/**
 * Result of a batch import operation
 */
export interface BatchImportResult {
  /** Total companies processed */
  total: number;
  /** Successfully created (new companies) */
  success: number;
  /** Successfully updated (existing companies) */
  updated: number;
  /** Failed to import */
  failed: number;
  /** Skipped (no changes needed) */
  skipped: number;
}

/**
 * Props for the batch import modal component
 */
export interface CompanyBatchImportModalProps {
  /** Whether the modal is open */
  open: boolean;
  /** Callback when modal is closed */
  onClose: () => void;
  /** Callback when import is complete */
  onImportComplete?: (result: BatchImportResult) => void;
}
