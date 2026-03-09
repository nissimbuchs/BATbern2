/**
 * useFeatureFlags Tests
 *
 * Coverage for:
 * - Returns default flags when data not yet loaded
 * - Returns fetched flags on success
 * - Falls back to defaults on error
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/apiClient', () => ({
  default: { get: vi.fn() },
}));

import apiClient from '@/services/api/apiClient';
import { useFeatureFlags } from './useFeatureFlags';

const mockGet = vi.mocked(apiClient.get);

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

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should return default flags while loading', () => {
    mockGet.mockImplementation(() => new Promise(() => {})); // never resolves

    const { result } = renderHook(() => useFeatureFlags(), { wrapper: wrapper(qc) });

    // Before data loads, returns defaults
    expect(result.current.aiContentEnabled).toBe(false);
  });

  it('should return fetched flags on success', async () => {
    mockGet.mockResolvedValue({ data: { aiContentEnabled: true } });

    const { result } = renderHook(() => useFeatureFlags(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.aiContentEnabled).toBe(true));

    expect(mockGet).toHaveBeenCalledWith('/public/settings/features');
  });

  it('should fall back to defaults on error', async () => {
    mockGet.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useFeatureFlags(), { wrapper: wrapper(qc) });

    // Wait for error state to settle
    await waitFor(() => {
      // After error, data should be undefined and we return defaults
      return result.current.aiContentEnabled === false;
    });

    expect(result.current.aiContentEnabled).toBe(false);
  });

  it('should return all expected flag keys', async () => {
    mockGet.mockResolvedValue({ data: { aiContentEnabled: false } });

    const { result } = renderHook(() => useFeatureFlags(), { wrapper: wrapper(qc) });

    await waitFor(() => mockGet.mock.calls.length > 0);

    expect('aiContentEnabled' in result.current).toBe(true);
  });
});
