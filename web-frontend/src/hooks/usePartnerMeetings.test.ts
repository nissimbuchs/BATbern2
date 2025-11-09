/**
 * usePartnerMeetings Hook Tests (RED Phase -> GREEN Phase)
 *
 * TDD tests for Partner Meetings React Query hook
 * Story 2.8.2: Partner Detail View
 *
 * Test Scenarios:
 * - AC5, AC13: Partner meetings fetching for Meetings tab
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePartnerMeetings } from './usePartnerMeetings';
import React from 'react';

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

const createWrapper = (queryClient: QueryClient) => {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
};

describe('usePartnerMeetings', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = createTestQueryClient();
  });

  // Test 1: should_fetchMeetings_when_usePartnerMeetingsCalled
  it('should_fetchMeetings_when_usePartnerMeetingsCalled', async () => {
    const { result } = renderHook(() => usePartnerMeetings('GoogleZH'), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBeTruthy();
  });

  // Test 2: should_notFetch_when_companyNameUndefined
  it('should_notFetch_when_companyNameUndefined', () => {
    const { result } = renderHook(() => usePartnerMeetings(undefined as unknown as string), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.data).toBeUndefined();
  });

  // Test 3: should_haveCorrectQueryKey_when_hookCalled
  it('should_haveCorrectQueryKey_when_hookCalled', async () => {
    const companyName = 'GoogleZH';

    renderHook(() => usePartnerMeetings(companyName), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => {
      const queries = queryClient.getQueryCache().findAll();
      expect(queries.length).toBeGreaterThan(0);
    });

    const queries = queryClient.getQueryCache().findAll();
    const query = queries.find((q) => {
      const key = q.queryKey;
      return (
        Array.isArray(key) &&
        key[0] === 'partner' &&
        key[1] === companyName &&
        key[2] === 'meetings'
      );
    });

    expect(query).toBeDefined();
  });
});
