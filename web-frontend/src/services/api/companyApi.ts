/**
 * Company API Client
 *
 * HTTP client for Company Management Service APIs
 * Based on Story 1.14 backend API contracts
 * Integrates with Story 1.11 security and Story 1.9 error handling
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type {
  Company,
  CompanyListResponse,
  CreateCompanyRequest,
  UpdateCompanyRequest,
  CompanyFilters,
  PaginationParams,
  PresignedUrlResponse,
  LogoUploadConfirmation
} from '@/types/company.types';

// API base path for company endpoints
// Note: apiClient already has baseURL='/api', so we only need '/v1/companies'
const COMPANY_API_PATH = '/v1/companies';

// File upload constraints
const MAX_LOGO_SIZE_MB = 5;
const ALLOWED_LOGO_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp'];

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
   */
  async getCompanies(
    pagination: PaginationParams = { page: 1, limit: 20 },
    filters?: CompanyFilters
  ): Promise<CompanyListResponse> {
    try {
      const params = new URLSearchParams();
      params.append('page', pagination.page.toString());
      params.append('limit', pagination.limit.toString());

      if (filters?.isPartner !== undefined) {
        params.append('isPartner', filters.isPartner.toString());
      }
      if (filters?.isVerified !== undefined) {
        params.append('isVerified', filters.isVerified.toString());
      }
      if (filters?.industry) {
        params.append('industry', filters.industry);
      }
      if (filters?.searchQuery) {
        params.append('search', filters.searchQuery);
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
   * Get single company by ID with optional resource expansion
   */
  async getCompany(
    id: string,
    options?: { expand?: string[] }
  ): Promise<Company> {
    try {
      const params = new URLSearchParams();
      if (options?.expand && options.expand.length > 0) {
        params.append('expand', options.expand.join(','));
      }

      const url = params.toString()
        ? `${COMPANY_API_PATH}/${id}?${params.toString()}`
        : `${COMPANY_API_PATH}/${id}`;

      const response = await apiClient.get<Company>(url);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Search companies by query string
   */
  async searchCompanies(
    query: string,
    pagination: PaginationParams = { page: 1, limit: 20 }
  ): Promise<CompanyListResponse> {
    try {
      const params = new URLSearchParams();
      params.append('q', query);
      params.append('page', pagination.page.toString());
      params.append('limit', pagination.limit.toString());

      const response = await apiClient.get<CompanyListResponse>(
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
   */
  async updateCompany(id: string, data: UpdateCompanyRequest): Promise<Company> {
    try {
      // Client-side validation for Swiss UID format
      if (data.swissUID && !this.isValidSwissUID(data.swissUID)) {
        throw new Error('Invalid Swiss UID format. Expected format: CHE-XXX.XXX.XXX');
      }

      const response = await apiClient.patch<Company>(`${COMPANY_API_PATH}/${id}`, data);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Delete company (soft delete)
   */
  async deleteCompany(id: string): Promise<void> {
    try {
      await apiClient.delete(`${COMPANY_API_PATH}/${id}`);
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Request presigned URL for logo upload
   */
  async requestLogoUploadUrl(
    companyId: string,
    fileName: string,
    contentType: string,
    fileSize?: number
  ): Promise<PresignedUrlResponse> {
    try {
      // Validate file type
      if (!ALLOWED_LOGO_TYPES.includes(contentType)) {
        throw new Error(
          `Unsupported file type: ${contentType}. Allowed types: ${ALLOWED_LOGO_TYPES.join(', ')}`
        );
      }

      // Validate file size
      if (fileSize && fileSize > MAX_LOGO_SIZE_MB * 1024 * 1024) {
        throw new Error(`File size exceeds maximum allowed size of ${MAX_LOGO_SIZE_MB}MB`);
      }

      const response = await apiClient.post<PresignedUrlResponse>(
        `${COMPANY_API_PATH}/${companyId}/logo/upload-url`,
        {
          fileName,
          contentType,
          fileSize
        }
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Confirm logo upload completion
   */
  async confirmLogoUpload(
    companyId: string,
    fileId: string
  ): Promise<LogoUploadConfirmation> {
    try {
      const response = await apiClient.post<LogoUploadConfirmation>(
        `${COMPANY_API_PATH}/${companyId}/logo/confirm`,
        { fileId }
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

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
