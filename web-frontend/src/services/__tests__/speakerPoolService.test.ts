/**
 * Speaker Pool Service Tests (Story 6.1c - Task 1)
 *
 * Tests for sendInvitation method in Speaker Pool Service
 * TDD: Tests written BEFORE implementation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AxiosError } from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import { speakerPoolService } from '../speakerPoolService';
import type { SendInvitationRequest, SendInvitationResponse } from '@/types/speakerPool.types';

// Mock apiClient
vi.mock('@/services/api/apiClient', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

describe('SpeakerPoolService', () => {
  const mockPost = vi.mocked(apiClient.post);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('sendInvitation', () => {
    const eventCode = 'BATbern56';
    const username = 'speaker-uuid-123';

    const mockSuccessResponse: SendInvitationResponse = {
      speakerPoolId: 'pool-entry-uuid-123',
      username: 'speaker-uuid-123',
      email: 'speaker@example.com',
      status: 'INVITED',
      invitedAt: '2026-01-25T12:00:00Z',
      responseDeadline: '2026-02-08T12:00:00Z',
      contentDeadline: null,
    };

    // Test 5.1: should_callSendInvitationEndpoint_when_sendInvitationCalled
    it('should_callSendInvitationEndpoint_when_sendInvitationCalled', async () => {
      mockPost.mockResolvedValueOnce({ data: mockSuccessResponse });

      await speakerPoolService.sendInvitation(eventCode, username);

      expect(mockPost).toHaveBeenCalledWith(
        `/events/${eventCode}/speakers/${username}/send-invitation`,
        {}
      );
    });

    // Test 5.2: should_returnInvitation_when_invitationSuccessful
    it('should_returnInvitation_when_invitationSuccessful', async () => {
      mockPost.mockResolvedValueOnce({ data: mockSuccessResponse });

      const result = await speakerPoolService.sendInvitation(eventCode, username);

      expect(result).toEqual(mockSuccessResponse);
      expect(result.speakerPoolId).toBe('pool-entry-uuid-123');
      expect(result.status).toBe('INVITED');
      expect(result.email).toBe('speaker@example.com');
    });

    // Test 5.3: should_throwError_when_speakerNotFound
    it('should_throwError_when_speakerNotFound', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: { error: 'SPEAKER_NOT_FOUND', message: 'Speaker not found in pool' },
        status: 404,
        statusText: 'Not Found',
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      };
      mockPost.mockRejectedValueOnce(axiosError);

      await expect(speakerPoolService.sendInvitation(eventCode, username)).rejects.toThrow();
    });

    // Test 5.4: should_includeOptions_when_provided
    it('should_includeOptions_when_provided', async () => {
      mockPost.mockResolvedValueOnce({ data: mockSuccessResponse });
      const options: SendInvitationRequest = {
        responseDeadline: '2026-02-25T12:00:00Z',
        contentDeadline: '2026-03-25T12:00:00Z',
        locale: 'en',
      };

      await speakerPoolService.sendInvitation(eventCode, username, options);

      expect(mockPost).toHaveBeenCalledWith(
        `/events/${eventCode}/speakers/${username}/send-invitation`,
        options
      );
    });

    // Additional test: should handle ALREADY_INVITED error
    it('should_throwError_when_speakerAlreadyInvited', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: { error: 'ALREADY_INVITED', message: 'Speaker has already been invited' },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      };
      mockPost.mockRejectedValueOnce(axiosError);

      await expect(speakerPoolService.sendInvitation(eventCode, username)).rejects.toThrow();
    });

    // Additional test: should handle INVALID_STATE error
    it('should_throwError_when_speakerInInvalidState', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: { error: 'INVALID_STATE', message: 'Speaker must be in IDENTIFIED state' },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      };
      mockPost.mockRejectedValueOnce(axiosError);

      await expect(speakerPoolService.sendInvitation(eventCode, username)).rejects.toThrow();
    });
  });
});
