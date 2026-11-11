/**
 * Business logic hook for participant batch import
 *
 * The CSV holds one row per person with the events they attended; the API takes the people of
 * one event at a time (Story 4.10c, `POST /events/{eventCode}/participants` with `people`, at
 * most 100 per call). The hook regroups the rows per event, sends each event's people in chunks
 * of 100, one request after the other, and aggregates each person's status over their events:
 * - success: every event registered or already there (skipped)
 * - error: at least one event failed (the message names how many and why)
 *
 * Sequential on purpose: one person in two events would otherwise be created twice at once.
 */

import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { importParticipants } from '../../services/api/eventRegistrationService';
import { constructUsername } from '../../utils/participantImportUtils';
import { isAxiosError } from 'axios';
import type {
  ParticipantImportCandidate,
  ParticipantBatchImportResult,
  ParticipantImportRow,
  ParticipantImportPerson,
} from '../../types/participantImport.types';

/**
 * People per request. The server takes up to 100, but it looks each person up in CUMS one after
 * the other, and 100 sequential CUMS calls can run past the frontend's 30 s request timeout; 20
 * keeps a request well inside it.
 */
export const PEOPLE_PER_REQUEST = 20;

/** One request: up to 100 people of one event, with the index of each in the candidate list. */
interface EventChunk {
  eventCode: string;
  people: ParticipantImportPerson[];
  candidateIndexes: number[];
}

/** Per candidate: events still to do, events failed, and the first reason. */
interface CandidateProgress {
  pending: number;
  failed: number;
  reason?: string;
}

/** Regroup person rows into per-event chunks of at most 100 people, events in first-seen order. */
export function chunkPerEvent(rows: ParticipantImportRow[]): EventChunk[] {
  const byEvent = new Map<string, { people: ParticipantImportPerson[]; indexes: number[] }>();
  rows.forEach((row, index) => {
    for (const eventCode of row.eventCodes) {
      const group = byEvent.get(eventCode) ?? { people: [], indexes: [] };
      group.people.push({
        email: row.email,
        firstName: row.firstName,
        lastName: row.lastName,
        ...(row.companyId ? { companyId: row.companyId } : {}),
        status: 'ATTENDED',
      });
      group.indexes.push(index);
      byEvent.set(eventCode, group);
    }
  });
  const chunks: EventChunk[] = [];
  byEvent.forEach((group, eventCode) => {
    for (let i = 0; i < group.people.length; i += PEOPLE_PER_REQUEST) {
      chunks.push({
        eventCode,
        people: group.people.slice(i, i + PEOPLE_PER_REQUEST),
        candidateIndexes: group.indexes.slice(i, i + PEOPLE_PER_REQUEST),
      });
    }
  });
  return chunks;
}

function errorMessageOf(error: unknown): string {
  if (isAxiosError(error)) {
    if (error.response) {
      return error.response.data?.message || error.response.statusText;
    }
    if (error.request) {
      return 'No response from server';
    }
    return error.message;
  }
  return error instanceof Error ? error.message : 'Unknown error';
}

/**
 * Hook for managing participant batch import
 */
export function useParticipantBatchImport() {
  const queryClient = useQueryClient();
  const [isImporting, setIsImporting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [candidates, setCandidates] = useState<ParticipantImportCandidate[]>([]);

  const updateCandidate = useCallback(
    (index: number, updates: Partial<ParticipantImportCandidate>) => {
      setCandidates((prev) =>
        prev.map((candidate, i) => (i === index ? { ...candidate, ...updates } : candidate))
      );
    },
    []
  );

  /**
   * Import the rows event by event.
   *
   * @param rows one row per person with the events they attended
   * @param onProgress called after each request with (requests done, requests total)
   */
  const importCandidates = useCallback(
    async (
      rows: ParticipantImportRow[],
      onProgress?: (current: number, total: number) => void
    ): Promise<ParticipantBatchImportResult> => {
      setIsImporting(true);

      setCandidates(
        rows.map((row) => ({
          firstName: row.firstName,
          lastName: row.lastName,
          email: row.email,
          username: constructUsername(row.firstName, row.lastName),
          eventCount: row.eventCodes.length,
          eventCodes: row.eventCodes,
          isSyntheticEmail: row.email.endsWith('@batbern.ch'),
          importStatus: 'pending',
        }))
      );
      setCurrentIndex(0);

      const progress: CandidateProgress[] = rows.map((row) => ({
        pending: row.eventCodes.length,
        failed: 0,
      }));
      const result: ParticipantBatchImportResult = {
        total: rows.length,
        success: 0,
        failed: 0,
        skipped: 0,
      };

      const settle = (index: number) => {
        const p = progress[index];
        if (p.pending > 0) {
          return;
        }
        const events = rows[index].eventCodes.length;
        if (p.failed > 0) {
          result.failed++;
          updateCandidate(index, {
            importStatus: 'error',
            errorMessage: `${p.failed}/${events} events failed: ${p.reason ?? 'Unknown error'}`,
          });
        } else {
          result.success++;
          updateCandidate(index, { importStatus: 'success', errorMessage: undefined });
        }
      };

      // A person without events has nothing to import.
      rows.forEach((row, index) => {
        if (row.eventCodes.length === 0) {
          result.skipped++;
          updateCandidate(index, { importStatus: 'skipped' });
        }
      });

      const chunks = chunkPerEvent(rows);
      for (let c = 0; c < chunks.length; c++) {
        const chunk = chunks[c];
        chunk.candidateIndexes.forEach((index) => {
          if (progress[index].pending > 0) {
            updateCandidate(index, { importStatus: 'importing' });
          }
        });

        try {
          const data = await importParticipants(chunk.eventCode, chunk.people);
          (data.results ?? []).forEach((personResult, i) => {
            const index = chunk.candidateIndexes[i];
            if (index === undefined) {
              return;
            }
            if (personResult.outcome === 'FAILED') {
              progress[index].failed++;
              progress[index].reason ??= `${chunk.eventCode}: ${personResult.reason ?? 'failed'}`;
            }
          });
        } catch (error) {
          const message = `${chunk.eventCode}: ${errorMessageOf(error)}`;
          chunk.candidateIndexes.forEach((index) => {
            progress[index].failed++;
            progress[index].reason ??= message;
          });
        }

        chunk.candidateIndexes.forEach((index) => {
          progress[index].pending--;
          settle(index);
        });
        setCurrentIndex(c);
        onProgress?.(c + 1, chunks.length);
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
