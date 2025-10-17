/**
 * useCompany Hook Tests (RED Phase - TDD)
 *
 * Tests for React Query hook that fetches single company detail with optional resource expansion
 * AC 8 (Company Detail View), AC 10 (Performance), AC 15 (Service Integration)
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCompany } from '@/hooks/useCompany/useCompany';
import { companyApiClient } from '@/services/api/companyApi';
import type { Company } from '@/types/company.types';

// Mock the API client
vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: {
    getCompany: vi.fn(),
  },
}));

describe('useCompany Hook', () => {
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

  describe('Basic Query Functionality', () => {
    it('should_fetchCompanyById_when_idProvided', async () => {
      // Arrange
      const mockCompany: Company = {
        id: 'company-123',
        name: 'Test Company AG',
        displayName: 'Test Company',
        industry: 'Technology',
        sector: 'Private',
        location: {
          city: 'Zurich',
          canton: 'ZH',
          country: 'Switzerland',
        },
        swissUID: 'CHE-123.456.789',
        website: 'https://testcompany.ch',
        description: 'A test company',
        logoUrl: 'https://cdn.example.com/logo.png',
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.getCompany).mockResolvedValue(mockCompany);

      // Act
      const { result } = renderHook(() => useCompany('company-123'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.getCompany).toHaveBeenCalledWith('company-123', undefined);
      expect(result.current.data).toEqual(mockCompany);
    });

    it('should_notFetch_when_idIsEmpty', () => {
      // Arrange
      vi.mocked(companyApiClient.getCompany).mockResolvedValue({} as Company);

      // Act
      const { result } = renderHook(() => useCompany(''), {
        wrapper: createWrapper(),
      });

      // Assert
      expect(result.current.isLoading).toBe(false);
      expect(result.current.isFetching).toBe(false);
      expect(companyApiClient.getCompany).not.toHaveBeenCalled();
    });

    it('should_notFetch_when_idIsUndefined', () => {
      // Arrange
      vi.mocked(companyApiClient.getCompany).mockResolvedValue({} as Company);

      // Act
      const { result } = renderHook(() => useCompany(undefined as unknown as string), {
        wrapper: createWrapper(),
      });

      // Assert
      expect(result.current.isLoading).toBe(false);
      expect(companyApiClient.getCompany).not.toHaveBeenCalled();
    });
  });

  describe('Resource Expansion (AC 15 - Service Integration)', () => {
    it('should_includeStatistics_when_expandStatisticsRequested', async () => {
      // Arrange
      const mockCompany: Company = {
        id: 'company-123',
        name: 'Test Company',
        industry: 'Technology',
        location: { city: 'Bern', canton: 'BE', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.getCompany).mockResolvedValue(mockCompany);

      // Act
      const { result } = renderHook(
        () => useCompany('company-123', { expand: ['statistics'] }),
        { wrapper: createWrapper() }
      );

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.getCompany).toHaveBeenCalledWith('company-123', {
        expand: ['statistics'],
      });
    });

    it('should_includeMultipleResources_when_multipleExpansionsRequested', async () => {
      // Arrange
      const mockCompany: Company = {
        id: 'company-123',
        name: 'Test Company',
        industry: 'Technology',
        location: { city: 'Geneva', canton: 'GE', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.getCompany).mockResolvedValue(mockCompany);

      // Act
      const { result } = renderHook(
        () => useCompany('company-123', { expand: ['statistics', 'logo'] }),
        { wrapper: createWrapper() }
      );

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.getCompany).toHaveBeenCalledWith('company-123', {
        expand: ['statistics', 'logo'],
      });
    });

    it('should_notIncludeExpandParam_when_noExpansionRequested', async () => {
      // Arrange
      const mockCompany: Company = {
        id: 'company-123',
        name: 'Test Company',
        industry: 'Finance',
        location: { city: 'Basel', canton: 'BS', country: 'Switzerland' },
        isVerified: false,
        verificationStatus: 'Pending',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.getCompany).mockResolvedValue(mockCompany);

      // Act
      const { result } = renderHook(() => useCompany('company-123'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.getCompany).toHaveBeenCalledWith('company-123', undefined);
    });
  });

  describe('Caching Behavior (AC 10 - Performance)', () => {
    it('should_cacheCompanyData_when_fetched', async () => {
      // Arrange
      const mockCompany: Company = {
        id: 'company-cached',
        name: 'Cached Company',
        industry: 'Healthcare',
        location: { city: 'Lausanne', canton: 'VD', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      // Enable caching for this test
      queryClient = new QueryClient({
        defaultOptions: {
          queries: {
            retry: false,
            gcTime: 10 * 60 * 1000, // 10 minutes
            staleTime: 10 * 60 * 1000, // 10 minutes
          },
        },
      });

      const wrapper = ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      );

      vi.mocked(companyApiClient.getCompany).mockResolvedValue(mockCompany);

      // Act - First call
      const { result: result1 } = renderHook(() => useCompany('company-cached'), {
        wrapper,
      });

      await waitFor(() => {
        expect(result1.current.isSuccess).toBe(true);
      });

      // Act - Second call with same ID
      const { result: result2 } = renderHook(() => useCompany('company-cached'), {
        wrapper,
      });

      // Assert - Should use cached data
      expect(result2.current.data).toEqual(mockCompany);
      expect(companyApiClient.getCompany).toHaveBeenCalledTimes(1);
    });

    it('should_haveDifferentCacheKeys_when_expansionDiffers', async () => {
      // Arrange
      const mockCompany: Company = {
        id: 'company-123',
        name: 'Test Company',
        industry: 'Technology',
        location: { city: 'Zurich', canton: 'ZH', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.getCompany).mockResolvedValue(mockCompany);

      // Act - First query without expansion
      const { result: result1 } = renderHook(() => useCompany('company-123'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result1.current.isSuccess).toBe(true);
      });

      // Act - Second query with expansion
      const { result: result2 } = renderHook(
        () => useCompany('company-123', { expand: ['statistics'] }),
        { wrapper: createWrapper() }
      );

      await waitFor(() => {
        expect(result2.current.isSuccess).toBe(true);
      });

      // Assert - Both API calls should have been made (different cache keys)
      expect(companyApiClient.getCompany).toHaveBeenCalledTimes(2);
    });
  });

  describe('Error Handling (AC 13)', () => {
    it('should_returnError_when_companyNotFound', async () => {
      // Arrange
      const notFoundError = new Error('Company not found') as Error & { status?: number };
      notFoundError.status = 404;
      vi.mocked(companyApiClient.getCompany).mockRejectedValue(notFoundError);

      // Act
      const { result } = renderHook(() => useCompany('nonexistent-id'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(notFoundError);
      expect((result.current.error as typeof notFoundError).status).toBe(404);
    });

    it('should_returnError_when_unauthorized', async () => {
      // Arrange
      const authError = new Error('Unauthorized') as Error & { status?: number };
      authError.status = 401;
      vi.mocked(companyApiClient.getCompany).mockRejectedValue(authError);

      // Act
      const { result } = renderHook(() => useCompany('company-123'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect((result.current.error as typeof authError).status).toBe(401);
    });

    it('should_exposeCorrelationId_when_apiError', async () => {
      // Arrange
      const apiError = new Error('Server error') as Error & {
        status?: number;
        correlationId?: string;
      };
      apiError.status = 500;
      apiError.correlationId = 'correlation-xyz-789';
      vi.mocked(companyApiClient.getCompany).mockRejectedValue(apiError);

      // Act
      const { result } = renderHook(() => useCompany('company-123'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect((result.current.error as typeof apiError).correlationId).toBe(
        'correlation-xyz-789'
      );
    });
  });

  describe('Query Key Generation', () => {
    it('should_generateUniqueKey_when_companyIdDiffers', async () => {
      // Arrange
      const mockCompany1: Company = {
        id: 'company-1',
        name: 'Company One',
        industry: 'Tech',
        location: { city: 'Zurich', canton: 'ZH', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      const mockCompany2: Company = {
        id: 'company-2',
        name: 'Company Two',
        industry: 'Finance',
        location: { city: 'Geneva', canton: 'GE', country: 'Switzerland' },
        isVerified: false,
        verificationStatus: 'Pending',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-2',
      };

      vi.mocked(companyApiClient.getCompany)
        .mockResolvedValueOnce(mockCompany1)
        .mockResolvedValueOnce(mockCompany2);

      // Act
      const { result: result1 } = renderHook(() => useCompany('company-1'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result1.current.isSuccess).toBe(true));

      const { result: result2 } = renderHook(() => useCompany('company-2'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result2.current.isSuccess).toBe(true));

      // Assert
      expect(companyApiClient.getCompany).toHaveBeenCalledTimes(2);
      expect(result1.current.data?.name).toBe('Company One');
      expect(result2.current.data?.name).toBe('Company Two');
    });

    it('should_includeExpansionInKey_when_expandProvided', async () => {
      // Arrange
      const mockCompany: Company = {
        id: 'company-123',
        name: 'Test Company',
        industry: 'Technology',
        location: { city: 'Bern', canton: 'BE', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.getCompany).mockResolvedValue(mockCompany);

      // Act
      const { result } = renderHook(
        () => useCompany('company-123', { expand: ['statistics', 'logo'] }),
        { wrapper: createWrapper() }
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Assert - Query key should include expansion
      const queryKey = ['company', 'company-123', { expand: ['statistics', 'logo'] }];
      const cachedData = queryClient.getQueryData(queryKey);
      expect(cachedData).toBeDefined();
    });
  });

  describe('Loading States', () => {
    it('should_showLoadingState_when_fetching', () => {
      // Arrange
      vi.mocked(companyApiClient.getCompany).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      // Act
      const { result } = renderHook(() => useCompany('company-123'), {
        wrapper: createWrapper(),
      });

      // Assert
      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();
    });

    it('should_clearLoadingState_when_fetchComplete', async () => {
      // Arrange
      const mockCompany: Company = {
        id: 'company-123',
        name: 'Test Company',
        industry: 'Technology',
        location: { city: 'Zurich', canton: 'ZH', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.getCompany).mockResolvedValue(mockCompany);

      // Act
      const { result } = renderHook(() => useCompany('company-123'), {
        wrapper: createWrapper(),
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.isSuccess).toBe(true);
      expect(result.current.data).toEqual(mockCompany);
    });
  });
});
