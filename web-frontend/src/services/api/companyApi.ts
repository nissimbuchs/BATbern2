/**
 * Company API Client
 *
 * HTTP client for Company Management Service APIs
 * Based on Story 1.14 backend API contracts
 * Integrates with Story 1.11 security and Story 1.9 error handling
 */

import apiClient from '@/services/api/apiClient';
import { isAxiosError } from 'axios';
import type { components } from '@/types/generated/company-api.types';
import type { CompanyFilters, PaginationParams } from '@/types/company.types';

// Type aliases for cleaner code
type Company = components['schemas']['CompanyResponse'];
type CompanyListResponse = components['schemas']['PaginatedCompanyResponse'];
type CompanySearchResult = components['schemas']['CompanySearchResponse'];
type CreateCompanyRequest = components['schemas']['CreateCompanyRequest'];
type UpdateCompanyRequest = components['schemas']['UpdateCompanyRequest'];
// Note: Logo upload types removed in Story 1.16.3 - use generic file upload API instead

// API base path for company endpoints
// Note: apiClient baseURL is set from runtime config to 'http://localhost:8080/api/v1'
// so we only need '/companies' (the /v1 prefix is already in the baseURL)
const COMPANY_API_PATH = '/companies';

/**
 * Company API Client Class
 *
 * Handles all HTTP requests to the Company Management Service
 * Features:
 * - JWT authentication via interceptors (Story 1.17)
 * - Error handling with correlation IDs (Story 1.9)
 * - Security headers (Story 1.11)
 * - Request/response transformation
 */
class CompanyApiClient {
  /**
   * Get paginated list of companies with optional filters
   * Uses MongoDB-style JSON filter syntax as per backend API
   */
  async getCompanies(
    pagination: PaginationParams = { page: 1, limit: 20 },
    filters?: CompanyFilters,
    options?: { expand?: string[] }
  ): Promise<CompanyListResponse> {
    try {
      const params = new URLSearchParams();
      params.append('page', pagination.page.toString());
      params.append('limit', pagination.limit.toString());

      // Free-text search runs in the backend over every company before paging,
      // so totalItems and page 2 are real. The autocomplete endpoint returned one cut page.
      const search = filters?.searchQuery?.trim();
      if (search) {
        params.append('search', search);
      }

      // Build MongoDB-style JSON filter object for non-search filters
      const filterObj: Record<string, boolean | string> = {};
      if (filters?.isVerified !== undefined) {
        filterObj.isVerified = filters.isVerified;
      }
      if (filters?.industry) {
        filterObj.industry = filters.industry;
      }

      // Only add filter parameter if we have filters
      if (Object.keys(filterObj).length > 0) {
        params.append('filter', JSON.stringify(filterObj));
      }

      // Add include parameter for resource expansion (e.g., ?include=logo)
      // Backend uses 'include' not 'expand' for resource expansion
      if (options?.expand && options.expand.length > 0) {
        params.append('include', options.expand.join(','));
      }

      const response = await apiClient.get<CompanyListResponse>(
        `${COMPANY_API_PATH}?${params.toString()}`
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Every company matching `filters`, read page by page at the spec maximum of 100. For the batch
   * import duplicate check, which used to ask for `limit=1000` in one call (#1072 class; found by
   * the MSW harness, 17.0c1).
   */
  async getAllCompanies(
    filters?: CompanyFilters,
    options?: { expand?: string[] }
  ): Promise<CompanyListResponse> {
    const first = await this.getCompanies({ page: 1, limit: 100 }, filters, options);
    const data = [...first.data];
    let current = first;
    // Our own page counter, never the response's (a server repeating `page: 1, hasNext: true`
    // would loop forever). Bounded: 100 pages of 100 cover 10,000 companies.
    for (let page = 2; current.pagination.hasNext && page <= 100; page++) {
      current = await this.getCompanies({ page, limit: 100 }, filters, options);
      data.push(...current.data);
    }
    return {
      data,
      pagination: {
        ...current.pagination,
        page: 1,
        limit: data.length,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    };
  }

  /**
   * Get single company by name. Story 1.16.2: the name is the identifier.
   *
   * GET /companies/{name} declares no query parameter and always returns the logo. The
   * `?expand=logo` this used to add was ignored by CUMS (found by the MSW harness, 17.0c1).
   */
  async getCompany(name: string): Promise<Company> {
    try {
      const response = await apiClient.get<Company>(`${COMPANY_API_PATH}/${name}`);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Search companies by query string (returns simple array, not paginated)
   */
  async searchCompanies(
    query: string,
    limit: number = 20,
    options?: { expand?: string[] }
  ): Promise<CompanySearchResult[]> {
    try {
      const params = new URLSearchParams();
      params.append('query', query);
      params.append('limit', limit.toString());

      // Add include parameter for resource expansion (e.g., ?include=logo)
      if (options?.expand && options.expand.length > 0) {
        params.append('include', options.expand.join(','));
      }

      const response = await apiClient.get<CompanySearchResult[]>(
        `${COMPANY_API_PATH}/search?${params.toString()}`
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Create a new company
   */
  async createCompany(data: CreateCompanyRequest): Promise<Company> {
    try {
      const response = await apiClient.post<Company>(COMPANY_API_PATH, data);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get an existing company or create one from a display name (idempotent).
   *
   * Materialises a brand-new company picked in the autocomplete and returns its
   * canonical slug (`name`) so callers store a real company reference instead of
   * a free-typed string (ADR-003). Safe to call repeatedly — the backend resolves
   * the slug and returns the existing company if it already exists.
   */
  async getOrCreateCompany(displayName: string): Promise<Company> {
    try {
      // Story 4.10d: slash form, 201 when created, 200 when found (ADR-013 §2)
      const response = await apiClient.post<Company>(`${COMPANY_API_PATH}/get-or-create`, {
        displayName,
      });
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Update existing company
   * Story 1.16.2: Uses company name as identifier instead of UUID
   */
  async updateCompany(name: string, data: UpdateCompanyRequest): Promise<Company> {
    try {
      const response = await apiClient.patch<Company>(`${COMPANY_API_PATH}/${name}`, data);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Delete company (soft delete)
   * Story 1.16.2: Uses company name as identifier instead of UUID
   */
  async deleteCompany(name: string): Promise<void> {
    try {
      await apiClient.delete(`${COMPANY_API_PATH}/${name}`);
    } catch (error) {
      throw this.transformError(error);
    }
  }

  // Logo upload: the upload contract (`@/services/uploadService`, purpose `company-logo`, Story
  // 17.11); the create and edit take the CONFIRMED upload as `logoUploadId`.

  /**
   * Transform axios errors into standardized error format
   * Includes correlation ID from Story 1.9 error handling
   * @private
   */
  private transformError(error: unknown): Error {
    if (isAxiosError(error)) {
      const correlationId =
        error.config?.headers?.['X-Correlation-ID'] ||
        error.response?.headers?.['x-correlation-id'] ||
        'unknown';

      const status = error.response?.status;
      const message = error.response?.data?.message || error.message;

      const enhancedError = new Error(message) as Error & {
        status?: number;
        correlationId: string;
        originalError: unknown;
      };

      enhancedError.status = status;
      enhancedError.correlationId = correlationId;
      enhancedError.originalError = error;

      return enhancedError;
    }

    return error as Error;
  }
}

// Export singleton instance
export const companyApiClient = new CompanyApiClient();

// Exported for direct use. Bound: every method's catch path calls this.transformError, and a
// destructured method has no `this` (it rejected with a TypeError instead, found in 17.0c2).
export const getCompanies = companyApiClient.getCompanies.bind(companyApiClient);
export const getCompany = companyApiClient.getCompany.bind(companyApiClient);
export const searchCompanies = companyApiClient.searchCompanies.bind(companyApiClient);
export const createCompany = companyApiClient.createCompany.bind(companyApiClient);
export const updateCompany = companyApiClient.updateCompany.bind(companyApiClient);
export const deleteCompany = companyApiClient.deleteCompany.bind(companyApiClient);
export const getOrCreateCompany = companyApiClient.getOrCreateCompany.bind(companyApiClient);
