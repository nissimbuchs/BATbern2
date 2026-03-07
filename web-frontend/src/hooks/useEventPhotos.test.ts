/**
 * useEventPhotos Hook Tests
 *
 * Coverage for:
 * - useEventPhotos: fetch list, enabled guard
 * - useUploadEventPhoto: 3-phase upload mutation + cache invalidation
 * - useDeleteEventPhoto: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    listEventPhotos: vi.fn(),
    requestEventPhotoUploadUrl: vi.fn(),
    uploadPhotoToS3: vi.fn(),
    confirmEventPhotoUpload: vi.fn(),
    deleteEventPhoto: vi.fn(),
  },
}));

import { eventApiClient } from '@/services/eventApiClient';
import { useEventPhotos, useUploadEventPhoto, useDeleteEventPhoto } from './useEventPhotos';

const mockListPhotos = vi.mocked(eventApiClient.listEventPhotos);
const mockRequestUploadUrl = vi.mocked(eventApiClient.requestEventPhotoUploadUrl);
const mockUploadToS3 = vi.mocked(eventApiClient.uploadPhotoToS3);
const mockConfirmUpload = vi.mocked(eventApiClient.confirmEventPhotoUpload);
const mockDeletePhoto = vi.mocked(eventApiClient.deleteEventPhoto);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_PHOTOS = [
  { photoId: 'p1', s3Key: 'events/BAT142/p1.jpg', url: 'https://cdn.x.com/p1.jpg' },
];

describe('useEventPhotos', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch photos for the given eventCode', async () => {
    mockListPhotos.mockResolvedValue(MOCK_PHOTOS as never);

    const { result } = renderHook(() => useEventPhotos('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockListPhotos).toHaveBeenCalledWith('BAT142');
    expect(result.current.data).toEqual(MOCK_PHOTOS);
  });

  it('should not fetch when eventCode is empty', () => {
    const { result } = renderHook(() => useEventPhotos(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockListPhotos).not.toHaveBeenCalled();
  });

  it('should not fetch when enabled=false', () => {
    const { result } = renderHook(() => useEventPhotos('BAT142', false), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(mockListPhotos).not.toHaveBeenCalled();
  });

  it('should set isError on failure', async () => {
    mockListPhotos.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useEventPhotos('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useUploadEventPhoto', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should execute 3-phase upload: requestUrl -> uploadToS3 -> confirm', async () => {
    mockRequestUploadUrl.mockResolvedValue({
      photoId: 'p1',
      uploadUrl: 'https://s3.x.com/put',
      s3Key: 'events/BAT142/p1.jpg',
    } as never);
    mockUploadToS3.mockResolvedValue(undefined as never);
    mockConfirmUpload.mockResolvedValue(MOCK_PHOTOS[0] as never);

    const { result } = renderHook(() => useUploadEventPhoto('BAT142'), { wrapper: wrapper(qc) });

    const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' });

    await act(async () => {
      await result.current.mutateAsync({
        file,
        request: { filename: 'photo.jpg' } as never,
      });
    });

    expect(mockRequestUploadUrl).toHaveBeenCalledWith('BAT142', { filename: 'photo.jpg' });
    expect(mockUploadToS3).toHaveBeenCalledWith('https://s3.x.com/put', file);
    expect(mockConfirmUpload).toHaveBeenCalledWith('BAT142', {
      photoId: 'p1',
      s3Key: 'events/BAT142/p1.jpg',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event-photos cache on success', async () => {
    mockRequestUploadUrl.mockResolvedValue({
      photoId: 'p2',
      uploadUrl: 'https://s3.x.com',
      s3Key: 'k',
    } as never);
    mockUploadToS3.mockResolvedValue(undefined as never);
    mockConfirmUpload.mockResolvedValue(MOCK_PHOTOS[0] as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUploadEventPhoto('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ file: new File([], 'f.jpg'), request: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event-photos', 'BAT142'] });
  });

  it('should set isError when upload URL request fails', async () => {
    mockRequestUploadUrl.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useUploadEventPhoto('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ file: new File([], 'f.jpg'), request: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useDeleteEventPhoto', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call deleteEventPhoto with eventCode and photoId', async () => {
    mockDeletePhoto.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useDeleteEventPhoto('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('p1');
    });

    expect(mockDeletePhoto).toHaveBeenCalledWith('BAT142', 'p1');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event-photos cache on success', async () => {
    mockDeletePhoto.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteEventPhoto('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('p1');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event-photos', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    mockDeletePhoto.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useDeleteEventPhoto('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('ghost').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
