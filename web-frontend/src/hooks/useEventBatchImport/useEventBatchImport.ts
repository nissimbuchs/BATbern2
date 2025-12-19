/**
 * useEventBatchImport Hook (Enhanced for Story 5.2a)
 *
 * Handles batch import of historical events from legacy JSON format (topics.json + sessions.json).
 * Uses sequential API calls (one per event) with status tracking, duplicate detection,
 * update mode, and topic assignment.
 *
 * Features:
 * - Create or update mode
 * - Selective field updates (via fieldSelection)
 * - Automatic topic assignment from category
 * - Progress tracking and status updates
 */

import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import { buildPartialPayload } from '@/utils/eventImport';
import { getOrCreateTopicByCategory } from '@/utils/topicLookup';
import type {
  EventImportCandidate,
  EventBatchImportResult,
  UpdateFieldSelection,
} from '@/types/eventImport.types';

interface UseEventBatchImportOptions {
  /** Field selection (which fields to import/upsert vs ignore) */
  fieldSelection?: UpdateFieldSelection;
  /** Progress callback */
  onProgress?: (current: number, total: number) => void;
  /** Completion callback */
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
  const {
    fieldSelection = {
      title: true,
      description: true,
      topic: true,
      date: true,
      venue: true,
      organizer: true,
    },
    onProgress,
    onComplete,
  } = options;
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
          const eventNumber = candidate.apiPayload.eventNumber;
          const eventCode = `BATbern${eventNumber}`;

          // Step 1: Upsert logic - try update first, create if not exists
          try {
            // Try to update existing event
            const partialPayload = buildPartialPayload(candidate, fieldSelection);
            await eventApiClient.patchEvent(eventCode, partialPayload);
          } catch (updateError) {
            // If 404, event doesn't exist - create it with selected fields only
            const errorMessage =
              updateError instanceof Error ? updateError.message : 'Unknown error';
            const isNotFound =
              errorMessage.includes('404') ||
              errorMessage.includes('Not Found') ||
              errorMessage.toLowerCase().includes('not found');

            if (isNotFound) {
              // Build create payload with only selected fields
              const createPayload = buildPartialPayload(candidate, fieldSelection);

              // Always include required fields for creation
              const fullCreatePayload = {
                ...candidate.apiPayload, // Start with full payload
                ...createPayload, // Override with selected fields
              };

              await eventApiClient.createEvent(fullCreatePayload);
            } else {
              // Other update errors - rethrow
              throw updateError;
            }
          }

          // Step 2: Assign topic if category exists and topic field is selected
          if (candidate.topicCategory && fieldSelection.topic) {
            try {
              const topicId = await getOrCreateTopicByCategory(candidate.topicCategory);
              await eventApiClient.assignTopicToEvent(eventCode, topicId);
            } catch (topicError) {
              // Log topic assignment failure but don't fail the entire import
              console.warn(
                `Failed to assign topic for ${eventCode}:`,
                topicError instanceof Error ? topicError.message : 'Unknown error'
              );
            }
          }

          // Success
          updateCandidate(i, { importStatus: 'success' });
          result.success++;
        } catch (error) {
          // Handle errors
          const errorMessage = error instanceof Error ? error.message : 'Unknown error';

          // All errors (couldn't update or create)
          updateCandidate(i, {
            importStatus: 'error',
            errorMessage: errorMessage,
          });
          result.failed++;
        }
      }

      // Invalidate events cache to show new events
      await queryClient.invalidateQueries({ queryKey: ['events'] });

      setIsImporting(false);
      onComplete?.(result);

      return result;
    },
    [queryClient, onProgress, onComplete, updateCandidate, fieldSelection]
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
