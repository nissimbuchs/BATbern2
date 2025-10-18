/**
 * Company Management Types
 *
 * TypeScript interfaces for Company Management feature
 * Based on Story 2.5.1 and backend API from Story 1.14
 *
 * IMPORTANT: Core types are imported from auto-generated OpenAPI types.
 * This ensures type safety between frontend and backend.
 */

import type { components } from './generated/company-api.types';

// ============================================================================
// Core Types (from OpenAPI spec)
// ============================================================================

/**
 * Company - Full company data with optional expansions
 * Maps to CompanyResponse from backend
 */
export type Company = components['schemas']['CompanyResponse'];

/**
 * Company Statistics - Included when ?include=statistics
 */
export type CompanyStatistics = components['schemas']['CompanyStatistics'];

/**
 * Company Logo - Included when ?include=logo
 */
export type CompanyLogo = components['schemas']['CompanyLogo'];

/**
 * Create Company Request
 */
export type CreateCompanyRequest = components['schemas']['CreateCompanyRequest'];

/**
 * Update Company Request (for PATCH)
 */
export type UpdateCompanyRequest = components['schemas']['UpdateCompanyRequest'];

/**
 * Paginated Company Response
 */
export type PaginatedCompanyResponse = components['schemas']['PaginatedCompanyResponse'];

/**
 * Pagination Metadata
 */
export type PaginationMetadata = components['schemas']['PaginationMetadata'];

/**
 * Company Search Response Item
 */
export type CompanySearchResponse = components['schemas']['CompanySearchResponse'];

// ============================================================================
// Frontend-Specific Types (not in OpenAPI spec)
// ============================================================================

/**
 * CompanyListItem - Simplified type for list views
 * Extends search response with optional logo for display
 */
export type CompanyListItem = CompanySearchResponse & {
  logo?: CompanyLogo;
};

/**
 * CompanyDetail - Alias for full company data
 * Same as Company, kept for semantic clarity
 */
export type CompanyDetail = Company;

/**
 * Company Filters - Frontend filter state
 */
export interface CompanyFilters {
  isVerified?: boolean;
  industry?: string;
}

/**
 * CompanyStore - Zustand store interface
 */
export interface CompanyStore {
  filters: CompanyFilters;
  viewMode: 'grid' | 'list';
  selectedCompanyId?: string;
  isCreateModalOpen: boolean;
  isEditModalOpen: boolean;
  setFilters: (filters: CompanyFilters) => void;
  toggleViewMode: () => void;
  setSelectedCompanyId: (id?: string) => void;
  openCreateModal: () => void;
  closeCreateModal: () => void;
  openEditModal: (id: string) => void;
  closeEditModal: () => void;
}

/**
 * Pagination Parameters - Frontend request params
 */
export interface PaginationParams {
  page: number;
  limit: number;
}

/**
 * CompanyListResponse - Paginated list response
 * Alias for the OpenAPI type
 */
export type CompanyListResponse = PaginatedCompanyResponse;

/**
 * PaginationMeta - Alias for metadata
 * Kept for backward compatibility
 */
export type PaginationMeta = PaginationMetadata;

// ============================================================================
// Logo Upload Types (not in main OpenAPI spec yet)
// ============================================================================

/**
 * Presigned URL Response for logo upload
 */
export interface PresignedUrlResponse {
  presignedUrl: string;
  fileId: string;
  expiresAt: string;
}

/**
 * Logo Upload Confirmation Response
 */
export interface LogoUploadConfirmation {
  logoUrl: string;
  logoS3Key: string;
  logoFileId: string;
}
