/**
 * useEventPhotos Hook Tests, on the MSW harness (the real eventApiClient and uploadService).
 *
 * Coverage for:
 * - useEventPhotos: fetch list, enabled guard
 * - useUploadEventPhoto: upload contract `event-photo` with the target eventCode (Story 17.11b)
 * - useDeleteEventPhoto: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import { server } from '@/test/msw/server';
import {
  photoFileName,
  useEventPhotos,
  useUploadEventPhoto,
  useDeleteEventPhoto,
} from './useEventPhotos';

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const PHOTO = {
  id: '51ae2297-44ab-4364-9fd7-36c4dd95daa8',
  eventCode: 'BATbern58',
  displayUrl:
    'https://cdn.batbern.ch/events/BATbern58/photos/51ae2297-44ab-4364-9fd7-36c4dd95daa8.jpg',
  filename: 'photo.jpg',
  uploadedBy: 'nina.organizer',
  uploadedAt: '2026-06-12T09:01:58Z',
  sortOrder: 0,
};

const S3_URL = 'https://bucket.example/event-photo';

const problem = (status: number, message: string) =>
  HttpResponse.json(
    { error: 'ERROR', errorCode: String(status), message, timestamp: '2026-11-13T10:00:00Z' },
    { status }
  );

describe('useEventPhotos', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
  });

  it('should fetch photos for the given eventCode', async () => {
    api.on('listEventPhotos', () => [PHOTO]);

    const { result } = renderHook(() => useEventPhotos('BATbern58'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(api.calls('listEventPhotos')[0].params).toEqual({ eventCode: 'BATbern58' });
    expect(result.current.data).toEqual([PHOTO]);
  });

  it('should not fetch when eventCode is empty', () => {
    const { result } = renderHook(() => useEventPhotos(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(api.calls('listEventPhotos')).toHaveLength(0);
  });

  it('should not fetch when enabled=false', () => {
    const { result } = renderHook(() => useEventPhotos('BATbern58', false), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(api.calls('listEventPhotos')).toHaveLength(0);
  });

  it('should set isError on failure', async () => {
    api.on('listEventPhotos', () => problem(500, 'boom'));

    const { result } = renderHook(() => useEventPhotos('BATbern58'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useUploadEventPhoto', () => {
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
        uploadId: PHOTO.id,
        uploadUrl: S3_URL,
        expiresAt: '2026-11-13T10:15:00Z',
        requiredHeaders: { 'Content-Type': 'image/jpeg' },
      }),
      { status: 201 }
    );
    api.on('confirmFileUpload', () => ({
      uploadId: PHOTO.id,
      purpose: 'event-photo' as const,
      status: 'ASSOCIATED' as const,
      fileUrl: PHOTO.displayUrl,
    }));
  });

  it('reserves for the event, PUTs to S3 and confirms with the same target', async () => {
    const { result } = renderHook(() => useUploadEventPhoto('BATbern58'), {
      wrapper: wrapper(qc),
    });
    const file = new File(['img'], 'Apero.jpg', { type: 'image/jpeg' });

    let confirmed: unknown;
    await act(async () => {
      confirmed = await result.current.mutateAsync({ file });
    });

    expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'event-photo' });
    expect(api.calls('createFileUpload')[0].body).toEqual({
      fileName: 'Apero.jpg',
      contentType: 'image/jpeg',
      size: 3,
      target: { eventCode: 'BATbern58' },
    });
    expect(s3Puts).toEqual(['image/jpeg']);
    expect(api.calls('confirmFileUpload')[0].params).toEqual({
      purpose: 'event-photo',
      uploadId: PHOTO.id,
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({ target: { eventCode: 'BATbern58' } });
    expect(confirmed).toEqual({ fileUrl: PHOTO.displayUrl });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('sends a file name with the extension of its type when the name has none', async () => {
    const { result } = renderHook(() => useUploadEventPhoto('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({
        file: new File(['img'], 'IMG_0042', { type: 'image/webp' }),
      });
    });

    expect(api.calls('createFileUpload')[0].body).toMatchObject({
      fileName: 'IMG_0042.webp',
      contentType: 'image/webp',
    });
  });

  it('should invalidate event-photos cache on success', async () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUploadEventPhoto('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({ file: new File(['x'], 'f.jpg', { type: 'image/jpeg' }) });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event-photos', 'BATbern58'] });
  });

  it('should set isError and not confirm when the reservation is refused', async () => {
    api.on('createFileUpload', () => problem(403, 'Only organizers upload event photos'));
    const { result } = renderHook(() => useUploadEventPhoto('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current
        .mutateAsync({ file: new File(['x'], 'f.jpg', { type: 'image/jpeg' }) })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(s3Puts).toEqual([]);
    expect(api.calls('confirmFileUpload')).toHaveLength(0);
  });
});

describe('photoFileName', () => {
  const file = (name: string, type: string) => new File(['x'], name, { type });

  it('keeps a name with an accepted extension', () => {
    expect(photoFileName(file('Apéro.JPEG', 'image/jpeg'))).toBe('Apéro.JPEG');
    expect(photoFileName(file('a.webp', 'image/webp'))).toBe('a.webp');
  });

  it('adds the extension of the type to a name without an accepted one', () => {
    expect(photoFileName(file('IMG_0042', 'image/jpeg'))).toBe('IMG_0042.jpg');
    expect(photoFileName(file('scan.heic', 'image/png'))).toBe('scan.heic.png');
    expect(photoFileName(file('', 'image/png'))).toBe('photo.png');
  });

  it('leaves the name alone when the type is not a photo type (the server answers 400)', () => {
    expect(photoFileName(file('anim.gif', 'image/gif'))).toBe('anim.gif');
  });
});

describe('useDeleteEventPhoto', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
  });

  it('should call deleteEventPhoto with eventCode and photoId', async () => {
    api.on('deleteEventPhoto', () => new HttpResponse(null, { status: 204 }));
    const { result } = renderHook(() => useDeleteEventPhoto('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync(PHOTO.id);
    });

    expect(api.calls('deleteEventPhoto')[0].params).toEqual({
      eventCode: 'BATbern58',
      photoId: PHOTO.id,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event-photos cache on success', async () => {
    api.on('deleteEventPhoto', () => new HttpResponse(null, { status: 204 }));
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useDeleteEventPhoto('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync(PHOTO.id);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event-photos', 'BATbern58'] });
  });

  it('should set isError on failure', async () => {
    api.on('deleteEventPhoto', () => problem(404, 'Photo not found'));
    const { result } = renderHook(() => useDeleteEventPhoto('BATbern58'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync('00000000-0000-4000-8000-000000000000').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
