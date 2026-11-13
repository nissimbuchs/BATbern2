/**
 * useEventTeaserImages Tests, on the MSW harness (the real eventApiClient and uploadService).
 *
 * Coverage for:
 * - useUploadTeaserImage: upload contract `teaser-image` with the target eventCode, `_global` for
 *   the global set (Story 17.11c)
 * - useDeleteTeaserImage: delete mutation + cache invalidation
 * - useUpdateTeaserImagePosition: update position mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import { server } from '@/test/msw/server';
import {
  useUploadTeaserImage,
  useDeleteTeaserImage,
  useUpdateTeaserImagePosition,
} from './useEventTeaserImages';

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const UPLOAD_ID = '6c1f0e4a-2b7d-4c55-9b0e-3a8f1d2e4b6c';
const IMAGE = {
  id: '3f2b8c1e-5d4a-4e6b-9a7c-1b2d3e4f5a6b',
  imageUrl: `https://cdn.batbern.ch/events/BATbern58/teaser/${UPLOAD_ID}.jpg`,
  displayOrder: 0,
  presentationPosition: 'AFTER_TOPIC_REVEAL' as const,
};

const S3_URL = 'https://bucket.example/teaser-image';

const problem = (status: number, message: string) =>
  HttpResponse.json(
    { error: 'ERROR', errorCode: String(status), message, timestamp: '2026-11-13T10:00:00Z' },
    { status }
  );

// ── useUploadTeaserImage ──────────────────────────────────────────────────────

describe('useUploadTeaserImage', () => {
  let qc: QueryClient;
  let s3Puts: string[];

  beforeEach(() => {
    qc = createQC();
    s3Puts = [];
    server.use(
      http.put(S3_URL, ({ request }) => {
        s3Puts.push(request.headers.get('Content-Type') ?? '');
        return new HttpResponse(null, { status: 200 });
      })
    );
    api.on(
      'createFileUpload',
      () => ({
        uploadId: UPLOAD_ID,
        uploadUrl: S3_URL,
        expiresAt: '2026-11-13T10:15:00Z',
        requiredHeaders: { 'Content-Type': 'image/jpeg' },
      }),
      { status: 201 }
    );
    api.on('confirmFileUpload', () => ({
      uploadId: UPLOAD_ID,
      purpose: 'teaser-image' as const,
      status: 'ASSOCIATED' as const,
      fileUrl: IMAGE.imageUrl,
    }));
  });

  it('reserves for the event, PUTs to S3 and confirms with the same target', async () => {
    const { result } = renderHook(() => useUploadTeaserImage('BATbern58'), {
      wrapper: wrapper(qc),
    });
    const file = new File(['img'], 'teaser.jpg', { type: 'image/jpeg' });

    let confirmed: unknown;
    await act(async () => {
      confirmed = await result.current.mutateAsync({ file });
    });

    expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'teaser-image' });
    expect(api.calls('createFileUpload')[0].body).toEqual({
      fileName: 'teaser.jpg',
      contentType: 'image/jpeg',
      size: 3,
      target: { eventCode: 'BATbern58' },
    });
    expect(s3Puts).toEqual(['image/jpeg']);
    expect(api.calls('confirmFileUpload')[0].params).toEqual({
      purpose: 'teaser-image',
      uploadId: UPLOAD_ID,
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({ target: { eventCode: 'BATbern58' } });
    expect(confirmed).toEqual({ fileUrl: IMAGE.imageUrl });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('targets the global set (_global) without an event code', async () => {
    const { result } = renderHook(() => useUploadTeaserImage(null), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        file: new File(['<svg/>'], 'logo-wall.svg', { type: 'image/svg+xml' }),
      });
    });

    expect(api.calls('createFileUpload')[0].body).toMatchObject({
      fileName: 'logo-wall.svg',
      contentType: 'image/svg+xml',
      target: { eventCode: '_global' },
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({ target: { eventCode: '_global' } });
  });

  it('sends a file name with the extension of its type when the name has none', async () => {
    const { result } = renderHook(() => useUploadTeaserImage('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({
        file: new File(['img'], 'Sponsoren', { type: 'image/svg+xml' }),
      });
    });

    expect(api.calls('createFileUpload')[0].body).toMatchObject({ fileName: 'Sponsoren.svg' });
  });

  it('should invalidate the event cache on success', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUploadTeaserImage('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({ file: new File(['x'], 'f.jpg', { type: 'image/jpeg' }) });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern58'] });
  });

  it('should invalidate the global list on success for the global set', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUploadTeaserImage(null), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ file: new File(['x'], 'f.png', { type: 'image/png' }) });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['global-teaser-images'] });
  });

  it('fails with the server message and does not upload when the set is full', async () => {
    api.on('createFileUpload', () =>
      problem(409, 'Maximum of 10 teaser images reached for event: BATbern58')
    );
    const { result } = renderHook(() => useUploadTeaserImage('BATbern58'), {
      wrapper: wrapper(qc),
    });

    let failure: unknown;
    await act(async () => {
      await result.current
        .mutateAsync({ file: new File(['x'], 'f.jpg', { type: 'image/jpeg' }) })
        .catch((e: unknown) => {
          failure = e;
        });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect((failure as Error).message).toBe(
      'Maximum of 10 teaser images reached for event: BATbern58'
    );
    expect(s3Puts).toEqual([]);
    expect(api.calls('confirmFileUpload')).toHaveLength(0);
  });
});

// ── useDeleteTeaserImage ──────────────────────────────────────────────────────

describe('useDeleteTeaserImage', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
  });

  it('should call deleteTeaserImage with eventCode and imageId', async () => {
    api.on('deleteTeaserImage', () => new HttpResponse(null, { status: 204 }));

    const { result } = renderHook(() => useDeleteTeaserImage('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync(IMAGE.id);
    });

    expect(api.calls('deleteTeaserImage')[0].params).toEqual({
      eventCode: 'BATbern58',
      imageId: IMAGE.id,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should delete from the global set (_global) without an event code', async () => {
    api.on('deleteTeaserImage', () => new HttpResponse(null, { status: 204 }));

    const { result } = renderHook(() => useDeleteTeaserImage(null), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync(IMAGE.id);
    });

    expect(api.calls('deleteTeaserImage')[0].params).toEqual({
      eventCode: '_global',
      imageId: IMAGE.id,
    });
  });

  it('should invalidate event cache on success', async () => {
    api.on('deleteTeaserImage', () => new HttpResponse(null, { status: 204 }));
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteTeaserImage('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync(IMAGE.id);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern58'] });
  });

  it('should set isError on failure', async () => {
    api.on('deleteTeaserImage', () => problem(404, 'Teaser image not found'));

    const { result } = renderHook(() => useDeleteTeaserImage('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync(IMAGE.id).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateTeaserImagePosition ──────────────────────────────────────────────

describe('useUpdateTeaserImagePosition', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
  });

  it('should call updateTeaserImage with imageId and request', async () => {
    api.on('updateTeaserImage', () => ({
      ...IMAGE,
      presentationPosition: 'AFTER_WELCOME' as const,
    }));

    const { result } = renderHook(() => useUpdateTeaserImagePosition('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({
        imageId: IMAGE.id,
        request: { presentationPosition: 'AFTER_WELCOME' },
      });
    });

    expect(api.calls('updateTeaserImage')[0].params).toEqual({
      eventCode: 'BATbern58',
      imageId: IMAGE.id,
    });
    expect(api.calls('updateTeaserImage')[0].body).toEqual({
      presentationPosition: 'AFTER_WELCOME',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    api.on('updateTeaserImage', () => IMAGE);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateTeaserImagePosition('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({
        imageId: IMAGE.id,
        request: { presentationPosition: 'AFTER_TOPIC_REVEAL' },
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern58'] });
  });

  it('should set isError on failure', async () => {
    api.on('updateTeaserImage', () => problem(404, 'Teaser image not found'));

    const { result } = renderHook(() => useUpdateTeaserImagePosition('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current
        .mutateAsync({ imageId: IMAGE.id, request: { presentationPosition: 'AFTER_WELCOME' } })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
