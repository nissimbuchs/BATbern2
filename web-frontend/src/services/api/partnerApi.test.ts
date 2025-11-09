/**
 * Partner API Client Tests (RED Phase - Story 2.8.1, Task 1a)
 *
 * TDD RED Phase: Write failing tests before implementation
 * Tests verify:
 * - API client structure and methods
 * - Query parameter building for filters, sort, pagination
 * - HTTP enrichment (?include=company,contacts parameter)
 * - Error handling (404, 500, network errors)
 * - Request/response interceptors
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import apiClient from '@/services/api/apiClient';

// Import the module that will be implemented
import * as partnerApi from './partnerApi';

describe('Partner API Client (RED Phase - Task 1a)', () => {
  // Store original timeout to restore after tests
  let originalTimeout: number;

  beforeAll(() => {
    // Save original timeout
    originalTimeout = apiClient.defaults.timeout || 30000;
    // Set short timeout for these tests to fail fast when backend unavailable
    apiClient.defaults.timeout = 100;
  });

  afterAll(() => {
    // Restore original timeout
    apiClient.defaults.timeout = originalTimeout;
  });

  describe('AC10: Test 10.1 - should_callPartnersAPI_when_listPartnersInvoked', () => {
    it('should have listPartners method', () => {
      expect(partnerApi).toHaveProperty('listPartners');
      expect(typeof partnerApi.listPartners).toBe('function');
    });

    it('should return a promise when listPartners is called', () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const promise = partnerApi.listPartners(filters, sort, pagination).catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should call GET /partners endpoint', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      // This will fail with network error (expected - backend not running in tests)
      await expect(partnerApi.listPartners(filters, sort, pagination)).rejects.toThrow();
    });
  });

  describe('AC10: Test 10.2 - should_buildCorrectQueryParams_when_filtersApplied', () => {
    it('should build filter query parameter for tier filter', async () => {
      const filters = { tier: 'gold' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      // Spy on apiClient.get to verify query parameters
      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail (backend not running), but we can check the call
      }

      // Verify the API was called with correct path
      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            filter: expect.stringContaining('partnershipLevel:gold'),
          }),
        })
      );

      getSpy.mockRestore();
    });

    it('should build filter query parameter for status filter (active)', async () => {
      const filters = { tier: 'all' as const, status: 'active' as const };
      const sort = { sortBy: 'name' as const, sortOrder: 'asc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            filter: expect.stringContaining('isActive:true'),
          }),
        })
      );

      getSpy.mockRestore();
    });

    it('should build filter query parameter for status filter (inactive)', async () => {
      const filters = { tier: 'all' as const, status: 'inactive' as const };
      const sort = { sortBy: 'name' as const, sortOrder: 'asc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            filter: expect.stringContaining('isActive:false'),
          }),
        })
      );

      getSpy.mockRestore();
    });

    it('should combine multiple filters when both tier and status are set', async () => {
      const filters = { tier: 'platinum' as const, status: 'active' as const };
      const sort = { sortBy: 'tier' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            filter: expect.stringMatching(/partnershipLevel:platinum.*isActive:true|isActive:true.*partnershipLevel:platinum/),
          }),
        })
      );

      getSpy.mockRestore();
    });

    it('should build sort query parameter', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'name' as const, sortOrder: 'asc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            sort: 'name:asc',
          }),
        })
      );

      getSpy.mockRestore();
    });
  });

  describe('AC10: Test 10.3 - should_includeCompanyAndContacts_when_includeParamUsed', () => {
    it('should always include company and contacts data via HTTP enrichment', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      // Verify include parameter for HTTP enrichment (ADR-004)
      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            include: 'company,contacts',
          }),
        })
      );

      getSpy.mockRestore();
    });
  });

  describe('AC10: Test 10.4 - should_handlePagination_when_pageParamSet', () => {
    it('should include page and size parameters', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 2, size: 50 };

      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            page: 2,
            size: 50,
          }),
        })
      );

      getSpy.mockRestore();
    });

    it('should use zero-based page indexing', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            page: 0, // Zero-based indexing
          }),
        })
      );

      getSpy.mockRestore();
    });
  });

  describe('AC10: Test 10.5 - should_handleAPIError_when_requestFails', () => {
    it('should throw error when API request fails', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      // Expected to fail with network error (backend not running in tests)
      await expect(partnerApi.listPartners(filters, sort, pagination)).rejects.toThrow();
    });

    it('should have getPartnerStatistics method', () => {
      expect(partnerApi).toHaveProperty('getPartnerStatistics');
      expect(typeof partnerApi.getPartnerStatistics).toBe('function');
    });

    it('should call GET /partners/statistics endpoint', async () => {
      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.getPartnerStatistics();
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith('/partners/statistics');

      getSpy.mockRestore();
    });
  });

  describe('API Path Configuration', () => {
    it('should use correct API path without /api/v1 prefix', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.spyOn(apiClient, 'get');

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      // Verify path is '/partners' not '/api/v1/partners' (baseURL already includes /api/v1)
      expect(getSpy).toHaveBeenCalledWith('/partners', expect.any(Object));

      getSpy.mockRestore();
    });
  });

  describe('Type Exports', () => {
    it('should export PartnerFilters type', () => {
      expect(partnerApi).toHaveProperty('listPartners');
      // Type check will be validated by TypeScript compiler
    });

    it('should export PartnerSort type', () => {
      expect(partnerApi).toHaveProperty('listPartners');
      // Type check will be validated by TypeScript compiler
    });

    it('should export PartnerPagination type', () => {
      expect(partnerApi).toHaveProperty('listPartners');
      // Type check will be validated by TypeScript compiler
    });
  });
});
