/**
 * Company API Client Tests
 *
 * Comprehensive tests for companyApiClient HTTP methods
 * Tests all API methods with mocked responses
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { companyApiClient } from './companyApi';
import apiClient from './apiClient';
import type { CreateCompanyRequest, UpdateCompanyRequest } from '@/types/company.types';

// Mock the apiClient module
vi.mock('./apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('companyApiClient', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getCompanies', () => {
    it('should fetch companies with default pagination', async () => {
      const mockResponse = {
        data: [
          {
            id: 'company-1',
            name: 'TechCorp AG',
            swissUID: 'CHE-123.456.789',
            industry: 'Technology',
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await companyApiClient.getCompanies();

      expect(apiClient.get).toHaveBeenCalledWith('/companies?page=1&limit=20');
      expect(result).toEqual(mockResponse);
    });

    it('should fetch companies with custom pagination', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 2,
          limit: 50,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: true,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await companyApiClient.getCompanies({ page: 2, limit: 50 });

      expect(apiClient.get).toHaveBeenCalledWith('/companies?page=2&limit=50');
      expect(result).toEqual(mockResponse);
    });

    it('should fetch companies with filters', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      await companyApiClient.getCompanies(
        { page: 1, limit: 20 },
        { isVerified: true, industry: 'Technology' }
      );

      const call = vi.mocked(apiClient.get).mock.calls[0][0];
      expect(call).toContain('page=1');
      expect(call).toContain('limit=20');
      expect(call).toContain('filter=');
    });

    it('should use search endpoint when searchQuery provided', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 0,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      await companyApiClient.getCompanies({ page: 1, limit: 20 }, { searchQuery: 'TechCorp' });

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('/companies/search'));
    });

    it('should include expand parameter when provided', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      await companyApiClient.getCompanies({ page: 1, limit: 20 }, undefined, {
        expand: ['logo', 'statistics'],
      });

      const call = vi.mocked(apiClient.get).mock.calls[0][0];
      expect(call).toContain('include=logo,statistics');
    });
  });

  describe('getCompany', () => {
    it('should fetch single company by name', async () => {
      const mockCompany = {
        id: 'company-1',
        name: 'TechCorp AG',
        swissUID: 'CHE-123.456.789',
        industry: 'Technology',
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCompany });

      const result = await companyApiClient.getCompany('TechCorp AG');

      expect(apiClient.get).toHaveBeenCalledWith('/companies/TechCorp AG');
      expect(result).toEqual(mockCompany);
    });

    it('should fetch company with expand options', async () => {
      const mockCompany = {
        id: 'company-1',
        name: 'TechCorp AG',
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCompany });

      await companyApiClient.getCompany('TechCorp AG', { expand: ['logo'] });

      expect(apiClient.get).toHaveBeenCalledWith('/companies/TechCorp AG?expand=logo');
    });

    it('should handle errors gracefully', async () => {
      const error = new Error('Company not found');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(companyApiClient.getCompany('NonExistent')).rejects.toThrow();
    });
  });

  describe('searchCompanies', () => {
    it('should search companies with query', async () => {
      const mockCompanies = [
        {
          id: 'company-1',
          name: 'TechCorp AG',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCompanies });

      const result = await companyApiClient.searchCompanies('Tech');

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('/companies/search'));
      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('query=Tech'));
      expect(result).toEqual(mockCompanies);
    });

    it('should search with custom limit', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      await companyApiClient.searchCompanies('Tech', 50);

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('limit=50'));
    });

    it('should search with expand options', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      await companyApiClient.searchCompanies('Tech', 20, { expand: ['logo'] });

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('include=logo'));
    });
  });

  describe('searchCompaniesWithPagination', () => {
    it('should wrap search results in pagination format', async () => {
      const mockCompanies = [
        { id: 'company-1', name: 'TechCorp AG' },
        { id: 'company-2', name: 'TechSoft GmbH' },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCompanies });

      const result = await companyApiClient.searchCompaniesWithPagination('Tech');

      expect(result.data).toEqual(mockCompanies);
      expect(result.pagination).toEqual({
        page: 1,
        limit: 20,
        totalItems: 2,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      });
    });

    it('should use custom pagination params', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      await companyApiClient.searchCompaniesWithPagination('Tech', { page: 2, limit: 50 });

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('limit=50'));
    });
  });

  describe('createCompany', () => {
    it('should create company with valid data', async () => {
      const request: CreateCompanyRequest = {
        name: 'NewCorp AG',
        swissUID: 'CHE-111.222.333',
        industry: 'Technology',
        location: {
          city: 'Bern',
          canton: 'BE',
          country: 'Switzerland',
        },
      };

      const mockResponse = {
        id: 'company-new',
        ...request,
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await companyApiClient.createCompany(request);

      expect(apiClient.post).toHaveBeenCalledWith('/companies', request);
      expect(result).toEqual(mockResponse);
    });

    it('should reject invalid Swiss UID format', async () => {
      const request: CreateCompanyRequest = {
        name: 'InvalidCorp',
        swissUID: 'INVALID-FORMAT',
        industry: 'Technology',
        location: {
          city: 'Bern',
          canton: 'BE',
          country: 'Switzerland',
        },
      };

      await expect(companyApiClient.createCompany(request)).rejects.toThrow(
        'Invalid Swiss UID format'
      );

      expect(apiClient.post).not.toHaveBeenCalled();
    });

    it('should create company without Swiss UID', async () => {
      const request: CreateCompanyRequest = {
        name: 'NoCorp',
        industry: 'Technology',
        location: {
          city: 'Bern',
          canton: 'BE',
          country: 'Switzerland',
        },
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: { id: 'company-1', ...request } });

      await companyApiClient.createCompany(request);

      expect(apiClient.post).toHaveBeenCalledWith('/companies', request);
    });
  });

  describe('updateCompany', () => {
    it('should update company with valid data', async () => {
      const updates: UpdateCompanyRequest = {
        industry: 'Finance',
        swissUID: 'CHE-999.888.777',
      };

      const mockResponse = {
        id: 'company-1',
        name: 'TechCorp AG',
        ...updates,
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await companyApiClient.updateCompany('TechCorp AG', updates);

      expect(apiClient.put).toHaveBeenCalledWith('/companies/TechCorp AG', updates);
      expect(result).toEqual(mockResponse);
    });

    it('should reject invalid Swiss UID in update', async () => {
      const updates: UpdateCompanyRequest = {
        swissUID: 'INVALID',
      };

      await expect(companyApiClient.updateCompany('TechCorp AG', updates)).rejects.toThrow(
        'Invalid Swiss UID format'
      );

      expect(apiClient.put).not.toHaveBeenCalled();
    });

    it('should update company without Swiss UID', async () => {
      const updates: UpdateCompanyRequest = {
        industry: 'Healthcare',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: { id: 'company-1', ...updates } });

      await companyApiClient.updateCompany('TechCorp AG', updates);

      expect(apiClient.put).toHaveBeenCalledWith('/companies/TechCorp AG', updates);
    });
  });

  describe('deleteCompany', () => {
    it('should delete company by name', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: undefined });

      await companyApiClient.deleteCompany('TechCorp AG');

      expect(apiClient.delete).toHaveBeenCalledWith('/companies/TechCorp AG');
    });

    it('should handle deletion errors', async () => {
      const error = new Error('Company not found');
      vi.mocked(apiClient.delete).mockRejectedValue(error);

      await expect(companyApiClient.deleteCompany('NonExistent')).rejects.toThrow();
    });
  });

  describe('Error Handling', () => {
    it('should transform network errors', async () => {
      const error = new Error('Network Error');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(companyApiClient.getCompanies()).rejects.toThrow();
    });

    it('should transform API errors', async () => {
      const error = { response: { status: 404, data: { message: 'Not found' } } };
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(companyApiClient.getCompany('NonExistent')).rejects.toThrow();
    });
  });

  describe('Swiss UID Validation', () => {
    const validUIDs = ['CHE-123.456.789', 'CHE-999.888.777', 'CHE-111.222.333'];

    const invalidUIDs = [
      'INVALID',
      'CHE123456789',
      'CHE-1234567',
      '123.456.789',
      'CHE-ABC.DEF.GHI',
    ];

    validUIDs.forEach((uid) => {
      it(`should accept valid UID: ${uid}`, async () => {
        const request: CreateCompanyRequest = {
          name: 'TestCorp',
          swissUID: uid,
          industry: 'Technology',
          location: { city: 'Bern', canton: 'BE', country: 'Switzerland' },
        };

        vi.mocked(apiClient.post).mockResolvedValue({ data: { id: 'test', ...request } });

        await companyApiClient.createCompany(request);

        expect(apiClient.post).toHaveBeenCalled();
      });
    });

    invalidUIDs.forEach((uid) => {
      it(`should reject invalid UID: ${uid}`, async () => {
        const request: CreateCompanyRequest = {
          name: 'TestCorp',
          swissUID: uid,
          industry: 'Technology',
          location: { city: 'Bern', canton: 'BE', country: 'Switzerland' },
        };

        await expect(companyApiClient.createCompany(request)).rejects.toThrow();
        expect(apiClient.post).not.toHaveBeenCalled();
      });
    });
  });

  describe('Client Singleton', () => {
    it('should export a singleton instance', () => {
      expect(companyApiClient).toBeDefined();
      expect(typeof companyApiClient.getCompanies).toBe('function');
      expect(typeof companyApiClient.getCompany).toBe('function');
      expect(typeof companyApiClient.searchCompanies).toBe('function');
      expect(typeof companyApiClient.createCompany).toBe('function');
      expect(typeof companyApiClient.updateCompany).toBe('function');
      expect(typeof companyApiClient.deleteCompany).toBe('function');
    });
  });
});
