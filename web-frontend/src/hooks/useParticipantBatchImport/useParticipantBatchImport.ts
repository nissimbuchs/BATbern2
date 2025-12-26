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
   * Import candidates sequentially with progress tracking
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

      // Sequential processing with rate limiting
      for (let i = 0; i < requests.length; i++) {
        const request = requests[i];
        setCurrentIndex(i);

        updateCandidate(i, { importStatus: 'importing' });
        onProgress?.(i + 1, requests.length);

        try {
          const data = await batchRegisterParticipant(request);

          if (data.failedRegistrations && data.failedRegistrations.length > 0) {
            // Partial success
            const errorMsg = `${data.successfulRegistrations}/${data.totalRegistrations} registrations succeeded`;
            updateCandidate(i, {
              importStatus: 'success',
              errorMessage: errorMsg,
            });
          } else {
            // Full success
            updateCandidate(i, { importStatus: 'success' });
          }

          result.success++;
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

          updateCandidate(i, {
            importStatus: 'error',
            errorMessage,
          });
          result.failed++;
        }

        // Rate limiting: 10 requests/second max (100ms delay)
        if (i < requests.length - 1) {
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
