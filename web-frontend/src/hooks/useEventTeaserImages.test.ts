/**
 * useEventTeaserImages Tests
 *
 * Coverage for:
 * - useUploadTeaserImage: 3-phase S3 upload mutation
 * - useDeleteTeaserImage: delete mutation + cache invalidation
 * - useUpdateTeaserImagePosition: update position mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';
import {
  useUploadTeaserImage,
  useDeleteTeaserImage,
  useUpdateTeaserImagePosition,
} from './useEventTeaserImages';

// HTTP is stubbed at the boundaries: the API on apiClient, the presigned S3 PUT
// on bare axios. The real eventApiClient runs.
const BASE = '/events/BAT142/teaser-images';
const S3_URL = 'https://s3.example.com/put-url';
let api: MockAdapter;
let s3: MockAdapter;
beforeEach(() => {
  api = new MockAdapter(apiClient);
  s3 = new MockAdapter(axios);
});
afterEach(() => {
  api.restore();
  s3.restore();
});

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
    api.onPost(`${BASE}/upload-url`).reply(200, {
      uploadUrl: S3_URL,
      s3Key: 'events/BAT142/teaser-1.jpg',
    });
    s3.onPut(S3_URL).reply(200);
    api.onPost(`${BASE}/confirm`).reply(200, MOCK_IMAGE);

    const { result } = renderHook(() => useUploadTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    const file = new File(['img'], 'teaser.jpg', { type: 'image/jpeg' });

    await act(async () => {
      await result.current.mutateAsync({
        file,
        request: { filename: 'teaser.jpg' } as never,
      });
    });

    expect(api.history.post.map((r) => r.url)).toEqual([`${BASE}/upload-url`, `${BASE}/confirm`]);
    expect(JSON.parse(api.history.post[0].data)).toEqual({ filename: 'teaser.jpg' });
    expect(s3.history.put).toHaveLength(1);
    expect(s3.history.put[0].url).toBe(S3_URL);
    expect(s3.history.put[0].data).toBe(file);
    expect(s3.history.put[0].headers?.['Content-Type']).toBe('image/jpeg');
    expect(JSON.parse(api.history.post[1].data)).toEqual({
      s3Key: 'events/BAT142/teaser-1.jpg',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    api.onPost(`${BASE}/upload-url`).reply(200, { uploadUrl: S3_URL, s3Key: 'k' });
    s3.onPut(S3_URL).reply(200);
    api.onPost(`${BASE}/confirm`).reply(200, MOCK_IMAGE);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUploadTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ file: new File([], 'f.jpg'), request: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError when upload URL request fails', async () => {
    api.onPost(`${BASE}/upload-url`).reply(403);

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
    api.onDelete(`${BASE}/img-1`).reply(204);

    const { result } = renderHook(() => useDeleteTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('img-1');
    });

    expect(api.history.delete.map((r) => r.url)).toEqual([`${BASE}/img-1`]);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    api.onDelete(`${BASE}/img-1`).reply(204);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteTeaserImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('img-1');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    api.onDelete(`${BASE}/ghost`).reply(404);

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
    api.onPatch(`${BASE}/img-1`).reply(200, MOCK_IMAGE);

    const { result } = renderHook(() => useUpdateTeaserImagePosition('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({
        imageId: 'img-1',
        request: { position: 2 } as never,
      });
    });

    expect(api.history.patch.map((r) => r.url)).toEqual([`${BASE}/img-1`]);
    expect(JSON.parse(api.history.patch[0].data)).toEqual({ position: 2 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    api.onPatch(`${BASE}/img-1`).reply(200, MOCK_IMAGE);
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
    api.onPatch(`${BASE}/img-1`).reply(409);

    const { result } = renderHook(() => useUpdateTeaserImagePosition('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({ imageId: 'img-1', request: {} as never }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
