/**
 * Workflow Service Tests (RED Phase - TDD)
 *
 * Story 5.1a - Task 8 (Frontend Component Tests)
 * AC: 15-17 (Frontend Integration)
 *
 * Tests for workflow status API calls:
 * - GET /api/v1/events/{code}/workflow/status
 * - PUT /api/v1/events/{code}/workflow/transition
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { workflowService } from './workflowService';
import apiClient from '@/services/api/apiClient';
import type { AxiosResponse } from 'axios';

// Mock apiClient
vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('workflowService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getWorkflowStatus', () => {
    it('should_returnWorkflowStatus_when_validEventCodeProvided', async () => {
      // Given: Mock API response
      const mockResponse: AxiosResponse = {
        data: {
          currentState: 'SPEAKER_OUTREACH',
          blockedTransitions: [],
          nextAvailableStates: ['SPEAKER_CONFIRMATION'],
          validationMessages: [],
        },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockResolvedValue(mockResponse);

      // When: Fetching workflow status
      const result = await workflowService.getWorkflowStatus('BATbern56');

      // Then: Should call correct endpoint and return status
      expect(apiClient.get).toHaveBeenCalledWith('/events/BATbern56/workflow/status');
      expect(result.currentState).toBe('SPEAKER_OUTREACH');
      expect(result.nextAvailableStates).toEqual(['SPEAKER_CONFIRMATION']);
    });

    it('should_returnEmptyArrays_when_noBlockersOrNextStates', async () => {
      // Given: Mock API response with no blockers
      const mockResponse: AxiosResponse = {
        data: {
          currentState: 'CREATED',
          blockedTransitions: [],
          nextAvailableStates: [],
          validationMessages: [],
        },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockResolvedValue(mockResponse);

      // When: Fetching workflow status
      const result = await workflowService.getWorkflowStatus('BATbern56');

      // Then: Should return empty arrays
      expect(result.blockedTransitions).toEqual([]);
      expect(result.nextAvailableStates).toEqual([]);
      expect(result.validationMessages).toEqual([]);
    });

    it('should_returnValidationMessages_when_transitionBlocked', async () => {
      // Given: Mock API response with validation messages
      const mockResponse: AxiosResponse = {
        data: {
          currentState: 'TOPIC_SELECTION',
          blockedTransitions: ['SPEAKER_OUTREACH'],
          nextAvailableStates: ['SPEAKER_BRAINSTORMING'],
          validationMessages: ['Need 6 speakers, have 3', 'Venue not confirmed'],
        },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockResolvedValue(mockResponse);

      // When: Fetching workflow status
      const result = await workflowService.getWorkflowStatus('BATbern56');

      // Then: Should return validation messages
      expect(result.validationMessages).toEqual(['Need 6 speakers, have 3', 'Venue not confirmed']);
      expect(result.blockedTransitions).toEqual(['SPEAKER_OUTREACH']);
    });

    it('should_throwError_when_eventNotFound', async () => {
      // Given: Mock API 404 error
      const mockError = {
        response: {
          status: 404,
          data: {
            message: 'Event not found',
            code: 'EVENT_NOT_FOUND',
          },
        },
        isAxiosError: true,
      };

      vi.mocked(apiClient.get).mockRejectedValue(mockError);

      // When/Then: Should throw error
      await expect(workflowService.getWorkflowStatus('INVALID_CODE')).rejects.toThrow();
    });

    it('should_throwError_when_networkFailure', async () => {
      // Given: Mock network error
      const mockError = {
        message: 'Network Error',
        isAxiosError: true,
      };

      vi.mocked(apiClient.get).mockRejectedValue(mockError);

      // When/Then: Should throw error
      await expect(workflowService.getWorkflowStatus('BATbern56')).rejects.toThrow();
    });
  });

  describe('transitionWorkflowState', () => {
    it('should_transitionSuccessfully_when_validTransitionRequested', async () => {
      // Given: Mock successful transition
      const mockResponse: AxiosResponse = {
        data: {
          eventCode: 'BATbern56',
          title: 'BATbern 56',
          workflowState: 'TOPIC_SELECTION',
          status: 'planning',
        },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.put).mockResolvedValue(mockResponse);

      // When: Transitioning to new state
      const result = await workflowService.transitionWorkflowState('BATbern56', 'TOPIC_SELECTION');

      // Then: Should call correct endpoint with request body
      expect(apiClient.put).toHaveBeenCalledWith('/events/BATbern56/workflow/transition', {
        targetState: 'TOPIC_SELECTION',
      });
      expect(result.workflowState).toBe('TOPIC_SELECTION');
    });

    it('should_throwError_when_invalidStateTransition', async () => {
      // Given: Mock 400 error for invalid transition
      const mockError = {
        response: {
          status: 400,
          data: {
            message: 'Invalid state transition from CREATED to ARCHIVED',
            code: 'INVALID_STATE_TRANSITION',
          },
        },
        isAxiosError: true,
      };

      vi.mocked(apiClient.put).mockRejectedValue(mockError);

      // When/Then: Should throw error
      await expect(
        workflowService.transitionWorkflowState('BATbern56', 'ARCHIVED')
      ).rejects.toThrow();
    });

    it('should_throwError_when_validationFails', async () => {
      // Given: Mock 422 error for validation failure
      const mockError = {
        response: {
          status: 422,
          data: {
            message: 'Insufficient speakers identified',
            code: 'WORKFLOW_VALIDATION_FAILED',
            details: {
              required: 6,
              identified: 3,
            },
          },
        },
        isAxiosError: true,
      };

      vi.mocked(apiClient.put).mockRejectedValue(mockError);

      // When/Then: Should throw error with details
      await expect(
        workflowService.transitionWorkflowState('BATbern56', 'SPEAKER_OUTREACH')
      ).rejects.toThrow();
    });

    it('should_throwError_when_unauthorized', async () => {
      // Given: Mock 401 error
      const mockError = {
        response: {
          status: 401,
          data: {
            message: 'Unauthorized',
            code: 'UNAUTHORIZED',
          },
        },
        isAxiosError: true,
      };

      vi.mocked(apiClient.put).mockRejectedValue(mockError);

      // When/Then: Should throw error
      await expect(
        workflowService.transitionWorkflowState('BATbern56', 'TOPIC_SELECTION')
      ).rejects.toThrow();
    });

    it('should_throwError_when_forbidden', async () => {
      // Given: Mock 403 error (non-ORGANIZER role)
      const mockError = {
        response: {
          status: 403,
          data: {
            message: 'Forbidden - ORGANIZER role required',
            code: 'FORBIDDEN',
          },
        },
        isAxiosError: true,
      };

      vi.mocked(apiClient.put).mockRejectedValue(mockError);

      // When/Then: Should throw error
      await expect(
        workflowService.transitionWorkflowState('BATbern56', 'TOPIC_SELECTION')
      ).rejects.toThrow();
    });
  });
});
