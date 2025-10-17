/**
 * useCompanyMutations Hook Tests (RED Phase - TDD)
 *
 * Tests for React Query mutation hooks (create, update, delete) with optimistic updates
 * AC 3 (Create Company), AC 4 (Edit Company), AC 10 (Performance), AC 14 (State Management)
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  useCreateCompany,
  useUpdateCompany,
  useDeleteCompany,
} from '@/hooks/useCompanyMutations/useCompanyMutations';
import { companyApiClient } from '@/services/api/companyApi';
import type { Company, CreateCompanyRequest, UpdateCompanyRequest } from '@/types/company.types';

// Mock the API client
vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: {
    createCompany: vi.fn(),
    updateCompany: vi.fn(),
    deleteCompany: vi.fn(),
  },
}));

describe('useCompanyMutations Hooks', () => {
  let queryClient: QueryClient;
  let wrapper: ({ children }: { children: ReactNode }) => JSX.Element;

  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
        },
        mutations: {
          retry: false,
        },
      },
    });

    wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    return wrapper;
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient?.clear();
  });

  describe('useCreateCompany Hook (AC 3)', () => {
    it('should_createCompany_when_mutationCalled', async () => {
      // Arrange
      const createRequest: CreateCompanyRequest = {
        name: 'New Company AG',
        industry: 'Technology',
        location: {
          city: 'Zurich',
          canton: 'ZH',
          country: 'Switzerland',
        },
      };

      const createdCompany: Company = {
        id: 'company-new-123',
        ...createRequest,
        isVerified: false,
        verificationStatus: 'Pending',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.createCompany).mockResolvedValue(createdCompany);

      // Act
      const { result } = renderHook(() => useCreateCompany(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate(createRequest);
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.createCompany).toHaveBeenCalledWith(createRequest);
      expect(result.current.data).toEqual(createdCompany);
    });

    it('should_invalidateCompaniesCache_when_createSucceeds', async () => {
      // Arrange
      const createRequest: CreateCompanyRequest = {
        name: 'Test Company',
        industry: 'Finance',
        location: { city: 'Geneva', canton: 'GE', country: 'Switzerland' },
      };

      const createdCompany: Company = {
        id: 'company-123',
        ...createRequest,
        isVerified: false,
        verificationStatus: 'Pending',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.createCompany).mockResolvedValue(createdCompany);

      // Pre-populate cache with companies list
      queryClient.setQueryData(
        ['companies', { page: 1, limit: 20 }, undefined],
        {
          data: [],
          pagination: { currentPage: 1, totalPages: 1, totalItems: 0, itemsPerPage: 20, hasNextPage: false, hasPreviousPage: false },
        }
      );

      // Act
      const { result } = renderHook(() => useCreateCompany(), {
        wrapper: createWrapper(),
      });

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      await act(async () => {
        result.current.mutate(createRequest);
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['companies'] });
      });
    });

    it('should_returnError_when_createFails', async () => {
      // Arrange
      const createRequest: CreateCompanyRequest = {
        name: 'Invalid Company',
        industry: 'Technology',
        location: { city: 'Zurich', canton: 'ZH', country: 'Switzerland' },
      };

      const createError = new Error('Company name already exists');
      vi.mocked(companyApiClient.createCompany).mockRejectedValue(createError);

      // Act
      const { result } = renderHook(() => useCreateCompany(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate(createRequest);
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(createError);
    });

    it('should_supportDraftMode_when_isDraftTrue', async () => {
      // Arrange
      const draftRequest: CreateCompanyRequest & { isDraft?: boolean } = {
        name: 'Draft Company',
        industry: 'Technology',
        location: { city: 'Bern', canton: 'BE', country: 'Switzerland' },
        isDraft: true,
      };

      const createdDraft: Company = {
        id: 'draft-123',
        name: 'Draft Company',
        industry: 'Technology',
        location: { city: 'Bern', canton: 'BE', country: 'Switzerland' },
        isVerified: false,
        verificationStatus: 'Pending',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.createCompany).mockResolvedValue(createdDraft);

      // Act
      const { result } = renderHook(() => useCreateCompany(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate(draftRequest);
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.createCompany).toHaveBeenCalled();
    });
  });

  describe('useUpdateCompany Hook (AC 4, AC 14)', () => {
    it('should_updateCompany_when_mutationCalled', async () => {
      // Arrange
      const updateRequest: UpdateCompanyRequest = {
        name: 'Updated Company Name',
        description: 'New description',
      };

      const updatedCompany: Company = {
        id: 'company-123',
        name: 'Updated Company Name',
        description: 'New description',
        industry: 'Technology',
        location: { city: 'Zurich', canton: 'ZH', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-02T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.updateCompany).mockResolvedValue(updatedCompany);

      // Act
      const { result } = renderHook(() => useUpdateCompany(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({ id: 'company-123', data: updateRequest });
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.updateCompany).toHaveBeenCalledWith('company-123', updateRequest);
      expect(result.current.data).toEqual(updatedCompany);
    });

    it('should_applyOptimisticUpdate_when_mutationCalled', async () => {
      // Arrange
      const existingCompany: Company = {
        id: 'company-123',
        name: 'Original Name',
        industry: 'Technology',
        location: { city: 'Zurich', canton: 'ZH', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      const updateRequest: UpdateCompanyRequest = {
        name: 'Optimistic Name',
      };

      vi.mocked(companyApiClient.updateCompany).mockImplementation(
        () => new Promise((resolve) => setTimeout(resolve, 100))
      );

      // Act - Create wrapper first to initialize queryClient
      const { result } = renderHook(() => useUpdateCompany(), {
        wrapper: createWrapper(),
      });

      // Pre-populate cache AFTER wrapper created
      queryClient.setQueryData(['company', 'company-123', undefined], existingCompany);

      // Verify cache was set correctly
      const verifyCache = queryClient.getQueryData(['company', 'company-123', undefined]) as Company;
      expect(verifyCache).toBeDefined();
      expect(verifyCache.name).toBe('Original Name');

      // Trigger mutation
      await act(async () => {
        result.current.mutate({ id: 'company-123', data: updateRequest });
      });

      // Assert - Check optimistic update applied immediately (synchronously after onMutate runs)
      await waitFor(() => {
        const cachedData = queryClient.getQueryData(['company', 'company-123', undefined]) as Company;
        expect(cachedData).toBeDefined();
        expect(cachedData.name).toBe('Optimistic Name');
      });
    });

    it('should_rollbackOptimisticUpdate_when_mutationFails', async () => {
      // Arrange
      const existingCompany: Company = {
        id: 'company-123',
        name: 'Original Name',
        industry: 'Technology',
        location: { city: 'Bern', canton: 'BE', country: 'Switzerland' },
        isVerified: false,
        verificationStatus: 'Pending',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      const updateRequest: UpdateCompanyRequest = {
        name: 'Failed Update',
      };

      const updateError = new Error('Update failed');
      vi.mocked(companyApiClient.updateCompany).mockRejectedValue(updateError);

      // Act - Create wrapper first to initialize queryClient
      const { result } = renderHook(() => useUpdateCompany(), {
        wrapper: createWrapper(),
      });

      // Pre-populate cache AFTER wrapper created
      act(() => {
        queryClient.setQueryData(['company', 'company-123', undefined], existingCompany);
      });

      // Verify cache was set correctly
      const verifyCache = queryClient.getQueryData(['company', 'company-123', undefined]) as Company;
      expect(verifyCache).toBeDefined();
      expect(verifyCache.name).toBe('Original Name');

      // Spy on setQueryData to verify rollback is called
      const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData');

      await act(async () => {
        result.current.mutate({ id: 'company-123', data: updateRequest });
      });

      // Assert - Wait for error
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      // Verify rollback was attempted by checking setQueryData was called with original data
      // The rollback happens in onError handler
      const rollbackCalls = setQueryDataSpy.mock.calls.filter(call => {
        const data = call[1];
        return data && typeof data === 'object' && 'name' in data && data.name === 'Original Name';
      });
      expect(rollbackCalls.length).toBeGreaterThan(0);
    });

    it('should_invalidateCompanyCache_when_updateSucceeds', async () => {
      // Arrange
      const updateRequest: UpdateCompanyRequest = {
        industry: 'Finance',
      };

      const updatedCompany: Company = {
        id: 'company-123',
        name: 'Test Company',
        industry: 'Finance',
        location: { city: 'Geneva', canton: 'GE', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-02T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.updateCompany).mockResolvedValue(updatedCompany);

      // Act
      const { result } = renderHook(() => useUpdateCompany(), {
        wrapper: createWrapper(),
      });

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      await act(async () => {
        result.current.mutate({ id: 'company-123', data: updateRequest });
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['companies'] });
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['company', 'company-123'] });
      });
    });

    it('should_supportPartialUpdate_when_onlyChangedFieldsProvided', async () => {
      // Arrange
      const partialUpdate: UpdateCompanyRequest = {
        description: 'Only description updated',
      };

      const updatedCompany: Company = {
        id: 'company-123',
        name: 'Original Name',
        description: 'Only description updated',
        industry: 'Technology',
        location: { city: 'Basel', canton: 'BS', country: 'Switzerland' },
        isVerified: true,
        verificationStatus: 'Verified',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-02T00:00:00Z',
        createdBy: 'user-1',
      };

      vi.mocked(companyApiClient.updateCompany).mockResolvedValue(updatedCompany);

      // Act
      const { result } = renderHook(() => useUpdateCompany(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({ id: 'company-123', data: partialUpdate });
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.updateCompany).toHaveBeenCalledWith('company-123', partialUpdate);
    });
  });

  describe('useDeleteCompany Hook', () => {
    it('should_deleteCompany_when_mutationCalled', async () => {
      // Arrange
      vi.mocked(companyApiClient.deleteCompany).mockResolvedValue();

      // Act
      const { result } = renderHook(() => useDeleteCompany(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate('company-to-delete');
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(companyApiClient.deleteCompany).toHaveBeenCalledWith('company-to-delete');
    });

    it('should_invalidateCompaniesCache_when_deleteSucceeds', async () => {
      // Arrange
      vi.mocked(companyApiClient.deleteCompany).mockResolvedValue();

      // Act
      const { result } = renderHook(() => useDeleteCompany(), {
        wrapper: createWrapper(),
      });

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      await act(async () => {
        result.current.mutate('company-123');
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['companies'] });
      });
    });

    it('should_removeCompanyFromCache_when_deleteSucceeds', async () => {
      // Arrange
      const companyToDelete: Company = {
        id: 'company-delete-123',
        name: 'Company to Delete',
        industry: 'Technology',
        location: { city: 'Lausanne', canton: 'VD', country: 'Switzerland' },
        isVerified: false,
        verificationStatus: 'Pending',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        createdBy: 'user-1',
      };

      // Pre-populate cache
      queryClient.setQueryData(['company', 'company-delete-123', undefined], companyToDelete);

      vi.mocked(companyApiClient.deleteCompany).mockResolvedValue();

      // Act
      const { result } = renderHook(() => useDeleteCompany(), {
        wrapper: createWrapper(),
      });

      const removeSpy = vi.spyOn(queryClient, 'removeQueries');

      await act(async () => {
        result.current.mutate('company-delete-123');
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
        expect(removeSpy).toHaveBeenCalledWith({ queryKey: ['company', 'company-delete-123'] });
      });
    });

    it('should_returnError_when_deleteFails', async () => {
      // Arrange
      const deleteError = new Error('Cannot delete company with active partnerships');
      vi.mocked(companyApiClient.deleteCompany).mockRejectedValue(deleteError);

      // Act
      const { result } = renderHook(() => useDeleteCompany(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate('company-123');
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(deleteError);
    });

    it('should_returnUnauthorizedError_when_noPermission', async () => {
      // Arrange
      const authError = new Error('Unauthorized') as Error & { status?: number };
      authError.status = 403;
      vi.mocked(companyApiClient.deleteCompany).mockRejectedValue(authError);

      // Act
      const { result } = renderHook(() => useDeleteCompany(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate('company-123');
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect((result.current.error as typeof authError).status).toBe(403);
    });
  });

  describe('Mutation Loading States', () => {
    it('should_showLoadingState_when_createInProgress', async () => {
      // Arrange
      vi.mocked(companyApiClient.createCompany).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      // Act
      const { result } = renderHook(() => useCreateCompany(), {
        wrapper: createWrapper(),
      });

      act(() => {
        result.current.mutate({
          name: 'Test',
          industry: 'Tech',
          location: { city: 'Zurich', canton: 'ZH', country: 'Switzerland' },
        });
      });

      // Assert - Wait for mutation to start
      await waitFor(() => {
        expect(result.current.isPending).toBe(true);
      });
    });

    it('should_showLoadingState_when_updateInProgress', async () => {
      // Arrange
      vi.mocked(companyApiClient.updateCompany).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      // Act
      const { result } = renderHook(() => useUpdateCompany(), {
        wrapper: createWrapper(),
      });

      act(() => {
        result.current.mutate({ id: 'company-123', data: { name: 'Updated' } });
      });

      // Assert - Wait for mutation to start
      await waitFor(() => {
        expect(result.current.isPending).toBe(true);
      });
    });

    it('should_showLoadingState_when_deleteInProgress', async () => {
      // Arrange
      vi.mocked(companyApiClient.deleteCompany).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      // Act
      const { result } = renderHook(() => useDeleteCompany(), {
        wrapper: createWrapper(),
      });

      act(() => {
        result.current.mutate('company-123');
      });

      // Assert - Wait for mutation to start
      await waitFor(() => {
        expect(result.current.isPending).toBe(true);
      });
    });
  });
});
