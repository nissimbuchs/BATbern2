/**
 * Business logic hook for participant batch import
 *
 * Handles sequential processing of batch registration requests with:
 * - Progress tracking
 * - Rate limiting (10 requests/second)
 * - Real-time status updates
 * - Error handling
 */

import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { batchRegisterParticipant } from '../../services/api/eventApi';
import { constructUsername } from '../../utils/participantImportUtils';
import { AxiosError } from 'axios';
import type {
  ParticipantImportCandidate,
  ParticipantBatchImportResult,
  BatchRegistrationRequest,
} from '../../types/participantImport.types';

/**
 * Hook for managing participant batch import
 */
export function useParticipantBatchImport() {
  const queryClient = useQueryClient();
  const [isImporting, setIsImporting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [candidates, setCandidates] = useState<ParticipantImportCandidate[]>([]);

  /**
   * Update a specific candidate's status
   */
  const updateCandidate = useCallback(
    (index: number, updates: Partial<ParticipantImportCandidate>) => {
      setCandidates((prev) =>
        prev.map((candidate, i) => (i === index ? { ...candidate, ...updates } : candidate))
      );
    },
    []
  );

  /**
   * Import candidates in parallel batches with progress tracking
   *
   * Performance optimization: Processes participants in batches of 10 concurrent requests
   * instead of sequentially. This reduces import time by ~10x.
   *
   * Example: 200 participants
   * - Sequential: 200 × 100ms = 20+ seconds
   * - Parallel (10 concurrent): 20 batches × 100ms = 2+ seconds
   */
  const importCandidates = useCallback(
    async (
      requests: BatchRegistrationRequest[],
      onProgress?: (current: number, total: number) => void
    ): Promise<ParticipantBatchImportResult> => {
      setIsImporting(true);

      // Create initial candidates
      const initialCandidates: ParticipantImportCandidate[] = requests.map((req) => ({
        firstName: req.firstName,
        lastName: req.lastName,
        email: req.participantEmail,
        username: constructUsername(req.firstName, req.lastName),
        eventCount: req.registrations.length,
        isSyntheticEmail: req.participantEmail.endsWith('@batbern.ch'),
        importStatus: 'pending',
      }));
      setCandidates(initialCandidates);
      setCurrentIndex(0);

      const result: ParticipantBatchImportResult = {
        total: requests.length,
        success: 0,
        failed: 0,
        skipped: 0,
      };

      // Parallel batch processing (10 concurrent requests per batch)
      const BATCH_SIZE = 10;
      const batches: BatchRegistrationRequest[][] = [];

      // Split requests into batches
      for (let i = 0; i < requests.length; i += BATCH_SIZE) {
        batches.push(requests.slice(i, i + BATCH_SIZE));
      }

      // Process each batch in parallel
      let processedCount = 0;

      for (const batch of batches) {
        // Process all requests in this batch concurrently
        const batchPromises = batch.map(async (request, batchIndex) => {
          const globalIndex = processedCount + batchIndex;

          updateCandidate(globalIndex, { importStatus: 'importing' });

          try {
            const data = await batchRegisterParticipant(request);

            if (data.failedRegistrations && data.failedRegistrations.length > 0) {
              // Partial success
              const errorMsg = `${data.successfulRegistrations}/${data.totalRegistrations} registrations succeeded`;
              updateCandidate(globalIndex, {
                importStatus: 'success',
                errorMessage: errorMsg,
              });
            } else {
              // Full success
              updateCandidate(globalIndex, { importStatus: 'success' });
            }

            return { success: true, index: globalIndex };
          } catch (error) {
            let errorMessage = 'Unknown error';

            if (error instanceof AxiosError) {
              if (error.response) {
                errorMessage = error.response.data?.message || error.response.statusText;
              } else if (error.request) {
                errorMessage = 'No response from server';
              } else {
                errorMessage = error.message;
              }
            } else if (error instanceof Error) {
              errorMessage = error.message;
            }

            updateCandidate(globalIndex, {
              importStatus: 'error',
              errorMessage,
            });

            return { success: false, index: globalIndex };
          }
        });

        // Wait for all requests in this batch to complete
        const batchResults = await Promise.all(batchPromises);

        // Update result counts
        batchResults.forEach((batchResult) => {
          if (batchResult.success) {
            result.success++;
          } else {
            result.failed++;
          }
        });

        processedCount += batch.length;
        setCurrentIndex(processedCount - 1);
        onProgress?.(processedCount, requests.length);

        // Rate limiting between batches: 100ms delay
        // This allows 10 batches/second = 100 requests/second max throughput
        if (processedCount < requests.length) {
          await new Promise((resolve) => setTimeout(resolve, 100));
        }
      }

      // Invalidate cache to refresh user/registration data
      await queryClient.invalidateQueries({ queryKey: ['users'] });
      await queryClient.invalidateQueries({ queryKey: ['registrations'] });

      setIsImporting(false);
      return result;
    },
    [queryClient, updateCandidate]
  );

  /**
   * Reset all state (for closing modal)
   */
  const reset = useCallback(() => {
    setCandidates([]);
    setCurrentIndex(0);
    setIsImporting(false);
  }, []);

  return {
    importCandidates,
    isImporting,
    currentIndex,
    candidates,
    reset,
  };
}
