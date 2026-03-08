/**
 * sessionService Tests
 *
 * Coverage for Session API Client:
 * - batchImportSessions: POST with extended timeout, error handling
 * - updateSession: PATCH, error handling
 * - transformError: AxiosError paths (with/without correlation ID, non-Axios errors)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AxiosError } from 'axios';
import { sessionApiClient } from './sessionService';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    post: vi.fn(),
    patch: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

const mockPost = vi.mocked(apiClient.post);
const mockPatch = vi.mocked(apiClient.patch);

const MOCK_RESULT = { imported: 5, skipped: 1, failed: 0, errors: [] };

const makeAxiosError = (
  message: string,
  status = 400,
  responseData: Record<string, unknown> = {},
  correlationId?: string
): AxiosError => {
  const err = new AxiosError(message);
  err.response = {
    data: responseData,
    status,
    statusText: 'Error',
    headers: correlationId ? { 'x-correlation-id': correlationId } : {},
    config: {} as never,
  };
  return err;
};

describe('sessionApiClient.batchImportSessions', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should POST to the correct endpoint with sessions', async () => {
    mockPost.mockResolvedValue({ data: MOCK_RESULT });

    const sessions = [{ title: 'Cloud Talk', speakerName: 'Alice' }] as never[];
    const result = await sessionApiClient.batchImportSessions('BAT142', sessions);

    expect(mockPost).toHaveBeenCalledWith(
      '/events/BAT142/sessions/batch-import',
      sessions,
      expect.objectContaining({ timeout: 120000 })
    );
    expect(result).toEqual(MOCK_RESULT);
  });

  it('should throw transformed error on AxiosError with response message', async () => {
    mockPost.mockRejectedValue(
      makeAxiosError('Validation failed', 422, { message: 'Invalid session data' })
    );

    await expect(sessionApiClient.batchImportSessions('BAT142', [])).rejects.toThrow(
      'Invalid session data'
    );
  });

  it('should include correlation ID in error when present', async () => {
    mockPost.mockRejectedValue(
      makeAxiosError('Server error', 500, { error: 'Internal error' }, 'abc-123')
    );

    await expect(sessionApiClient.batchImportSessions('BAT142', [])).rejects.toThrow(
      'Internal error (Correlation ID: abc-123)'
    );
  });

  it('should use response error field when message is absent', async () => {
    mockPost.mockRejectedValue(
      makeAxiosError('Request failed', 400, { error: 'Bad request format' })
    );

    await expect(sessionApiClient.batchImportSessions('BAT142', [])).rejects.toThrow(
      'Bad request format'
    );
  });

  it('should fall back to AxiosError message when no response data', async () => {
    const err = new AxiosError('Network Error');
    mockPost.mockRejectedValue(err);

    await expect(sessionApiClient.batchImportSessions('BAT142', [])).rejects.toThrow(
      'Network Error'
    );
  });

  it('should re-throw plain Error instances unchanged', async () => {
    mockPost.mockRejectedValue(new Error('Something broke'));

    await expect(sessionApiClient.batchImportSessions('BAT142', [])).rejects.toThrow(
      'Something broke'
    );
  });

  it('should wrap unknown thrown values as generic Error', async () => {
    mockPost.mockRejectedValue('raw string error');

    await expect(sessionApiClient.batchImportSessions('BAT142', [])).rejects.toThrow(
      'An unexpected error occurred'
    );
  });
});

describe('sessionApiClient.updateSession', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should PATCH the correct session endpoint with updates', async () => {
    mockPatch.mockResolvedValue({ data: {} });

    await sessionApiClient.updateSession('BAT142', 'cloud-talk', {
      title: 'Cloud Security',
      durationMinutes: 45,
    });

    expect(mockPatch).toHaveBeenCalledWith('/events/BAT142/sessions/cloud-talk', {
      title: 'Cloud Security',
      durationMinutes: 45,
    });
  });

  it('should accept partial updates (description only)', async () => {
    mockPatch.mockResolvedValue({ data: {} });

    await sessionApiClient.updateSession('BAT142', 'cloud-talk', {
      description: 'Updated description',
    });

    expect(mockPatch).toHaveBeenCalledWith('/events/BAT142/sessions/cloud-talk', {
      description: 'Updated description',
    });
  });

  it('should throw transformed error on failure', async () => {
    mockPatch.mockRejectedValue(makeAxiosError('Not found', 404, { message: 'Session not found' }));

    await expect(
      sessionApiClient.updateSession('BAT142', 'nonexistent', { title: 'x' })
    ).rejects.toThrow('Session not found');
  });

  it('should include correlation ID in error on PATCH failure', async () => {
    mockPatch.mockRejectedValue(
      makeAxiosError('Server error', 500, { message: 'DB error' }, 'corr-456')
    );

    await expect(sessionApiClient.updateSession('BAT142', 'cloud-talk', {})).rejects.toThrow(
      'DB error (Correlation ID: corr-456)'
    );
  });
});
