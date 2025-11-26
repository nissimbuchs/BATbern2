/**
 * useEventBatchImport Hook
 *
 * Handles batch import of historical events from legacy JSON format (topics.json + sessions.json).
 * Uses sequential API calls (one per event) with status tracking and duplicate detection.
 */

import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import type { EventImportCandidate, EventBatchImportResult } from '@/types/eventImport.types';

interface UseEventBatchImportOptions {
  onProgress?: (current: number, total: number) => void;
  onComplete?: (result: EventBatchImportResult) => void;
}

interface UseEventBatchImportReturn {
  /** Start importing the candidates */
  importEvents: (candidates: EventImportCandidate[]) => Promise<EventBatchImportResult>;
  /** Whether an import is in progress */
  isImporting: boolean;
  /** Current progress (index of event being imported) */
  currentIndex: number;
  /** Total number of events to import */
  totalCount: number;
  /** Updated candidates with status */
  candidates: EventImportCandidate[];
  /** Reset the hook state */
  reset: () => void;
}

export function useEventBatchImport(
  options: UseEventBatchImportOptions = {}
): UseEventBatchImportReturn {
  const { onProgress, onComplete } = options;
  const queryClient = useQueryClient();

  const [isImporting, setIsImporting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [candidates, setCandidates] = useState<EventImportCandidate[]>([]);

  const reset = useCallback(() => {
    setIsImporting(false);
    setCurrentIndex(0);
    setTotalCount(0);
    setCandidates([]);
  }, []);

  const updateCandidate = useCallback((index: number, updates: Partial<EventImportCandidate>) => {
    setCandidates((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], ...updates };
      return updated;
    });
  }, []);

  const importEvents = useCallback(
    async (importCandidates: EventImportCandidate[]): Promise<EventBatchImportResult> => {
      setIsImporting(true);
      setCandidates(importCandidates);
      setTotalCount(importCandidates.length);
      setCurrentIndex(0);

      const result: EventBatchImportResult = {
        total: importCandidates.length,
        success: 0,
        failed: 0,
        skipped: 0,
      };

      // Process events sequentially
      for (let i = 0; i < importCandidates.length; i++) {
        const candidate = importCandidates[i];
        setCurrentIndex(i);
        onProgress?.(i + 1, importCandidates.length);

        // Update status to importing
        updateCandidate(i, { importStatus: 'importing' });

        try {
          // Create event via API
          await eventApiClient.createEvent(candidate.apiPayload);

          // Success
          updateCandidate(i, { importStatus: 'success' });
          result.success++;
        } catch (error) {
          // Check if it's a conflict (event already exists)
          const errorMessage = error instanceof Error ? error.message : 'Unknown error';
          const isConflict =
            errorMessage.includes('409') ||
            errorMessage.includes('Conflict') ||
            errorMessage.toLowerCase().includes('already exists') ||
            errorMessage.toLowerCase().includes('duplicate');

          if (isConflict) {
            updateCandidate(i, {
              importStatus: 'skipped',
              errorMessage: 'Event already exists',
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

      // Invalidate events cache to show new events
      await queryClient.invalidateQueries({ queryKey: ['events'] });

      setIsImporting(false);
      onComplete?.(result);

      return result;
    },
    [queryClient, onProgress, onComplete, updateCandidate]
  );

  return {
    importEvents,
    isImporting,
    currentIndex,
    totalCount,
    candidates,
    reset,
  };
}
