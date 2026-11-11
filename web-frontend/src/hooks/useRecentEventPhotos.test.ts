/**
 * useRecentEventPhotos (Story 10.21) on the MSW harness (Story 4.10b, Epic 17 test fidelity): the
 * real eventApiClient and apiClient run, and the request is checked against event-media-api, so
 * the old `/events/recent-photos` path would fail the test.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import { useRecentEventPhotos } from './useRecentEventPhotos';

let qc: QueryClient;

const wrapper = ({ children }: { children: React.ReactNode }) =>
  React.createElement(QueryClientProvider, { client: qc }, children);

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
});

describe('useRecentEventPhotos', () => {
  it('should_loadThePublicSample_withDefaultParams', async () => {
    const { result } = renderHook(() => useRecentEventPhotos(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const [call] = api.calls('getRecentEventPhotos');
    expect(call.url.pathname).toMatch(/\/public\/event-photos$/);
    expect(call.query.get('limit')).toBe('20');
    expect(call.query.get('lastNEvents')).toBe('5');
    // The spec example: two photos of past events.
    expect(result.current.data?.map((p) => p.eventCode)).toEqual(['BATbern58', 'BATbern56']);
  });

  it('should_passCustomLimitAndWindow', async () => {
    api.on('getRecentEventPhotos', () => []);

    const { result } = renderHook(() => useRecentEventPhotos(10, 3), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const [call] = api.calls('getRecentEventPhotos');
    expect(call.query.get('limit')).toBe('10');
    expect(call.query.get('lastNEvents')).toBe('3');
  });

  it('should_setIsError_when_fetchFails', async () => {
    api.on('getRecentEventPhotos', () => HttpResponse.error());

    const { result } = renderHook(() => useRecentEventPhotos(), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
