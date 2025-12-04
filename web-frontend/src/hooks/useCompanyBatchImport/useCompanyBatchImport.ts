/**
 * useCompanyBatchImport Hook
 *
 * Handles batch import of companies from legacy JSON format.
 * Uses sequential API calls (one per company) with status tracking.
 * Implements ADR-002 3-step logo upload process for companies with logoUrl.
 */

import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { companyApiClient } from '@/services/api/companyApi';
import apiClient from '@/services/api/apiClient';
import type { ImportCandidate, BatchImportResult } from '@/types/companyImport.types';

/**
 * Upload logo using server-side upload endpoint
 * This completely bypasses the frontend to avoid binary data corruption
 * with JPEG/BMP files (UTF-8 encoding issue).
 *
 * Returns the uploadId to include in CreateCompanyRequest
 */
async function uploadLogo(logoUrl: string, companyName: string): Promise<string> {
  try {
    const filename = `${companyName}-logo`;

    // Use server-side upload endpoint (bypasses frontend entirely)
    const response = await apiClient.post<{ uploadId: string }>('/logos/upload-from-url', {
      url: logoUrl,
      filename,
    });

    return response.data.uploadId;
  } catch (error) {
    // Re-throw with more context
    if (error instanceof Error) {
      throw new Error(`Logo upload failed for ${companyName}: ${error.message}`);
    }
    throw error;
  }
}

interface UseCompanyBatchImportOptions {
  onProgress?: (current: number, total: number) => void;
  onComplete?: (result: BatchImportResult) => void;
}

interface UseCompanyBatchImportReturn {
  /** Start importing the candidates */
  importCompanies: (candidates: ImportCandidate[]) => Promise<BatchImportResult>;
  /** Whether an import is in progress */
  isImporting: boolean;
  /** Current progress (index of company being imported) */
  currentIndex: number;
  /** Total number of companies to import */
  totalCount: number;
  /** Updated candidates with status */
  candidates: ImportCandidate[];
  /** Reset the hook state */
  reset: () => void;
}

export function useCompanyBatchImport(
  options: UseCompanyBatchImportOptions = {}
): UseCompanyBatchImportReturn {
  const { onProgress, onComplete } = options;
  const queryClient = useQueryClient();

  const [isImporting, setIsImporting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [candidates, setCandidates] = useState<ImportCandidate[]>([]);

  const reset = useCallback(() => {
    setIsImporting(false);
    setCurrentIndex(0);
    setTotalCount(0);
    setCandidates([]);
  }, []);

  const updateCandidate = useCallback((index: number, updates: Partial<ImportCandidate>) => {
    setCandidates((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], ...updates };
      return updated;
    });
  }, []);

  const importCompanies = useCallback(
    async (importCandidates: ImportCandidate[]): Promise<BatchImportResult> => {
      setIsImporting(true);
      setCandidates(importCandidates);
      setTotalCount(importCandidates.length);
      setCurrentIndex(0);

      const result: BatchImportResult = {
        total: importCandidates.length,
        success: 0,
        updated: 0,
        failed: 0,
        skipped: 0,
      };

      // Process companies sequentially
      for (let i = 0; i < importCandidates.length; i++) {
        const candidate = importCandidates[i];
        setCurrentIndex(i);
        onProgress?.(i + 1, importCandidates.length);

        // Update status to importing
        updateCandidate(i, { importStatus: 'importing' });

        try {
          // Check if company exists
          const isUpdate = !!candidate.existingCompanyName;

          if (isUpdate) {
            // Update existing company (exclude logo from updates)
            // Cast to any to allow destructuring logoUploadId which may or may not exist
            // eslint-disable-next-line @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any
            const { logoUploadId, ...updatePayload } = candidate.apiPayload as any;

            console.log(
              `[BatchImport] Updating existing company: ${candidate.existingCompanyName}`
            );

            await companyApiClient.updateCompany(candidate.existingCompanyName!, updatePayload);

            // Success - updated
            updateCandidate(i, { importStatus: 'updated' });
            result.updated++;
          } else {
            // Step 1: Upload logo if logoUrl exists (ADR-002 3-step process)
            let logoUploadId: string | undefined;
            if (candidate.logoUrl) {
              try {
                console.log(
                  `[BatchImport] Uploading logo for ${candidate.apiPayload.name} from ${candidate.logoUrl}`
                );
                logoUploadId = await uploadLogo(candidate.logoUrl, candidate.apiPayload.name);
                console.log(
                  `[BatchImport] Successfully uploaded logo for ${candidate.apiPayload.name}, uploadId: ${logoUploadId}`
                );
              } catch (logoError) {
                console.error(
                  `[BatchImport] Failed to upload logo for ${candidate.apiPayload.name}:`,
                  logoError
                );
                // Continue without logo rather than failing the entire import
              }
            }

            // Step 2: Create company with logo upload ID if available
            const createRequest = {
              ...candidate.apiPayload,
              ...(logoUploadId ? { logoUploadId } : {}),
            };

            await companyApiClient.createCompany(createRequest);

            // Success - created
            updateCandidate(i, { importStatus: 'success' });
            result.success++;
          }
        } catch (error) {
          const errorMessage = error instanceof Error ? error.message : 'Unknown error';
          updateCandidate(i, {
            importStatus: 'error',
            errorMessage: errorMessage,
          });
          result.failed++;
        }
      }

      // Invalidate companies cache to show new companies
      await queryClient.invalidateQueries({ queryKey: ['companies'] });

      setIsImporting(false);
      onComplete?.(result);

      return result;
    },
    [queryClient, onProgress, onComplete, updateCandidate]
  );

  return {
    importCompanies,
    isImporting,
    currentIndex,
    totalCount,
    candidates,
    reset,
  };
}
