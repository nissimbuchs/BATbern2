/**
 * useSlotAssignment Hook (Story 5.7 - Task 4b GREEN Phase)
 *
 * State management hook for slot assignment functionality
 * Features:
 * - Fetch and manage unassigned sessions
 * - Assign timing to sessions (drag-drop)
 * - Bulk timing assignment
 * - Conflict detection and management
 * - Optimistic updates with rollback
 */

import { useState, useEffect, useCallback } from 'react';
import { AxiosError } from 'axios';
import { slotAssignmentService } from '@/services/slotAssignmentService/slotAssignmentService';
import type {
  Session,
  SessionTimingRequest,
  BulkTimingRequest,
  TimingConflictError,
  ConflictAnalysisResponse,
} from '@/types/event.types';

export interface UseSlotAssignmentReturn {
  // State
  unassignedSessions: Session[];
  isLoading: boolean;
  error: string | null;
  conflict: TimingConflictError | null;
  conflictAnalysis: ConflictAnalysisResponse | null;
  assignedCount: number;
  totalSessions: number;

  // Actions
  assignTiming: (sessionSlug: string, timing: SessionTimingRequest) => Promise<void>;
  bulkAssignTiming: (request: BulkTimingRequest) => Promise<void>;
  detectConflicts: () => Promise<void>;
  clearConflict: () => void;
  refreshSessions: () => Promise<void>;
  clearAllTimings: () => Promise<void>;
  autoAssignTimings: () => Promise<void>;
}

/**
 * Hook for managing slot assignment state and operations
 *
 * @param eventCode - Event code (e.g., "BATbern142")
 * @returns Slot assignment state and operations
 */
export const useSlotAssignment = (eventCode: string): UseSlotAssignmentReturn => {
  const [unassignedSessions, setUnassignedSessions] = useState<Session[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<TimingConflictError | null>(null);
  const [conflictAnalysis, setConflictAnalysis] = useState<ConflictAnalysisResponse | null>(null);
  const [totalSessions, setTotalSessions] = useState<number>(0);

  /**
   * Fetch unassigned sessions
   */
  const fetchUnassignedSessions = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const sessions = await slotAssignmentService.getUnassignedSessions(eventCode);
      setUnassignedSessions(sessions);
      // If this is first load, set total sessions
      if (totalSessions === 0) {
        setTotalSessions(sessions.length);
      }
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : 'Failed to fetch unassigned sessions';
      console.error('[useSlotAssignment] Fetch failed:', errorMessage);
      setError(errorMessage);
      setUnassignedSessions([]);
    } finally {
      setIsLoading(false);
    }
  }, [eventCode, totalSessions]);

  /**
   * Assign timing to a session
   */
  const assignTiming = useCallback(
    async (sessionSlug: string, timing: SessionTimingRequest): Promise<void> => {
      // Clear any existing conflicts
      setConflict(null);
      setError(null);

      // Optimistic update - remove from unassigned list and save snapshot for rollback
      let rollbackSessions: Session[] = [];
      setUnassignedSessions((prev) => {
        rollbackSessions = [...prev]; // Capture current state for rollback
        const filtered = prev.filter((s) => s.sessionSlug !== sessionSlug);
        return filtered;
      });

      try {
        await slotAssignmentService.assignSessionTiming(eventCode, sessionSlug, timing);
        // Success - optimistic update is kept, no need to refetch
      } catch (err) {
        console.error('[useSlotAssignment] ✗ API call failed - rolling back optimistic update');
        // Rollback optimistic update using captured snapshot
        setUnassignedSessions(rollbackSessions);

        // Handle conflict errors (409)
        if (err instanceof AxiosError && err.response?.status === 409) {
          setConflict(err.response.data as TimingConflictError);
        } else {
          const errorMessage = err instanceof Error ? err.message : 'Failed to assign timing';
          setError(errorMessage);
        }
        throw err;
      }
    },
    [eventCode]
  );

  /**
   * Bulk assign timing to multiple sessions
   */
  const bulkAssignTiming = useCallback(
    async (request: BulkTimingRequest): Promise<void> => {
      setConflict(null);
      setError(null);

      // Optimistic update - remove all assigned sessions from unassigned list
      const sessionSlugs = request.assignments.map((a) => a.sessionSlug);
      const originalSessions = [...unassignedSessions];
      setUnassignedSessions((prev) => prev.filter((s) => !sessionSlugs.includes(s.sessionSlug)));

      try {
        await slotAssignmentService.bulkAssignTiming(eventCode, request);
        // Success - optimistic update is kept
      } catch (err) {
        // Rollback optimistic update
        setUnassignedSessions(originalSessions);

        // Handle conflict errors (409)
        if (err instanceof AxiosError && err.response?.status === 409) {
          // For bulk conflicts, set error instead of conflict
          const errorMessage = err.response.data?.message || 'Bulk assignment conflicts detected';
          setError(errorMessage);
        } else {
          const errorMessage = err instanceof Error ? err.message : 'Failed to assign timing';
          setError(errorMessage);
        }
        throw err;
      }
    },
    [eventCode, unassignedSessions]
  );

  /**
   * Detect conflicts in event schedule
   */
  const detectConflicts = useCallback(async (): Promise<void> => {
    setError(null);

    try {
      const analysis = await slotAssignmentService.detectConflicts(eventCode);
      setConflictAnalysis(analysis);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to detect conflicts';
      setError(errorMessage);
    }
  }, [eventCode]);

  /**
   * Clear conflict state
   */
  const clearConflict = useCallback((): void => {
    setConflict(null);
  }, []);

  /**
   * Refresh unassigned sessions
   */
  const refreshSessions = useCallback(async (): Promise<void> => {
    await fetchUnassignedSessions();
  }, [fetchUnassignedSessions]);

  /**
   * Clear all session timings (reset all to unassigned)
   */
  const clearAllTimings = useCallback(async (): Promise<void> => {
    setError(null);

    try {
      await slotAssignmentService.clearAllTimings(eventCode);

      // Refresh sessions to update UI
      await fetchUnassignedSessions();
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to clear all timings';
      console.error('[useSlotAssignment] ✗ Failed to clear timings:', errorMessage);
      setError(errorMessage);
      throw err;
    }
  }, [eventCode, fetchUnassignedSessions]);

  /**
   * Auto-assign all unassigned sessions to available time slots
   */
  const autoAssignTimings = useCallback(async (): Promise<void> => {
    setError(null);

    try {
      await slotAssignmentService.autoAssignTimings(eventCode);

      // Refresh sessions to update UI
      await fetchUnassignedSessions();
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to auto-assign timings';
      console.error('[useSlotAssignment] ✗ Failed to auto-assign:', errorMessage);
      setError(errorMessage);
      throw err;
    }
  }, [eventCode, fetchUnassignedSessions]);

  // Fetch unassigned sessions on mount
  useEffect(() => {
    fetchUnassignedSessions();
  }, [fetchUnassignedSessions]);

  // Calculate assigned count
  const assignedCount = totalSessions - unassignedSessions.length;

  return {
    // State
    unassignedSessions,
    isLoading,
    error,
    conflict,
    conflictAnalysis,
    assignedCount,
    totalSessions,

    // Actions
    assignTiming,
    bulkAssignTiming,
    detectConflicts,
    clearConflict,
    refreshSessions,
    clearAllTimings,
    autoAssignTimings,
  };
};
