/**
 * Company Management Frontend-Specific Types
 *
 * TypeScript interfaces for Company Management feature
 * Based on Story 2.5.1 and backend API from Story 1.14
 *
 * IMPORTANT: For backend API types, import directly from './generated/company-api.types'
 * This file contains ONLY frontend-specific types and extensions.
 */

import type { components } from './generated/company-api.types';

// Re-export generated types for convenience
export type { components } from './generated/company-api.types';

// ============================================================================
// Frontend-Specific Types
// ============================================================================

/**
 * CompanyListItem - Simplified type for list views
 * Extends search response with optional logo for display
 */
export type CompanyListItem = components['schemas']['CompanySearchResponse'] & {
  logo?: components['schemas']['CompanyLogo'];
};

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
