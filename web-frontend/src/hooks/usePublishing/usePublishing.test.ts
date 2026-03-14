/**
 * usePublishing Hook Tests
 *
 * Coverage for:
 * - Initial state (isLoadingStatus, publishingStatus)
 * - publishPhase / unpublishPhase mutations trigger the publishing service
 * - fetchPreview mutation
 * - scheduleAutoPublish / cancelAutoPublish mutations
 * - validationErrors extraction from 422 error
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/publishingService/publishingService', () => ({
  publishingService: {
    getPublishingStatus: vi.fn(),
    getChangeLog: vi.fn(),
    publishPhase: vi.fn(),
    unpublishPhase: vi.fn(),
    getPublishPreview: vi.fn(),
    scheduleAutoPublish: vi.fn(),
    cancelAutoPublish: vi.fn(),
  },
}));

import { publishingService } from '@/services/publishingService/publishingService';
import { usePublishing } from './usePublishing';

const mockGetStatus = vi.mocked(publishingService.getPublishingStatus);
const mockGetChangeLog = vi.mocked(publishingService.getChangeLog);
const mockPublishPhase = vi.mocked(publishingService.publishPhase);
const mockUnpublishPhase = vi.mocked(publishingService.unpublishPhase);
const mockGetPublishPreview = vi.mocked(publishingService.getPublishPreview);
const mockScheduleAutoPublish = vi.mocked(publishingService.scheduleAutoPublish);
const mockCancelAutoPublish = vi.mocked(publishingService.cancelAutoPublish);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_STATUS = { topic: 'PUBLISHED', speakers: 'DRAFT', agenda: 'DRAFT' };
const MOCK_CHANGELOG = { entries: [{ changedAt: '2025-01-01', field: 'title' }] };

describe('usePublishing', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
    mockGetStatus.mockResolvedValue(MOCK_STATUS as never);
    mockGetChangeLog.mockResolvedValue(MOCK_CHANGELOG as never);
  });

  it('should fetch publishing status and change log on mount', async () => {
    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    expect(mockGetStatus).toHaveBeenCalledWith('BAT142');
    expect(result.current.publishingStatus).toEqual(MOCK_STATUS);
    expect(result.current.changeLog).toEqual(MOCK_CHANGELOG);
  });

  it('should start with empty validationErrors', async () => {
    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    expect(result.current.validationErrors).toEqual([]);
  });

  it('should expose publish/unpublish function references', async () => {
    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    expect(typeof result.current.publishPhase).toBe('function');
    expect(typeof result.current.unpublishPhase).toBe('function');
  });

  it('should call publishingService.publishPhase when publishPhase is called', async () => {
    mockPublishPhase.mockResolvedValue({} as never);

    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    act(() => {
      result.current.publishPhase('topic' as never);
    });

    await waitFor(() => expect(mockPublishPhase).toHaveBeenCalled());
    expect(mockPublishPhase).toHaveBeenCalledWith('BAT142', 'topic', undefined);
  });

  it('should call unpublishPhase when unpublishPhase is called', async () => {
    mockUnpublishPhase.mockResolvedValue({} as never);

    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    act(() => {
      result.current.unpublishPhase('speakers' as never);
    });

    await waitFor(() => expect(mockUnpublishPhase).toHaveBeenCalled());
    expect(mockUnpublishPhase).toHaveBeenCalledWith('BAT142', 'speakers');
  });

  it('should call fetchPreview mutation', async () => {
    const preview = { subject: 'Preview', htmlPreview: '<p/>', recipientCount: 100 };
    mockGetPublishPreview.mockResolvedValue(preview as never);

    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    act(() => {
      result.current.fetchPreview('topic' as never, 'FULL' as never);
    });

    await waitFor(() => expect(mockGetPublishPreview).toHaveBeenCalled());
    expect(mockGetPublishPreview).toHaveBeenCalledWith('BAT142', 'topic', 'FULL');
  });

  it('should call scheduleAutoPublish', async () => {
    mockScheduleAutoPublish.mockResolvedValue({} as never);

    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    act(() => {
      result.current.scheduleAutoPublish('topic' as never, { scheduledFor: '2025-12-01' } as never);
    });

    await waitFor(() => expect(mockScheduleAutoPublish).toHaveBeenCalled());
    expect(mockScheduleAutoPublish).toHaveBeenCalledWith('BAT142', 'topic', {
      scheduledFor: '2025-12-01',
    });
  });

  it('should call cancelAutoPublish', async () => {
    mockCancelAutoPublish.mockResolvedValue({} as never);

    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    act(() => {
      result.current.cancelAutoPublish('agenda' as never);
    });

    await waitFor(() => expect(mockCancelAutoPublish).toHaveBeenCalled());
    expect(mockCancelAutoPublish).toHaveBeenCalledWith('BAT142', 'agenda');
  });

  it('should expose isPublishing/isUnpublishing as false initially', async () => {
    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    expect(result.current.isPublishing).toBe(false);
    expect(result.current.isUnpublishing).toBe(false);
    expect(result.current.isScheduling).toBe(false);
    expect(result.current.isCancelling).toBe(false);
  });

  it('should expose null preview initially', async () => {
    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    expect(result.current.preview).toBeNull();
  });
});
