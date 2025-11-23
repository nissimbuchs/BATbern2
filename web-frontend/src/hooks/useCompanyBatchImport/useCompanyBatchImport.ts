/**
 * useCompanyBatchImport Hook
 *
 * Handles batch import of companies from legacy JSON format.
 * Uses sequential API calls (one per company) with status tracking.
 */

import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { companyApiClient } from '@/services/api/companyApi';
import type { ImportCandidate, BatchImportResult } from '@/types/companyImport.types';

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
          // Call the create company API
          await companyApiClient.createCompany(candidate.apiPayload);

          // Success
          updateCandidate(i, { importStatus: 'success' });
          result.success++;
        } catch (error) {
          // Check if it's a conflict (company already exists)
          const errorMessage = error instanceof Error ? error.message : 'Unknown error';
          const isConflict =
            errorMessage.includes('409') ||
            errorMessage.toLowerCase().includes('already exists') ||
            errorMessage.toLowerCase().includes('conflict');

          if (isConflict) {
            updateCandidate(i, {
              importStatus: 'skipped',
              errorMessage: 'Company already exists',
            });
            result.skipped++;
          } else {
            updateCandidate(i, {
              importStatus: 'error',
              errorMessage: errorMessage,
            });
            result.failed++;
          }
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
