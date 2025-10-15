/**
 * useCompanySearch Hook Tests (RED Phase - TDD)
 *
 * Tests for React Query hook that performs company autocomplete search
 * AC 2 (Search & Filters), AC 10 (Performance <500ms), AC 15 (Service Integration)
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCompanySearch } from '@/hooks/useCompanySearch/useCompanySearch';
import { companyApiClient } from '@/services/api/companyApi';
import type { CompanyListResponse } from '@/types/company.types';

// Mock the API client
vi.mock('../../services/companyApiClient', () => ({
  companyApiClient: {
    searchCompanies: vi.fn(),
  },
}));

describe('useCompanySearch Hook', () => {
  let queryClient: QueryClient;

  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
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

  describe('Basic Search Functionality (AC 2)', () => {
    it('should_searchCompanies_when_queryProvided', async () => {
      // Arrange
      const mockResults: CompanyListResponse = {
        data: [
          {
            id: '1',
            name: 'Tech Company AG',
            industry: 'Technology',
            location: { city: 'Zurich', country: 'Switzerland' },
            isPartner: false,
            isVerified: true,
            associatedUserCount: 3,
          },
          {
            id: '2',
            name: 'Tech Solutions GmbH',
            industry: 'Technology',
            location: { city: 'Bern', country: 'Switzerland' },
            isPartner: true,
            isVerified: true,
            associatedUserCount: 7,
          },
        ],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 2,
          itemsPerPage: 20,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('Tech'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.searchCompanies).toHaveBeenCalledWith('Tech', {
        page: 1,
        limit: 10,
      });
      expect(result.current.data).toEqual(mockResults);
      expect(result.current.data?.data).toHaveLength(2);
    });

    it('should_notSearch_when_queryTooShort', () => {
      // Arrange
      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue({
        data: [],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 0,
          itemsPerPage: 20,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      });

      // Act
      const { result } = renderHook(() => useCompanySearch('Te'), {
        wrapper: createWrapper(),
      });

      // Assert - Query disabled when less than 3 characters
      expect(result.current.isLoading).toBe(false);
      expect(result.current.isFetching).toBe(false);
      expect(companyApiClient.searchCompanies).not.toHaveBeenCalled();
    });

    it('should_notSearch_when_queryEmpty', () => {
      // Arrange
      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue({
        data: [],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 0,
          itemsPerPage: 20,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      });

      // Act
      const { result } = renderHook(() => useCompanySearch(''), {
        wrapper: createWrapper(),
      });

      // Assert
      expect(result.current.isLoading).toBe(false);
      expect(companyApiClient.searchCompanies).not.toHaveBeenCalled();
    });

    it('should_search_when_queryExactly3Characters', async () => {
      // Arrange
      const mockResults: CompanyListResponse = {
        data: [
          {
            id: '1',
            name: 'IBM Switzerland',
            industry: 'Technology',
            location: { city: 'Zurich', country: 'Switzerland' },
            isPartner: false,
            isVerified: true,
            associatedUserCount: 15,
          },
        ],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 1,
          itemsPerPage: 20,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('IBM'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.searchCompanies).toHaveBeenCalledWith('IBM', {
        page: 1,
        limit: 10,
      });
    });
  });

  describe('Custom Pagination', () => {
    it('should_useCustomLimit_when_limitProvided', async () => {
      // Arrange
      const mockResults: CompanyListResponse = {
        data: [],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 0,
          itemsPerPage: 5,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(
        () => useCompanySearch('Test', { page: 1, limit: 5 }),
        { wrapper: createWrapper() }
      );

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.searchCompanies).toHaveBeenCalledWith('Test', {
        page: 1,
        limit: 5,
      });
    });

    it('should_useDefaultLimit_when_noLimitProvided', async () => {
      // Arrange
      const mockResults: CompanyListResponse = {
        data: [],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 0,
          itemsPerPage: 10,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('Query'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.searchCompanies).toHaveBeenCalledWith('Query', {
        page: 1,
        limit: 10,
      });
    });
  });

  describe('Caching Behavior (AC 10 - Performance)', () => {
    it('should_cacheSearchResults_when_queryRepeated', async () => {
      // Arrange
      const mockResults: CompanyListResponse = {
        data: [
          {
            id: '1',
            name: 'Cached Company',
            industry: 'Finance',
            location: { city: 'Geneva', country: 'Switzerland' },
            isPartner: false,
            isVerified: true,
            associatedUserCount: 8,
          },
        ],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 1,
          itemsPerPage: 10,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      // Enable caching for this test
      queryClient = new QueryClient({
        defaultOptions: {
          queries: {
            retry: false,
            gcTime: 15 * 60 * 1000, // 15 minutes
            staleTime: 15 * 60 * 1000, // 15 minutes (AC requirement)
          },
        },
      });

      const wrapper = ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      );

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act - First call
      const { result: result1 } = renderHook(() => useCompanySearch('Finance'), {
        wrapper,
      });

      await waitFor(() => {
        expect(result1.current.isSuccess).toBe(true);
      });

      // Act - Second call with same query
      const { result: result2 } = renderHook(() => useCompanySearch('Finance'), {
        wrapper,
      });

      // Assert - Should use cached data (15min cache)
      expect(result2.current.data).toEqual(mockResults);
      expect(companyApiClient.searchCompanies).toHaveBeenCalledTimes(1);
    });

    it('should_haveDifferentCacheKeys_when_queryDiffers', async () => {
      // Arrange
      const mockResults1: CompanyListResponse = {
        data: [
          {
            id: '1',
            name: 'Tech Company',
            industry: 'Technology',
            location: { city: 'Zurich', country: 'Switzerland' },
            isPartner: false,
            isVerified: true,
            associatedUserCount: 5,
          },
        ],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 1,
          itemsPerPage: 10,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      const mockResults2: CompanyListResponse = {
        data: [
          {
            id: '2',
            name: 'Finance Corp',
            industry: 'Finance',
            location: { city: 'Geneva', country: 'Switzerland' },
            isPartner: true,
            isVerified: true,
            associatedUserCount: 12,
          },
        ],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 1,
          itemsPerPage: 10,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.searchCompanies)
        .mockResolvedValueOnce(mockResults1)
        .mockResolvedValueOnce(mockResults2);

      // Act
      const { result: result1 } = renderHook(() => useCompanySearch('Tech'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result1.current.isSuccess).toBe(true));

      const { result: result2 } = renderHook(() => useCompanySearch('Finance'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result2.current.isSuccess).toBe(true));

      // Assert - Different queries should trigger separate API calls
      expect(companyApiClient.searchCompanies).toHaveBeenCalledTimes(2);
      expect(result1.current.data?.data[0].industry).toBe('Technology');
      expect(result2.current.data?.data[0].industry).toBe('Finance');
    });
  });

  describe('Query Key Generation', () => {
    it('should_includeQueryInKey_when_searchExecuted', async () => {
      // Arrange
      const mockResults: CompanyListResponse = {
        data: [],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 0,
          itemsPerPage: 10,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('TestQuery'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Assert - Query key should include search query
      const queryKey = ['companySearch', 'TestQuery', { page: 1, limit: 10 }];
      const cachedData = queryClient.getQueryData(queryKey);
      expect(cachedData).toBeDefined();
    });

    it('should_includePaginationInKey_when_paginationProvided', async () => {
      // Arrange
      const mockResults: CompanyListResponse = {
        data: [],
        pagination: {
          currentPage: 2,
          totalPages: 3,
          totalItems: 25,
          itemsPerPage: 10,
          hasNextPage: true,
          hasPreviousPage: true,
        },
      };

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(
        () => useCompanySearch('Query', { page: 2, limit: 10 }),
        { wrapper: createWrapper() }
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Assert
      const queryKey = ['companySearch', 'Query', { page: 2, limit: 10 }];
      const cachedData = queryClient.getQueryData(queryKey);
      expect(cachedData).toBeDefined();
    });
  });

  describe('Error Handling (AC 13)', () => {
    it('should_returnError_when_searchFails', async () => {
      // Arrange
      const searchError = new Error('Search failed');
      vi.mocked(companyApiClient.searchCompanies).mockRejectedValue(searchError);

      // Act
      const { result } = renderHook(() => useCompanySearch('FailingQuery'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(searchError);
    });

    it('should_exposeCorrelationId_when_apiError', async () => {
      // Arrange
      const apiError = new Error('API Error') as Error & { correlationId?: string };
      apiError.correlationId = 'search-correlation-123';
      vi.mocked(companyApiClient.searchCompanies).mockRejectedValue(apiError);

      // Act
      const { result } = renderHook(() => useCompanySearch('Test'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect((result.current.error as typeof apiError).correlationId).toBe(
        'search-correlation-123'
      );
    });

    it('should_returnNetworkError_when_networkFails', async () => {
      // Arrange
      const networkError = new Error('Network error') as Error & { status?: number };
      networkError.status = 503;
      vi.mocked(companyApiClient.searchCompanies).mockRejectedValue(networkError);

      // Act
      const { result } = renderHook(() => useCompanySearch('Query'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect((result.current.error as typeof networkError).status).toBe(503);
    });
  });

  describe('Loading States', () => {
    it('should_showLoadingState_when_searching', () => {
      // Arrange
      vi.mocked(companyApiClient.searchCompanies).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      // Act
      const { result } = renderHook(() => useCompanySearch('TestQuery'), {
        wrapper: createWrapper(),
      });

      // Assert
      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();
    });

    it('should_clearLoadingState_when_searchComplete', async () => {
      // Arrange
      const mockResults: CompanyListResponse = {
        data: [
          {
            id: '1',
            name: 'Complete Company',
            industry: 'Healthcare',
            location: { city: 'Basel', country: 'Switzerland' },
            isPartner: false,
            isVerified: false,
            associatedUserCount: 2,
          },
        ],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 1,
          itemsPerPage: 10,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('Complete'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.isSuccess).toBe(true);
      expect(result.current.data).toEqual(mockResults);
    });
  });

  describe('Empty Results', () => {
    it('should_returnEmptyArray_when_noMatches', async () => {
      // Arrange
      const emptyResults: CompanyListResponse = {
        data: [],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 0,
          itemsPerPage: 10,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(emptyResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('NonExistent'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data?.data).toEqual([]);
      expect(result.current.data?.pagination.totalItems).toBe(0);
    });
  });
});
