/**
 * workflowService against the MSW harness (Epic 17 test fidelity, touch rule of Story 4.9b): the
 * real apiClient sends each call, the harness checks it against events-core-api and answers with
 * the spec example or a typed override.
 *
 * - GET  /events/{code}/workflow/status
 * - POST /events/{code}/transition (Story 4.9b: the one way to change the state). It answers a
 *   WorkflowTransitionResponse, not an Event.
 */

import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { HttpResponse } from 'msw';
import { setNavigationCallback } from '@/services/api/apiClient';
import { api } from '@/test/msw/api';
import { workflowService } from './workflowService';

const navigate = vi.fn();
beforeAll(() => setNavigationCallback(navigate));
afterAll(() => setNavigationCallback(() => undefined));

const problem = (status: number, error: string, message: string) =>
  HttpResponse.json(
    {
      timestamp: '2026-11-10T08:00:00Z',
      path: '/api/v1/events/BATbern56/transition',
      status,
      error,
      message,
    },
    { status }
  );

describe('workflowService', () => {
  describe('getWorkflowStatus', () => {
    it('reads the status of the event', async () => {
      api.on('getWorkflowStatus', () => ({
        currentState: 'SPEAKER_IDENTIFICATION',
        nextAvailableStates: ['SLOT_ASSIGNMENT'],
        blockedTransitions: ['SLOT_ASSIGNMENT'],
        validationMessages: ['Cannot transition to SLOT_ASSIGNMENT: no accepted speaker'],
      }));

      const result = await workflowService.getWorkflowStatus('BATbern56');

      expect(api.calls('getWorkflowStatus')[0].params.eventCode).toBe('BATbern56');
      expect(result.currentState).toBe('SPEAKER_IDENTIFICATION');
      expect(result.blockedTransitions).toEqual(['SLOT_ASSIGNMENT']);
      expect(result.validationMessages).toHaveLength(1);
    });

    it('answers the spec example by default', async () => {
      const result = await workflowService.getWorkflowStatus('BATbern56');

      expect(result.currentState).toBe('CREATED');
      expect(result.nextAvailableStates).toEqual(['TOPIC_SELECTION']);
    });

    it('rejects with 404 when the event does not exist', async () => {
      api.on('getWorkflowStatus', () => problem(404, 'Not Found', 'Event not found: BATbern56'));

      await expect(workflowService.getWorkflowStatus('BATbern56')).rejects.toMatchObject({
        response: { status: 404 },
      });
    });

    it('rejects on a network failure', async () => {
      api.on('getWorkflowStatus', () => HttpResponse.error());

      await expect(workflowService.getWorkflowStatus('BATbern56')).rejects.toMatchObject({
        code: 'ERR_NETWORK',
      });
    });
  });

  describe('transitionWorkflowState', () => {
    it('posts the target state to /events/{code}/transition and returns the transition result', async () => {
      api.on('transitionEventWorkflowState', ({ params, body }) => ({
        eventCode: params.eventCode,
        workflowState: body?.targetState ?? 'CREATED',
        updatedAt: '2026-11-10T08:00:00Z',
      }));

      const result = await workflowService.transitionWorkflowState('BATbern56', 'TOPIC_SELECTION');

      const [call] = api.calls('transitionEventWorkflowState');
      expect(call.method).toBe('POST');
      expect(call.url.pathname).toContain('/events/BATbern56/transition');
      expect(call.body).toEqual({ targetState: 'TOPIC_SELECTION', overrideValidation: false });
      expect(result).toEqual({
        eventCode: 'BATbern56',
        workflowState: 'TOPIC_SELECTION',
        updatedAt: '2026-11-10T08:00:00Z',
      });
    });

    it('sends the override flag and its reason', async () => {
      await workflowService.transitionWorkflowState(
        'BATbern56',
        'ARCHIVED',
        true,
        'Historisches Event, nachträglich archiviert'
      );

      expect(api.calls('transitionEventWorkflowState')[0].body).toEqual({
        targetState: 'ARCHIVED',
        overrideValidation: true,
        overrideReason: 'Historisches Event, nachträglich archiviert',
      });
    });

    it('sends no reason when none is given', async () => {
      await workflowService.transitionWorkflowState('BATbern56', 'ARCHIVED', true);

      expect(api.calls('transitionEventWorkflowState')[0].body).toEqual({
        targetState: 'ARCHIVED',
        overrideValidation: true,
      });
    });

    it.each([
      [400, 'Bad Request', 'No enum constant PUBLISHED'],
      [403, 'Forbidden', 'Access denied'],
      [422, 'Unprocessable Entity', 'Invalid transition from CREATED to ARCHIVED'],
    ])('rejects with the status EMS answers (%i)', async (status, error, message) => {
      api.on('transitionEventWorkflowState', () => problem(status, error, message));

      await expect(
        workflowService.transitionWorkflowState('BATbern56', 'ARCHIVED')
      ).rejects.toMatchObject({ response: { status, data: { message } } });
    });
  });
});
