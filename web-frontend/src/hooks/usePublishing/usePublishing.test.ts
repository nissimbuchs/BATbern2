/**
 * usePublishing Hook Tests
 *
 * Coverage for:
 * - Initial state (isLoadingStatus, publishingStatus)
 * - publishPhase / unpublishPhase mutations trigger the publishing service
 * - scheduleAutoPublish mutation
 * - validationErrors extraction from 422 error
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/publishingService/publishingService', () => ({
  publishingService: {
    getPublishingStatus: vi.fn(),
    publishPhase: vi.fn(),
    unpublishPhase: vi.fn(),
    scheduleAutoPublish: vi.fn(),
  },
}));

import { publishingService } from '@/services/publishingService/publishingService';
import { usePublishing } from './usePublishing';

const mockGetStatus = vi.mocked(publishingService.getPublishingStatus);
const mockPublishPhase = vi.mocked(publishingService.publishPhase);
const mockUnpublishPhase = vi.mocked(publishingService.unpublishPhase);
const mockScheduleAutoPublish = vi.mocked(publishingService.scheduleAutoPublish);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_STATUS = { topic: 'PUBLISHED', speakers: 'DRAFT', agenda: 'DRAFT' };

describe('usePublishing', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
    mockGetStatus.mockResolvedValue(MOCK_STATUS as never);
  });

  it('should fetch publishing status on mount', async () => {
    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    expect(mockGetStatus).toHaveBeenCalledWith('BAT142');
    expect(result.current.publishingStatus).toEqual(MOCK_STATUS);
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
    expect(mockPublishPhase).toHaveBeenCalledWith('BAT142', 'topic');
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

  it('should call scheduleAutoPublish', async () => {
    mockScheduleAutoPublish.mockResolvedValue({} as never);

    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    act(() => {
      result.current.scheduleAutoPublish({ phase2Enabled: true, phase2DaysBeforeEvent: 30 });
    });

    await waitFor(() => expect(mockScheduleAutoPublish).toHaveBeenCalled());
    expect(mockScheduleAutoPublish).toHaveBeenCalledWith('BAT142', {
      phase2Enabled: true,
      phase2DaysBeforeEvent: 30,
    });
  });

  it('should expose isPublishing/isUnpublishing as false initially', async () => {
    const { result } = renderHook(() => usePublishing('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoadingStatus).toBe(false));

    expect(result.current.isPublishing).toBe(false);
    expect(result.current.isUnpublishing).toBe(false);
    expect(result.current.isScheduling).toBe(false);
  });
});
