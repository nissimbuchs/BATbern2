/**
 * Session API Client Tests
 *
 * Tests for Session Management API client
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AxiosError } from 'axios';
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { SessionApiClient } from '../sessionService';
import type {
  BatchImportSessionRequest,
  SessionBatchImportResult,
} from '@/types/sessionImport.types';

// Mock apiClient
vi.mock('@/services/api/apiClient', () => ({
  default: {
    post: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

describe('SessionApiClient', () => {
  let client: SessionApiClient;
  const mockPost = vi.mocked(apiClient.post);

  beforeEach(() => {
    vi.clearAllMocks();
    client = new SessionApiClient();
  });

  describe('batchImportSessions', () => {
    const eventCode = 'BATbern142';
    const sessions: BatchImportSessionRequest[] = [
      {
        title: 'Test Session 1',
        abstract: 'Test abstract 1',
        bat: 142,
      },
      {
        title: 'Test Session 2',
        abstract: 'Test abstract 2',
        pdf: 'presentation.pdf',
        bat: 142,
        referenten: [{ name: 'Speaker 1' }],
      },
    ];

    const mockResult: SessionBatchImportResult = {
      totalProcessed: 2,
      successfullyCreated: 2,
      skipped: 0,
      failed: 0,
      details: [
        {
          title: 'Test Session 1',
          status: 'success',
          message: 'Created',
          sessionSlug: 'session-1',
        },
        {
          title: 'Test Session 2',
          status: 'success',
          message: 'Created',
          sessionSlug: 'session-2',
        },
      ],
    };

    it('should_callCorrectEndpoint_when_batchImportRequested', async () => {
      mockPost.mockResolvedValueOnce({ data: mockResult });

      await client.batchImportSessions(eventCode, sessions);

      expect(mockPost).toHaveBeenCalledWith('/events/BATbern142/sessions/batch-import', sessions);
    });

    it('should_returnImportResult_when_importSuccessful', async () => {
      mockPost.mockResolvedValueOnce({ data: mockResult });

      const result = await client.batchImportSessions(eventCode, sessions);

      expect(result).toEqual(mockResult);
      expect(result.totalProcessed).toBe(2);
      expect(result.successfullyCreated).toBe(2);
    });

    it('should_handleEmptySessionsArray_when_noSessionsProvided', async () => {
      const emptyResult: SessionBatchImportResult = {
        totalProcessed: 0,
        successfullyCreated: 0,
        skipped: 0,
        failed: 0,
        details: [],
      };
      mockPost.mockResolvedValueOnce({ data: emptyResult });

      const result = await client.batchImportSessions(eventCode, []);

      expect(result.totalProcessed).toBe(0);
    });

    it('should_throwError_when_apiCallFails', async () => {
      const axiosError = new AxiosError('Request failed');
      mockPost.mockRejectedValueOnce(axiosError);

      await expect(client.batchImportSessions(eventCode, sessions)).rejects.toThrow();
    });
  });

  describe('error transformation', () => {
    const eventCode = 'BATbern142';
    const sessions: BatchImportSessionRequest[] = [{ title: 'Test', abstract: '', bat: 142 }];

    it('should_extractMessage_when_responseHasMessage', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: { message: 'Sessions already exist' },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      };
      mockPost.mockRejectedValueOnce(axiosError);

      await expect(client.batchImportSessions(eventCode, sessions)).rejects.toThrow(
        'Sessions already exist'
      );
    });

    it('should_extractError_when_responseHasErrorField', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: { error: 'Invalid event code' },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      };
      mockPost.mockRejectedValueOnce(axiosError);

      await expect(client.batchImportSessions(eventCode, sessions)).rejects.toThrow(
        'Invalid event code'
      );
    });

    it('should_includeCorrelationId_when_headerPresent', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: { message: 'Server error' },
        status: 500,
        statusText: 'Internal Server Error',
        headers: { 'x-correlation-id': 'abc-123' },
        config: {} as InternalAxiosRequestConfig,
      };
      mockPost.mockRejectedValueOnce(axiosError);

      await expect(client.batchImportSessions(eventCode, sessions)).rejects.toThrow(
        'Server error (Correlation ID: abc-123)'
      );
    });

    it('should_useDefaultMessage_when_noMessageInResponse', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: {},
        status: 500,
        statusText: 'Internal Server Error',
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      };
      mockPost.mockRejectedValueOnce(axiosError);

      await expect(client.batchImportSessions(eventCode, sessions)).rejects.toThrow(
        'Request failed'
      );
    });

    it('should_handleNonAxiosError_when_unexpectedErrorThrown', async () => {
      const genericError = new Error('Unknown error');
      mockPost.mockRejectedValueOnce(genericError);

      await expect(client.batchImportSessions(eventCode, sessions)).rejects.toThrow(
        'Unknown error'
      );
    });

    it('should_handleNonErrorObject_when_unexpectedValueThrown', async () => {
      mockPost.mockRejectedValueOnce('String error');

      await expect(client.batchImportSessions(eventCode, sessions)).rejects.toThrow(
        'An unexpected error occurred'
      );
    });
  });
});
