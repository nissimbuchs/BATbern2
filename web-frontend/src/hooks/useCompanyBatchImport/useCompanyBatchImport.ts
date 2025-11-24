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
 * Presigned URL response from backend
 */
interface PresignedUrlResponse {
  uploadUrl: string;
  fileId: string;
  s3Key: string;
  fileExtension: string;
  requiredHeaders: Record<string, string>;
}

/**
 * Calculate SHA-256 checksum of a file
 */
async function calculateChecksum(file: File): Promise<string> {
  const arrayBuffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Fetch image from URL and convert to File object
 */
async function fetchImageAsFile(url: string, filename: string): Promise<File> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch image: ${response.statusText}`);
  }
  const blob = await response.blob();
  return new File([blob], filename, { type: blob.type });
}

/**
 * Upload logo using ADR-002 3-step process
 * Returns the uploadId to include in CreateCompanyRequest
 */
async function uploadLogo(logoUrl: string, companyName: string): Promise<string> {
  // Step 1: Fetch the image from the URL
  const filename = `${companyName}-logo.${logoUrl.split('.').pop() || 'png'}`;
  const file = await fetchImageAsFile(logoUrl, filename);

  // Step 2: Request presigned URL from backend
  const presignedResponse = await apiClient.post<PresignedUrlResponse>('/logos/presigned-url', {
    fileName: file.name,
    fileSize: file.size,
    mimeType: file.type,
  });

  const { uploadUrl, fileId, fileExtension, requiredHeaders } = presignedResponse.data;

  // Step 3: Upload file directly to S3 using presigned URL
  await fetch(uploadUrl, {
    method: 'PUT',
    headers: requiredHeaders,
    body: file,
  });

  // Step 4: Confirm upload with backend
  const checksum = await calculateChecksum(file);
  await apiClient.post(`/logos/${fileId}/confirm`, {
    fileId,
    fileExtension,
    checksum,
  });

  return fileId;
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
          // Step 1: Upload logo if logoUrl exists (ADR-002 3-step process)
          let logoUploadId: string | undefined;
          if (candidate.logoUrl) {
            try {
              logoUploadId = await uploadLogo(candidate.logoUrl, candidate.apiPayload.name);
            } catch (logoError) {
              console.warn(`Failed to upload logo for ${candidate.apiPayload.name}:`, logoError);
              // Continue without logo rather than failing the entire import
            }
          }

          // Step 2: Create company with logo upload ID if available
          const createRequest = {
            ...candidate.apiPayload,
            ...(logoUploadId ? { logoUploadId } : {}),
          };

          await companyApiClient.createCompany(createRequest);

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
