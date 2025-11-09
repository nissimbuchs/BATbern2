/**
 * Integration tests for Partner API
 * These tests verify API integration with backend services from Story 2.7
 *
 * IMPORTANT: These tests require running backend services:
 * - Partner Coordination Service (Story 2.7)
 * - Company Management Service (for HTTP enrichment)
 * - User Management Service (for HTTP enrichment)
 * - API Gateway (for authentication and routing)
 *
 * Run with: npm run test:integration
 * Or skip in unit tests: npm test (integration tests filtered out)
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import axios from 'axios';
import { listPartners, getPartnerStatistics } from '../partnerApi';
import type { PartnerFilters, PartnerSort, PartnerPagination } from '../partnerApi';

// Integration test flag - skip if backend not available
const INTEGRATION_TESTS_ENABLED = process.env.RUN_INTEGRATION_TESTS === 'true';
const TEST_TIMEOUT = 10000; // 10 seconds for backend calls

// Test configuration
const API_BASE_URL = process.env.VITE_API_URL || 'http://localhost:8080/api/v1';
const TEST_JWT_TOKEN = process.env.TEST_JWT_TOKEN || 'test-jwt-token';

// Skip all tests if integration tests not enabled
const describeIntegration = INTEGRATION_TESTS_ENABLED ? describe : describe.skip;

describeIntegration('Partner API Integration Tests', () => {
  beforeAll(() => {
    // Configure axios with test JWT token
    axios.defaults.headers.common['Authorization'] = `Bearer ${TEST_JWT_TOKEN}`;
    console.log('Integration tests running against:', API_BASE_URL);
  });

  afterAll(() => {
    // Clean up axios defaults
    delete axios.defaults.headers.common['Authorization'];
  });

  describe('AC10.1: Partner API Integration with Story 2.7 Backend', () => {
    it('should_fetchPartnersList_when_backendServiceRunning', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert
      expect(response).toBeDefined();
      expect(response.content).toBeInstanceOf(Array);
      expect(response.pagination).toBeDefined();
      expect(response.pagination.page).toBe(0);
      expect(response.pagination.size).toBe(20);
    }, TEST_TIMEOUT);

    it('should_returnPaginatedResults_when_multiplePagesExist', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const paginationPage1: PartnerPagination = { page: 0, size: 5 };
      const paginationPage2: PartnerPagination = { page: 1, size: 5 };

      // Act
      const page1Response = await listPartners(filters, sort, paginationPage1);
      const page2Response = await listPartners(filters, sort, paginationPage2);

      // Assert
      expect(page1Response.content.length).toBeLessThanOrEqual(5);
      expect(page2Response.content.length).toBeLessThanOrEqual(5);

      // Pages should have different content (if enough partners exist)
      if (page1Response.pagination.totalPages > 1) {
        expect(page1Response.content[0].id).not.toBe(page2Response.content[0].id);
      }
    }, TEST_TIMEOUT);

    it('should_filterByTier_when_tierFilterApplied', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'GOLD', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert
      expect(response).toBeDefined();
      response.content.forEach(partner => {
        expect(partner.partnershipLevel).toBe('GOLD');
      });
    }, TEST_TIMEOUT);

    it('should_filterByActiveStatus_when_statusFilterApplied', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'active' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert
      expect(response).toBeDefined();
      response.content.forEach(partner => {
        expect(partner.isActive).toBe(true);
      });
    }, TEST_TIMEOUT);

    it('should_sortByName_when_sortParameterSet', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sortAsc: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act
      const response = await listPartners(filters, sortAsc, pagination);

      // Assert
      expect(response).toBeDefined();
      if (response.content.length > 1) {
        const firstPartner = response.content[0].companyName;
        const secondPartner = response.content[1].companyName;
        expect(firstPartner.localeCompare(secondPartner)).toBeLessThanOrEqual(0);
      }
    }, TEST_TIMEOUT);
  });

  describe('AC10.2: HTTP Enrichment - Company Data Integration', () => {
    it('should_includeCompanyData_when_includeCompanyParameterUsed', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 5 };

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert
      expect(response).toBeDefined();
      if (response.content.length > 0) {
        const firstPartner = response.content[0];

        // Verify company data enrichment (ADR-004)
        expect(firstPartner.company).toBeDefined();
        expect(firstPartner.company?.companyName).toBeDefined();
        expect(firstPartner.company?.industry).toBeDefined();

        // Verify company logo URL if available
        if (firstPartner.company?.logoUrl) {
          expect(firstPartner.company.logoUrl).toMatch(/^https?:\/\//);
        }
      }
    }, TEST_TIMEOUT);

    it('should_handleMissingCompanyData_when_companyNotFound', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert - Should not throw error, company field should be optional
      expect(response).toBeDefined();
      expect(response.content).toBeInstanceOf(Array);
      // Some partners may not have company data enriched
      response.content.forEach(partner => {
        if (!partner.company) {
          expect(partner.companyName).toBeDefined(); // Still has companyName
        }
      });
    }, TEST_TIMEOUT);
  });

  describe('AC10.3: HTTP Enrichment - Contact Data Integration', () => {
    it('should_includeContactData_when_includeContactsParameterUsed', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 5 };

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert
      expect(response).toBeDefined();
      if (response.content.length > 0) {
        const firstPartner = response.content[0];

        // Verify contact data enrichment (ADR-004)
        if (firstPartner.contacts && firstPartner.contacts.length > 0) {
          const contact = firstPartner.contacts[0];
          expect(contact.firstName).toBeDefined();
          expect(contact.lastName).toBeDefined();
          expect(contact.email).toBeDefined();
          expect(contact.email).toMatch(/^[^\s@]+@[^\s@]+\.[^\s@]+$/); // Valid email format
        }
      }
    }, TEST_TIMEOUT);

    it('should_identifyPrimaryContact_when_multipleContactsExist', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert
      expect(response).toBeDefined();
      response.content.forEach(partner => {
        if (partner.contacts && partner.contacts.length > 0) {
          const primaryContacts = partner.contacts.filter(c => c.isPrimary);
          // Should have at most one primary contact
          expect(primaryContacts.length).toBeLessThanOrEqual(1);
        }
      });
    }, TEST_TIMEOUT);

    it('should_handleMissingContactData_when_contactsNotFound', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert - Should not throw error, contacts field should be optional
      expect(response).toBeDefined();
      expect(response.content).toBeInstanceOf(Array);
      // Some partners may not have contact data enriched
      response.content.forEach(partner => {
        if (!partner.contacts || partner.contacts.length === 0) {
          expect(partner.companyName).toBeDefined(); // Still has companyName
        }
      });
    }, TEST_TIMEOUT);
  });

  describe('AC10.4: Error Handling', () => {
    it('should_return404_when_invalidEndpointCalled', async () => {
      // Arrange
      const invalidUrl = `${API_BASE_URL}/partners/invalid-endpoint-xyz`;

      // Act & Assert
      await expect(
        axios.get(invalidUrl, {
          headers: { Authorization: `Bearer ${TEST_JWT_TOKEN}` }
        })
      ).rejects.toThrow();
    }, TEST_TIMEOUT);

    it('should_returnErrorResponse_when_invalidPageNumber', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 9999, size: 20 }; // Very large page number

      // Act
      const response = await listPartners(filters, sort, pagination);

      // Assert - Should return empty content, not throw error
      expect(response).toBeDefined();
      expect(response.content).toBeInstanceOf(Array);
      expect(response.content.length).toBe(0);
    }, TEST_TIMEOUT);

    it('should_returnErrorResponse_when_invalidFilterValue', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'INVALID_TIER' as any, status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act & Assert
      await expect(
        listPartners(filters, sort, pagination)
      ).rejects.toThrow();
    }, TEST_TIMEOUT);

    it('should_handleNetworkTimeout_when_backendSlow', async () => {
      // Arrange - Create axios instance with very short timeout
      const axiosWithTimeout = axios.create({
        baseURL: API_BASE_URL,
        timeout: 1, // 1ms - will definitely timeout
        headers: { Authorization: `Bearer ${TEST_JWT_TOKEN}` }
      });

      // Act & Assert
      await expect(
        axiosWithTimeout.get('/partners')
      ).rejects.toThrow();
    }, TEST_TIMEOUT);
  });

  describe('AC10.5: JWT Token Propagation', () => {
    it('should_return401_when_noJWTTokenProvided', async () => {
      // Arrange
      const axiosWithoutAuth = axios.create({
        baseURL: API_BASE_URL,
      });

      // Act & Assert
      await expect(
        axiosWithoutAuth.get('/partners')
      ).rejects.toThrow();
    }, TEST_TIMEOUT);

    it('should_return401_when_invalidJWTTokenProvided', async () => {
      // Arrange
      const axiosWithInvalidAuth = axios.create({
        baseURL: API_BASE_URL,
        headers: { Authorization: 'Bearer invalid-token-xyz' }
      });

      // Act & Assert
      await expect(
        axiosWithInvalidAuth.get('/partners')
      ).rejects.toThrow();
    }, TEST_TIMEOUT);

    it('should_propagateJWT_when_validTokenProvided', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 20 };

      // Act - Should succeed with valid token
      const response = await listPartners(filters, sort, pagination);

      // Assert
      expect(response).toBeDefined();
      expect(response.content).toBeInstanceOf(Array);
    }, TEST_TIMEOUT);
  });

  describe('Partner Statistics API Integration', () => {
    it('should_fetchStatistics_when_statisticsEndpointCalled', async () => {
      // Act
      const statistics = await getPartnerStatistics();

      // Assert
      expect(statistics).toBeDefined();
      expect(statistics.totalPartners).toBeGreaterThanOrEqual(0);
      expect(statistics.activePartners).toBeGreaterThanOrEqual(0);
      expect(statistics.tierDistribution).toBeDefined();
      expect(statistics.tierDistribution).toHaveProperty('STRATEGIC');
      expect(statistics.tierDistribution).toHaveProperty('PLATINUM');
      expect(statistics.tierDistribution).toHaveProperty('GOLD');
      expect(statistics.tierDistribution).toHaveProperty('SILVER');
      expect(statistics.tierDistribution).toHaveProperty('BRONZE');
    }, TEST_TIMEOUT);

    it('should_matchListCount_when_comparingStatisticsToList', async () => {
      // Arrange
      const filters: PartnerFilters = { tier: 'all', status: 'all' };
      const sort: PartnerSort = { sortBy: 'name', sortOrder: 'asc' };
      const pagination: PartnerPagination = { page: 0, size: 100 }; // Large size to get all

      // Act
      const listResponse = await listPartners(filters, sort, pagination);
      const statistics = await getPartnerStatistics();

      // Assert - Statistics total should match pagination total
      expect(statistics.totalPartners).toBe(listResponse.pagination.totalElements);
    }, TEST_TIMEOUT);
  });
});
