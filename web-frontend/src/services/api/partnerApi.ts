/**
 * Partner API Client (GREEN Phase - Story 2.8.1, Task 1b)
 *
 * Provides API integration for Partner Coordination Service
 * - List partners with filtering, sorting, pagination
 * - HTTP enrichment via ?include=company,contacts (ADR-004)
 * - Statistics endpoint for partner overview metrics
 *
 * CRITICAL: Path is '/partners' WITHOUT /api/v1 prefix (baseURL already includes it)
 */

import apiClient from './apiClient';

// CRITICAL: Use path WITHOUT /api/v1 prefix (already in baseURL)
const PARTNER_API_PATH = '/partners';

/**
 * Partnership tier levels (matches OpenAPI enum)
 */
export type PartnershipLevel = 'bronze' | 'silver' | 'gold' | 'platinum' | 'strategic';

/**
 * Filter parameters for partner list
 */
export interface PartnerFilters {
  tier: PartnershipLevel | 'all';
  status: 'all' | 'active' | 'inactive';
}

/**
 * Pagination parameters
 */
export interface PartnerPagination {
  page: number; // Zero-based indexing
  size: number;
}

/**
 * Sort parameters
 */
export interface PartnerSort {
  sortBy: 'engagement' | 'name' | 'tier' | 'lastEvent';
  sortOrder: 'asc' | 'desc';
}

/**
 * Partner response (will be replaced with generated types)
 * Temporary interface for initial implementation
 */
export interface PartnerResponse {
  id: string;
  companyName: string;
  partnershipLevel: PartnershipLevel;
  partnershipStartDate: string;
  partnershipEndDate?: string;
  isActive: boolean;
  company?: {
    companyName: string;
    displayName: string;
    logoUrl?: string;
    industry?: string;
  };
  contacts?: Array<{
    id: string;
    username: string;
    contactRole: string;
    isPrimary: boolean;
    email?: string;
    firstName?: string;
    lastName?: string;
    profilePictureUrl?: string;
  }>;
  lastEventName?: string;
  votesCount?: number;
  nextMeetingDate?: string;
}

/**
 * Partner list response with pagination metadata
 */
export interface PartnerListResponse {
  data: PartnerResponse[];
  metadata: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

/**
 * Partner statistics response (Epic 8 feature - placeholder)
 */
export interface PartnerStatistics {
  totalPartners: number;
  activePartners: number;
  engagedPercentage?: number; // Epic 8
  tierDistribution: {
    strategic: number;
    platinum: number;
    gold: number;
    silver: number;
    bronze: number;
  };
}

/**
 * List partners with filters, sorting, and pagination
 * Includes HTTP enrichment via ?include=company,contacts (ADR-004)
 *
 * @param filters - Tier and status filters
 * @param sort - Sort configuration
 * @param pagination - Page and size parameters
 * @returns Promise<PartnerListResponse>
 */
export const listPartners = async (
  filters: PartnerFilters,
  sort: PartnerSort,
  pagination: PartnerPagination
): Promise<PartnerListResponse> => {
  const params: Record<string, string | number> = {
    page: pagination.page,
    size: pagination.size,
    include: 'company,contacts', // ADR-004 HTTP enrichment
  };

  // Build filter query parameter
  const filterParts: string[] = [];

  if (filters.tier && filters.tier !== 'all') {
    filterParts.push(`partnershipLevel:${filters.tier}`);
  }

  if (filters.status === 'active') {
    filterParts.push('isActive:true');
  } else if (filters.status === 'inactive') {
    filterParts.push('isActive:false');
  }

  if (filterParts.length > 0) {
    params.filter = filterParts.join(',');
  }

  // Build sort query parameter
  params.sort = `${sort.sortBy}:${sort.sortOrder}`;

  const response = await apiClient.get<PartnerListResponse>(PARTNER_API_PATH, { params });
  // ✅ Resolves to: http://localhost:8080/api/v1/partners?page=0&size=20&include=company,contacts&filter=...&sort=...
  return response.data;
};

/**
 * Get partner statistics for overview dashboard
 * Epic 8 feature - currently returns mock data
 *
 * @returns Promise<PartnerStatistics>
 */
export const getPartnerStatistics = async (): Promise<PartnerStatistics> => {
  const response = await apiClient.get<PartnerStatistics>(`${PARTNER_API_PATH}/statistics`);
  return response.data;
};
