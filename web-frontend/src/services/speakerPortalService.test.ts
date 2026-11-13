/**
 * Story 11.F.1 RD5: Cognito-side replacement for the legacy magic-link test suite.
 *
 * After Phase E (Story 11.E.3) every per-event method takes `eventCode` first (path parameter), the
 * magic-link `token` field is gone from every request body and the axios interceptor attaches the
 * Cognito Bearer.
 *
 * The real apiClient runs against the MSW harness (Epic 17 test fidelity): every request is checked
 * against event-speakers-api and uploads-api, and each test reads what went over the wire. Errors
 * from the backend surface with status, error code and correlation id.
 */

import { describe, it, expect, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { server } from '@/test/msw/server';
import { speakerPortalService } from './speakerPortalService';

/** An error answer in the services' error shape, optionally with a correlation id header. */
const problem = (status: number, body: Record<string, unknown>, correlationId?: string) =>
  HttpResponse.json(
    { timestamp: '2026-11-13T08:00:00Z', status, ...body },
    { status, headers: correlationId ? { 'x-correlation-id': correlationId } : {} }
  );

const respondResult = {
  success: true,
  speakerName: 'Test Speaker',
  eventName: 'BATbern99',
  nextSteps: [],
  contentDeadline: null,
};

describe('speakerPortalService — Cognito contract (Story 11.E.3 + 11.F.1)', () => {
  describe('respond()', () => {
    it('should_postWithEventCodeInPath_when_submittingAcceptResponse', async () => {
      api.on('respond', () => respondResult);

      await speakerPortalService.respond('BATbern99', { response: 'ACCEPT' });

      const [call] = api.calls('respond');
      expect(call.params).toEqual({ eventCode: 'BATbern99' });
      expect(call.body).toEqual({ response: 'ACCEPT' });
      // Story 11.F.1: no magic-link token field in the request body.
      expect(call.body).not.toHaveProperty('token');
    });

    it('should_urlEncodeEventCode_when_eventCodeHasReservedChars', async () => {
      api.on('respond', () => respondResult);

      await speakerPortalService.respond('BAT/99', { response: 'DECLINE', reason: 'busy' });

      expect(api.calls('respond')[0].url.pathname).toMatch(
        /\/speaker-portal\/events\/BAT%2F99\/respond$/
      );
    });

    it('should_surfaceBackend409_when_alreadyResponded', async () => {
      api.on('respond', () =>
        problem(409, {
          errorCode: 'ALREADY_RESPONDED',
          message: 'Already responded',
          previousResponse: 'ACCEPTED',
          respondedAt: '2026-05-20T10:00:00Z',
        })
      );

      await expect(
        speakerPortalService.respond('BATbern99', { response: 'ACCEPT' })
      ).rejects.toMatchObject({
        status: 409,
        errorCode: 'ALREADY_RESPONDED',
        previousResponse: 'ACCEPTED',
      });
    });

    it('should_surfaceBackend403_when_speakerHasNoAccessToEvent', async () => {
      api.on('respond', () => problem(403, { errorCode: 'FORBIDDEN', message: 'Forbidden' }));

      await expect(
        speakerPortalService.respond('OtherEvent', { response: 'ACCEPT' })
      ).rejects.toMatchObject({ status: 403, errorCode: 'FORBIDDEN' });
    });

    it('should_returnNetworkError_when_axiosErrorHasNoResponse', async () => {
      api.on('respond', () => HttpResponse.error());

      await expect(
        speakerPortalService.respond('BATbern99', { response: 'ACCEPT' })
      ).rejects.toThrow(/Network Error/);
    });
  });

  describe('getDashboard()', () => {
    it('should_getFromDashboardEndpoint_when_calledWithoutArgs', async () => {
      api.on('getDashboard', () => ({
        speakerName: 'X',
        profilePictureUrl: null,
        profileCompleteness: 100,
        upcomingEvents: [],
        pastEvents: [],
      }));

      await expect(speakerPortalService.getDashboard()).resolves.toMatchObject({
        speakerName: 'X',
      });
      expect(api.calls('getDashboard')).toHaveLength(1);
    });

    it('should_surfaceBackend401_when_authenticationMissing', async () => {
      api.on('getDashboard', () => problem(401, { message: 'Unauthorized' }));

      await expect(speakerPortalService.getDashboard()).rejects.toMatchObject({ status: 401 });
    });
  });

  describe('getContentInfo() / submitContent()', () => {
    it('should_getContentInfoFromEventScopedPath_when_called', async () => {
      api.on('getContentInfo', () => ({ eventCode: 'BATbern99', sessionSlug: 'keynote' }));

      await expect(speakerPortalService.getContentInfo('BATbern99')).resolves.toMatchObject({
        sessionSlug: 'keynote',
      });
      expect(api.calls('getContentInfo')[0].params).toEqual({ eventCode: 'BATbern99' });
    });

    it('should_postToContentSubmitEndpoint_when_submittingContent', async () => {
      api.on(
        'submitContent',
        () => ({ submissionId: 's-1', version: 1, status: 'SUBMITTED', sessionTitle: 'T' }),
        { status: 201 }
      );

      await speakerPortalService.submitContent('BATbern99', {
        title: 'My Talk',
        contentAbstract: 'Abstract',
      });

      const [call] = api.calls('submitContent');
      expect(call.params).toEqual({ eventCode: 'BATbern99' });
      expect(call.body).toMatchObject({ title: 'My Talk', contentAbstract: 'Abstract' });
      expect(call.body).not.toHaveProperty('token');
    });

    it('should_surfaceBackend404_when_eventNotFound', async () => {
      api.on('getContentInfo', () => problem(404, { message: 'Event not found' }));

      await expect(speakerPortalService.getContentInfo('Unknown')).rejects.toMatchObject({
        status: 404,
      });
    });
  });

  describe('uploadMaterial()', () => {
    const S3_PUT = 'https://bucket.example/materials/temp/up-1/file-up-1.pdf';
    const FILE_URL =
      'https://cdn.batbern.ch/materials/events/BATbern99/sessions/keynote/file-up-1.pdf';

    // Story 17.11d: purpose session-material with the speaker's session as target; the confirm
    // with the target attaches the slides and returns their CDN URL.
    it('should_reserveAndConfirmForTheSession_when_uploading', async () => {
      api.on(
        'createFileUpload',
        () => ({
          uploadId: 'up-1',
          uploadUrl: S3_PUT,
          expiresAt: '2026-11-13T10:15:00Z',
          requiredHeaders: { 'Content-Type': 'application/pdf' },
        }),
        { status: 201 }
      );
      let putType: string | null = null;
      server.use(
        http.put(S3_PUT, ({ request }) => {
          putType = request.headers.get('Content-Type');
          return new HttpResponse(null);
        })
      );
      api.on('confirmFileUpload', () => ({
        uploadId: 'up-1',
        purpose: 'session-material' as const,
        status: 'ASSOCIATED' as const,
        fileUrl: FILE_URL,
      }));
      const file = new File(['x'], 'Vortrag Müller.pdf', { type: 'application/pdf' });

      await expect(
        speakerPortalService.uploadMaterial('BATbern99', 'keynote', file)
      ).resolves.toEqual({ fileUrl: FILE_URL, fileName: 'Vortrag Müller.pdf' });

      const target = { eventCode: 'BATbern99', sessionSlug: 'keynote' };
      expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'session-material' });
      expect(api.calls('createFileUpload')[0].body).toEqual({
        fileName: 'Vortrag Müller.pdf',
        contentType: 'application/pdf',
        size: 1,
        target,
      });
      expect(putType).toBe('application/pdf');
      expect(api.calls('confirmFileUpload')[0].params).toEqual({
        purpose: 'session-material',
        uploadId: 'up-1',
      });
      expect(api.calls('confirmFileUpload')[0].body).toEqual({ target });
    });

    it('should_surfaceBackend403_when_sessionIsNotTheSpeakers', async () => {
      api.on('createFileUpload', () =>
        problem(403, { message: 'Speakers may only change the materials of their own sessions' })
      );
      const open = vi.spyOn(XMLHttpRequest.prototype, 'open');
      const file = new File(['x'], 'talk.pdf', { type: 'application/pdf' });

      await expect(
        speakerPortalService.uploadMaterial('BATbern99', 'other-session', file)
      ).rejects.toMatchObject({ status: 403 });

      // Only the reserve went out: no S3 PUT, no confirm.
      expect(open.mock.calls.filter(([method]) => method === 'PUT')).toHaveLength(0);
      expect(api.calls('confirmFileUpload')).toHaveLength(0);
      open.mockRestore();
    });
  });

  describe('correlation ID propagation', () => {
    it('should_appendCorrelationIdToErrorMessage_when_headerPresent', async () => {
      api.on('respond', () => problem(500, { message: 'Server error' }, 'abc-123'));

      await expect(
        speakerPortalService.respond('BATbern99', { response: 'ACCEPT' })
      ).rejects.toThrow(/Server error.*ID: abc-123/);
    });
  });
});
