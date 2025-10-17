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
import type { Company } from '@/types/company.types';

// Mock the API client
vi.mock('@/services/api/companyApi', () => ({
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
      const mockResults: Company[] = [
        {
          id: '1',
          name: 'Tech Company AG',
          industry: 'Technology',
          location: { city: 'Zurich', country: 'Switzerland' },
          isVerified: true,
          verificationStatus: 'Verified',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          createdBy: 'user-1',
        },
        {
          id: '2',
          name: 'Tech Solutions GmbH',
          industry: 'Technology',
          location: { city: 'Bern', country: 'Switzerland' },
          isVerified: true,
          verificationStatus: 'Verified',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          createdBy: 'user-1',
        },
      ];

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('Tech'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.searchCompanies).toHaveBeenCalledWith('Tech', 10);
      expect(result.current.data).toEqual(mockResults);
      expect(result.current.data).toHaveLength(2);
    });

    it('should_notSearch_when_queryTooShort', () => {
      // Arrange
      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue([]);

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
      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue([]);

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
      const mockResults: Company[] = [
        {
          id: '1',
          name: 'IBM Switzerland',
          industry: 'Technology',
          location: { city: 'Zurich', country: 'Switzerland' },
          isVerified: true,
          verificationStatus: 'Verified',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          createdBy: 'user-1',
        },
      ];

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('IBM'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.searchCompanies).toHaveBeenCalledWith('IBM', 10);
    });
  });

  describe('Custom Pagination', () => {
    it('should_useCustomLimit_when_limitProvided', async () => {
      // Arrange
      const mockResults: Company[] = [];

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(
        () => useCompanySearch('Test', 5),
        { wrapper: createWrapper() }
      );

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.searchCompanies).toHaveBeenCalledWith('Test', 5);
    });

    it('should_useDefaultLimit_when_noLimitProvided', async () => {
      // Arrange
      const mockResults: Company[] = [];

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('Query'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.searchCompanies).toHaveBeenCalledWith('Query', 10);
    });
  });

  describe('Caching Behavior (AC 10 - Performance)', () => {
    it('should_cacheSearchResults_when_queryRepeated', async () => {
      // Arrange
      const mockResults: Company[] = [
        {
          id: '1',
          name: 'Cached Company',
          industry: 'Finance',
          location: { city: 'Geneva', country: 'Switzerland' },
          isVerified: true,
          verificationStatus: 'Verified',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          createdBy: 'user-1',
        },
      ];

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
      const mockResults1: Company[] = [
        {
          id: '1',
          name: 'Tech Company',
          industry: 'Technology',
          location: { city: 'Zurich', country: 'Switzerland' },
          isVerified: true,
          verificationStatus: 'Verified',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          createdBy: 'user-1',
        },
      ];

      const mockResults2: Company[] = [
        {
          id: '2',
          name: 'Finance Corp',
          industry: 'Finance',
          location: { city: 'Geneva', country: 'Switzerland' },
          isVerified: true,
          verificationStatus: 'Verified',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          createdBy: 'user-1',
        },
      ];

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
      expect(result1.current.data?.[0].industry).toBe('Technology');
      expect(result2.current.data?.[0].industry).toBe('Finance');
    });
  });

  describe('Query Key Generation', () => {
    it('should_includeQueryInKey_when_searchExecuted', async () => {
      // Arrange
      const mockResults: Company[] = [];

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('TestQuery'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Assert - Query key should include search query
      const queryKey = ['companySearch', 'TestQuery', 10];
      const cachedData = queryClient.getQueryData(queryKey);
      expect(cachedData).toBeDefined();
    });

    it('should_includeLimitInKey_when_limitProvided', async () => {
      // Arrange
      const mockResults: Company[] = [];

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(mockResults);

      // Act
      const { result } = renderHook(
        () => useCompanySearch('Query', 5),
        { wrapper: createWrapper() }
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Assert
      const queryKey = ['companySearch', 'Query', 5];
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
      const mockResults: Company[] = [
        {
          id: '1',
          name: 'Complete Company',
          industry: 'Healthcare',
          location: { city: 'Basel', country: 'Switzerland' },
          isVerified: false,
          verificationStatus: 'Pending',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          createdBy: 'user-1',
        },
      ];

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
      const emptyResults: Company[] = [];

      vi.mocked(companyApiClient.searchCompanies).mockResolvedValue(emptyResults);

      // Act
      const { result } = renderHook(() => useCompanySearch('NonExistent'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual([]);
      expect(result.current.data).toHaveLength(0);
    });
  });
});
