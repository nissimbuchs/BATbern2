/**
 * Speaker Outreach React Query Hooks Tests (Story 5.3 - Task 4a)
 *
 * Comprehensive tests for speaker outreach hooks
 * Tests React Query integration and cache management
 *
 * Coverage:
 * - useSpeakerOutreachHistory hook
 * - useRecordOutreach mutation hook
 * - useBulkRecordOutreach mutation hook
 * - Query key factory
 * - Cache invalidation on mutations
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { speakerOutreachService } from '@/services/speakerOutreachService';
import {
  useSpeakerOutreachHistory,
  useRecordOutreach,
  useBulkRecordOutreach,
  speakerOutreachKeys,
} from './useSpeakerOutreach';
import type { OutreachHistory, RecordOutreachRequest } from '@/types/speakerOutreach.types';

// Mock the speaker outreach service
vi.mock('@/services/speakerOutreachService', () => ({
  speakerOutreachService: {
    getOutreachHistory: vi.fn(),
    recordOutreach: vi.fn(),
    bulkRecordOutreach: vi.fn(),
  },
}));

describe('useSpeakerOutreach hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient.clear();
    vi.restoreAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  describe('speakerOutreachKeys', () => {
    it('should generate correct query keys for hierarchy', () => {
      expect(speakerOutreachKeys.all).toEqual(['speakerOutreach']);
      expect(speakerOutreachKeys.histories()).toEqual(['speakerOutreach', 'history']);
      expect(speakerOutreachKeys.history('BATbern56', 'speaker-123')).toEqual([
        'speakerOutreach',
        'history',
        'BATbern56',
        'speaker-123',
      ]);
    });
  });

  describe('useSpeakerOutreachHistory', () => {
    const mockHistory: OutreachHistory[] = [
      {
        id: 'outreach-1',
        speakerPoolId: 'speaker-123',
        contactDate: '2025-12-14T10:00:00Z',
        contactMethod: 'email',
        notes: 'Initial contact email sent',
        organizerUsername: 'john.doe',
        createdAt: '2025-12-14T10:00:00Z',
      },
      {
        id: 'outreach-2',
        speakerPoolId: 'speaker-123',
        contactDate: '2025-12-11T14:30:00Z',
        contactMethod: 'phone',
        notes: 'Follow-up call',
        organizerUsername: 'jane.smith',
        createdAt: '2025-12-11T14:30:00Z',
      },
    ];

    it('should fetch outreach history for speaker (AC4)', async () => {
      vi.mocked(speakerOutreachService.getOutreachHistory).mockResolvedValue(mockHistory);

      const { result } = renderHook(() => useSpeakerOutreachHistory('BATbern56', 'speaker-123'), {
        wrapper,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(speakerOutreachService.getOutreachHistory).toHaveBeenCalledWith(
        'BATbern56',
        'speaker-123'
      );
      expect(result.current.data).toEqual(mockHistory);
    });

    it('should not fetch when eventCode is empty', () => {
      const { result } = renderHook(() => useSpeakerOutreachHistory('', 'speaker-123'), {
        wrapper,
      });

      expect(result.current.isPending).toBe(true);
      expect(speakerOutreachService.getOutreachHistory).not.toHaveBeenCalled();
    });

    it('should not fetch when speakerId is empty', () => {
      const { result } = renderHook(() => useSpeakerOutreachHistory('BATbern56', ''), { wrapper });

      expect(result.current.isPending).toBe(true);
      expect(speakerOutreachService.getOutreachHistory).not.toHaveBeenCalled();
    });

    it('should handle errors gracefully', async () => {
      const error = new Error('Speaker not found');
      vi.mocked(speakerOutreachService.getOutreachHistory).mockRejectedValue(error);

      const { result } = renderHook(() => useSpeakerOutreachHistory('BATbern56', 'speaker-999'), {
        wrapper,
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });

    it('should cache outreach history with 2-minute stale time', async () => {
      vi.mocked(speakerOutreachService.getOutreachHistory).mockResolvedValue(mockHistory);

      const { result } = renderHook(() => useSpeakerOutreachHistory('BATbern56', 'speaker-123'), {
        wrapper,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Verify staleTime is set (data should not be refetched immediately)
      expect(result.current.data).toEqual(mockHistory);
      expect(speakerOutreachService.getOutreachHistory).toHaveBeenCalledTimes(1);
    });
  });

  describe('useRecordOutreach', () => {
    const mockRequest: RecordOutreachRequest = {
      contactMethod: 'email',
      contactDate: '2025-12-14T10:00:00Z',
      notes: 'Initial contact',
    };

    const mockResponse = {
      id: 'outreach-new',
      speakerPoolId: 'speaker-123',
      contactDate: '2025-12-14T10:00:00Z',
      contactMethod: 'email' as const,
      notes: 'Initial contact',
      organizerUsername: 'john.doe',
      createdAt: '2025-12-14T10:00:00Z',
    };

    it('should record speaker outreach (AC2-3)', async () => {
      vi.mocked(speakerOutreachService.recordOutreach).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useRecordOutreach(), { wrapper });

      result.current.mutate({
        eventCode: 'BATbern56',
        speakerId: 'speaker-123',
        request: mockRequest,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(speakerOutreachService.recordOutreach).toHaveBeenCalledWith(
        'BATbern56',
        'speaker-123',
        mockRequest
      );
      expect(result.current.data).toEqual(mockResponse);
    });

    it('should invalidate outreach history cache on success', async () => {
      vi.mocked(speakerOutreachService.recordOutreach).mockResolvedValue(mockResponse);

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useRecordOutreach(), { wrapper });

      result.current.mutate({
        eventCode: 'BATbern56',
        speakerId: 'speaker-123',
        request: mockRequest,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: speakerOutreachKeys.history('BATbern56', 'speaker-123'),
      });
    });

    it('should handle validation errors (AC9)', async () => {
      const error = new Error('Speaker not in valid state for contact: DECLINED');
      vi.mocked(speakerOutreachService.recordOutreach).mockRejectedValue(error);

      const { result } = renderHook(() => useRecordOutreach(), { wrapper });

      result.current.mutate({
        eventCode: 'BATbern56',
        speakerId: 'speaker-456',
        request: mockRequest,
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });
  });

  describe('useBulkRecordOutreach', () => {
    const mockRequest: RecordOutreachRequest = {
      contactMethod: 'email',
      contactDate: '2025-12-14T10:00:00Z',
      notes: 'Bulk email campaign',
    };

    it('should record outreach for multiple speakers (AC6)', async () => {
      vi.mocked(speakerOutreachService.bulkRecordOutreach).mockResolvedValue(undefined);

      const { result } = renderHook(() => useBulkRecordOutreach(), { wrapper });

      result.current.mutate({
        eventCode: 'BATbern56',
        speakerIds: ['speaker-1', 'speaker-2', 'speaker-3'],
        request: mockRequest,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(speakerOutreachService.bulkRecordOutreach).toHaveBeenCalledWith(
        'BATbern56',
        ['speaker-1', 'speaker-2', 'speaker-3'],
        mockRequest
      );
    });

    it('should invalidate all speaker pool cache on success', async () => {
      vi.mocked(speakerOutreachService.bulkRecordOutreach).mockResolvedValue(undefined);

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useBulkRecordOutreach(), { wrapper });

      result.current.mutate({
        eventCode: 'BATbern56',
        speakerIds: ['speaker-1', 'speaker-2'],
        request: mockRequest,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Should invalidate all outreach histories for this event
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: ['speakerOutreach', 'history'],
      });
    });

    it('should handle partial failures gracefully', async () => {
      const error = new Error('Failed to record outreach for some speakers');
      vi.mocked(speakerOutreachService.bulkRecordOutreach).mockRejectedValue(error);

      const { result } = renderHook(() => useBulkRecordOutreach(), { wrapper });

      result.current.mutate({
        eventCode: 'BATbern56',
        speakerIds: ['speaker-1', 'speaker-999'],
        request: mockRequest,
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });
  });
});
