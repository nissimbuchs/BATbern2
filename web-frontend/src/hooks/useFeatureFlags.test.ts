/**
 * useFeatureFlags Tests
 *
 * Coverage for:
 * - Returns default flags when data not yet loaded
 * - Returns fetched flags on success
 * - Falls back to defaults on error
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import MockAdapter from 'axios-mock-adapter';

import apiClient from '@/services/api/apiClient';
import { useFeatureFlags } from './useFeatureFlags';

const FLAGS_URL = '/public/settings/features';

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

describe('useFeatureFlags', () => {
  let qc: QueryClient;
  let http: MockAdapter;

  beforeEach(() => {
    qc = createQC();
    http = new MockAdapter(apiClient);
  });

  afterEach(() => {
    http.restore();
  });

  it('should return default flags while loading', () => {
    http.onGet(FLAGS_URL).reply(() => new Promise(() => {})); // never resolves

    const { result } = renderHook(() => useFeatureFlags(), { wrapper: wrapper(qc) });

    // Before data loads, returns defaults
    expect(result.current.aiContentEnabled).toBe(false);
  });

  it('should return fetched flags on success', async () => {
    http.onGet(FLAGS_URL).reply(200, { aiContentEnabled: true });

    const { result } = renderHook(() => useFeatureFlags(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.aiContentEnabled).toBe(true));

    expect(http.history.get.map((r) => r.url)).toEqual([FLAGS_URL]);
  });

  it('should fall back to defaults on error', async () => {
    http.onGet(FLAGS_URL).reply(404);

    const { result } = renderHook(() => useFeatureFlags(), { wrapper: wrapper(qc) });

    // Wait for error state to settle
    await waitFor(() => {
      // After error, data should be undefined and we return defaults
      return result.current.aiContentEnabled === false;
    });

    expect(result.current.aiContentEnabled).toBe(false);
  });

  it('should return all expected flag keys', async () => {
    http.onGet(FLAGS_URL).reply(200, { aiContentEnabled: false });

    const { result } = renderHook(() => useFeatureFlags(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(http.history.get.length).toBeGreaterThan(0));

    expect('aiContentEnabled' in result.current).toBe(true);
  });
});
