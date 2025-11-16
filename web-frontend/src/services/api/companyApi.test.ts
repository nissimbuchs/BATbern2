/**
 * Company API Client Tests (GREEN Phase)
 *
 * TDD tests for API client methods focusing on implementation logic
 * Note: Full API integration tests with MSW deferred due to Vite/MSW compatibility
 * These tests verify client-side validation and structure
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { companyApiClient } from '@/services/api/companyApi';
import type { CreateCompanyRequest, UpdateCompanyRequest } from '@/types/company.types';
import apiClient from '@/services/api/apiClient';

describe('Company API Client', () => {
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

  describe('Client-side Validation', () => {
    describe('createCompany', () => {
      it('should reject invalid Swiss UID format', async () => {
        const invalidCompany: CreateCompanyRequest = {
          name: 'Invalid UID Corp',
          swissUID: 'INVALID-FORMAT',
          industry: 'Technology',
          location: {
            city: 'Bern',
            canton: 'BE',
            country: 'Switzerland',
          },
        };

        await expect(companyApiClient.createCompany(invalidCompany)).rejects.toThrow(
          /Invalid Swiss UID format/
        );
      });

      it('should accept valid Swiss UID format', async () => {
        const validCompany: CreateCompanyRequest = {
          name: 'Valid Corp',
          swissUID: 'CHE-123.456.789',
          industry: 'Technology',
          location: {
            city: 'Bern',
            canton: 'BE',
            country: 'Switzerland',
          },
        };

        // If validation passes, the function should attempt to make the API call
        // In test environment without backend, this will fail with network/timeout error or 500
        // We expect network/server/timeout error (not validation error)
        await expect(companyApiClient.createCompany(validCompany)).rejects.toThrow(
          /(Network Error|status code 500|timeout)/
        );
      });
    });

    describe('updateCompany', () => {
      it('should reject invalid Swiss UID format in update', async () => {
        const updates: UpdateCompanyRequest = {
          swissUID: 'INVALID-UID',
        };

        await expect(companyApiClient.updateCompany('test-id', updates)).rejects.toThrow(
          /Invalid Swiss UID format/
        );
      });

      it('should accept valid Swiss UID format in update', async () => {
        const updates: UpdateCompanyRequest = {
          swissUID: 'CHE-999.888.777',
        };

        // Expect network/server/timeout error (not validation error)
        await expect(companyApiClient.updateCompany('test-id', updates)).rejects.toThrow(
          /(Network Error|status code 500|timeout)/
        );
      });
    });

    // DEPRECATED: requestLogoUploadUrl tests removed in Story 1.16.3
    // Logo upload now uses generic file upload API (see file-upload-api.openapi.yml)

    describe('deleteCompany', () => {
      it('should attempt to delete company', async () => {
        // Expect network/server/timeout error (not validation error)
        await expect(companyApiClient.deleteCompany('test-company')).rejects.toThrow(
          /(Network Error|status code 500|timeout)/
        );
      });
    });
  });

  describe('API Structure', () => {
    it('should have all required methods', () => {
      expect(companyApiClient).toHaveProperty('getCompanies');
      expect(companyApiClient).toHaveProperty('getCompany');
      expect(companyApiClient).toHaveProperty('searchCompanies');
      expect(companyApiClient).toHaveProperty('createCompany');
      expect(companyApiClient).toHaveProperty('updateCompany');
      expect(companyApiClient).toHaveProperty('deleteCompany');
      // Note: requestLogoUploadUrl and confirmLogoUpload removed in Story 1.16.3
      // Logo uploads now use generic file upload API
    });

    it('should have methods that return promises', () => {
      // All methods should be async functions - catch errors to prevent unhandled rejections
      const promise1 = companyApiClient.getCompanies().catch(() => {});
      const promise2 = companyApiClient.getCompany('test-id').catch(() => {});
      const promise3 = companyApiClient.searchCompanies('test').catch(() => {});

      expect(promise1).toBeInstanceOf(Promise);
      expect(promise2).toBeInstanceOf(Promise);
      expect(promise3).toBeInstanceOf(Promise);
    });
  });
});
