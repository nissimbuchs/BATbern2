/**
 * Session Speaker Hook Tests
 *
 * Tests for useAssignSpeaker and useRemoveSpeaker hooks.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useAssignSpeaker, useRemoveSpeaker } from './useSessionSpeakers';

vi.mock('@/services/api/sessionApiClient', () => ({
  sessionApiClient: {
    assignSpeaker: vi.fn(),
    removeSpeaker: vi.fn(),
  },
}));

import { sessionApiClient } from '@/services/api/sessionApiClient';

const mockClient = vi.mocked(sessionApiClient);

const createQC = () =>
  new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });

const createWrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

describe('useSessionSpeakers', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  describe('useAssignSpeaker', () => {
    it('should call assignSpeaker and invalidate event queries on success', async () => {
      const mockSpeaker = { username: 'alice', speakerRole: 'PRIMARY_SPEAKER' };
      mockClient.assignSpeaker.mockResolvedValue(
        mockSpeaker as ReturnType<typeof sessionApiClient.assignSpeaker> extends Promise<infer T>
          ? T
          : never
      );
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

      const { result } = renderHook(() => useAssignSpeaker(), { wrapper: createWrapper(qc) });

      await act(async () => {
        result.current.mutate({
          eventCode: 'BATbern142',
          sessionSlug: 'cloud-talk',
          request: { username: 'alice', speakerRole: 'PRIMARY_SPEAKER' },
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockClient.assignSpeaker).toHaveBeenCalledWith('BATbern142', 'cloud-talk', {
        username: 'alice',
        speakerRole: 'PRIMARY_SPEAKER',
      });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern142'] });
    });

    it('should set isError when assignSpeaker fails', async () => {
      mockClient.assignSpeaker.mockRejectedValue(new Error('Forbidden'));

      const { result } = renderHook(() => useAssignSpeaker(), { wrapper: createWrapper(qc) });

      await act(async () => {
        result.current.mutate({
          eventCode: 'BATbern142',
          sessionSlug: 'cloud-talk',
          request: { username: 'alice', speakerRole: 'PRIMARY_SPEAKER' },
        });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });
  });

  describe('useRemoveSpeaker', () => {
    it('should call removeSpeaker and invalidate event queries on success', async () => {
      mockClient.removeSpeaker.mockResolvedValue(undefined);
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

      const { result } = renderHook(() => useRemoveSpeaker(), { wrapper: createWrapper(qc) });

      await act(async () => {
        result.current.mutate({
          eventCode: 'BATbern142',
          sessionSlug: 'cloud-talk',
          username: 'alice',
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockClient.removeSpeaker).toHaveBeenCalledWith('BATbern142', 'cloud-talk', 'alice');
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern142'] });
    });
  });
});
