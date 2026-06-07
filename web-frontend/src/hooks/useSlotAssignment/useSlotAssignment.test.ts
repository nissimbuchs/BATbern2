/**
 * useSlotAssignment Hook Tests (Story 5.7 - Task 4a RED Phase)
 *
 * Tests for slot assignment state management hook
 * Following TDD: These tests MUST fail until implementation (Task 4b)
 *
 * Coverage:
 * - AC5-AC6: Fetch and display unassigned sessions with real-time updates
 * - AC5: Drag-and-drop session timing assignment
 * - AC7-AC8: Speaker preference display
 * - AC9: Conflict detection and warnings
 * - AC12: Real-time unassigned speakers list updates
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useSlotAssignment } from './useSlotAssignment';
import * as slotAssignmentServiceModule from '@/services/slotAssignmentService/slotAssignmentService';
import type { Session, SessionTimingRequest, ConflictAnalysisResponse } from '@/types/event.types';
import { AxiosError } from 'axios';

// Mock slotAssignmentService module
vi.mock('@/services/slotAssignmentService/slotAssignmentService', () => ({
  slotAssignmentService: {
    getUnassignedSessions: vi.fn(),
    assignSessionTiming: vi.fn(),
    bulkAssignTiming: vi.fn(),
    detectConflicts: vi.fn(),
    clearAllTimings: vi.fn(),
    autoAssignTimings: vi.fn(),
  },
}));

describe('useSlotAssignment Hook (Story 5.7 - Task 4a RED Phase)', () => {
  const mockEventCode = 'BATbern142';

  const mockUnassignedSessions: Session[] = [
    {
      sessionSlug: 'session-1',
      eventCode: mockEventCode,
      title: 'John Doe - Acme Corp',
      startTime: null,
      endTime: null,
      room: null,
      speakers: [{ username: 'john.doe', displayName: 'John Doe', companyName: 'Acme Corp' }],
    },
    {
      sessionSlug: 'session-2',
      eventCode: mockEventCode,
      title: 'Jane Smith - Tech Inc',
      startTime: null,
      endTime: null,
      room: null,
      speakers: [{ username: 'jane.smith', displayName: 'Jane Smith', companyName: 'Tech Inc' }],
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Loading Unassigned Sessions', () => {
    it('should_fetchUnassignedSessions_when_hookInitialized', async () => {
      // AC5-AC6: Fetch and display unassigned sessions
      // Given: Hook is initialized with event code
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);

      // When: Hook renders
      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      // Then: Initially shows loading state
      expect(result.current.isLoading).toBe(true);

      // Wait for data to load
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Then: Unassigned sessions are loaded
      expect(result.current.unassignedSessions).toHaveLength(2);
      expect(result.current.unassignedSessions[0].sessionSlug).toBe('session-1');
      expect(result.current.error).toBeNull();
    });

    it('should_handleError_when_fetchFails', async () => {
      // Given: API fetch fails
      const mockError = new Error('Failed to fetch unassigned sessions');
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockRejectedValue(mockError);

      // When: Hook tries to fetch unassigned sessions
      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Then: Error state is set
      expect(result.current.error).toBeDefined();
      expect(result.current.unassignedSessions).toHaveLength(0);
    });
  });

  describe('Assigning Session Timing', () => {
    it('should_assignTiming_when_dragDropCompleted', async () => {
      // AC5: Drag-and-drop UI to assign speaker to time slot
      // Given: User drags session to time slot
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.assignSessionTiming
      ).mockResolvedValue({
        ...mockUnassignedSessions[0],
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
      });

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const timingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
        changeReason: 'drag_drop_reassignment',
      };

      // When: assignTiming is called
      await act(async () => {
        await result.current.assignTiming('session-1', timingRequest);
      });

      // Then: Session is removed from unassigned list
      expect(result.current.unassignedSessions).toHaveLength(1);
      expect(
        result.current.unassignedSessions.find((s) => s.sessionSlug === 'session-1')
      ).toBeUndefined();
      expect(result.current.error).toBeNull();
    });

    it('should_rollbackOptimisticUpdate_when_assignmentFails', async () => {
      // Given: Optimistic update is applied
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      const mockError = new Error('Failed to assign timing');
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.assignSessionTiming
      ).mockRejectedValue(mockError);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const initialLength = result.current.unassignedSessions.length;

      const timingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
        changeReason: 'drag_drop_reassignment',
      };

      // When: Assignment fails (API returns error)
      await act(async () => {
        try {
          await result.current.assignTiming('session-1', timingRequest);
        } catch {
          // Expected to fail
        }
      });

      // Then: Optimistic update is rolled back
      expect(result.current.unassignedSessions).toHaveLength(initialLength);
      expect(result.current.error).toBeDefined();
    });

    it('should_showConflictWarning_when_409ConflictReturned', async () => {
      // AC9: Warn if session has conflicting commitment
      // Given: Timing conflicts with existing schedule
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);

      const conflictError = new AxiosError('Timing conflict');
      conflictError.response = {
        status: 409,
        data: {
          error: 'TIMING_CONFLICT',
          message: 'Session timing conflicts with existing schedule',
          conflicts: [
            {
              type: 'room_overlap',
              conflictingSessionSlug: 'existing-session-1',
              conflictingTimeRange: {
                start: '2025-05-15T09:00:00Z',
                end: '2025-05-15T10:00:00Z',
              },
              details: 'Main Hall is already booked during this time',
            },
          ],
        },
        statusText: 'Conflict',
        headers: {},
        config: {} as any,
      };
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.assignSessionTiming
      ).mockRejectedValue(conflictError);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const conflictingTimingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
        changeReason: 'drag_drop_reassignment',
      };

      // When: Assignment returns conflict error
      await act(async () => {
        try {
          await result.current.assignTiming('session-1', conflictingTimingRequest);
        } catch {
          // Expected 409 conflict
        }
      });

      // Then: Conflict state is set
      expect(result.current.conflict).toBeDefined();
      expect(result.current.conflict?.conflicts).toHaveLength(1);
      expect(result.current.conflict?.conflicts[0].type).toBe('room_overlap');
    });
  });

  describe('Bulk Assignment', () => {
    it('should_bulkAssign_when_autoAssignmentTriggered', async () => {
      // AC13: Provide bulk auto-assignment based on preferences
      // Given: Multiple unassigned sessions
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.bulkAssignTiming
      ).mockResolvedValue({
        successCount: 2,
        failureCount: 0,
        results: [],
      });

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const bulkRequest = {
        assignments: [
          {
            sessionSlug: 'session-1',
            startTime: '2025-05-15T09:00:00Z',
            endTime: '2025-05-15T09:45:00Z',
            room: 'Main Hall',
          },
          {
            sessionSlug: 'session-2',
            startTime: '2025-05-15T10:00:00Z',
            endTime: '2025-05-15T10:45:00Z',
            room: 'Main Hall',
          },
        ],
        changeReason: 'preference_matching' as const,
      };

      // When: Bulk assignment is performed
      await act(async () => {
        await result.current.bulkAssignTiming(bulkRequest);
      });

      // Then: All assigned sessions are removed from unassigned list
      expect(result.current.unassignedSessions).toHaveLength(0);
      expect(result.current.error).toBeNull();
    });
  });

  describe('Conflict Detection', () => {
    it('should_detectConflicts_when_requested', async () => {
      // AC9: Analyze all scheduling conflicts
      // Given: Event has scheduling conflicts
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.detectConflicts
      ).mockResolvedValue({
        hasConflicts: true,
        conflicts: [
          {
            type: 'room_overlap',
            sessionSlug: 'session-1',
            conflictingSessionSlug: 'session-2',
            timeRange: {
              start: '2025-05-15T09:00:00Z',
              end: '2025-05-15T10:00:00Z',
            },
            details: 'Room overlap detected',
          },
        ],
      });

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: Conflict detection is triggered
      await act(async () => {
        await result.current.detectConflicts();
      });

      // Then: Conflicts are returned and stored
      expect(result.current.conflictAnalysis).toBeDefined();
      expect(result.current.conflictAnalysis?.hasConflicts).toBeDefined();
    });

    it('should_clearConflict_when_conflictResolved', async () => {
      // Given: Conflict exists
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);

      const conflictError = new AxiosError('Timing conflict');
      conflictError.response = {
        status: 409,
        data: {
          error: 'TIMING_CONFLICT',
          message: 'Session timing conflicts with existing schedule',
          conflicts: [
            {
              type: 'room_overlap',
              conflictingSessionSlug: 'existing-session-1',
              conflictingTimeRange: {
                start: '2025-05-15T09:00:00Z',
                end: '2025-05-15T10:00:00Z',
              },
              details: 'Main Hall is already booked during this time',
            },
          ],
        },
        statusText: 'Conflict',
        headers: {},
        config: {} as any,
      };
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.assignSessionTiming
      ).mockRejectedValue(conflictError);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Set a mock conflict
      await act(async () => {
        try {
          await result.current.assignTiming('session-1', {
            startTime: '2025-05-15T09:00:00Z',
            endTime: '2025-05-15T09:45:00Z',
            room: 'Main Hall',
            changeReason: 'drag_drop_reassignment',
          });
        } catch {
          // Expected conflict
        }
      });

      expect(result.current.conflict).toBeDefined();

      // When: Conflict is cleared
      act(() => {
        result.current.clearConflict();
      });

      // Then: Conflict state is null
      expect(result.current.conflict).toBeNull();
    });
  });

  describe('Bulk Assignment Error Handling', () => {
    it('should_rollbackAndSetError_when_bulkAssignmentFailsWithGenericError', async () => {
      // Given: Bulk assignment fails with a generic error
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.bulkAssignTiming
      ).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const bulkRequest = {
        assignments: [
          {
            sessionSlug: 'session-1',
            startTime: '2025-05-15T09:00:00Z',
            endTime: '2025-05-15T09:45:00Z',
            room: 'Main Hall',
          },
        ],
        changeReason: 'preference_matching' as const,
      };

      // When: Bulk assignment fails
      await act(async () => {
        try {
          await result.current.bulkAssignTiming(bulkRequest);
        } catch {
          // Expected to fail
        }
      });

      // Then: Optimistic update is rolled back and error is set
      expect(result.current.unassignedSessions).toHaveLength(2);
      expect(result.current.error).toBe('Network error');
    });

    it('should_rollbackAndSetError_when_bulkAssignmentFailsWith409Conflict', async () => {
      // Given: Bulk assignment returns a 409 conflict
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);

      const conflictError = new AxiosError('Bulk conflict');
      conflictError.response = {
        status: 409,
        data: {
          message: 'Multiple room overlaps detected',
        },
        statusText: 'Conflict',
        headers: {},
        config: {} as any,
      };
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.bulkAssignTiming
      ).mockRejectedValue(conflictError);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const bulkRequest = {
        assignments: [
          {
            sessionSlug: 'session-1',
            startTime: '2025-05-15T09:00:00Z',
            endTime: '2025-05-15T09:45:00Z',
            room: 'Main Hall',
          },
          {
            sessionSlug: 'session-2',
            startTime: '2025-05-15T09:00:00Z',
            endTime: '2025-05-15T09:45:00Z',
            room: 'Main Hall',
          },
        ],
        changeReason: 'preference_matching' as const,
      };

      // When: Bulk assignment returns 409
      await act(async () => {
        try {
          await result.current.bulkAssignTiming(bulkRequest);
        } catch {
          // Expected conflict
        }
      });

      // Then: Sessions are rolled back and error message from response is set
      expect(result.current.unassignedSessions).toHaveLength(2);
      expect(result.current.error).toBe('Multiple room overlaps detected');
    });

    it('should_useFallbackMessage_when_bulkAssignment409HasNoMessage', async () => {
      // Given: Bulk assignment returns 409 without a message field
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);

      const conflictError = new AxiosError('Bulk conflict');
      conflictError.response = {
        status: 409,
        data: {},
        statusText: 'Conflict',
        headers: {},
        config: {} as any,
      };
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.bulkAssignTiming
      ).mockRejectedValue(conflictError);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const bulkRequest = {
        assignments: [
          {
            sessionSlug: 'session-1',
            startTime: '2025-05-15T09:00:00Z',
            endTime: '2025-05-15T09:45:00Z',
            room: 'Main Hall',
          },
        ],
        changeReason: 'preference_matching' as const,
      };

      // When: Bulk assignment returns 409 without message
      await act(async () => {
        try {
          await result.current.bulkAssignTiming(bulkRequest);
        } catch {
          // Expected conflict
        }
      });

      // Then: Fallback error message is used
      expect(result.current.error).toBe('Bulk assignment conflicts detected');
    });

    it('should_useFallbackMessage_when_bulkAssignmentFailsWithNonErrorObject', async () => {
      // Given: Bulk assignment fails with a non-Error object
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.bulkAssignTiming
      ).mockRejectedValue('string error');

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const bulkRequest = {
        assignments: [
          {
            sessionSlug: 'session-1',
            startTime: '2025-05-15T09:00:00Z',
            endTime: '2025-05-15T09:45:00Z',
            room: 'Main Hall',
          },
        ],
        changeReason: 'preference_matching' as const,
      };

      // When: Bulk assignment fails with non-Error
      await act(async () => {
        try {
          await result.current.bulkAssignTiming(bulkRequest);
        } catch {
          // Expected
        }
      });

      // Then: Fallback error message is used
      expect(result.current.error).toBe('Failed to assign timing');
    });
  });

  describe('Conflict Detection Error Handling', () => {
    it('should_setError_when_detectConflictsFails', async () => {
      // Given: Conflict detection API fails
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.detectConflicts
      ).mockRejectedValue(new Error('Conflict detection service unavailable'));

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: Conflict detection fails
      await act(async () => {
        await result.current.detectConflicts();
      });

      // Then: Error state is set
      expect(result.current.error).toBe('Conflict detection service unavailable');
      expect(result.current.conflictAnalysis).toBeNull();
    });

    it('should_useFallbackMessage_when_detectConflictsFailsWithNonError', async () => {
      // Given: Conflict detection fails with non-Error object
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.detectConflicts
      ).mockRejectedValue('unexpected');

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: Conflict detection fails with non-Error
      await act(async () => {
        await result.current.detectConflicts();
      });

      // Then: Fallback error message is used
      expect(result.current.error).toBe('Failed to detect conflicts');
    });
  });

  describe('Clear All Timings', () => {
    it('should_clearAllTimingsAndRefresh_when_clearAllTimingsCalled', async () => {
      // Given: Sessions exist and clearAllTimings succeeds
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.clearAllTimings
      ).mockResolvedValue(undefined);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: clearAllTimings is called
      await act(async () => {
        await result.current.clearAllTimings();
      });

      // Then: Service was called and sessions were refreshed
      expect(
        slotAssignmentServiceModule.slotAssignmentService.clearAllTimings
      ).toHaveBeenCalledWith(mockEventCode);
      // getUnassignedSessions called on mount (+ possible re-fire from totalSessions dep) + once on refresh after clear
      expect(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).toHaveBeenCalledTimes(3);
      expect(result.current.error).toBeNull();
    });

    it('should_setErrorAndRethrow_when_clearAllTimingsFails', async () => {
      // Given: clearAllTimings API fails
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.clearAllTimings
      ).mockRejectedValue(new Error('Server error'));

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: clearAllTimings fails
      await act(async () => {
        try {
          await result.current.clearAllTimings();
        } catch {
          // Expected to rethrow
        }
      });

      // Then: Error is set
      expect(result.current.error).toBe('Server error');
    });

    it('should_useFallbackMessage_when_clearAllTimingsFailsWithNonError', async () => {
      // Given: clearAllTimings fails with non-Error
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.clearAllTimings
      ).mockRejectedValue(42);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: clearAllTimings fails with non-Error
      await act(async () => {
        try {
          await result.current.clearAllTimings();
        } catch {
          // Expected
        }
      });

      // Then: Fallback error message is used
      expect(result.current.error).toBe('Failed to clear all timings');
    });
  });

  describe('Auto-Assign Timings', () => {
    it('should_autoAssignAndRefresh_when_autoAssignTimingsCalled', async () => {
      // Given: autoAssignTimings succeeds
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.autoAssignTimings
      ).mockResolvedValue(undefined);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: autoAssignTimings is called
      await act(async () => {
        await result.current.autoAssignTimings();
      });

      // Then: Service was called and sessions were refreshed
      expect(
        slotAssignmentServiceModule.slotAssignmentService.autoAssignTimings
      ).toHaveBeenCalledWith(mockEventCode);
      // getUnassignedSessions called on mount (+ possible re-fire from totalSessions dep) + once on refresh after auto-assign
      expect(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).toHaveBeenCalledTimes(3);
      expect(result.current.error).toBeNull();
    });

    it('should_setErrorAndRethrow_when_autoAssignTimingsFails', async () => {
      // Given: autoAssignTimings API fails
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.autoAssignTimings
      ).mockRejectedValue(new Error('No available slots'));

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: autoAssignTimings fails
      await act(async () => {
        try {
          await result.current.autoAssignTimings();
        } catch {
          // Expected to rethrow
        }
      });

      // Then: Error is set
      expect(result.current.error).toBe('No available slots');
    });

    it('should_useFallbackMessage_when_autoAssignTimingsFailsWithNonError', async () => {
      // Given: autoAssignTimings fails with non-Error
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.autoAssignTimings
      ).mockRejectedValue(null);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: autoAssignTimings fails with non-Error
      await act(async () => {
        try {
          await result.current.autoAssignTimings();
        } catch {
          // Expected
        }
      });

      // Then: Fallback error message is used
      expect(result.current.error).toBe('Failed to auto-assign timings');
    });
  });

  describe('Fetch Error Edge Cases', () => {
    it('should_useFallbackMessage_when_fetchFailsWithNonErrorObject', async () => {
      // Given: API fetch rejects with a non-Error value
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockRejectedValue('string error');

      // When: Hook tries to fetch
      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Then: Fallback error message is used
      expect(result.current.error).toBe('Failed to fetch unassigned sessions');
      expect(result.current.unassignedSessions).toHaveLength(0);
    });
  });

  describe('Real-time Updates', () => {
    it('should_updateUnassignedList_when_sessionAssigned', async () => {
      // AC12: Show unassigned speakers list with real-time updates
      // Given: Initial list of 2 unassigned sessions
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.assignSessionTiming
      ).mockResolvedValue({
        ...mockUnassignedSessions[0],
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
      });

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      const initialCount = result.current.unassignedSessions.length;

      // When: One session is assigned
      await act(async () => {
        await result.current.assignTiming('session-1', {
          startTime: '2025-05-15T09:00:00Z',
          endTime: '2025-05-15T09:45:00Z',
          room: 'Main Hall',
          changeReason: 'drag_drop_reassignment',
        });
      });

      // Then: Unassigned count decreases
      expect(result.current.unassignedSessions).toHaveLength(initialCount - 1);
      expect(result.current.assignedCount).toBe(1);
      expect(result.current.totalSessions).toBe(initialCount);
    });

    it('should_refreshSessions_when_manuallyTriggered', async () => {
      // Given: Sessions may have changed externally
      vi.mocked(
        slotAssignmentServiceModule.slotAssignmentService.getUnassignedSessions
      ).mockResolvedValue(mockUnassignedSessions);

      const { result } = renderHook(() => useSlotAssignment(mockEventCode));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When: Refresh is triggered
      await act(async () => {
        await result.current.refreshSessions();
      });

      // Then: Sessions are re-fetched
      expect(result.current.isLoading).toBe(false);
      expect(result.current.unassignedSessions).toBeDefined();
    });
  });
});
