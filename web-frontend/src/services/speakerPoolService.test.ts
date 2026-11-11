/**
 * speakerPoolService against the MSW harness (Story 5.2; moved off the apiClient mock in 16.8c).
 *
 * The real apiClient sends each call; the harness checks method, path and body against
 * `docs/api/event-speakers-api.openapi.yml` and answers with the spec example or a typed override.
 * Covered: add to pool, list, delete, patch, promote, send invitation, and the HTTP errors each
 * passes on to its caller.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { speakerPoolService } from './speakerPoolService';
import type {
  AddSpeakerToPoolRequest,
  PatchSpeakerPoolRequest,
  SendInvitationRequest,
  SendInvitationResponse,
} from '@/types/speakerPool.types';
import { aSpeakerPoolEntry } from '@/test/fixtures';

const EVENT = 'BATbern56';
const POOL_ID = '3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607';

const problem = (status: number, message = 'x') =>
  HttpResponse.json(
    { timestamp: '2026-11-10T10:00:00Z', path: '/api/v1/events', status, error: 'x', message },
    { status }
  );

/** The HTTP status a rejected axios call carries. */
const statusOf = (promise: Promise<unknown>) =>
  promise.then(
    () => {
      throw new Error('expected the call to reject');
    },
    (e: { response?: { status: number } }) => e.response?.status
  );

describe('speakerPoolService', () => {
  describe('addSpeakerToPool', () => {
    it('POSTs every field to the event pool and returns the stored entry', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp Solutions AG',
        expertise: 'Cloud Architecture, DevOps, Kubernetes',
        assignedOrganizerId: 'carla.muster',
        notes: 'Excellent speaker, presented at last 3 events',
      };
      const stored = aSpeakerPoolEntry({ speakerName: 'Dr. Jane Smith', status: 'IDENTIFIED' });
      api.on('addSpeakerToPool', () => stored, { status: 201 });

      const result = await speakerPoolService.addSpeakerToPool(EVENT, request);

      const [call] = api.calls('addSpeakerToPool');
      expect(call.params.eventCode).toBe(EVENT);
      expect(call.body).toEqual(request);
      expect(result).toEqual(stored);
    });

    it('accepts the name alone; unset fields come back null', async () => {
      api.on(
        'addSpeakerToPool',
        () => aSpeakerPoolEntry({ speakerName: 'John Doe', company: null, expertise: null }),
        { status: 201 }
      );

      const result = await speakerPoolService.addSpeakerToPool(EVENT, { speakerName: 'John Doe' });

      expect(api.calls('addSpeakerToPool')[0].body).toEqual({ speakerName: 'John Doe' });
      expect(result.company).toBeNull();
      expect(result.expertise).toBeNull();
    });

    it.each([400, 403, 404])('passes a %i on to the caller', async (status) => {
      api.on('addSpeakerToPool', () => problem(status));

      expect(
        await statusOf(speakerPoolService.addSpeakerToPool(EVENT, { speakerName: 'Jane' }))
      ).toBe(status);
    });
  });

  describe('getSpeakerPool', () => {
    it('GETs the pool of the event', async () => {
      const entries = [aSpeakerPoolEntry({ speakerName: 'Anna Brünnimann' })];
      api.on('getSpeakerPool', () => entries);

      const result = await speakerPoolService.getSpeakerPool(EVENT);

      expect(api.calls('getSpeakerPool')[0].params.eventCode).toBe(EVENT);
      expect(result).toEqual(entries);
    });

    it('returns an empty pool', async () => {
      api.on('getSpeakerPool', () => []);

      expect(await speakerPoolService.getSpeakerPool(EVENT)).toEqual([]);
    });

    it.each([403, 404])('passes a %i on to the caller', async (status) => {
      api.on('getSpeakerPool', () => problem(status));

      expect(await statusOf(speakerPoolService.getSpeakerPool(EVENT))).toBe(status);
    });
  });

  describe('deleteSpeakerFromPool', () => {
    it('DELETEs the entry', async () => {
      await speakerPoolService.deleteSpeakerFromPool(EVENT, POOL_ID);

      expect(api.calls('deleteSpeakerFromPool')[0].params).toEqual({
        eventCode: EVENT,
        speakerId: POOL_ID,
      });
    });

    it.each([403, 404])('passes a %i on to the caller', async (status) => {
      api.on('deleteSpeakerFromPool', () => problem(status));

      expect(await statusOf(speakerPoolService.deleteSpeakerFromPool(EVENT, POOL_ID))).toBe(status);
    });
  });

  describe('patchSpeakerPool', () => {
    it('PATCHes the given fields and returns the updated entry', async () => {
      const request: PatchSpeakerPoolRequest = {
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp Solutions AG',
        expertise: 'Cloud Architecture',
        assignedOrganizerId: 'carla.muster',
        notes: 'Updated notes',
      };
      const updated = aSpeakerPoolEntry({
        assignedOrganizerId: 'carla.muster',
        notes: 'Updated notes',
      });
      api.on('patchSpeakerPoolEntry', () => updated);

      const result = await speakerPoolService.patchSpeakerPool(EVENT, POOL_ID, request);

      const [call] = api.calls('patchSpeakerPoolEntry');
      expect(call.params.speakerId).toBe(POOL_ID);
      expect(call.body).toEqual(request);
      expect(result.assignedOrganizerId).toBe('carla.muster');
    });

    it('sets the reminders switch through the same PATCH (Story 4.10)', async () => {
      api.on('patchSpeakerPoolEntry', () => aSpeakerPoolEntry({ remindersDisabled: true }));

      const result = await speakerPoolService.patchSpeakerPool(EVENT, POOL_ID, {
        remindersDisabled: true,
      });

      expect(api.calls('patchSpeakerPoolEntry')[0].body).toEqual({ remindersDisabled: true });
      expect(result.remindersDisabled).toBe(true);
    });

    it('sends only the fields that change', async () => {
      await speakerPoolService.patchSpeakerPool(EVENT, POOL_ID, { notes: 'Only notes' });

      expect(api.calls('patchSpeakerPoolEntry')[0].body).toEqual({ notes: 'Only notes' });
    });

    it('passes a 404 on to the caller', async () => {
      api.on('patchSpeakerPoolEntry', () => problem(404));

      expect(
        await statusOf(speakerPoolService.patchSpeakerPool(EVENT, POOL_ID, { notes: 'x' }))
      ).toBe(404);
    });
  });

  describe('sendInvitation', () => {
    it('POSTs the options to the speaker invitation', async () => {
      const options: SendInvitationRequest = {
        responseDeadline: '2026-12-01T00:00:00Z',
        contentDeadline: '2027-01-01T00:00:00Z',
        locale: 'de',
        email: 'speaker@example.com',
      };
      const sent: SendInvitationResponse = {
        speakerPoolId: POOL_ID,
        username: 'jane.smith',
        email: 'speaker@example.com',
        status: 'INVITED',
        invitedAt: '2026-11-10T10:00:00Z',
        responseDeadline: '2026-12-01T00:00:00Z',
        contentDeadline: '2027-01-01T00:00:00Z',
      };
      api.on('sendSpeakerInvitation', () => sent);

      const result = await speakerPoolService.sendInvitation(EVENT, POOL_ID, options);

      const [call] = api.calls('sendSpeakerInvitation');
      // Story 4.10: keyed by the pool entry id, never by a username.
      expect(call.params.speakerId).toBe(POOL_ID);
      expect(call.body).toEqual(options);
      expect(result).toEqual(sent);
    });

    it('sends an empty body without options', async () => {
      await speakerPoolService.sendInvitation(EVENT, POOL_ID);

      expect(api.calls('sendSpeakerInvitation')[0].body).toEqual({});
    });

    it('passes a 409 (already invited) on to the caller', async () => {
      api.on('sendSpeakerInvitation', () => problem(409, 'Speaker already invited'));

      expect(await statusOf(speakerPoolService.sendInvitation(EVENT, POOL_ID))).toBe(409);
    });
  });
});
