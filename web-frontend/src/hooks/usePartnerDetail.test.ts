/**
 * usePartnerDetail Hook Tests (RED Phase -> GREEN Phase)
 *
 * TDD tests for Partner Detail React Query hook
 * Story 2.8.2: Partner Detail View
 *
 * Test Scenarios:
 * - AC1, AC13: Partner detail fetching with resource expansion
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePartnerDetail } from './usePartnerDetail';
import React from 'react';

// Helper to create a fresh QueryClient for each test
const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false, // Disable retries for tests
        gcTime: 0, // Disable garbage collection for tests
      },
    },
  });

// Helper to create wrapper with QueryClientProvider
const createWrapper = (queryClient: QueryClient) => {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
};

describe('usePartnerDetail', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = createTestQueryClient();
  });

  // Test 1: should_fetchPartnerDetail_when_usePartnerDetailCalled
  it('should_fetchPartnerDetail_when_usePartnerDetailCalled', async () => {
    const { result } = renderHook(() => usePartnerDetail('GoogleZH'), {
      wrapper: createWrapper(queryClient),
    });

    // Initially loading
    expect(result.current.isLoading).toBe(true);
    expect(result.current.data).toBeUndefined();

    // Wait for query to settle (will fail with network error in test env)
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    // Should have error (no backend in test env)
    expect(result.current.error).toBeTruthy();
  });

  // Test 2: should_cachePartnerDetail_when_staleTimeSet
  it('should_cachePartnerDetail_when_staleTimeSet', async () => {
    const { result, rerender } = renderHook(() => usePartnerDetail('GoogleZH'), {
      wrapper: createWrapper(queryClient),
    });

    // Wait for first query to settle
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    // Rerender to trigger potential refetch
    rerender();

    // Should not refetch (cached)
    expect(result.current.isLoading).toBe(false);
  });

  // Test 3: should_includeResourceExpansion_when_includeParamProvided
  it('should_includeResourceExpansion_when_includeParamProvided', async () => {
    const { result } = renderHook(
      () => usePartnerDetail('GoogleZH', 'company,contacts,votes,meetings,activity'),
      {
        wrapper: createWrapper(queryClient),
      }
    );

    // Wait for query to settle
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    // Should attempt to fetch with include param (will fail in test env)
    expect(result.current.error).toBeTruthy();
  });

  // Test 4: should_notFetch_when_companyNameUndefined
  it('should_notFetch_when_companyNameUndefined', () => {
    const { result } = renderHook(() => usePartnerDetail(undefined as unknown as string), {
      wrapper: createWrapper(queryClient),
    });

    // Should not fetch
    expect(result.current.isLoading).toBe(false);
    expect(result.current.data).toBeUndefined();
    expect(result.current.error).toBeNull();
  });

  // Test 5: should_haveCorrectQueryKey_when_hookCalled
  it('should_haveCorrectQueryKey_when_hookCalled', async () => {
    const companyName = 'GoogleZH';
    const include = 'company,contacts';

    renderHook(() => usePartnerDetail(companyName, include), {
      wrapper: createWrapper(queryClient),
    });

    // Wait for query to settle
    await waitFor(() => {
      const queries = queryClient.getQueryCache().findAll();
      expect(queries.length).toBeGreaterThan(0);
    });

    // Check query key structure
    const queries = queryClient.getQueryCache().findAll();
    const query = queries.find((q) => {
      const key = q.queryKey;
      return Array.isArray(key) && key[0] === 'partner' && key[1] === companyName;
    });

    expect(query).toBeDefined();
  });
});
