/**
 * sessionApiClient tests (session update, materials, deletion, speakers)
 *
 * The client runs with the real apiClient against the MSW harness (Epic 17 test fidelity): every
 * request is checked against event-sessions-api and each test reads what went over the wire. A
 * 401/403 reaches the caller as the AxiosError itself (the auth handling needs it); any other HTTP
 * error as an `Error` with the server's message, "Event not found" / "Session not found" for a 404
 * naming one, and the correlation id when the response carries one.
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { isAxiosError } from 'axios';
import { api } from '@/test/msw/api';
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
      const material = {
        uploadId: 'up-1',
        materialType: 'PRESENTATION',
        fileName: 'Präsentation Brünnimann.pdf',
        fileExtension: 'pdf',
        fileSize: 500_000,
        mimeType: 'application/pdf',
      };

      await sessionApiClient.associateMaterials(EVENT, 'session-1', { materials: [material] });

      const [call] = api.calls('associateMaterials');
      expect(call.params).toEqual({ eventCode: EVENT, sessionSlug: 'session-1' });
      expect(call.body).toEqual({ materials: [material] });
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
    it.each(authPassingMethods)('%s re-throws a 403 as the AxiosError', async (op, call) => {
      api.on(op, () => problem(403, { error: 'Forbidden', message: 'Organizer role required' }));

      const error = await rejectionOf(call());

      expect(isAxiosError(error)).toBe(true);
      expect(isAxiosError(error) && error.response?.status).toBe(403);
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
});
