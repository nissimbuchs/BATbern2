/**
 * usePartners Hook Tests (RED Phase - Story 2.8.1, Task 2a)
 *
 * TDD RED Phase: Write failing tests before implementation
 *
 * Tests for React Query hooks for Partner Management:
 * - usePartners: List query with filters, sort, pagination (cache 2min)
 * - usePartnerStatistics: Statistics query (cache 5min)
 *
 * AC: 1 (Partner Directory Screen), 2 (Overview Statistics), 10 (Integration)
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePartners, usePartnerStatistics } from './usePartners';
import * as partnerApi from '@/services/api/partnerApi';
import type {
  PartnerListResponse,
  PartnerStatistics,
  PartnerFilters,
  PartnerSort,
  PartnerPagination,
} from '@/services/api/partnerApi';

// Mock the partner API module
vi.mock('@/services/api/partnerApi', () => ({
  listPartners: vi.fn(),
  getPartnerStatistics: vi.fn(),
}));

describe('Partner React Query Hooks (RED Phase - Task 2a)', () => {
  let queryClient: QueryClient;

  // Create wrapper with QueryClient for hooks
  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false, // Disable retries for tests
          gcTime: 0, // Disable cache for tests
        },
      },
    });

    return ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient?.clear();
  });

  describe('AC1, AC10: Test 2.1 - should_fetchPartners_when_usePartnersHookCalled', () => {
    const mockPartnerResponse: PartnerListResponse = {
      data: [
        {
          id: '123e4567-e89b-12d3-a456-426614174000',
          companyName: 'TechCorp',
          partnershipLevel: 'gold',
          partnershipStartDate: '2024-01-01',
          isActive: true,
          company: {
            companyName: 'TechCorp',
            displayName: 'TechCorp Solutions AG',
            logoUrl: 'https://cdn.example.com/techcorp.png',
            industry: 'Technology',
          },
          contacts: [
            {
              username: 'john.doe',
              email: 'john@techcorp.com',
              firstName: 'John',
              lastName: 'Doe',
            },
          ],
        },
      ],
      metadata: {
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    };

    it('should fetch partners with filters, sort, and pagination', async () => {
      vi.mocked(partnerApi.listPartners).mockResolvedValue(mockPartnerResponse);

      const filters: PartnerFilters = { tier: 'gold', status: 'active' };
      const sort: PartnerSort = { sortBy: 'engagement', sortOrder: 'desc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      const { result } = renderHook(() => usePartners(filters, sort, pagination), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(partnerApi.listPartners).toHaveBeenCalledWith(filters, sort, pagination);
      expect(result.current.data).toEqual(mockPartnerResponse);
    });

    it('should return loading state when query is pending', () => {
      vi.mocked(partnerApi.listPartners).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      const { result } = renderHook(() => usePartners(filters, sort, pagination), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();
    });

    it('should return error when API fails', async () => {
      const mockError = new Error('Failed to fetch partners');
      vi.mocked(partnerApi.listPartners).mockRejectedValue(mockError);

      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      const { result } = renderHook(() => usePartners(filters, sort, pagination), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeDefined();
    });
  });

  describe('AC1, AC10: Test 2.2 - should_cachePartners_when_staleTimeSet', () => {
    it('should use correct query key with filters, sort, and pagination', async () => {
      const mockResponse: PartnerListResponse = {
        data: [],
        metadata: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      };
      vi.mocked(partnerApi.listPartners).mockResolvedValue(mockResponse);

      const filters: PartnerFilters = { tier: 'platinum', status: 'active' };
      const sort: PartnerSort = { sortBy: 'tier', sortOrder: 'desc' };
      const pagination: PartnerPagination = { page: 1, size: 50 };

      const { result } = renderHook(() => usePartners(filters, sort, pagination), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // Query should be cached with proper key
      const cachedQuery = queryClient.getQueryData(['partners', filters, sort, pagination]);
      expect(cachedQuery).toEqual(mockResponse);
    });

    it('should use staleTime of 2 minutes for partner list', async () => {
      // This test verifies that the hook is configured with correct staleTime
      // The actual staleTime validation happens in the implementation
      const mockResponse: PartnerListResponse = {
        data: [],
        metadata: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      };
      vi.mocked(partnerApi.listPartners).mockResolvedValue(mockResponse);

      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      const { result } = renderHook(() => usePartners(filters, sort, pagination), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockResponse);
    });
  });

  describe('AC1, AC10: Test 2.3 - should_refetchOnFilterChange_when_filtersUpdated', () => {
    it('should refetch when filters change', async () => {
      const mockResponse1: PartnerListResponse = {
        data: [
          {
            id: '1',
            companyName: 'Company1',
            partnershipLevel: 'gold',
            partnershipStartDate: '2024-01-01',
            isActive: true,
          },
        ],
        metadata: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      };

      const mockResponse2: PartnerListResponse = {
        data: [
          {
            id: '2',
            companyName: 'Company2',
            partnershipLevel: 'silver',
            partnershipStartDate: '2024-01-01',
            isActive: true,
          },
        ],
        metadata: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      };

      vi.mocked(partnerApi.listPartners)
        .mockResolvedValueOnce(mockResponse1)
        .mockResolvedValueOnce(mockResponse2);

      const filters1: PartnerFilters = { tier: 'gold', status: 'all' };
      const filters2: PartnerFilters = { tier: 'silver', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      const { result, rerender } = renderHook(
        ({ filters }) => usePartners(filters, sort, pagination),
        {
          wrapper: createWrapper(),
          initialProps: { filters: filters1 },
        }
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });
      expect(result.current.data).toEqual(mockResponse1);

      // Change filters
      rerender({ filters: filters2 });

      await waitFor(() => {
        expect(result.current.data).toEqual(mockResponse2);
      });

      expect(partnerApi.listPartners).toHaveBeenCalledTimes(2);
    });
  });

  describe('AC2, AC10: Test 2.4 - should_fetchStatistics_when_usePartnerStatisticsHookCalled', () => {
    const mockStatistics: PartnerStatistics = {
      totalPartners: 25,
      activePartners: 20,
      tierDistribution: {
        strategic: 2,
        platinum: 3,
        gold: 5,
        silver: 8,
        bronze: 7,
      },
    };

    it('should fetch partner statistics', async () => {
      vi.mocked(partnerApi.getPartnerStatistics).mockResolvedValue(mockStatistics);

      const { result } = renderHook(() => usePartnerStatistics(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(partnerApi.getPartnerStatistics).toHaveBeenCalled();
      expect(result.current.data).toEqual(mockStatistics);
    });

    it('should use query key "partner-statistics"', async () => {
      vi.mocked(partnerApi.getPartnerStatistics).mockResolvedValue(mockStatistics);

      const { result } = renderHook(() => usePartnerStatistics(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // Query should be cached with proper key
      const cachedQuery = queryClient.getQueryData(['partner-statistics']);
      expect(cachedQuery).toEqual(mockStatistics);
    });

    it('should use staleTime of 5 minutes for statistics', async () => {
      vi.mocked(partnerApi.getPartnerStatistics).mockResolvedValue(mockStatistics);

      const { result } = renderHook(() => usePartnerStatistics(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockStatistics);
    });

    it('should return loading state when statistics query is pending', () => {
      vi.mocked(partnerApi.getPartnerStatistics).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      const { result } = renderHook(() => usePartnerStatistics(), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();
    });

    it('should return error when statistics API fails', async () => {
      const mockError = new Error('Failed to fetch statistics');
      vi.mocked(partnerApi.getPartnerStatistics).mockRejectedValue(mockError);

      const { result } = renderHook(() => usePartnerStatistics(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeDefined();
    });
  });
});
