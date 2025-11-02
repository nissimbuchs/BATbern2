/**
 * Company API Client
 *
 * HTTP client for Company Management Service APIs
 * Based on Story 1.14 backend API contracts
 * Integrates with Story 1.11 security and Story 1.9 error handling
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type { components } from '@/types/generated/company-api.types';
import type { CompanyFilters, PaginationParams } from '@/types/company.types';

// Type aliases for cleaner code
type Company = components['schemas']['CompanyResponse'];
type CompanyListResponse = components['schemas']['PaginatedCompanyResponse'];
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

      // Build MongoDB-style JSON filter object
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

      // Add include parameter for resource expansion (e.g., ?include=logo,statistics)
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
   * Get single company by name with optional resource expansion
   * Story 1.16.2: Uses company name as identifier instead of UUID
   */
  async getCompany(name: string, options?: { expand?: string[] }): Promise<Company> {
    try {
      const params = new URLSearchParams();
      if (options?.expand && options.expand.length > 0) {
        params.append('expand', options.expand.join(','));
      }

      const url = params.toString()
        ? `${COMPANY_API_PATH}/${name}?${params.toString()}`
        : `${COMPANY_API_PATH}/${name}`;

      const response = await apiClient.get<Company>(url);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Search companies by query string (returns simple array, not paginated)
   */
  async searchCompanies(query: string, limit: number = 20): Promise<Company[]> {
    try {
      const params = new URLSearchParams();
      params.append('query', query);
      params.append('limit', limit.toString());

      const response = await apiClient.get<Company[]>(
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
      // Client-side validation for Swiss UID format
      if (data.swissUID && !this.isValidSwissUID(data.swissUID)) {
        throw new Error('Invalid Swiss UID format. Expected format: CHE-XXX.XXX.XXX');
      }

      const response = await apiClient.post<Company>(COMPANY_API_PATH, data);
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
      // Client-side validation for Swiss UID format
      if (data.swissUID && !this.isValidSwissUID(data.swissUID)) {
        throw new Error('Invalid Swiss UID format. Expected format: CHE-XXX.XXX.XXX');
      }

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

  /**
   * DEPRECATED: Logo upload methods removed in Story 1.16.3
   * Use the generic file upload API instead (see file-upload-api.openapi.yml)
   *
   * Migration path:
   * 1. POST /api/v1/logos/presigned-url - Generate upload URL
   * 2. PUT (presigned URL) - Upload file to S3
   * 3. POST /api/v1/logos/{uploadId}/confirm - Confirm upload
   * 4. POST /api/v1/companies (with logoUploadId) - Associate logo with company
   */

  // async requestLogoUploadUrl(...) - REMOVED - Use generic file upload API
  // async confirmLogoUpload(...) - REMOVED - Use generic file upload API

  /**
   * Validate Swiss UID format
   * @private
   */
  private isValidSwissUID(uid: string): boolean {
    const pattern = /^CHE-\d{3}\.\d{3}\.\d{3}$/;
    return pattern.test(uid.trim());
  }

  /**
   * Transform axios errors into standardized error format
   * Includes correlation ID from Story 1.9 error handling
   * @private
   */
  private transformError(error: unknown): Error {
    if (error instanceof AxiosError) {
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
