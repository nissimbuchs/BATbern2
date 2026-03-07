/**
 * useEventTeaserImages Tests
 *
 * Coverage for:
 * - useUploadTeaserImage: 3-phase S3 upload mutation
 * - useDeleteTeaserImage: delete mutation + cache invalidation
 * - useUpdateTeaserImagePosition: update position mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    requestTeaserImageUploadUrl: vi.fn(),
    confirmTeaserImageUpload: vi.fn(),
    deleteTeaserImage: vi.fn(),
    updateTeaserImage: vi.fn(),
  },
}));

vi.mock('axios', () => ({
  default: { put: vi.fn() },
}));

import { eventApiClient } from '@/services/eventApiClient';
import axios from 'axios';
import {
  useUploadTeaserImage,
  useDeleteTeaserImage,
  useUpdateTeaserImagePosition,
} from './useEventTeaserImages';

const mockRequestUploadUrl = vi.mocked(eventApiClient.requestTeaserImageUploadUrl);
const mockConfirmUpload = vi.mocked(eventApiClient.confirmTeaserImageUpload);
const mockDeleteTeaserImage = vi.mocked(eventApiClient.deleteTeaserImage);
const mockUpdateTeaserImage = vi.mocked(eventApiClient.updateTeaserImage);
const mockAxiosPut = vi.mocked(axios.put);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_IMAGE = { id: 'img-1', s3Key: 'events/BAT142/teaser-1.jpg', position: 0 };

// ── useUploadTeaserImage ──────────────────────────────────────────────────────

describe('useUploadTeaserImage', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should execute 3-phase upload: get URL → PUT to S3 → confirm', async () => {
    mockRequestUploadUrl.mockResolvedValue({
      uploadUrl: 'https://s3.example.com/put-url',
      s3Key: 'events/BAT142/teaser-1.jpg',
    } as never);
    mockAxiosPut.mockResolvedValue({});
    mockConfirmUpload.mockResolvedValue(MOCK_IMAGE as never);

    const { result } = renderHook(() => useUploadTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    const file = new File(['img'], 'teaser.jpg', { type: 'image/jpeg' });

    await act(async () => {
      await result.current.mutateAsync({
        file,
        request: { filename: 'teaser.jpg' } as never,
      });
    });

    expect(mockRequestUploadUrl).toHaveBeenCalledWith('BAT142', { filename: 'teaser.jpg' });
    expect(mockAxiosPut).toHaveBeenCalledWith(
      'https://s3.example.com/put-url',
      file,
      expect.objectContaining({ headers: { 'Content-Type': 'image/jpeg' } })
    );
    expect(mockConfirmUpload).toHaveBeenCalledWith('BAT142', {
      s3Key: 'events/BAT142/teaser-1.jpg',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    mockRequestUploadUrl.mockResolvedValue({ uploadUrl: 'https://s3.x.com', s3Key: 'k' } as never);
    mockAxiosPut.mockResolvedValue({});
    mockConfirmUpload.mockResolvedValue(MOCK_IMAGE as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUploadTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ file: new File([], 'f.jpg'), request: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError when upload URL request fails', async () => {
    mockRequestUploadUrl.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useUploadTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ file: new File([], 'f.jpg'), request: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeleteTeaserImage ──────────────────────────────────────────────────────

describe('useDeleteTeaserImage', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call deleteTeaserImage with eventCode and imageId', async () => {
    mockDeleteTeaserImage.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useDeleteTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('img-1');
    });

    expect(mockDeleteTeaserImage).toHaveBeenCalledWith('BAT142', 'img-1');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    mockDeleteTeaserImage.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('img-1');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    mockDeleteTeaserImage.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useDeleteTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('ghost').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateTeaserImagePosition ──────────────────────────────────────────────

describe('useUpdateTeaserImagePosition', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateTeaserImage with imageId and request', async () => {
    mockUpdateTeaserImage.mockResolvedValue(MOCK_IMAGE as never);

    const { result } = renderHook(() => useUpdateTeaserImagePosition('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({
        imageId: 'img-1',
        request: { position: 2 } as never,
      });
    });

    expect(mockUpdateTeaserImage).toHaveBeenCalledWith('BAT142', 'img-1', { position: 2 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    mockUpdateTeaserImage.mockResolvedValue(MOCK_IMAGE as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateTeaserImagePosition('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({ imageId: 'img-1', request: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    mockUpdateTeaserImage.mockRejectedValue(new Error('Conflict'));

    const { result } = renderHook(() => useUpdateTeaserImagePosition('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({ imageId: 'img-1', request: {} as never }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
