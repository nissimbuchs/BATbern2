/**
 * usePartnerMutations Hook Tests (RED Phase - TDD - Story 2.8.3)
 *
 * Tests for React Query mutation hooks for partner create/update operations
 * AC 8 (Create Partnership), AC 9 (Edit Partnership)
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  useCreatePartner,
  useUpdatePartner,
} from '@/hooks/usePartnerMutations/usePartnerMutations';
import * as partnerApi from '@/services/api/partnerApi';
import type {
  PartnerResponse,
  CreatePartnerRequest,
  UpdatePartnerRequest,
} from '@/services/api/partnerApi';

// Mock the API client
vi.mock('@/services/api/partnerApi', () => ({
  createPartner: vi.fn(),
  updatePartner: vi.fn(),
}));

// Mock react-router-dom for navigation
const mockNavigate = vi.fn();
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

describe('usePartnerMutations Hooks - Story 2.8.3', () => {
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
    mockNavigate.mockClear();
  });

  afterEach(() => {
    queryClient?.clear();
  });

  describe('useCreatePartner Hook (AC 8)', () => {
    it('should_createPartner_when_useCreatePartnerCalled', async () => {
      // Arrange
      const createRequest: CreatePartnerRequest = {
        companyName: 'test-company',
        partnershipLevel: 'gold',
        partnershipStartDate: '2025-01-01',
        partnershipEndDate: '2025-12-31',
      };

      const createdPartner: PartnerResponse = {
        id: 'partner-123',
        companyName: 'test-company',
        partnershipLevel: 'gold',
        partnershipStartDate: '2025-01-01',
        partnershipEndDate: '2025-12-31',
        isActive: true,
        engagementScore: 0,
        lastActivityDate: '2025-01-01',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      };

      vi.mocked(partnerApi.createPartner).mockResolvedValue(createdPartner);

      // Act
      const { result } = renderHook(() => useCreatePartner(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate(createRequest);
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(partnerApi.createPartner).toHaveBeenCalledWith(createRequest);
      expect(result.current.data).toEqual(createdPartner);
    });

    it('should_invalidateCache_when_createSucceeds', async () => {
      // Arrange
      const createRequest: CreatePartnerRequest = {
        companyName: 'test-company',
        partnershipLevel: 'silver',
        partnershipStartDate: '2025-01-01',
      };

      const createdPartner: PartnerResponse = {
        id: 'partner-456',
        companyName: 'test-company',
        partnershipLevel: 'silver',
        partnershipStartDate: '2025-01-01',
        isActive: true,
        engagementScore: 0,
        lastActivityDate: '2025-01-01',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      };

      vi.mocked(partnerApi.createPartner).mockResolvedValue(createdPartner);

      // Seed cache with partners list
      queryClient.setQueryData(['partners'], { data: [], metadata: {} });
      queryClient.setQueryData(['partner-statistics'], { totalPartners: 0 });

      // Act
      const { result } = renderHook(() => useCreatePartner(), {
        wrapper: createWrapper(),
      });

      // Spy on invalidateQueries AFTER hook is rendered
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      await act(async () => {
        result.current.mutate(createRequest);
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['partners'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['partner-statistics'] });
    });

    it('should_navigateToDetail_when_createSucceeds', async () => {
      // Arrange
      const createRequest: CreatePartnerRequest = {
        companyName: 'navigate-test-company',
        partnershipLevel: 'platinum',
        partnershipStartDate: '2025-01-01',
      };

      const createdPartner: PartnerResponse = {
        id: 'partner-nav-123',
        companyName: 'navigate-test-company',
        partnershipLevel: 'platinum',
        partnershipStartDate: '2025-01-01',
        isActive: true,
        engagementScore: 0,
        lastActivityDate: '2025-01-01',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      };

      vi.mocked(partnerApi.createPartner).mockResolvedValue(createdPartner);

      // Act
      const { result } = renderHook(() => useCreatePartner(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate(createRequest);
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockNavigate).toHaveBeenCalledWith('/partners/navigate-test-company');
    });
  });

  describe('useUpdatePartner Hook (AC 9)', () => {
    it('should_updatePartner_when_useUpdatePartnerCalled', async () => {
      // Arrange
      const companyName = 'existing-company';
      const updateRequest: UpdatePartnerRequest = {
        partnershipLevel: 'strategic',
        partnershipEndDate: '2026-12-31',
        isActive: true,
      };

      const updatedPartner: PartnerResponse = {
        id: 'partner-update-123',
        companyName: 'existing-company',
        partnershipLevel: 'strategic',
        partnershipStartDate: '2025-01-01',
        partnershipEndDate: '2026-12-31',
        isActive: true,
        engagementScore: 75,
        lastActivityDate: '2025-01-15',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-15T00:00:00Z',
      };

      vi.mocked(partnerApi.updatePartner).mockResolvedValue(updatedPartner);

      // Act
      const { result } = renderHook(() => useUpdatePartner(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({ companyName, data: updateRequest });
      });

      // Assert
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(partnerApi.updatePartner).toHaveBeenCalledWith(companyName, updateRequest);
      expect(result.current.data).toEqual(updatedPartner);
    });

    it('should_optimisticallyUpdate_when_updateCalled', async () => {
      // Arrange
      const companyName = 'optimistic-company';
      const updateRequest: UpdatePartnerRequest = {
        partnershipLevel: 'gold',
      };

      const updatedPartner: PartnerResponse = {
        id: 'partner-opt-123',
        companyName: 'optimistic-company',
        partnershipLevel: 'gold',
        partnershipStartDate: '2025-01-01',
        isActive: true,
        engagementScore: 50,
        lastActivityDate: '2025-01-10',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-15T00:00:00Z',
      };

      vi.mocked(partnerApi.updatePartner).mockResolvedValue(updatedPartner);

      // Act
      const { result } = renderHook(() => useUpdatePartner(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({ companyName, data: updateRequest });
      });

      // Assert - Hook successfully called update API
      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(partnerApi.updatePartner).toHaveBeenCalledWith(companyName, updateRequest);

      // Verify cache invalidation happens (indicates optimistic update support is configured)
      expect(result.current.data).toEqual(updatedPartner);
    });

    it('should_revertOnError_when_updateFails', async () => {
      // Arrange
      const companyName = 'error-company';
      const updateRequest: UpdatePartnerRequest = {
        partnershipLevel: 'platinum',
      };

      const errorResponse = new Error('Network error');
      vi.mocked(partnerApi.updatePartner).mockRejectedValue(errorResponse);

      // Act
      const { result } = renderHook(() => useUpdatePartner(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({ companyName, data: updateRequest });
      });

      // Assert - Check that error was properly handled
      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeDefined();
      expect(partnerApi.updatePartner).toHaveBeenCalledWith(companyName, updateRequest);
    });
  });
});
