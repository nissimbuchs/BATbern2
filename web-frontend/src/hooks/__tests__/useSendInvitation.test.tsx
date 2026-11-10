/**
 * useSendInvitation Hook Tests (Story 6.1c - Task 2)
 *
 * Tests for React Query mutation hook that sends speaker invitations
 * TDD: Tests written BEFORE implementation
 */

import React from 'react';
import { describe, test, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSendInvitation } from '../useSpeakerPool';
import { speakerPoolService } from '@/services/speakerPoolService';
import type { SendInvitationRequest, SendInvitationResponse } from '@/types/speakerPool.types';

// Mock the speakerPoolService
vi.mock('@/services/speakerPoolService', () => ({
  speakerPoolService: {
    sendInvitation: vi.fn(),
    getSpeakerPool: vi.fn(),
    addSpeakerToPool: vi.fn(),
    deleteSpeakerFromPool: vi.fn(),
  },
}));

describe('useSendInvitation Hook', () => {
  let queryClient: QueryClient;
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

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
        mutations: {
          retry: false,
        },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };

  test('should_callSendInvitation_when_mutationTriggered', async () => {
    vi.mocked(speakerPoolService.sendInvitation).mockResolvedValue(mockSuccessResponse);

    const { result } = renderHook(() => useSendInvitation(eventCode), { wrapper });

    await act(async () => {
      result.current.mutate({ username });
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(speakerPoolService.sendInvitation).toHaveBeenCalledWith(eventCode, username, undefined);
  });

  test('should_returnResponse_when_invitationSuccessful', async () => {
    vi.mocked(speakerPoolService.sendInvitation).mockResolvedValue(mockSuccessResponse);

    const { result } = renderHook(() => useSendInvitation(eventCode), { wrapper });

    await act(async () => {
      result.current.mutate({ username });
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockSuccessResponse);
  });

  test('should_passOptions_when_provided', async () => {
    vi.mocked(speakerPoolService.sendInvitation).mockResolvedValue(mockSuccessResponse);
    const options: SendInvitationRequest = {
      responseDeadline: '2026-02-25T12:00:00Z',
      contentDeadline: '2026-03-25T12:00:00Z',
      locale: 'en',
    };

    const { result } = renderHook(() => useSendInvitation(eventCode), { wrapper });

    await act(async () => {
      result.current.mutate({ username, options });
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(speakerPoolService.sendInvitation).toHaveBeenCalledWith(eventCode, username, options);
  });

  test('should_invalidateSpeakerPoolCache_when_invitationSuccessful', async () => {
    vi.mocked(speakerPoolService.sendInvitation).mockResolvedValue(mockSuccessResponse);

    // Pre-populate the cache
    queryClient.setQueryData(['speakerPool', 'list', eventCode], [{ id: 'old-data' }]);

    const { result } = renderHook(() => useSendInvitation(eventCode), { wrapper });

    await act(async () => {
      result.current.mutate({ username });
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    // Check that the cache was invalidated (state should be invalidated)
    const queryState = queryClient.getQueryState(['speakerPool', 'list', eventCode]);
    expect(queryState?.isInvalidated).toBe(true);
  });

  test('should_handleError_when_invitationFails', async () => {
    const mockError = new Error('SPEAKER_NOT_FOUND');
    vi.mocked(speakerPoolService.sendInvitation).mockRejectedValue(mockError);

    const { result } = renderHook(() => useSendInvitation(eventCode), { wrapper });

    await act(async () => {
      result.current.mutate({ username });
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toEqual(mockError);
  });

  test('should_setLoadingState_when_mutationInProgress', async () => {
    // Use a promise that we control to check loading state
    let resolvePromise: (value: SendInvitationResponse) => void;
    const pendingPromise = new Promise<SendInvitationResponse>((resolve) => {
      resolvePromise = resolve;
    });

    vi.mocked(speakerPoolService.sendInvitation).mockReturnValue(pendingPromise);

    const { result } = renderHook(() => useSendInvitation(eventCode), { wrapper });

    act(() => {
      result.current.mutate({ username });
    });

    // Wait for mutation to start and set pending state
    await waitFor(() => {
      expect(result.current.isPending).toBe(true);
    });

    // Resolve the promise
    await act(async () => {
      resolvePromise!(mockSuccessResponse);
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.isPending).toBe(false);
  });
});
