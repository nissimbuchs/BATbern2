/**
 * Slot Assignment Service Tests (Story 5.7 - Task 4a RED Phase)
 *
 * Tests for slot assignment API client service
 * Following TDD: These tests MUST fail until implementation (Task 4b)
 *
 * Coverage:
 * - AC5: Get unassigned sessions
 * - AC5: Assign timing to session (drag-drop)
 * - AC9: Detect timing conflicts
 * - AC13: Bulk auto-assignment
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { slotAssignmentService } from './slotAssignmentService';
import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type {
  Session,
  SessionTimingRequest,
  BulkTimingRequest,
  ConflictAnalysisResponse,
  TimingConflictError,
} from '@/types/event.types';

describe('SlotAssignmentService (Story 5.7 - Task 4a RED Phase)', () => {
  const mockEventCode = 'BATbern142';
  const mockSessionSlug = 'john-doe-acme-corp';

  beforeEach(() => {
    // Mock apiClient methods to prevent real network calls
    vi.spyOn(apiClient, 'get').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'post').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'patch').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'delete').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getUnassignedSessions', () => {
    it('should_fetchUnassignedSessions_when_eventCodeProvided', async () => {
      // AC5: Get sessions without timing (placeholder sessions)
      // Given: Event has 3 unassigned sessions (placeholder sessions with null timing)
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
          speakers: [
            { username: 'jane.smith', displayName: 'Jane Smith', companyName: 'Tech Inc' },
          ],
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockUnassignedSessions } as any);

      // When: Service fetches unassigned sessions
      const result = await slotAssignmentService.getUnassignedSessions(mockEventCode);

      // Then: Returns array of sessions without timing
      expect(result).toEqual(mockUnassignedSessions);
      expect(result).toHaveLength(2);
      expect(result[0].startTime).toBeNull();
      expect(result[0].endTime).toBeNull();
      expect(result[0].room).toBeNull();
      expect(apiClient.get).toHaveBeenCalledWith(`/events/${mockEventCode}/sessions/unassigned`);
    });

    it('should_return404Error_when_eventNotFound', async () => {
      // Given: Event does not exist
      const nonExistentEventCode = 'BATbern999';
      const error = new AxiosError('Event not found');
      error.response = {
        status: 404,
        data: { message: 'Event not found' },
        statusText: 'Not Found',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      // When/Then: Service throws 404 error
      await expect(
        slotAssignmentService.getUnassignedSessions(nonExistentEventCode)
      ).rejects.toThrow('Event not found');
    });
  });

  describe('assignSessionTiming', () => {
    it('should_assignTiming_when_validTimingProvided', async () => {
      // AC5: Drag-and-drop UI to assign speaker to time slot
      // Given: Valid timing request for placeholder session
      const timingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
        sessionType: 'presentation',
        changeReason: 'drag_drop_reassignment',
      };

      const mockUpdatedSession: Session = {
        sessionSlug: mockSessionSlug,
        eventCode: mockEventCode,
        title: 'John Doe - Acme Corp',
        startTime: timingRequest.startTime,
        endTime: timingRequest.endTime,
        room: timingRequest.room,
        sessionType: timingRequest.sessionType,
        speakers: [{ username: 'john.doe', displayName: 'John Doe', companyName: 'Acme Corp' }],
      };

      vi.mocked(apiClient.patch).mockResolvedValueOnce({ data: mockUpdatedSession } as any);

      // When: Service assigns timing to session
      const result = await slotAssignmentService.assignSessionTiming(
        mockEventCode,
        mockSessionSlug,
        timingRequest
      );

      // Then: Returns session with assigned timing
      expect(result).toEqual(mockUpdatedSession);
      expect(result.startTime).toBe(timingRequest.startTime);
      expect(result.endTime).toBe(timingRequest.endTime);
      expect(result.room).toBe(timingRequest.room);
      expect(apiClient.patch).toHaveBeenCalledWith(
        `/events/${mockEventCode}/sessions/${mockSessionSlug}/timing`,
        timingRequest
      );
    });

    it('should_return409Conflict_when_roomOverlapDetected', async () => {
      // AC9: Warn if room has overlapping sessions
      // Given: Timing overlaps with existing session in same room
      const conflictingTimingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
        changeReason: 'drag_drop_reassignment',
      };

      const mockConflictError: TimingConflictError = {
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
      };

      const error = new AxiosError('Conflict');
      error.response = {
        status: 409,
        data: mockConflictError,
        statusText: 'Conflict',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.patch).mockRejectedValueOnce(error);

      // When/Then: Service throws 409 conflict error
      await expect(
        slotAssignmentService.assignSessionTiming(
          mockEventCode,
          mockSessionSlug,
          conflictingTimingRequest
        )
      ).rejects.toMatchObject({
        response: {
          status: 409,
          data: mockConflictError,
        },
      });
    });

    it('should_return409Conflict_when_speakerDoubleBookingDetected', async () => {
      // AC9: Warn if speaker has conflicting commitment at same time
      // Given: Timing overlaps with another session for same speaker
      const conflictingTimingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Conference Room A',
        changeReason: 'drag_drop_reassignment',
      };

      const mockConflictError: TimingConflictError = {
        error: 'TIMING_CONFLICT',
        message: 'Speaker already scheduled at this time',
        conflicts: [
          {
            type: 'speaker_double_booked',
            conflictingSessionSlug: 'john-doe-other-session',
            conflictingTimeRange: {
              start: '2025-05-15T09:00:00Z',
              end: '2025-05-15T10:00:00Z',
            },
            details: 'Speaker john.doe is already scheduled in another session',
          },
        ],
      };

      const error = new AxiosError('Conflict');
      error.response = {
        status: 409,
        data: mockConflictError,
        statusText: 'Conflict',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.patch).mockRejectedValueOnce(error);

      // When/Then: Service throws 409 conflict error
      await expect(
        slotAssignmentService.assignSessionTiming(
          mockEventCode,
          mockSessionSlug,
          conflictingTimingRequest
        )
      ).rejects.toMatchObject({
        response: {
          status: 409,
          data: mockConflictError,
        },
      });
    });

    it('should_return404Error_when_sessionNotFound', async () => {
      // Given: Session does not exist
      const nonExistentSessionSlug = 'non-existent-session';
      const timingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
      };

      const error = new AxiosError('Session not found');
      error.response = {
        status: 404,
        data: { message: 'Session not found' },
        statusText: 'Not Found',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.patch).mockRejectedValueOnce(error);

      // When/Then: Service throws 404 error
      await expect(
        slotAssignmentService.assignSessionTiming(
          mockEventCode,
          nonExistentSessionSlug,
          timingRequest
        )
      ).rejects.toThrow('Session not found');
    });
  });

  describe('bulkAssignTiming', () => {
    it('should_assignMultipleSessions_when_validBulkRequest', async () => {
      // AC13: Provide bulk auto-assignment based on preferences
      // Given: Valid bulk timing request for 3 sessions
      const bulkRequest: BulkTimingRequest = {
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
          {
            sessionSlug: 'session-3',
            startTime: '2025-05-15T11:00:00Z',
            endTime: '2025-05-15T11:45:00Z',
            room: 'Conference Room A',
          },
        ],
        changeReason: 'preference_matching',
      };

      const mockResponse = {
        assignedCount: 3,
        sessions: bulkRequest.assignments.map((a) => ({
          sessionSlug: a.sessionSlug,
          eventCode: mockEventCode,
          startTime: a.startTime,
          endTime: a.endTime,
          room: a.room,
        })),
      };

      vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockResponse } as any);

      // When: Service performs bulk assignment
      const result = await slotAssignmentService.bulkAssignTiming(mockEventCode, bulkRequest);

      // Then: Returns assigned count and updated sessions
      expect(result.assignedCount).toBe(3);
      expect(result.sessions).toHaveLength(3);
      expect(result.sessions[0].startTime).toBe(bulkRequest.assignments[0].startTime);
      expect(apiClient.post).toHaveBeenCalledWith(
        `/events/${mockEventCode}/sessions/bulk-timing`,
        bulkRequest
      );
    });

    it('should_return409Conflict_when_bulkConflictsDetected', async () => {
      // Given: Bulk request with conflicts (atomic operation - all or nothing)
      const conflictingBulkRequest: BulkTimingRequest = {
        assignments: [
          {
            sessionSlug: 'session-1',
            startTime: '2025-05-15T09:00:00Z',
            endTime: '2025-05-15T09:45:00Z',
            room: 'Main Hall',
          },
          {
            sessionSlug: 'session-2',
            startTime: '2025-05-15T09:30:00Z', // Overlaps with session-1
            endTime: '2025-05-15T10:15:00Z',
            room: 'Main Hall',
          },
        ],
        changeReason: 'preference_matching',
      };

      const error = new AxiosError('Bulk conflicts');
      error.response = {
        status: 409,
        data: {
          error: 'BULK_TIMING_CONFLICTS',
          message: 'Multiple conflicts detected - no changes applied',
          conflictCount: 1,
        },
        statusText: 'Conflict',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      // When/Then: Service throws 409 conflict error, no changes applied
      await expect(
        slotAssignmentService.bulkAssignTiming(mockEventCode, conflictingBulkRequest)
      ).rejects.toMatchObject({
        response: {
          status: 409,
          data: {
            error: 'BULK_TIMING_CONFLICTS',
            message: 'Multiple conflicts detected - no changes applied',
            conflictCount: 1,
          },
        },
      });
    });
  });

  describe('detectConflicts', () => {
    it('should_returnConflicts_when_schedulingConflictsExist', async () => {
      // AC9: Detect all types of conflicts in event schedule
      // Given: Event has 2 scheduling conflicts
      const mockConflictAnalysis: ConflictAnalysisResponse = {
        hasConflicts: true,
        conflictCount: 2,
        conflicts: [
          {
            sessionSlug: 'session-1',
            conflictType: 'room_overlap',
            severity: 'error',
            affectedSessions: ['session-2'],
            timeRange: {
              start: '2025-05-15T09:00:00Z',
              end: '2025-05-15T09:45:00Z',
            },
            resolution: 'Assign session-2 to different room or time slot',
          },
          {
            sessionSlug: 'session-3',
            conflictType: 'speaker_double_booked',
            severity: 'error',
            affectedSessions: ['session-4'],
            timeRange: {
              start: '2025-05-15T10:00:00Z',
              end: '2025-05-15T10:45:00Z',
            },
            resolution: 'Reschedule one of the sessions to avoid speaker overlap',
          },
        ],
      };

      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockConflictAnalysis } as any);

      // When: Service analyzes conflicts
      const result = await slotAssignmentService.detectConflicts(mockEventCode);

      // Then: Returns conflict analysis with resolution suggestions
      expect(result.hasConflicts).toBe(true);
      expect(result.conflictCount).toBe(2);
      expect(result.conflicts).toHaveLength(2);
      expect(result.conflicts[0].conflictType).toBe('room_overlap');
      expect(result.conflicts[1].conflictType).toBe('speaker_double_booked');
      expect(apiClient.get).toHaveBeenCalledWith(`/events/${mockEventCode}/sessions/conflicts`);
    });

    it('should_returnNoConflicts_when_scheduleValid', async () => {
      // Given: Event has no scheduling conflicts
      const mockConflictAnalysis: ConflictAnalysisResponse = {
        hasConflicts: false,
        conflictCount: 0,
        conflicts: [],
      };

      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockConflictAnalysis } as any);

      // When: Service analyzes conflicts
      const result = await slotAssignmentService.detectConflicts(mockEventCode);

      // Then: Returns no conflicts
      expect(result.hasConflicts).toBe(false);
      expect(result.conflictCount).toBe(0);
      expect(result.conflicts).toHaveLength(0);
    });
  });

  describe('clearAllTimings', () => {
    it('should_clearAllTimings_when_eventCodeProvided', async () => {
      // Given: Event has sessions with timing that should be cleared
      const mockResponse = { message: 'All timings cleared', clearedCount: 5 };

      vi.mocked(apiClient.delete).mockResolvedValueOnce({ data: mockResponse } as any);

      // When: Service clears all timings
      const result = await slotAssignmentService.clearAllTimings(mockEventCode);

      // Then: Returns cleared count
      expect(result).toEqual(mockResponse);
      expect(result.clearedCount).toBe(5);
      expect(result.message).toBe('All timings cleared');
      expect(apiClient.delete).toHaveBeenCalledWith(`/events/${mockEventCode}/sessions/timing`);
    });

    it('should_return401Error_when_notAuthenticatedOnClear', async () => {
      // Given: User is not authenticated
      const error = new AxiosError('Unauthorized');
      error.response = {
        status: 401,
        data: { message: 'Unauthorized' },
        statusText: 'Unauthorized',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.delete).mockRejectedValueOnce(error);

      // When/Then: Service re-throws 401 AxiosError
      await expect(slotAssignmentService.clearAllTimings(mockEventCode)).rejects.toMatchObject({
        response: { status: 401 },
      });
    });

    it('should_return403Error_when_insufficientPermissionsOnClear', async () => {
      // Given: User lacks organizer permissions
      const error = new AxiosError('Forbidden');
      error.response = {
        status: 403,
        data: { message: 'Forbidden' },
        statusText: 'Forbidden',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.delete).mockRejectedValueOnce(error);

      // When/Then: Service re-throws 403 AxiosError
      await expect(slotAssignmentService.clearAllTimings(mockEventCode)).rejects.toMatchObject({
        response: { status: 403 },
      });
    });

    it('should_transformError_when_nonAuthErrorOnClear', async () => {
      // Given: Server returns 500 error
      const error = new AxiosError('Internal Server Error');
      error.response = {
        status: 500,
        data: { message: 'Database connection failed' },
        statusText: 'Internal Server Error',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.delete).mockRejectedValueOnce(error);

      // When/Then: Service transforms the error
      await expect(slotAssignmentService.clearAllTimings(mockEventCode)).rejects.toThrow(
        'Database connection failed'
      );
    });
  });

  describe('generateStructuralSessions', () => {
    it('should_generateStructuralSessions_when_eventCodeProvided', async () => {
      // Given: Event needs structural sessions (moderation, break, lunch)
      const mockSessions: Session[] = [
        {
          sessionSlug: 'moderation-opening',
          eventCode: mockEventCode,
          title: 'Opening Moderation',
          startTime: '2025-05-15T08:30:00Z',
          endTime: '2025-05-15T09:00:00Z',
          room: 'Main Hall',
          sessionType: 'moderation',
          speakers: [],
        },
        {
          sessionSlug: 'break-morning',
          eventCode: mockEventCode,
          title: 'Morning Break',
          startTime: '2025-05-15T10:00:00Z',
          endTime: '2025-05-15T10:30:00Z',
          room: null,
          sessionType: 'break',
          speakers: [],
        },
      ];

      vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockSessions } as any);

      // When: Service generates structural sessions (default overwrite=false)
      const result = await slotAssignmentService.generateStructuralSessions(mockEventCode);

      // Then: Returns created structural sessions
      expect(result).toEqual(mockSessions);
      expect(result).toHaveLength(2);
      expect(apiClient.post).toHaveBeenCalledWith(
        `/events/${mockEventCode}/sessions/structural`,
        null,
        { params: { overwrite: false } }
      );
    });

    it('should_generateStructuralSessions_when_overwriteEnabled', async () => {
      // Given: Overwrite existing structural sessions
      const mockSessions: Session[] = [
        {
          sessionSlug: 'moderation-opening',
          eventCode: mockEventCode,
          title: 'Opening Moderation',
          startTime: '2025-05-15T08:30:00Z',
          endTime: '2025-05-15T09:00:00Z',
          room: 'Main Hall',
          sessionType: 'moderation',
          speakers: [],
        },
      ];

      vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockSessions } as any);

      // When: Service generates structural sessions with overwrite=true
      const result = await slotAssignmentService.generateStructuralSessions(mockEventCode, true);

      // Then: Passes overwrite=true as query param
      expect(result).toEqual(mockSessions);
      expect(apiClient.post).toHaveBeenCalledWith(
        `/events/${mockEventCode}/sessions/structural`,
        null,
        { params: { overwrite: true } }
      );
    });

    it('should_return401Error_when_notAuthenticatedOnGenerate', async () => {
      const error = new AxiosError('Unauthorized');
      error.response = {
        status: 401,
        data: { message: 'Unauthorized' },
        statusText: 'Unauthorized',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      await expect(
        slotAssignmentService.generateStructuralSessions(mockEventCode)
      ).rejects.toMatchObject({
        response: { status: 401 },
      });
    });

    it('should_return403Error_when_insufficientPermissionsOnGenerate', async () => {
      const error = new AxiosError('Forbidden');
      error.response = {
        status: 403,
        data: { message: 'Forbidden' },
        statusText: 'Forbidden',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      await expect(
        slotAssignmentService.generateStructuralSessions(mockEventCode)
      ).rejects.toMatchObject({
        response: { status: 403 },
      });
    });

    it('should_return409Conflict_when_structuralSessionsExistAndNoOverwrite', async () => {
      const error = new AxiosError('Conflict');
      error.response = {
        status: 409,
        data: { message: 'Structural sessions already exist. Use overwrite=true to replace.' },
        statusText: 'Conflict',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      await expect(
        slotAssignmentService.generateStructuralSessions(mockEventCode)
      ).rejects.toMatchObject({
        response: { status: 409 },
      });
    });

    it('should_transformError_when_nonAuthErrorOnGenerate', async () => {
      const error = new AxiosError('Internal Server Error');
      error.response = {
        status: 500,
        data: { error: 'Failed to generate sessions' },
        statusText: 'Internal Server Error',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      await expect(slotAssignmentService.generateStructuralSessions(mockEventCode)).rejects.toThrow(
        'Failed to generate sessions'
      );
    });
  });

  describe('autoAssignTimings', () => {
    it('should_autoAssign_when_eventCodeProvided', async () => {
      // Given: Event has unassigned sessions
      const mockResponse = { message: 'Auto-assignment complete', assignedCount: 4 };

      vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockResponse } as any);

      // When: Service auto-assigns timings
      const result = await slotAssignmentService.autoAssignTimings(mockEventCode);

      // Then: Returns assigned count
      expect(result).toEqual(mockResponse);
      expect(result.assignedCount).toBe(4);
      expect(result.message).toBe('Auto-assignment complete');
      expect(apiClient.post).toHaveBeenCalledWith(`/events/${mockEventCode}/sessions/auto-assign`);
    });

    it('should_return401Error_when_notAuthenticatedOnAutoAssign', async () => {
      const error = new AxiosError('Unauthorized');
      error.response = {
        status: 401,
        data: { message: 'Unauthorized' },
        statusText: 'Unauthorized',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      await expect(slotAssignmentService.autoAssignTimings(mockEventCode)).rejects.toMatchObject({
        response: { status: 401 },
      });
    });

    it('should_return403Error_when_insufficientPermissionsOnAutoAssign', async () => {
      const error = new AxiosError('Forbidden');
      error.response = {
        status: 403,
        data: { message: 'Forbidden' },
        statusText: 'Forbidden',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      await expect(slotAssignmentService.autoAssignTimings(mockEventCode)).rejects.toMatchObject({
        response: { status: 403 },
      });
    });

    it('should_transformError_when_nonAuthErrorOnAutoAssign', async () => {
      const error = new AxiosError('Internal Server Error');
      error.response = {
        status: 500,
        data: { message: 'Auto-assignment failed' },
        statusText: 'Internal Server Error',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      await expect(slotAssignmentService.autoAssignTimings(mockEventCode)).rejects.toThrow(
        'Auto-assignment failed'
      );
    });
  });

  describe('Error Handling', () => {
    it('should_return401Error_when_notAuthenticated', async () => {
      // Given: User is not authenticated
      const error = new AxiosError('Unauthorized');
      error.response = {
        status: 401,
        data: { message: 'Unauthorized' },
        statusText: 'Unauthorized',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      // When/Then: Service throws 401 error
      await expect(
        slotAssignmentService.getUnassignedSessions(mockEventCode)
      ).rejects.toMatchObject({
        response: { status: 401 },
      });
    });

    it('should_return403Error_when_insufficientPermissions', async () => {
      // Given: User lacks organizer permissions
      const error = new AxiosError('Forbidden');
      error.response = {
        status: 403,
        data: { message: 'Forbidden' },
        statusText: 'Forbidden',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      // When/Then: Service throws 403 error
      await expect(
        slotAssignmentService.getUnassignedSessions(mockEventCode)
      ).rejects.toMatchObject({
        response: { status: 403 },
      });
    });
  });

  describe('transformError (private)', () => {
    it('should_includeCorrelationId_when_headerPresent', async () => {
      // Given: Server error response includes correlation ID header
      const error = new AxiosError('Server Error');
      error.response = {
        status: 500,
        data: { message: 'Internal failure' },
        statusText: 'Internal Server Error',
        headers: { 'x-correlation-id': 'abc-123-def' },
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      // When/Then: Error message includes correlation ID
      await expect(slotAssignmentService.getUnassignedSessions(mockEventCode)).rejects.toThrow(
        'Internal failure (Correlation ID: abc-123-def)'
      );
    });

    it('should_fallbackToErrorField_when_noMessageField', async () => {
      // Given: Response has "error" field instead of "message"
      const error = new AxiosError('Server Error');
      error.response = {
        status: 500,
        data: { error: 'Something went wrong' },
        statusText: 'Internal Server Error',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      // When/Then: Uses error field as message
      await expect(slotAssignmentService.getUnassignedSessions(mockEventCode)).rejects.toThrow(
        'Something went wrong'
      );
    });

    it('should_fallbackToAxiosMessage_when_noResponseData', async () => {
      // Given: Network error with no response body
      const error = new AxiosError('Network Error', 'ERR_NETWORK');

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      // When/Then: Uses axios error message
      await expect(slotAssignmentService.getUnassignedSessions(mockEventCode)).rejects.toThrow(
        'Network Error'
      );
    });

    it('should_returnEventNotFound_when_404WithEventMessage', async () => {
      // Given: 404 response mentioning Event
      const error = new AxiosError('Not Found');
      error.response = {
        status: 404,
        data: { message: 'Event BATbern999 not found' },
        statusText: 'Not Found',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      // When/Then: Returns "Event not found"
      await expect(slotAssignmentService.detectConflicts('BATbern999')).rejects.toThrow(
        'Event not found'
      );
    });

    it('should_returnSessionNotFound_when_404WithSessionMessage', async () => {
      // Given: 404 response mentioning Session
      const error = new AxiosError('Not Found');
      error.response = {
        status: 404,
        data: { message: 'Session xyz not found' },
        statusText: 'Not Found',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.patch).mockRejectedValueOnce(error);

      const timingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
      };

      // When/Then: Returns "Session not found"
      await expect(
        slotAssignmentService.assignSessionTiming(mockEventCode, 'xyz', timingRequest)
      ).rejects.toThrow('Session not found');
    });

    it('should_handleNonErrorObjects_when_unknownThrown', async () => {
      // Given: Something unexpected is thrown (not an Error or AxiosError)
      vi.mocked(apiClient.get).mockRejectedValueOnce('unexpected string error');

      // When/Then: Returns generic error
      await expect(slotAssignmentService.getUnassignedSessions(mockEventCode)).rejects.toThrow(
        'An unexpected error occurred'
      );
    });

    it('should_preserveErrorInstance_when_nonAxiosErrorThrown', async () => {
      // Given: A regular Error is thrown (not AxiosError)
      const regularError = new Error('Some regular error');
      vi.mocked(apiClient.get).mockRejectedValueOnce(regularError);

      // When/Then: The error is returned as-is
      await expect(slotAssignmentService.getUnassignedSessions(mockEventCode)).rejects.toThrow(
        'Some regular error'
      );
    });

    it('should_return401Error_when_notAuthenticatedOnAssignTiming', async () => {
      // Given: 401 on assignSessionTiming
      const error = new AxiosError('Unauthorized');
      error.response = {
        status: 401,
        data: { message: 'Unauthorized' },
        statusText: 'Unauthorized',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.patch).mockRejectedValueOnce(error);

      const timingRequest: SessionTimingRequest = {
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T09:45:00Z',
        room: 'Main Hall',
      };

      await expect(
        slotAssignmentService.assignSessionTiming(mockEventCode, mockSessionSlug, timingRequest)
      ).rejects.toMatchObject({
        response: { status: 401 },
      });
    });

    it('should_return401Error_when_notAuthenticatedOnBulkAssign', async () => {
      // Given: 401 on bulkAssignTiming
      const error = new AxiosError('Unauthorized');
      error.response = {
        status: 401,
        data: { message: 'Unauthorized' },
        statusText: 'Unauthorized',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      const bulkRequest: BulkTimingRequest = {
        assignments: [],
        changeReason: 'test',
      };

      await expect(
        slotAssignmentService.bulkAssignTiming(mockEventCode, bulkRequest)
      ).rejects.toMatchObject({
        response: { status: 401 },
      });
    });

    it('should_transformError_when_nonAuthErrorOnBulkAssign', async () => {
      // Given: 500 on bulkAssignTiming
      const error = new AxiosError('Internal Server Error');
      error.response = {
        status: 500,
        data: { message: 'Bulk assignment failed' },
        statusText: 'Internal Server Error',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      const bulkRequest: BulkTimingRequest = {
        assignments: [],
        changeReason: 'test',
      };

      await expect(
        slotAssignmentService.bulkAssignTiming(mockEventCode, bulkRequest)
      ).rejects.toThrow('Bulk assignment failed');
    });

    it('should_return403Error_when_insufficientPermissionsOnBulkAssign', async () => {
      // Given: 403 on bulkAssignTiming
      const error = new AxiosError('Forbidden');
      error.response = {
        status: 403,
        data: { message: 'Forbidden' },
        statusText: 'Forbidden',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.post).mockRejectedValueOnce(error);

      const bulkRequest: BulkTimingRequest = {
        assignments: [],
        changeReason: 'test',
      };

      await expect(
        slotAssignmentService.bulkAssignTiming(mockEventCode, bulkRequest)
      ).rejects.toMatchObject({
        response: { status: 403 },
      });
    });

    it('should_return401Error_when_notAuthenticatedOnDetectConflicts', async () => {
      // Given: 401 on detectConflicts
      const error = new AxiosError('Unauthorized');
      error.response = {
        status: 401,
        data: { message: 'Unauthorized' },
        statusText: 'Unauthorized',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      await expect(slotAssignmentService.detectConflicts(mockEventCode)).rejects.toMatchObject({
        response: { status: 401 },
      });
    });

    it('should_fallbackToGenericMessage_when_noDataFieldsPresent', async () => {
      // Given: Response data has neither message nor error field
      const error = new AxiosError('Bad Request');
      error.response = {
        status: 400,
        data: {},
        statusText: 'Bad Request',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(error);

      // When/Then: Falls back to axios error.message
      await expect(slotAssignmentService.getUnassignedSessions(mockEventCode)).rejects.toThrow(
        'Bad Request'
      );
    });
  });
});
