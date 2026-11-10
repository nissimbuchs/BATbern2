/**
 * useSlotAssignment Hook Tests (Story 5.7, Story 15.3)
 *
 * The hook runs with the real slotAssignmentService and apiClient against the MSW harness (Epic 17
 * test fidelity): every request is checked against event-sessions-api, each test answers with a
 * typed override (or an error `Response`) and reads what went over the wire. The service's own
 * error mapping runs too: a 409 reaches the hook as the AxiosError with its body, other errors as
 * an `Error` carrying the server's message.
 *
 * Coverage: unassigned sessions (AC5-AC6), drag-and-drop timing and slot assignment with
 * optimistic update and rollback (AC5), conflicts (AC9), bulk, clear and auto assignment, refresh.
 */

import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { useSlotAssignment } from './useSlotAssignment';
import type { BulkTimingRequest, SessionTimingRequest } from '@/types/event.types';
import type { components } from '@/types/generated/event-sessions-api.types';
import { aSessionResponse, aSessionSpeaker } from '@/test/fixtures';

type SessionResponse = components['schemas']['SessionResponse'];

const EVENT = 'BATbern142';

const session = (
  slug: string,
  firstName: string,
  lastName: string,
  company: string
): SessionResponse => ({
  ...aSessionResponse(),
  sessionSlug: slug,
  eventCode: EVENT,
  title: `${firstName} ${lastName} - ${company}`,
  startTime: null,
  endTime: null,
  language: 'de',
  speakers: [
    {
      ...aSessionSpeaker(),
      username: `${firstName}.${lastName}`.toLowerCase(),
      firstName,
      lastName,
      company,
      speakerRole: 'PRIMARY_SPEAKER',
      isConfirmed: true,
    },
  ],
});

const unassigned: SessionResponse[] = [
  session('session-1', 'Hông Ân', 'Meier', 'Acme AG'),
  session('session-2', 'Jean-Luc', 'O’Neill', 'Tech Inc'),
];

const timing: SessionTimingRequest = {
  startTime: '2025-05-15T09:00:00Z',
  endTime: '2025-05-15T09:45:00Z',
  room: 'Main Hall',
  changeReason: 'drag_drop_reassignment',
};

const problem = (status: number, message: string) =>
  HttpResponse.json(
    { timestamp: '2026-11-09T08:00:00Z', status, error: 'Error', message },
    { status }
  );

const timingConflict = {
  error: 'TIMING_CONFLICT',
  message: 'Session timing conflicts with existing schedule',
  conflicts: [{ type: 'room_overlap', conflictingSessionSlug: 'existing-session-1' }],
};

/** Renders the hook on the given pool and waits until it has loaded. */
const renderLoaded = async (pool: SessionResponse[] = unassigned) => {
  api.on('getUnassignedSessions', () => pool);
  const view = renderHook(() => useSlotAssignment(EVENT));
  await waitFor(() => expect(view.result.current.isLoading).toBe(false));
  return view;
};

/** Runs an action of the hook that is expected to reject, as the UI does (it catches). */
const failing = async (action: () => Promise<void>) => {
  let thrown: unknown;
  await act(async () => {
    await action().catch((e: unknown) => {
      thrown = e;
    });
  });
  expect(thrown).toBeDefined();
  return thrown;
};

describe('useSlotAssignment', () => {
  describe('Loading Unassigned Sessions', () => {
    it('should_fetchUnassignedSessions_when_hookInitialized', async () => {
      api.on('getUnassignedSessions', () => unassigned);

      const { result } = renderHook(() => useSlotAssignment(EVENT));

      expect(result.current.isLoading).toBe(true);
      await waitFor(() => expect(result.current.isLoading).toBe(false));
      expect(result.current.unassignedSessions).toEqual(unassigned);
      expect(result.current.totalSessions).toBe(2);
      expect(result.current.error).toBeNull();
      expect(api.calls('getUnassignedSessions')[0].params.eventCode).toBe(EVENT);
    });

    it('should_handleError_when_fetchFails', async () => {
      api.on('getUnassignedSessions', () => problem(404, 'Event not found: BATbern142'));

      const { result } = renderHook(() => useSlotAssignment(EVENT));

      await waitFor(() => expect(result.current.isLoading).toBe(false));
      expect(result.current.error).toBe('Event not found');
      expect(result.current.unassignedSessions).toHaveLength(0);
    });
  });

  describe('Assigning Session Timing', () => {
    it('should_assignTiming_when_dragDropCompleted', async () => {
      api.on('assignTiming', ({ params }) => ({
        ...unassigned[0],
        sessionSlug: params.sessionSlug,
        ...timing,
      }));
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.assignTiming('session-1', timing);
      });

      const [call] = api.calls('assignTiming');
      expect(call.params).toEqual({ eventCode: EVENT, sessionSlug: 'session-1' });
      expect(call.body).toEqual(timing);
      expect(result.current.unassignedSessions.map((s) => s.sessionSlug)).toEqual(['session-2']);
      expect(result.current.assignedCount).toBe(1);
      expect(result.current.totalSessions).toBe(2);
      expect(result.current.error).toBeNull();
    });

    it('should_rollbackOptimisticUpdate_when_assignmentFails', async () => {
      api.on('assignTiming', () => problem(400, 'End time must be after start time'));
      const { result } = await renderLoaded();

      await failing(() => result.current.assignTiming('session-1', timing));

      expect(result.current.unassignedSessions).toEqual(unassigned);
      expect(result.current.error).toBe('End time must be after start time');
      expect(result.current.conflict).toBeNull();
    });

    it('should_showConflictWarning_when_409ConflictReturned', async () => {
      api.on('assignTiming', () => HttpResponse.json(timingConflict, { status: 409 }));
      const { result } = await renderLoaded();

      await failing(() => result.current.assignTiming('session-1', timing));

      expect(result.current.unassignedSessions).toEqual(unassigned);
      expect(result.current.conflict).toEqual(timingConflict);
      expect(result.current.error).toBeNull();
    });

    it('should_clearConflict_when_conflictResolved', async () => {
      api.on('assignTiming', () => HttpResponse.json(timingConflict, { status: 409 }));
      const { result } = await renderLoaded();
      await failing(() => result.current.assignTiming('session-1', timing));
      expect(result.current.conflict).not.toBeNull();

      act(() => result.current.clearConflict());

      expect(result.current.conflict).toBeNull();
    });

    it('should_unassignAndRefreshPool_when_unassignTimingCalled (Story 15.3)', async () => {
      api.on('unassignTiming', ({ params }) => ({
        ...unassigned[0],
        sessionSlug: params.sessionSlug,
      }));
      const { result } = await renderLoaded();
      expect(api.calls('getUnassignedSessions')).toHaveLength(1);

      await act(async () => {
        await result.current.unassignTiming('occupant-session');
      });

      expect(api.calls('unassignTiming')[0].params).toEqual({
        eventCode: EVENT,
        sessionSlug: 'occupant-session',
      });
      expect(api.calls('getUnassignedSessions')).toHaveLength(2);
    });

    it('should_rollbackAndSetConflict_when_assignToSlotReturns409 (Story 15.3)', async () => {
      api.on('assignSessionToSlot', () =>
        HttpResponse.json({ message: 'Agenda is full' }, { status: 409 })
      );
      const { result } = await renderLoaded();

      await failing(() =>
        result.current.assignToSlot('session-1', {
          targetSlotKey: 'SPEAKER_SLOT-1',
          mode: 'INSERT',
        })
      );

      expect(api.calls('assignSessionToSlot')[0].body).toEqual({
        targetSlotKey: 'SPEAKER_SLOT-1',
        mode: 'INSERT',
      });
      expect(result.current.unassignedSessions).toEqual(unassigned);
      expect(result.current.conflict).toEqual({ message: 'Agenda is full' });
    });

    it('should_useAgendaFullMessage_when_assignToSlot409HasNoBody (Story 15.3)', async () => {
      api.on('assignSessionToSlot', () => new HttpResponse(null, { status: 409 }));
      const { result } = await renderLoaded();

      await failing(() =>
        result.current.assignToSlot('session-1', {
          targetSlotKey: 'SPEAKER_SLOT-1',
          mode: 'ASSIGN',
        })
      );

      expect(result.current.conflict).toEqual({ message: 'Agenda is full' });
    });
  });

  describe('Bulk Assignment', () => {
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
      ],
      changeReason: 'preference_matching',
    };

    it('should_bulkAssign_when_autoAssignmentTriggered', async () => {
      api.on('bulkAssignTiming', () => ({ assignedCount: 2, sessions: unassigned }));
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.bulkAssignTiming(bulkRequest);
      });

      expect(api.calls('bulkAssignTiming')[0].body).toEqual(bulkRequest);
      expect(result.current.unassignedSessions).toHaveLength(0);
      expect(result.current.assignedCount).toBe(2);
    });

    it('should_rollbackAndSetError_when_bulkAssignmentFailsWithGenericError', async () => {
      api.on('bulkAssignTiming', () => problem(500, 'Database unavailable'));
      const { result } = await renderLoaded();

      await failing(() => result.current.bulkAssignTiming(bulkRequest));

      expect(result.current.unassignedSessions).toEqual(unassigned);
      expect(result.current.error).toBe('Database unavailable');
    });

    it('should_rollbackAndSetError_when_bulkAssignmentFailsWith409Conflict', async () => {
      api.on('bulkAssignTiming', () =>
        HttpResponse.json({ ...timingConflict, message: 'Two sessions overlap' }, { status: 409 })
      );
      const { result } = await renderLoaded();

      await failing(() => result.current.bulkAssignTiming(bulkRequest));

      expect(result.current.unassignedSessions).toEqual(unassigned);
      expect(result.current.error).toBe('Two sessions overlap');
    });

    it('should_useFallbackMessage_when_bulkAssignment409HasNoMessage', async () => {
      api.on('bulkAssignTiming', () => HttpResponse.json({}, { status: 409 }));
      const { result } = await renderLoaded();

      await failing(() => result.current.bulkAssignTiming(bulkRequest));

      expect(result.current.error).toBe('Bulk assignment conflicts detected');
    });
  });

  describe('Conflict Detection', () => {
    it('should_detectConflicts_when_requested', async () => {
      const analysis = {
        hasConflicts: true,
        conflictCount: 1,
        conflicts: [
          {
            sessionSlug: 'session-1',
            conflictType: 'room_overlap' as const,
            severity: 'error' as const,
            affectedSessions: ['session-1', 'session-2'],
            timeRange: { start: '2025-05-15T09:00:00Z', end: '2025-05-15T10:00:00Z' },
            resolution: 'Move one session to another room',
          },
        ],
      };
      api.on('analyzeConflicts', () => analysis);
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.detectConflicts();
      });

      expect(api.calls('analyzeConflicts')[0].params.eventCode).toBe(EVENT);
      expect(result.current.conflictAnalysis).toEqual(analysis);
    });

    it('should_setError_when_detectConflictsFails', async () => {
      api.on('analyzeConflicts', () => problem(500, 'Conflict analysis failed'));
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.detectConflicts();
      });

      expect(result.current.error).toBe('Conflict analysis failed');
      expect(result.current.conflictAnalysis).toBeNull();
    });
  });

  describe('Clear All Timings', () => {
    it('should_clearAllTimingsAndRefresh_when_clearAllTimingsCalled', async () => {
      api.on('clearAllTimings', () => ({ message: 'Cleared', clearedCount: 2 }));
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.clearAllTimings();
      });

      expect(api.calls('clearAllTimings')).toHaveLength(1);
      expect(api.calls('getUnassignedSessions')).toHaveLength(2);
      expect(result.current.error).toBeNull();
    });

    it('should_setErrorAndRethrow_when_clearAllTimingsFails', async () => {
      api.on('clearAllTimings', () => problem(500, 'Could not clear timings'));
      const { result } = await renderLoaded();

      const thrown = await failing(() => result.current.clearAllTimings());

      expect((thrown as Error).message).toBe('Could not clear timings');
      expect(result.current.error).toBe('Could not clear timings');
      expect(api.calls('getUnassignedSessions')).toHaveLength(1);
    });
  });

  describe('Auto-Assign Timings', () => {
    it('should_autoAssignAndRefresh_when_autoAssignTimingsCalled', async () => {
      api.on('autoAssignTimings', () => ({ message: 'Assigned', assignedCount: 2 }));
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.autoAssignTimings();
      });

      expect(api.calls('autoAssignTimings')).toHaveLength(1);
      expect(api.calls('getUnassignedSessions')).toHaveLength(2);
      expect(result.current.error).toBeNull();
    });

    it('should_setErrorAndRethrow_when_autoAssignTimingsFails', async () => {
      api.on('autoAssignTimings', () => problem(500, 'No free slots'));
      const { result } = await renderLoaded();

      const thrown = await failing(() => result.current.autoAssignTimings());

      expect((thrown as Error).message).toBe('No free slots');
      expect(result.current.error).toBe('No free slots');
    });
  });

  describe('Refresh', () => {
    it('should_refreshSessions_when_manuallyTriggered', async () => {
      const { result } = await renderLoaded();
      api.on('getUnassignedSessions', () => [unassigned[1]]);

      await act(async () => {
        await result.current.refreshSessions();
      });

      expect(api.calls('getUnassignedSessions')).toHaveLength(2);
      expect(result.current.unassignedSessions).toEqual([unassigned[1]]);
      // The total is the first load's: the progress counts what was assigned since.
      expect(result.current.totalSessions).toBe(2);
      expect(result.current.assignedCount).toBe(1);
    });
  });
});
