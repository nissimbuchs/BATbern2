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
 * CompanyListItem - Type for list views
 * Based on CompanyResponse which includes all fields including website
 */
export type CompanyListItem = components['schemas']['CompanyResponse'];

/**
 * Company Filters - Frontend filter state
 */
export interface CompanyFilters {
  isVerified?: boolean;
  industry?: string;
  searchQuery?: string;
}

/**
 * CompanyStore - Zustand store interface
 * Story 1.16.2: Uses company name as identifier instead of UUID
 */
export interface CompanyStore {
  filters: CompanyFilters;
  viewMode: 'grid' | 'list';
  selectedCompanyName?: string;
  isCreateModalOpen: boolean;
  isEditModalOpen: boolean;
  setFilters: (filters: CompanyFilters) => void;
  toggleViewMode: () => void;
  setSelectedCompanyName: (name?: string) => void;
  openCreateModal: () => void;
  closeCreateModal: () => void;
  openEditModal: (name: string) => void;
  closeEditModal: () => void;
}

/**
 * Pagination Parameters - Frontend request params
 */
export interface PaginationParams {
  page: number;
  limit: number;
}
