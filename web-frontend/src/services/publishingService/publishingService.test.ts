/**
 * publishingService against the MSW harness (17.0c2): the real apiClient sends each call, the
 * harness checks it against events-core-api and answers with the spec example or a typed
 * override. The bodies are the generated types EMS sends (PublishingEngineController).
 */
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { HttpResponse } from 'msw';
import { setNavigationCallback } from '@/services/api/apiClient';
import { api } from '@/test/msw/api';
import type { AutoPublishScheduleRequest, PublishValidationError } from '@/types/event.types';
import { publishingService } from './publishingService';

const navigate = vi.fn();
beforeAll(() => setNavigationCallback(navigate));
afterAll(() => setNavigationCallback(() => undefined));

/** What a rejected axios call carries, for the assertions on errors. */
const rejection = (promise: Promise<unknown>) =>
  promise.then(
    () => {
      throw new Error('expected the call to reject');
    },
    (e: { response?: { status: number; data: unknown } }) => e
  );

describe('publishingService', () => {
  describe('publishPhase', () => {
    it('posts to /events/{code}/publish/{phase} and returns what EMS answers', async () => {
      api.on('publishPhase', ({ params }) => ({
        phase: params.phase,
        published: true,
        publishedAt: '2026-11-08T10:00:00Z',
        cdnInvalidated: true,
      }));

      const result = await publishingService.publishPhase('BATbern61', 'topic');

      const [call] = api.calls('publishPhase');
      expect(call.method).toBe('POST');
      expect(call.params).toEqual({ eventCode: 'BATbern61', phase: 'topic' });
      expect(call.body).toBeUndefined();
      expect(result).toEqual({
        phase: 'topic',
        published: true,
        publishedAt: '2026-11-08T10:00:00Z',
        cdnInvalidated: true,
      });
    });

    it('rejects with the 422 validation body when the content is not ready', async () => {
      const notReady: PublishValidationError = {
        error: 'VALIDATION_ERROR',
        message: 'Content not ready for this publishing phase',
        validationErrors: [
          {
            sessionSlug: 'untimed-session',
            field: 'startTime',
            message: 'Not all sessions have timing assigned',
          },
        ],
      };
      api.on('publishPhase', () => HttpResponse.json(notReady, { status: 422 }));

      const error = await rejection(publishingService.publishPhase('BATbern61', 'agenda'));

      expect(error.response?.status).toBe(422);
      expect(error.response?.data).toEqual(notReady);
    });

    it('rejects a 401 and sends the user to the login page', async () => {
      api.on('publishPhase', () =>
        HttpResponse.json({ error: 'UNAUTHORIZED', message: 'Unauthorized' }, { status: 401 })
      );

      const error = await rejection(publishingService.publishPhase('BATbern61', 'topic'));

      expect(error.response?.status).toBe(401);
      expect(navigate).toHaveBeenCalledWith('/login');
    });

    it('rejects a 403 with its body', async () => {
      api.on('publishPhase', () =>
        HttpResponse.json(
          { error: 'FORBIDDEN', message: 'Insufficient permissions' },
          { status: 403 }
        )
      );

      const error = await rejection(publishingService.publishPhase('BATbern61', 'topic'));

      expect(error.response?.status).toBe(403);
      expect(error.response?.data).toMatchObject({ message: 'Insufficient permissions' });
    });
  });

  describe('unpublishPhase', () => {
    it('posts to /events/{code}/unpublish/{phase} and returns what EMS answers', async () => {
      api.on('unpublishPhase', ({ params }) => ({
        phase: params.phase,
        published: false,
        unpublishedAt: '2026-11-08T11:00:00Z',
        cdnInvalidated: true,
      }));

      const result = await publishingService.unpublishPhase('BATbern61', 'speakers');

      expect(api.calls('unpublishPhase')[0].params).toEqual({
        eventCode: 'BATbern61',
        phase: 'speakers',
      });
      expect(result).toMatchObject({ phase: 'speakers', published: false });
    });
  });

  describe('getPublishingStatus', () => {
    it('reads the status with the validation of every phase', async () => {
      api.on('getPublishingStatus', () => ({
        currentPhase: 'topic',
        publishedPhases: ['topic'],
        topic: { isValid: true, errors: [] },
        speakers: { isValid: false, errors: ['At least 3 sessions required'] },
        sessions: {
          isValid: false,
          errors: ['1 session without a time'],
          assignedCount: 2,
          totalCount: 3,
          unassignedSessions: [{ sessionSlug: 'keynote-bruennimann', title: 'Keynote' }],
        },
      }));

      const status = await publishingService.getPublishingStatus('BATbern61');

      expect(api.calls('getPublishingStatus')[0].params.eventCode).toBe('BATbern61');
      expect(status.currentPhase).toBe('topic');
      expect(status.speakers.errors).toEqual(['At least 3 sessions required']);
      expect(status.sessions.unassignedSessions).toHaveLength(1);
    });

    it('answers the spec example by default (no phase published yet)', async () => {
      const status = await publishingService.getPublishingStatus('BATbern61');

      expect(status.publishedPhases).toEqual([]);
      expect(status.topic.isValid).toBe(false);
    });

    it('rejects when the service is unavailable', async () => {
      api.on('getPublishingStatus', () => HttpResponse.error());

      await expect(publishingService.getPublishingStatus('BATbern61')).rejects.toMatchObject({
        code: 'ERR_NETWORK',
      });
    });
  });

  describe('scheduleAutoPublish', () => {
    it('sends the schedule body EMS reads (configureAutoPublish), not a date', async () => {
      const request: AutoPublishScheduleRequest = {
        phase2Enabled: true,
        phase2DaysBeforeEvent: 30,
        phase3Enabled: true,
        phase3DaysBeforeEvent: 14,
      };
      api.on('configureAutoPublish', ({ body }) => ({
        scheduled: true,
        ...body,
        phase2TriggerDate: '2027-02-12T08:00:00Z',
        phase3TriggerDate: '2027-02-28T08:00:00Z',
      }));

      const result = await publishingService.scheduleAutoPublish('BATbern61', request);

      const [call] = api.calls('configureAutoPublish');
      expect(call.method).toBe('POST');
      expect(call.params.eventCode).toBe('BATbern61');
      expect(call.body).toEqual(request);
      expect(result).toMatchObject({ scheduled: true, phase2DaysBeforeEvent: 30 });
    });
  });
});
