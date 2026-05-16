/**
 * useSpeakerPool + useSpeakerOutreach Hooks Tests
 *
 * Coverage for:
 * - useSpeakerPool: fetch by eventCode, enabled guard
 * - useAddSpeakerToPool: mutation + cache invalidation
 * - useDeleteSpeakerFromPool: mutation + cache invalidation
 * - useSpeakerOutreachHistory: fetch by eventCode+speakerId, enabled guard
 * - useRecordOutreach: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/speakerPoolService', () => ({
  speakerPoolService: {
    getSpeakerPool: vi.fn(),
    addSpeakerToPool: vi.fn(),
    deleteSpeakerFromPool: vi.fn(),
    patchSpeakerPool: vi.fn(),
    promoteToSpeaker: vi.fn(),
    sendInvitation: vi.fn(),
    sendReminder: vi.fn(),
  },
}));

vi.mock('@/services/speakerOutreachService', () => ({
  speakerOutreachService: {
    getOutreachHistory: vi.fn(),
    recordOutreach: vi.fn(),
  },
}));

import { speakerPoolService } from '@/services/speakerPoolService';
import { speakerOutreachService } from '@/services/speakerOutreachService';
import {
  useSpeakerPool,
  useAddSpeakerToPool,
  useDeleteSpeakerFromPool,
  usePatchSpeakerPool,
  usePromoteSpeakerToReady,
  useSendInvitation,
  useSendReminder,
} from './useSpeakerPool';
import { useSpeakerOutreachHistory, useRecordOutreach } from './useSpeakerOutreach';

const mockGetSpeakerPool = vi.mocked(speakerPoolService.getSpeakerPool);
const mockAddSpeakerToPool = vi.mocked(speakerPoolService.addSpeakerToPool);
const mockDeleteSpeakerFromPool = vi.mocked(speakerPoolService.deleteSpeakerFromPool);
const mockPatchSpeakerPool = vi.mocked(speakerPoolService.patchSpeakerPool);
const mockPromoteToSpeaker = vi.mocked(speakerPoolService.promoteToSpeaker);
const mockSendInvitation = vi.mocked(speakerPoolService.sendInvitation);
const mockSendReminder = vi.mocked(speakerPoolService.sendReminder);
const mockGetOutreachHistory = vi.mocked(speakerOutreachService.getOutreachHistory);
const mockRecordOutreach = vi.mocked(speakerOutreachService.recordOutreach);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_SPEAKER = {
  id: 'sp-1',
  speakerName: 'Alice Smith',
  company: 'Acme',
  expertise: 'Cloud',
};

const MOCK_OUTREACH = [
  { id: 'o-1', contactMethod: 'email', contactDate: '2025-12-01', notes: 'First contact' },
];

// ── useSpeakerPool ────────────────────────────────────────────────────────────

describe('useSpeakerPool', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch speaker pool for an event', async () => {
    mockGetSpeakerPool.mockResolvedValue([MOCK_SPEAKER] as never);

    const { result } = renderHook(() => useSpeakerPool('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetSpeakerPool).toHaveBeenCalledWith('BAT142');
    expect(result.current.data).toEqual([MOCK_SPEAKER]);
  });

  it('should not fetch when eventCode is empty', () => {
    const { result } = renderHook(() => useSpeakerPool(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetSpeakerPool).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGetSpeakerPool.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useSpeakerPool('BAT999'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAddSpeakerToPool ───────────────────────────────────────────────────────

describe('useAddSpeakerToPool', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call addSpeakerToPool with eventCode and request', async () => {
    mockAddSpeakerToPool.mockResolvedValue(MOCK_SPEAKER as never);

    const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        request: { speakerName: 'Alice', company: 'Acme', expertise: 'Cloud' } as never,
      });
    });

    expect(mockAddSpeakerToPool).toHaveBeenCalledWith('BAT142', {
      speakerName: 'Alice',
      company: 'Acme',
      expertise: 'Cloud',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate speaker pool and status caches on success', async () => {
    mockAddSpeakerToPool.mockResolvedValue(MOCK_SPEAKER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        request: { speakerName: 'Alice' } as never,
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['speakerPool']) })
    );
    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['speakerStatusSummary', 'BAT142'] })
    );
  });

  it('should set isError on failure', async () => {
    mockAddSpeakerToPool.mockRejectedValue(new Error('Conflict'));

    const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: 'BAT142', request: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeleteSpeakerFromPool ─────────────────────────────────────────────────

describe('useDeleteSpeakerFromPool', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call deleteSpeakerFromPool with eventCode and speakerId', async () => {
    mockDeleteSpeakerFromPool.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useDeleteSpeakerFromPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ eventCode: 'BAT142', speakerId: 'sp-1' });
    });

    expect(mockDeleteSpeakerFromPool).toHaveBeenCalledWith('BAT142', 'sp-1');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate speaker pool cache on success', async () => {
    mockDeleteSpeakerFromPool.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteSpeakerFromPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ eventCode: 'BAT142', speakerId: 'sp-1' });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['speakerPool']) })
    );
  });

  it('should set isError on failure', async () => {
    mockDeleteSpeakerFromPool.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useDeleteSpeakerFromPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: 'BAT142', speakerId: 'sp-999' })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── usePatchSpeakerPool ─────────────────────────────────────────────────────

describe('usePatchSpeakerPool', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call patchSpeakerPool with eventCode, speakerId, and request', async () => {
    const patchedSpeaker = { ...MOCK_SPEAKER, assignedOrganizerId: 'org-new' };
    mockPatchSpeakerPool.mockResolvedValue(patchedSpeaker as never);

    const { result } = renderHook(() => usePatchSpeakerPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        speakerId: 'sp-1',
        request: { assignedOrganizerId: 'org-new' },
      });
    });

    expect(mockPatchSpeakerPool).toHaveBeenCalledWith('BAT142', 'sp-1', {
      assignedOrganizerId: 'org-new',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate speaker pool cache on success', async () => {
    mockPatchSpeakerPool.mockResolvedValue(MOCK_SPEAKER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => usePatchSpeakerPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        speakerId: 'sp-1',
        request: { notes: 'Updated notes' },
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['speakerPool']) })
    );
  });

  it('should set isError on failure', async () => {
    mockPatchSpeakerPool.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => usePatchSpeakerPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({
          eventCode: 'BAT142',
          speakerId: 'sp-1',
          request: { notes: 'test' },
        })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── usePromoteSpeakerToReady (Story 11.D.1) ─────────────────────────────────

describe('usePromoteSpeakerToReady', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call promoteToSpeaker service and invalidate caches on success', async () => {
    mockPromoteToSpeaker.mockResolvedValue({
      ...MOCK_SPEAKER,
      status: 'READY',
      username: 'alice.smith',
      email: 'alice@example.com',
    } as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => usePromoteSpeakerToReady(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        speakerId: 'sp-1',
        request: { email: 'alice@example.com', firstName: 'Alice', lastName: 'Smith' },
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockPromoteToSpeaker).toHaveBeenCalledWith('BAT142', 'sp-1', {
      email: 'alice@example.com',
      firstName: 'Alice',
      lastName: 'Smith',
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['speakerPool', 'list', 'BAT142'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['speakerStatusSummary', 'BAT142'] });
  });

  it('should expose the error when the API returns 409 INVALID_PROMOTION_STATE', async () => {
    const err = Object.assign(new Error('Conflict'), {
      response: {
        status: 409,
        data: { details: { code: 'INVALID_PROMOTION_STATE', currentState: 'INVITED' } },
      },
    });
    mockPromoteToSpeaker.mockRejectedValue(err);

    const { result } = renderHook(() => usePromoteSpeakerToReady(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({
          eventCode: 'BAT142',
          speakerId: 'sp-1',
          request: { email: 'alice@example.com' },
        })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBe(err);
  });
});

// ── useSendInvitation ───────────────────────────────────────────────────────

describe('useSendInvitation', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call sendInvitation with eventCode, username, and options', async () => {
    const mockResponse = {
      token: 'magic-link-token',
      workflowState: 'INVITED',
      invitedAt: '2025-12-15T10:00:00Z',
      email: 'speaker@example.com',
    };
    mockSendInvitation.mockResolvedValue(mockResponse as never);

    const { result } = renderHook(() => useSendInvitation('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        username: 'speaker-uuid',
        options: {
          responseDeadline: '2026-01-15',
          contentDeadline: '2026-02-01',
        },
      });
    });

    expect(mockSendInvitation).toHaveBeenCalledWith('BAT142', 'speaker-uuid', {
      responseDeadline: '2026-01-15',
      contentDeadline: '2026-02-01',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockResponse);
  });

  it('should call sendInvitation without options', async () => {
    const mockResponse = {
      token: 'token-123',
      workflowState: 'INVITED',
      invitedAt: '2025-12-15T10:00:00Z',
      email: 'speaker@example.com',
    };
    mockSendInvitation.mockResolvedValue(mockResponse as never);

    const { result } = renderHook(() => useSendInvitation('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ username: 'speaker-uuid' });
    });

    expect(mockSendInvitation).toHaveBeenCalledWith('BAT142', 'speaker-uuid', undefined);
  });

  it('should invalidate speaker pool and status summary caches on success', async () => {
    mockSendInvitation.mockResolvedValue({
      token: 't',
      workflowState: 'INVITED',
      invitedAt: '2025-12-15T10:00:00Z',
      email: 'a@b.com',
    } as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useSendInvitation('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ username: 'speaker-uuid' });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['speakerPool']) })
    );
    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['speakerStatusSummary', 'BAT142'] })
    );
  });

  it('should set isError on failure', async () => {
    mockSendInvitation.mockRejectedValue(new Error('Speaker not in IDENTIFIED state'));

    const { result } = renderHook(() => useSendInvitation('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ username: 'speaker-uuid' }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSendReminder ─────────────────────────────────────────────────────────

describe('useSendReminder', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call sendReminder with eventCode, speakerPoolId, and request', async () => {
    const mockResponse = {
      message: 'Reminder sent successfully',
      tier: 'TIER_1',
      emailAddress: 'speaker@example.com',
    };
    mockSendReminder.mockResolvedValue(mockResponse as never);

    const { result } = renderHook(() => useSendReminder('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        speakerPoolId: 'sp-pool-1',
        request: { reminderType: 'RESPONSE' as const },
      });
    });

    expect(mockSendReminder).toHaveBeenCalledWith('BAT142', 'sp-pool-1', {
      reminderType: 'RESPONSE',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockResponse);
  });

  it('should send CONTENT reminder type', async () => {
    const mockResponse = {
      message: 'Reminder sent',
      tier: 'TIER_2',
      emailAddress: 'speaker@example.com',
    };
    mockSendReminder.mockResolvedValue(mockResponse as never);

    const { result } = renderHook(() => useSendReminder('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        speakerPoolId: 'sp-pool-1',
        request: { reminderType: 'CONTENT' as const, tier: 'TIER_2' },
      });
    });

    expect(mockSendReminder).toHaveBeenCalledWith('BAT142', 'sp-pool-1', {
      reminderType: 'CONTENT',
      tier: 'TIER_2',
    });
  });

  it('should invalidate speaker pool cache on success', async () => {
    mockSendReminder.mockResolvedValue({
      message: 'ok',
      tier: 'TIER_1',
      emailAddress: 'a@b.com',
    } as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useSendReminder('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        speakerPoolId: 'sp-pool-1',
        request: { reminderType: 'RESPONSE' as const },
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['speakerPool']) })
    );
  });

  it('should set isError on failure', async () => {
    mockSendReminder.mockRejectedValue(new Error('Speaker not eligible for reminder'));

    const { result } = renderHook(() => useSendReminder('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({
          speakerPoolId: 'sp-pool-1',
          request: { reminderType: 'RESPONSE' as const },
        })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSpeakerOutreachHistory ─────────────────────────────────────────────────

describe('useSpeakerOutreachHistory', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch outreach history for event+speaker', async () => {
    mockGetOutreachHistory.mockResolvedValue(MOCK_OUTREACH as never);

    const { result } = renderHook(() => useSpeakerOutreachHistory('BAT142', 'sp-1'), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetOutreachHistory).toHaveBeenCalledWith('BAT142', 'sp-1');
    expect(result.current.data).toEqual(MOCK_OUTREACH);
  });

  it('should not fetch when eventCode is empty', () => {
    const { result } = renderHook(() => useSpeakerOutreachHistory('', 'sp-1'), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetOutreachHistory).not.toHaveBeenCalled();
  });

  it('should not fetch when speakerId is empty', () => {
    const { result } = renderHook(() => useSpeakerOutreachHistory('BAT142', ''), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetOutreachHistory).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGetOutreachHistory.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useSpeakerOutreachHistory('BAT999', 'sp-1'), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useRecordOutreach ─────────────────────────────────────────────────────────

describe('useRecordOutreach', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call recordOutreach with all params', async () => {
    mockRecordOutreach.mockResolvedValue(MOCK_OUTREACH[0] as never);

    const { result } = renderHook(() => useRecordOutreach(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        speakerId: 'sp-1',
        request: {
          contactMethod: 'email',
          contactDate: '2025-12-14T10:00:00Z',
          notes: 'First contact',
        } as never,
      });
    });

    expect(mockRecordOutreach).toHaveBeenCalledWith(
      'BAT142',
      'sp-1',
      expect.objectContaining({
        contactMethod: 'email',
      })
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate outreach history cache on success', async () => {
    mockRecordOutreach.mockResolvedValue(MOCK_OUTREACH[0] as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useRecordOutreach(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        speakerId: 'sp-1',
        request: {} as never,
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['speakerOutreach']) })
    );
  });

  it('should set isError on failure', async () => {
    mockRecordOutreach.mockRejectedValue(new Error('Validation error'));

    const { result } = renderHook(() => useRecordOutreach(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: 'BAT142', speakerId: 'sp-1', request: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
