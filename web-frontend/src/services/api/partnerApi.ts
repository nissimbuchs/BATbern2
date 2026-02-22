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

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/partner-api.types';

// Type aliases for cleaner code
export type PartnerResponse = components['schemas']['PartnerResponse'];
export type PartnerListResponse = components['schemas']['PartnerListResponse'];
export type PartnershipLevel = components['schemas']['PartnershipLevel'];
export type TopicVoteResponse = components['schemas']['TopicVoteResponse'];
export type CreatePartnerRequest = components['schemas']['CreatePartnerRequest'];
export type UpdatePartnerRequest = components['schemas']['UpdatePartnerRequest'];
export type PartnerContactResponse = components['schemas']['PartnerContactResponse'];
export type TopicSuggestionResponse = components['schemas']['TopicSuggestionResponse'];
export type SubmitSuggestionRequest = components['schemas']['SubmitSuggestionRequest'];
export type PartnerStatistics = components['schemas']['PartnerStatistics'];

// API base path for partner endpoints
// CRITICAL: Use path WITHOUT /api/v1 prefix (already in baseURL)
// Note: apiClient baseURL is set from runtime config to 'http://localhost:8080/api/v1'
// so we only need '/partners' (the /v1 prefix is already in the baseURL)
const PARTNER_API_PATH = '/partners';

// ============================================================================
// Supporting types for Story 2.8.1 (Partner Directory)
// ============================================================================

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

// ============================================================================
// Supporting types for Story 2.8.2 (Partner Detail View)
// ============================================================================

/**
 * Activity filter parameters
 */
export interface ActivityFilters {
  type?: 'vote' | 'meeting' | 'note' | 'partnership_change';
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

// ============================================================================
// Story 2.8.2: Partner Detail View APIs
// ============================================================================

/**
 * Get partner detail by companyName with optional includes
 * Story 2.8.2
 * @param companyName - Company name (meaningful ID)
 * @param include - Optional comma-separated includes (company,contacts,votes,meetings,activity)
 * @returns PartnerResponse with enriched data
 */
export const getPartnerDetail = async (
  companyName: string,
  include?: string
): Promise<PartnerResponse> => {
  const params: Record<string, string> = {};
  if (include) {
    params.include = include;
  }
  const response = await apiClient.get(`${PARTNER_API_PATH}/${companyName}`, { params });
  return response.data;
};

/**
 * Get partner topic votes
 * Story 2.8.2
 * @param companyName - Company name (meaningful ID)
 * @returns Array of TopicVoteResponse
 */
export const getPartnerVotes = async (companyName: string): Promise<TopicVoteResponse[]> => {
  const response = await apiClient.get(`${PARTNER_API_PATH}/${companyName}/votes`);
  return response.data;
};

// TODO: Future feature - Add to OpenAPI spec when implemented
// /**
//  * Get partner meetings
//  * Story 2.8.2
//  * @param companyName - Company name (meaningful ID)
//  * @returns Array of MeetingResponse
//  */
// export const getPartnerMeetings = async (companyName: string): Promise<MeetingResponse[]> => {
//   const response = await apiClient.get(`${PARTNER_API_PATH}/${companyName}/meetings`);
//   return response.data;
// };

// TODO: Future feature - Add to OpenAPI spec when implemented
// /**
//  * Get partner activity timeline
//  * Story 2.8.2
//  * @param companyName - Company name (meaningful ID)
//  * @param filters - Optional activity filters (type)
//  * @returns Array of ActivityResponse
//  */
// export const getPartnerActivity = async (
//   companyName: string,
//   filters?: ActivityFilters
// ): Promise<ActivityResponse[]> => {
//   const params: Record<string, string> = {};
//   if (filters?.type) {
//     params.filter = `type:${filters.type}`;
//   }
//   const response = await apiClient.get(`${PARTNER_API_PATH}/${companyName}/activity`, { params });
//   return response.data;
// };

// TODO: Future feature - Add to OpenAPI spec when implemented
// /**
//  * Get partner notes
//  * Story 2.8.2
//  * @param companyName - Company name (meaningful ID)
//  * @returns Array of NoteResponse
//  */
// export const getPartnerNotes = async (companyName: string): Promise<NoteResponse[]> => {
//   const response = await apiClient.get(`${PARTNER_API_PATH}/${companyName}/notes`);
//   return response.data;
// };

// TODO: Future feature - Add to OpenAPI spec when implemented
// /**
//  * Create a new partner note
//  * Story 2.8.2
//  * @param companyName - Company name (meaningful ID)
//  * @param note - CreateNoteRequest payload
//  * @returns Created NoteResponse
//  */
// export const createPartnerNote = async (
//   companyName: string,
//   note: CreateNoteRequest
// ): Promise<NoteResponse> => {
//   const response = await apiClient.post(`${PARTNER_API_PATH}/${companyName}/notes`, note);
//   return response.data;
// };

// TODO: Future feature - Add to OpenAPI spec when implemented
// /**
//  * Update an existing partner note
//  * Story 2.8.2
//  * @param companyName - Company name (meaningful ID)
//  * @param noteId - Note ID to update
//  * @param note - UpdateNoteRequest payload
//  * @returns Updated NoteResponse
//  */
// export const updatePartnerNote = async (
//   companyName: string,
//   noteId: string,
//   note: UpdateNoteRequest
// ): Promise<NoteResponse> => {
//   const response = await apiClient.patch(
//     `${PARTNER_API_PATH}/${companyName}/notes/${noteId}`,
//     note
//   );
//   return response.data;
// };

// TODO: Future feature - Add to OpenAPI spec when implemented
// /**
//  * Delete a partner note
//  * Story 2.8.2
//  * @param companyName - Company name (meaningful ID)
//  * @param noteId - Note ID to delete
//  * @returns void
//  */
// export const deletePartnerNote = async (companyName: string, noteId: string): Promise<void> => {
//   await apiClient.delete(`${PARTNER_API_PATH}/${companyName}/notes/${noteId}`);
// };

// ============================================================================
// Story 2.8.3: Partner Create/Edit Modal APIs
// ============================================================================

/**
 * Create a new partnership
 * Story 2.8.3
 * @param request - CreatePartnerRequest payload
 * @returns Created PartnerResponse
 */
export const createPartner = async (request: CreatePartnerRequest): Promise<PartnerResponse> => {
  const response = await apiClient.post<PartnerResponse>(PARTNER_API_PATH, request);
  return response.data;
};

/**
 * Update an existing partnership
 * Story 2.8.3
 * @param companyName - Company name (meaningful ID)
 * @param request - UpdatePartnerRequest payload
 * @returns Updated PartnerResponse
 */
export const updatePartner = async (
  companyName: string,
  request: UpdatePartnerRequest
): Promise<PartnerResponse> => {
  const response = await apiClient.patch<PartnerResponse>(
    `${PARTNER_API_PATH}/${companyName}`,
    request
  );
  return response.data;
};

/**
 * Get partner contacts for a company.
 * Returns all users with PARTNER role and matching companyId from User Service.
 *
 * @param companyName - Company name (meaningful ID per ADR-003)
 * @returns Array of PartnerContactResponse
 */
export const getPartnerContacts = async (
  companyName: string
): Promise<PartnerContactResponse[]> => {
  const response = await apiClient.get<PartnerContactResponse[]>(
    `${PARTNER_API_PATH}/${companyName}/contacts`
  );
  return response.data;
};
