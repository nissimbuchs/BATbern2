/**
 * usePartnerMeetings Hook Tests (Story 8.3 — GREEN phase)
 *
 * TDD tests for Partner Meetings React Query hook
 * Story 8.3: Partner Meeting Coordination
 *
 * Test Scenarios:
 * - AC5: Meeting list fetching
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePartnerMeetings } from './usePartnerMeetings';
import React from 'react';

// Mock the API so tests don't make real HTTP calls
vi.mock('@/services/api/partnerMeetingsApi', () => ({
  getMeetings: vi.fn().mockResolvedValue([]),
}));

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

    expect(result.current.error).toBeNull();
    expect(result.current.data).toEqual([]);
  });

  // Test 2: should_fetchWithoutCompanyName_when_companyNameUndefined
  // Story 8.3: meetings are global (not per-company), hook always fetches
  it('should_fetchWithoutCompanyName_when_companyNameUndefined', async () => {
    const { result } = renderHook(() => usePartnerMeetings(undefined), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.data).toEqual([]);
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
      return Array.isArray(key) && key[0] === 'partnerMeetings' && key[1] === companyName;
    });

    expect(query).toBeDefined();
  });
});
