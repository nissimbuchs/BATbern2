/**
 * Slot Assignment Service Tests (Story 5.7, Story 15.3)
 *
 * The service runs with the real apiClient against the MSW harness (Epic 17 test fidelity): every
 * request is checked against event-sessions-api, a test answers with a typed override or an error
 * `Response` and reads what went over the wire. Each method either re-throws the AxiosError (401,
 * 403 and, where the caller reads the conflict body, 409) or maps the error to an `Error` with the
 * server's message. The guard is `isAxiosError`, so the errors real axios raises are recognised.
 *
 * Coverage: AC5 unassigned sessions and timing, AC9 conflicts, AC13 bulk assignment, structural
 * sessions, auto-assignment, slot assignment (15.3), error mapping.
 */

import { afterEach, describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { isAxiosError } from 'axios';
import { api } from '@/test/msw/api';
import apiClient from '@/services/api/apiClient';
import { slotAssignmentService } from './slotAssignmentService';
import type { BulkTimingRequest, SessionTimingRequest } from '@/types/event.types';
import { aSessionResponse, aSessionSpeaker } from '@/test/fixtures';

const EVENT = 'BATbern142';

const timing: SessionTimingRequest = {
  startTime: '2025-05-15T09:00:00Z',
  endTime: '2025-05-15T09:45:00Z',
  room: 'Main Hall',
  changeReason: 'drag_drop_reassignment',
};

const bulk: BulkTimingRequest = {
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
      room: 'Conference Room A',
    },
  ],
  changeReason: 'preference_matching',
};

/** An error answer in the services' error shape, optionally with a correlation id header. */
const problem = (status: number, body: Record<string, unknown>, correlationId?: string) =>
  HttpResponse.json(
    { timestamp: '2026-11-10T08:00:00Z', status, ...body },
    { status, headers: correlationId ? { 'x-correlation-id': correlationId } : {} }
  );

/** Resolves to what `promise` rejected with; fails the test when it resolves. */
const rejectionOf = async (promise: Promise<unknown>): Promise<unknown> =>
  promise.then(
    () => {
      throw new Error('expected a rejection');
    },
    (e: unknown) => e
  );

/** Every method of the service, with the operation it calls. */
const methods = [
  ['getUnassignedSessions', () => slotAssignmentService.getUnassignedSessions(EVENT)],
  ['assignTiming', () => slotAssignmentService.assignSessionTiming(EVENT, 'session-1', timing)],
  [
    'assignSessionToSlot',
    () =>
      slotAssignmentService.assignSessionToSlot(EVENT, 'session-1', {
        targetSlotKey: 'SPEAKER_1',
        mode: 'ASSIGN',
      }),
  ],
  ['unassignTiming', () => slotAssignmentService.unassignSessionTiming(EVENT, 'session-1')],
  ['bulkAssignTiming', () => slotAssignmentService.bulkAssignTiming(EVENT, bulk)],
  ['analyzeConflicts', () => slotAssignmentService.detectConflicts(EVENT)],
  ['clearAllTimings', () => slotAssignmentService.clearAllTimings(EVENT)],
  ['generateStructuralSessions', () => slotAssignmentService.generateStructuralSessions(EVENT)],
  ['autoAssignTimings', () => slotAssignmentService.autoAssignTimings(EVENT)],
] as const;

/** The methods whose callers read the 409 conflict body, so the service passes the 409 on. */
const conflictMethods = [
  'assignTiming',
  'assignSessionToSlot',
  'bulkAssignTiming',
  'generateStructuralSessions',
] as const;

/**
 * Makes the next requests fail before they leave the process with `value`, a non-axios error (an
 * auth token lookup that throws, say). A request interceptor on the real apiClient: it runs first
 * and its rejection reaches the service unchanged.
 */
let ejectFailure: (() => void) | null = null;
const failBeforeSending = (value: unknown) => {
  const id = apiClient.interceptors.request.use(() => {
    throw value;
  });
  ejectFailure = () => apiClient.interceptors.request.eject(id);
};
afterEach(() => {
  ejectFailure?.();
  ejectFailure = null;
});

describe('slotAssignmentService', () => {
  describe('requests', () => {
    it('should_getUnassignedSessions_when_eventCodeGiven', async () => {
      const pool = [
        aSessionResponse({ sessionSlug: 'session-1', eventCode: EVENT, title: 'Hông Ân - Acme' }),
        aSessionResponse({
          sessionSlug: 'session-2',
          eventCode: EVENT,
          title: 'O’Neill - Tech',
          speakers: [aSessionSpeaker({ lastName: 'O’Neill' })],
        }),
      ];
      api.on('getUnassignedSessions', () => pool);

      const result = await slotAssignmentService.getUnassignedSessions(EVENT);

      expect(result).toEqual(pool);
      expect(api.calls('getUnassignedSessions')[0].params).toEqual({ eventCode: EVENT });
    });

    it('should_patchTiming_when_sessionAssigned', async () => {
      const updated = aSessionResponse({ sessionSlug: 'session-1', eventCode: EVENT, ...timing });
      api.on('assignTiming', () => updated);

      const result = await slotAssignmentService.assignSessionTiming(EVENT, 'session-1', timing);

      expect(result).toEqual(updated);
      const [call] = api.calls('assignTiming');
      expect(call.params).toEqual({ eventCode: EVENT, sessionSlug: 'session-1' });
      expect(call.body).toEqual(timing);
    });

    it('should_postSlotKeyAndMode_when_sessionAssignedToSlot', async () => {
      await slotAssignmentService.assignSessionToSlot(EVENT, 'session-1', {
        targetSlotKey: 'SPEAKER_2',
        mode: 'SWAP',
      });

      const [call] = api.calls('assignSessionToSlot');
      expect(call.params).toEqual({ eventCode: EVENT, sessionSlug: 'session-1' });
      expect(call.body).toEqual({ targetSlotKey: 'SPEAKER_2', mode: 'SWAP' });
    });

    it('should_deleteTiming_when_sessionUnassigned', async () => {
      const cleared = aSessionResponse({ sessionSlug: 'session-1', eventCode: EVENT });
      api.on('unassignTiming', () => cleared);

      const result = await slotAssignmentService.unassignSessionTiming(EVENT, 'session-1');

      expect(result).toEqual(cleared);
      expect(api.calls('unassignTiming')[0].params).toEqual({
        eventCode: EVENT,
        sessionSlug: 'session-1',
      });
    });

    it('should_postAssignments_when_bulkAssigned', async () => {
      const sessions = bulk.assignments.map((a) =>
        aSessionResponse({ eventCode: EVENT, ...a, room: a.room ?? null })
      );
      api.on('bulkAssignTiming', () => ({ assignedCount: 2, sessions }));

      const result = await slotAssignmentService.bulkAssignTiming(EVENT, bulk);

      expect(result).toEqual({ assignedCount: 2, sessions });
      expect(api.calls('bulkAssignTiming')[0].body).toEqual(bulk);
    });

    it('should_getConflictAnalysis_when_conflictsDetected', async () => {
      api.on('analyzeConflicts', () => ({ hasConflicts: false, conflictCount: 0, conflicts: [] }));

      const result = await slotAssignmentService.detectConflicts(EVENT);

      expect(result).toMatchObject({ conflictCount: 0, conflicts: [] });
      expect(api.calls('analyzeConflicts')).toHaveLength(1);
    });

    it('should_deleteAllTimings_when_cleared', async () => {
      api.on('clearAllTimings', () => ({ message: 'Cleared', clearedCount: 4 }));

      const result = await slotAssignmentService.clearAllTimings(EVENT);

      expect(result).toEqual({ message: 'Cleared', clearedCount: 4 });
    });

    it.each([
      [undefined, 'false'],
      [true, 'true'],
    ] as const)(
      'should_sendOverwrite_when_structuralSessionsGenerated (overwrite %s)',
      async (overwrite, sent) => {
        const created = [
          aSessionResponse({ sessionSlug: 'moderation-opening', sessionType: 'moderation' }),
        ];
        api.on('generateStructuralSessions', () => created);

        const result = await slotAssignmentService.generateStructuralSessions(EVENT, overwrite);

        expect(result).toEqual(created);
        expect(api.calls('generateStructuralSessions')[0].query.get('overwrite')).toBe(sent);
      }
    );

    it('should_postAutoAssign_when_autoAssigned', async () => {
      api.on('autoAssignTimings', () => ({ message: 'Assigned', assignedCount: 3 }));

      const result = await slotAssignmentService.autoAssignTimings(EVENT);

      expect(result).toEqual({ message: 'Assigned', assignedCount: 3 });
    });
  });

  describe('auth errors pass through', () => {
    const cases = methods.flatMap(([op, call]) =>
      ([401, 403] as const).map((status) => [op, status, call] as const)
    );

    it.each(cases)('%s re-throws a %i as the AxiosError', async (op, status, call) => {
      api.on(op, () => problem(status, { error: 'Unauthorized', message: 'Not allowed' }));

      const error = await rejectionOf(call());

      expect(isAxiosError(error)).toBe(true);
      expect(isAxiosError(error) && error.response?.status).toBe(status);
    });
  });

  describe('conflicts pass through with their body', () => {
    it.each(conflictMethods)('%s re-throws a 409 as the AxiosError', async (op) => {
      const conflict = {
        error: 'TIMING_CONFLICT',
        message: 'Session timing conflicts with existing schedule',
        conflicts: [{ type: 'room_overlap', conflictingSessionSlug: 'existing-session-1' }],
      };
      api.on(op, () => HttpResponse.json(conflict, { status: 409 }));
      const call = Object.fromEntries(methods)[op];

      const error = await rejectionOf(call());

      expect(isAxiosError(error)).toBe(true);
      expect(isAxiosError(error) && error.response?.data).toEqual(conflict);
    });
  });

  describe('other errors are mapped to the server message', () => {
    it.each(methods)('%s maps a 500 to its message and correlation id', async (op, call) => {
      api.on(op, () =>
        problem(500, { error: 'Internal Server Error', message: 'DB unavailable' }, 'corr-142')
      );

      const error = await rejectionOf(call());

      expect(isAxiosError(error)).toBe(false);
      expect(error).toEqual(new Error('DB unavailable (Correlation ID: corr-142)'));
    });

    it('should_useErrorField_when_noMessage', async () => {
      api.on('analyzeConflicts', () => problem(400, { error: 'Invalid event code' }));

      await expect(slotAssignmentService.detectConflicts(EVENT)).rejects.toEqual(
        new Error('Invalid event code')
      );
    });

    it('should_useAxiosMessage_when_networkFails', async () => {
      api.on('clearAllTimings', () => HttpResponse.error());

      await expect(slotAssignmentService.clearAllTimings(EVENT)).rejects.toEqual(
        new Error('Network Error')
      );
    });

    it('should_sayEventNotFound_when_404NamesTheEvent', async () => {
      api.on('getUnassignedSessions', () =>
        problem(404, { error: 'Not Found', message: 'Event not found: BATbern142' })
      );

      await expect(slotAssignmentService.getUnassignedSessions(EVENT)).rejects.toEqual(
        new Error('Event not found')
      );
    });

    it('should_saySessionNotFound_when_404NamesTheSession', async () => {
      api.on('assignTiming', () =>
        problem(404, { error: 'Not Found', message: 'Session not found: session-9' })
      );

      await expect(
        slotAssignmentService.assignSessionTiming(EVENT, 'session-9', timing)
      ).rejects.toEqual(new Error('Session not found'));
    });

    it('should_keepMessage_when_404NamesNeither', async () => {
      api.on('unassignTiming', () => problem(404, { error: 'Not Found', message: 'No such slot' }));

      await expect(slotAssignmentService.unassignSessionTiming(EVENT, 'session-1')).rejects.toEqual(
        new Error('No such slot')
      );
    });
  });
  describe('errors that are not from axios', () => {
    it('should_passPlainErrorThrough_when_detectConflictsThrowsBeforeSending', async () => {
      const thrown = new Error('Token lookup failed');
      failBeforeSending(thrown);

      await expect(slotAssignmentService.detectConflicts(EVENT)).rejects.toBe(thrown);
    });

    it('should_wrapNonError_when_detectConflictsThrowsBeforeSending', async () => {
      failBeforeSending('raw string error');

      await expect(slotAssignmentService.detectConflicts(EVENT)).rejects.toEqual(
        new Error('An unexpected error occurred')
      );
    });
  });
});
