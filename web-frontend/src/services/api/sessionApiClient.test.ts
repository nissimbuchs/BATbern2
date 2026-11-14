/**
 * sessionApiClient tests (session update, materials, deletion, speakers)
 *
 * The client runs with the real apiClient against the MSW harness (Epic 17 test fidelity): every
 * request is checked against event-sessions-api and each test reads what went over the wire. A
 * 401/403 reaches the caller as the AxiosError itself (the auth handling needs it); any other HTTP
 * error as an `Error` with the server's message, "Event not found" / "Session not found" for a 404
 * naming one, and the correlation id when the response carries one.
 */

import { afterEach, describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { isAxiosError } from 'axios';
import { api } from '@/test/msw/api';
import apiClient from './apiClient';
import { sessionApiClient } from './sessionApiClient';
import type { AssignSpeakerRequest } from './sessionApiClient';

const EVENT = 'BATbern142';
const alice: AssignSpeakerRequest = { username: 'hong.an', speakerRole: 'PRIMARY_SPEAKER' };

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

/** The methods that pass a 401/403 through, with the operation each calls. */
const authPassingMethods = [
  ['patchSession', () => sessionApiClient.updateSession(EVENT, 'session-1', {})],
  [
    'associateMaterials',
    () => sessionApiClient.associateMaterials(EVENT, 'session-1', { materials: [] }),
  ],
  ['deleteSessionMaterial', () => sessionApiClient.deleteMaterial(EVENT, 'session-1', 'mat-1')],
  ['deleteSession', () => sessionApiClient.deleteSession(EVENT, 'session-1')],
  ['assignSpeakerToSession', () => sessionApiClient.assignSpeaker(EVENT, 'session-1', alice)],
  ['removeSpeakerFromSession', () => sessionApiClient.removeSpeaker(EVENT, 'session-1', 'hong.an')],
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

describe('sessionApiClient', () => {
  describe('requests', () => {
    it('should_patchSession_when_updated', async () => {
      await sessionApiClient.updateSession(EVENT, 'session-1', {
        title: 'Updated Title',
        durationMinutes: 45,
      });

      const [call] = api.calls('patchSession');
      expect(call.params).toEqual({ eventCode: EVENT, sessionSlug: 'session-1' });
      expect(call.body).toEqual({ title: 'Updated Title', durationMinutes: 45 });
    });

    it('should_postMaterials_when_associated', async () => {
      // Story 17.11d: confirmed session-material uploads only; name, size and type come from the
      // upload, the material type is derived when omitted.
      const materials = [
        { uploadId: 'up-1', materialType: 'PRESENTATION' as const },
        { uploadId: 'up-2' },
      ];

      await sessionApiClient.associateMaterials(EVENT, 'session-1', { materials });

      const [call] = api.calls('associateMaterials');
      expect(call.params).toEqual({ eventCode: EVENT, sessionSlug: 'session-1' });
      expect(call.body).toEqual({ materials });
    });

    it('should_returnDownloadUrl_when_requested', async () => {
      api.on('getMaterialDownloadUrl', () => ({ downloadUrl: 'https://s3.example.com/file' }));

      const result = await sessionApiClient.getMaterialDownloadUrl(EVENT, 'session-1', 'mat-uuid');

      expect(result.downloadUrl).toBe('https://s3.example.com/file');
      expect(api.calls('getMaterialDownloadUrl')[0].params).toEqual({
        eventCode: EVENT,
        sessionSlug: 'session-1',
        materialId: 'mat-uuid',
      });
    });

    it('should_deleteMaterial_when_removed', async () => {
      await sessionApiClient.deleteMaterial(EVENT, 'session-1', 'mat-1');

      expect(api.calls('deleteSessionMaterial')[0].params).toEqual({
        eventCode: EVENT,
        sessionSlug: 'session-1',
        materialId: 'mat-1',
      });
    });

    it('should_deleteSession_when_removed', async () => {
      await sessionApiClient.deleteSession(EVENT, 'session-1');

      expect(api.calls('deleteSession')[0].params).toEqual({
        eventCode: EVENT,
        sessionSlug: 'session-1',
      });
    });

    it('should_postSpeaker_when_assigned', async () => {
      await sessionApiClient.assignSpeaker(EVENT, 'session-1', alice);

      expect(api.calls('assignSpeakerToSession')[0].body).toEqual(alice);
    });

    it('should_deleteSpeaker_when_removed', async () => {
      await sessionApiClient.removeSpeaker(EVENT, 'session-1', 'hong.an');

      expect(api.calls('removeSpeakerFromSession')[0].params).toEqual({
        eventCode: EVENT,
        sessionSlug: 'session-1',
        username: 'hong.an',
      });
    });
  });

  describe('auth errors pass through', () => {
    const cases = authPassingMethods.flatMap(([op, call]) =>
      ([401, 403] as const).map((status) => [op, status, call] as const)
    );

    it.each(cases)('%s re-throws a %i as the AxiosError', async (op, status, call) => {
      api.on(op, () => problem(status, { error: 'Unauthorized', message: 'Not allowed' }));

      const error = await rejectionOf(call());

      expect(isAxiosError(error)).toBe(true);
      expect(isAxiosError(error) && error.response?.status).toBe(status);
    });
  });

  describe('other errors are mapped to the server message', () => {
    it.each(authPassingMethods)(
      '%s maps a 500 to its message and correlation id',
      async (op, call) => {
        api.on(op, () =>
          problem(500, { error: 'Internal Server Error', message: 'DB unavailable' }, 'corr-99')
        );

        const error = await rejectionOf(call());

        expect(isAxiosError(error)).toBe(false);
        expect(error).toEqual(new Error('DB unavailable (Correlation ID: corr-99)'));
      }
    );

    it('should_mapDownloadUrlError_when_requestFails', async () => {
      api.on('getMaterialDownloadUrl', () =>
        problem(404, { error: 'Not Found', message: 'Session not found: no-session' })
      );

      await expect(
        sessionApiClient.getMaterialDownloadUrl(EVENT, 'no-session', 'mat')
      ).rejects.toEqual(new Error('Session not found'));
    });

    it('should_sayEventNotFound_when_404NamesTheEvent', async () => {
      api.on('patchSession', () =>
        problem(404, { error: 'Not Found', message: 'Event not found: NOTFOUND' })
      );

      await expect(sessionApiClient.updateSession('NOTFOUND', 'session-1', {})).rejects.toEqual(
        new Error('Event not found')
      );
    });

    it('should_useErrorField_when_noMessage', async () => {
      api.on('assignSpeakerToSession', () => problem(409, { error: 'Speaker already assigned' }));

      await expect(sessionApiClient.assignSpeaker(EVENT, 'session-1', alice)).rejects.toEqual(
        new Error('Speaker already assigned')
      );
    });

    it('should_useAxiosMessage_when_networkFails', async () => {
      api.on('removeSpeakerFromSession', () => HttpResponse.error());

      await expect(sessionApiClient.removeSpeaker(EVENT, 'session-1', 'hong.an')).rejects.toEqual(
        new Error('Network Error')
      );
    });
  });
  describe('errors that are not from axios', () => {
    it('should_passPlainErrorThrough_when_deleteSessionThrowsBeforeSending', async () => {
      const thrown = new Error('Token lookup failed');
      failBeforeSending(thrown);

      await expect(sessionApiClient.deleteSession(EVENT, 'session-1')).rejects.toBe(thrown);
    });

    it('should_wrapNonError_when_deleteSessionThrowsBeforeSending', async () => {
      failBeforeSending('raw string error');

      await expect(sessionApiClient.deleteSession(EVENT, 'session-1')).rejects.toEqual(
        new Error('An unexpected error occurred')
      );
    });
  });
});
