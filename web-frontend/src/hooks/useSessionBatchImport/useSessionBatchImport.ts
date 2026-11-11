/**
 * useSessionBatchImport Hook
 *
 * Handles batch import of historical sessions from legacy JSON format (sessions.json).
 * Groups sessions by event and makes batch import API calls per event.
 *
 * Features:
 * - Groups sessions by event number
 * - Makes batch import API call per event
 * - Progress tracking and status updates
 * - Error handling for missing events
 */

import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { sessionApiClient, SessionImportError } from '@/services/sessionService';
import type {
  SessionImportCandidate,
  SessionBatchImportResult,
  SessionImportDetail,
} from '@/types/sessionImport.types';

interface UseSessionBatchImportOptions {
  /** Progress callback */
  onProgress?: (current: number, total: number) => void;
  /** Completion callback */
  onComplete?: (result: SessionBatchImportResult) => void;
}

interface UseSessionBatchImportReturn {
  /** Start importing the candidates */
  importSessions: (candidates: SessionImportCandidate[]) => Promise<SessionBatchImportResult>;
  /** Whether an import is in progress */
  isImporting: boolean;
  /** Current progress (index of session being imported) */
  currentIndex: number;
  /** Total number of sessions to import */
  totalCount: number;
  /** Updated candidates with status */
  candidates: SessionImportCandidate[];
  /** Reset the hook state */
  reset: () => void;
}

export function useSessionBatchImport(
  options: UseSessionBatchImportOptions = {}
): UseSessionBatchImportReturn {
  const { onProgress, onComplete } = options;
  const queryClient = useQueryClient();

  const [isImporting, setIsImporting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [candidates, setCandidates] = useState<SessionImportCandidate[]>([]);

  const reset = useCallback(() => {
    setIsImporting(false);
    setCurrentIndex(0);
    setTotalCount(0);
    setCandidates([]);
  }, []);

  const updateCandidate = useCallback((index: number, updates: Partial<SessionImportCandidate>) => {
    setCandidates((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], ...updates };
      return updated;
    });
  }, []);

  const updateMultipleCandidates = useCallback(
    (indices: number[], updates: Partial<SessionImportCandidate>) => {
      setCandidates((prev) => {
        const updated = [...prev];
        indices.forEach((index) => {
          updated[index] = { ...updated[index], ...updates };
        });
        return updated;
      });
    },
    []
  );

  const importSessions = useCallback(
    async (importCandidates: SessionImportCandidate[]): Promise<SessionBatchImportResult> => {
      setIsImporting(true);
      setCandidates(importCandidates);
      setTotalCount(importCandidates.length);
      setCurrentIndex(0);

      const result: SessionBatchImportResult = {
        totalProcessed: 0,
        successfullyCreated: 0,
        updated: 0,
        skipped: 0,
        failed: 0,
        details: [],
      };

      // Group sessions by event code
      const sessionsByEvent = new Map<
        string,
        { candidates: SessionImportCandidate[]; indices: number[] }
      >();
      importCandidates.forEach((candidate, index) => {
        const eventCode = candidate.eventCode;
        const existing = sessionsByEvent.get(eventCode) || { candidates: [], indices: [] };
        existing.candidates.push(candidate);
        existing.indices.push(index);
        sessionsByEvent.set(eventCode, existing);
      });

      // Process sessions by event (batch import per event)
      for (const [eventCode, { candidates: eventCandidates, indices }] of sessionsByEvent) {
        // Update status to importing for all sessions in this event
        updateMultipleCandidates(indices, { importStatus: 'importing' });

        try {
          // Build batch import request payload (array of BatchImportSessionRequest)
          const batchPayload = eventCandidates.map((c) => c.apiPayload);

          // Make batch import API call
          const importResult = await sessionApiClient.batchImportSessions(eventCode, batchPayload);
          applySaved(importResult, indices);

          // Update progress
          setCurrentIndex((prev) => prev + eventCandidates.length);
          onProgress?.(Math.min(currentIndex + eventCandidates.length, totalCount), totalCount);
        } catch (error) {
          // Handle batch import errors (e.g., event not found, network error)
          const errorMessage = error instanceof Error ? error.message : 'Unknown error';

          // Chunks saved before the failure (Story 4.10b): count them and show their status;
          // only the items after them failed.
          const saved = error instanceof SessionImportError ? error.partial : null;
          if (saved) {
            applySaved(saved, indices);
          }
          const savedCount = saved?.details.length ?? 0;
          const failedIndices = indices.slice(savedCount);
          const failedCandidates = eventCandidates.slice(savedCount);

          // Mark the remaining sessions of this batch as failed
          updateMultipleCandidates(failedIndices, {
            importStatus: 'error',
            errorMessage: errorMessage,
          });

          result.failed += failedCandidates.length;
          result.totalProcessed += failedCandidates.length;

          // Add error details for each failed session
          failedCandidates.forEach((candidate) => {
            result.details.push({
              title: candidate.source.title,
              status: 'failed',
              message: errorMessage,
              sessionSlug: null,
            });
          });

          // Update progress
          setCurrentIndex((prev) => prev + eventCandidates.length);
          onProgress?.(Math.min(currentIndex + eventCandidates.length, totalCount), totalCount);
        }
      }

      // Invalidate sessions cache to show new sessions
      await queryClient.invalidateQueries({ queryKey: ['sessions'] });

      setIsImporting(false);
      onComplete?.(result);

      return result;

      /** Adds what the server saved to the totals and sets the status of those candidates. */
      function applySaved(importResult: SessionBatchImportResult, indices: number[]) {
        result.totalProcessed += importResult.totalProcessed;
        result.successfullyCreated += importResult.successfullyCreated;
        result.updated += importResult.updated || 0;
        result.skipped += importResult.skipped;
        result.failed += importResult.failed;
        result.details.push(...importResult.details);

        // Update candidate statuses based on API response
        importResult.details.forEach((detail: SessionImportDetail, detailIndex: number) => {
          const candidateIndex = indices[detailIndex];
          if (candidateIndex !== undefined) {
            const status =
              detail.status === 'success'
                ? 'success'
                : detail.status === 'updated'
                  ? 'updated'
                  : detail.status === 'skipped'
                    ? 'skipped'
                    : 'error';
            updateCandidate(candidateIndex, {
              importStatus: status,
              errorMessage: detail.status === 'failed' ? detail.message : undefined,
            });
          }
        });
      }
    },
    [
      queryClient,
      onProgress,
      onComplete,
      updateCandidate,
      updateMultipleCandidates,
      currentIndex,
      totalCount,
    ]
  );

  return {
    importSessions,
    isImporting,
    currentIndex,
    totalCount,
    candidates,
    reset,
  };
}
