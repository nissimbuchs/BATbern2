/**
 * useCompanies Hook Tests (RED Phase - TDD)
 *
 * Tests for React Query hook that fetches company list with filters and pagination
 * AC 10 (Performance), AC 15 (Service Integration)
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCompanies } from '@/hooks/useCompanies/useCompanies';
import { companyApiClient } from '@/services/api/companyApi';
import type { CompanyListResponse, CompanyFilters } from '@/types/company.types';

// Mock the API client
vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: {
    getCompanies: vi.fn(),
  },
}));

describe('useCompanies Hook', () => {
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

  describe('Basic Query Functionality', () => {
    it('should_fetchCompanies_when_hookCalled', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [
          {
            id: '1',
            name: 'Test Company',
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
          itemsPerPage: 20,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      // Act
      const { result } = renderHook(() => useCompanies({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.getCompanies).toHaveBeenCalledWith(
        { page: 1, limit: 20 },
        undefined
      );
      expect(result.current.data).toEqual(mockResponse);
    });

    it('should_returnLoadingState_when_queryPending', () => {
      // Arrange
      vi.mocked(companyApiClient.getCompanies).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      // Act
      const { result } = renderHook(() => useCompanies({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      // Assert
      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();
    });

    it('should_returnError_when_apiFails', async () => {
      // Arrange
      const mockError = new Error('API Error');
      vi.mocked(companyApiClient.getCompanies).mockRejectedValue(mockError);

      // Act
      const { result } = renderHook(() => useCompanies({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBe(mockError);
    });
  });

  describe('Filtering Functionality', () => {
    it('should_applyFilters_when_filtersProvided', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 0,
          itemsPerPage: 20,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      const filters: CompanyFilters = {
        isPartner: true,
        isVerified: true,
        industry: 'Technology',
      };

      // Act
      const { result } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }, filters),
        { wrapper: createWrapper() }
      );

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.getCompanies).toHaveBeenCalledWith(
        { page: 1, limit: 20 },
        filters
      );
    });

    it('should_filterPartners_when_isPartnerTrue', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [
          {
            id: '1',
            name: 'Partner Company',
            industry: 'Technology',
            location: { city: 'Bern', country: 'Switzerland' },
            isPartner: true,
            isVerified: true,
            associatedUserCount: 10,
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

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      // Act
      const { result } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }, { isPartner: true }),
        { wrapper: createWrapper() }
      );

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data?.data[0].isPartner).toBe(true);
    });

    it('should_filterByIndustry_when_industryFilterProvided', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [
          {
            id: '1',
            name: 'Tech Company',
            industry: 'Technology',
            location: { city: 'Zurich', country: 'Switzerland' },
            isPartner: false,
            isVerified: true,
            associatedUserCount: 3,
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

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      // Act
      const { result } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }, { industry: 'Technology' }),
        { wrapper: createWrapper() }
      );

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data?.data[0].industry).toBe('Technology');
    });
  });

  describe('Pagination Functionality', () => {
    it('should_fetchPage2_when_pageParameterIs2', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [],
        pagination: {
          currentPage: 2,
          totalPages: 5,
          totalItems: 100,
          itemsPerPage: 20,
          hasNextPage: true,
          hasPreviousPage: true,
        },
      };

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      // Act
      const { result } = renderHook(() => useCompanies({ page: 2, limit: 20 }), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.getCompanies).toHaveBeenCalledWith(
        { page: 2, limit: 20 },
        undefined
      );
      expect(result.current.data?.pagination.currentPage).toBe(2);
    });

    it('should_fetchWithCustomLimit_when_limitProvided', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 10,
          itemsPerPage: 50,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      // Act
      const { result } = renderHook(() => useCompanies({ page: 1, limit: 50 }), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.getCompanies).toHaveBeenCalledWith(
        { page: 1, limit: 50 },
        undefined
      );
      expect(result.current.data?.pagination.itemsPerPage).toBe(50);
    });
  });

  describe('Caching Behavior (AC 10 - Performance)', () => {
    it('should_useCachedData_when_sameQueryRepeated', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [{ id: '1', name: 'Cached Company', industry: 'Tech', location: { city: 'Bern', country: 'Switzerland' }, isPartner: false, isVerified: true, associatedUserCount: 2 }],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalItems: 1,
          itemsPerPage: 20,
          hasNextPage: false,
          hasPreviousPage: false,
        },
      };

      // Override queryClient to enable caching for this test
      queryClient = new QueryClient({
        defaultOptions: {
          queries: {
            retry: false,
            gcTime: 10 * 60 * 1000, // 10 minutes
            staleTime: 5 * 60 * 1000, // 5 minutes
          },
        },
      });

      const wrapper = ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      );

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      // Act - First call
      const { result: result1 } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }),
        { wrapper }
      );

      await waitFor(() => {
        expect(result1.current.isSuccess).toBe(true);
      });

      // Act - Second call with same params
      const { result: result2 } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }),
        { wrapper }
      );

      // Assert - Should use cached data, API called only once
      expect(result2.current.data).toEqual(mockResponse);
      expect(companyApiClient.getCompanies).toHaveBeenCalledTimes(1);
    });

    it('should_haveDifferentCacheKeys_when_filtersChange', async () => {
      // Arrange
      const mockResponse1: CompanyListResponse = {
        data: [{ id: '1', name: 'Company 1', industry: 'Tech', location: { city: 'Zurich', country: 'Switzerland' }, isPartner: false, isVerified: true, associatedUserCount: 1 }],
        pagination: { currentPage: 1, totalPages: 1, totalItems: 1, itemsPerPage: 20, hasNextPage: false, hasPreviousPage: false },
      };

      const mockResponse2: CompanyListResponse = {
        data: [{ id: '2', name: 'Partner Company', industry: 'Finance', location: { city: 'Geneva', country: 'Switzerland' }, isPartner: true, isVerified: true, associatedUserCount: 5 }],
        pagination: { currentPage: 1, totalPages: 1, totalItems: 1, itemsPerPage: 20, hasNextPage: false, hasPreviousPage: false },
      };

      vi.mocked(companyApiClient.getCompanies)
        .mockResolvedValueOnce(mockResponse1)
        .mockResolvedValueOnce(mockResponse2);

      // Act - First query without filters
      const { result: result1 } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }),
        { wrapper: createWrapper() }
      );

      await waitFor(() => {
        expect(result1.current.isSuccess).toBe(true);
      });

      // Act - Second query with filters
      const { result: result2 } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }, { isPartner: true }),
        { wrapper: createWrapper() }
      );

      await waitFor(() => {
        expect(result2.current.isSuccess).toBe(true);
      });

      // Assert - Both API calls should have been made
      expect(companyApiClient.getCompanies).toHaveBeenCalledTimes(2);
      expect(result1.current.data?.data[0].name).toBe('Company 1');
      expect(result2.current.data?.data[0].name).toBe('Partner Company');
    });
  });

  describe('Query Key Generation', () => {
    it('should_generateUniqueKey_when_paginationChanges', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [],
        pagination: { currentPage: 1, totalPages: 1, totalItems: 0, itemsPerPage: 20, hasNextPage: false, hasPreviousPage: false },
      };

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      // Act
      const { result: result1 } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }),
        { wrapper: createWrapper() }
      );

      await waitFor(() => expect(result1.current.isSuccess).toBe(true));

      const { result: result2 } = renderHook(
        () => useCompanies({ page: 2, limit: 20 }),
        { wrapper: createWrapper() }
      );

      await waitFor(() => expect(result2.current.isSuccess).toBe(true));

      // Assert - Different pages should trigger separate API calls
      expect(companyApiClient.getCompanies).toHaveBeenCalledTimes(2);
    });

    it('should_includeFiltersInKey_when_filtersProvided', async () => {
      // Arrange
      const mockResponse: CompanyListResponse = {
        data: [],
        pagination: { currentPage: 1, totalPages: 1, totalItems: 0, itemsPerPage: 20, hasNextPage: false, hasPreviousPage: false },
      };

      vi.mocked(companyApiClient.getCompanies).mockResolvedValue(mockResponse);

      // Act
      const { result } = renderHook(
        () => useCompanies({ page: 1, limit: 20 }, { industry: 'Technology' }),
        { wrapper: createWrapper() }
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Assert - Query key should include filters
      const queryKey = ['companies', { page: 1, limit: 20 }, { industry: 'Technology' }];
      const cachedData = queryClient.getQueryData(queryKey);
      expect(cachedData).toBeDefined();
    });
  });

  describe('Error Handling (AC 13)', () => {
    it('should_exposeError_when_networkError', async () => {
      // Arrange
      const networkError = new Error('Network error');
      vi.mocked(companyApiClient.getCompanies).mockRejectedValue(networkError);

      // Act
      const { result } = renderHook(() => useCompanies({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(networkError);
    });

    it('should_exposeCorrelationId_when_apiErrorWithCorrelationId', async () => {
      // Arrange
      const apiError = new Error('API Error') as Error & { correlationId?: string };
      apiError.correlationId = 'abc-123-xyz';
      vi.mocked(companyApiClient.getCompanies).mockRejectedValue(apiError);

      // Act
      const { result } = renderHook(() => useCompanies({ page: 1, limit: 20 }), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect((result.current.error as typeof apiError)?.correlationId).toBe('abc-123-xyz');
    });
  });
});
