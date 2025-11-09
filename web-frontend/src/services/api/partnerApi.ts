/**
 * Partner API Client
 *
 * HTTP client for Partner Coordination Service APIs
 * Based on Story 2.7 backend API contracts
 * Integrates with Story 1.11 security and Story 1.9 error handling
 *
 * This skeleton file contains all method signatures for parallel development:
 * - Story 2.8.1: Partner Directory (listPartners, getPartnerStatistics)
 * - Story 2.8.2: Partner Detail View (getPartnerDetail, getPartnerVotes, getPartnerMeetings, getPartnerActivity, getPartnerNotes, createPartnerNote, updatePartnerNote, deletePartnerNote)
 * - Story 2.8.3: Partner Create/Edit Modal (createPartner, updatePartner)
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/partner-api.types';

// Type aliases for cleaner code
export type PartnerResponse = components['schemas']['PartnerResponse'];
export type PartnerListResponse = components['schemas']['PartnerListResponse'];
export type PartnerStatistics = components['schemas']['PartnerStatistics'];
export type PartnershipLevel = components['schemas']['PartnershipLevel'];
export type TopicVoteResponse = components['schemas']['TopicVoteResponse'];
export type MeetingResponse = components['schemas']['MeetingResponse'];
export type ActivityResponse = components['schemas']['ActivityResponse'];
export type NoteResponse = components['schemas']['NoteResponse'];
export type CreatePartnerRequest = components['schemas']['CreatePartnerRequest'];
export type UpdatePartnerRequest = components['schemas']['UpdatePartnerRequest'];
export type CreateNoteRequest = components['schemas']['CreateNoteRequest'];
export type UpdateNoteRequest = components['schemas']['UpdateNoteRequest'];

// API base path for partner endpoints
// Note: apiClient baseURL is set from runtime config to 'http://localhost:8080/api/v1'
// so we only need '/partners' (the /v1 prefix is already in the baseURL)
const PARTNER_API_PATH = '/partners';

// Helper interfaces for filters and pagination
export interface PartnerFilters {
  tier?: PartnershipLevel | 'all';
  status?: 'all' | 'active' | 'inactive';
}

export interface PartnerPagination {
  page: number;
  size: number;
}

export interface PartnerSort {
  sortBy: 'engagement' | 'name' | 'tier' | 'lastEvent';
  sortOrder: 'asc' | 'desc';
}

export interface ActivityFilters {
  type?: string;
}

// ============================================================================
// Story 2.8.1: Partner Directory APIs
// ============================================================================

/* eslint-disable @typescript-eslint/no-unused-vars */

/**
 * Get paginated list of partners with filters and sorting
 * Story 2.8.1
 * @param filters - Filter criteria (tier, status)
 * @param sort - Sort configuration (sortBy, sortOrder)
 * @param pagination - Pagination params (page, size)
 * @returns PartnerListResponse with paginated results
 */
export const listPartners = async (
  _filters: PartnerFilters,
  _sort: PartnerSort,
  _pagination: PartnerPagination
): Promise<PartnerListResponse> => {
  throw new Error('Not implemented - Story 2.8.1');
};

/**
 * Get partner statistics and aggregations
 * Story 2.8.1
 * @returns PartnerStatistics with counts and tier distribution
 */
export const getPartnerStatistics = async (): Promise<PartnerStatistics> => {
  throw new Error('Not implemented - Story 2.8.1');
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

/**
 * Get partner meetings
 * Story 2.8.2
 * @param companyName - Company name (meaningful ID)
 * @returns Array of MeetingResponse
 */
export const getPartnerMeetings = async (companyName: string): Promise<MeetingResponse[]> => {
  const response = await apiClient.get(`${PARTNER_API_PATH}/${companyName}/meetings`);
  return response.data;
};

/**
 * Get partner activity timeline
 * Story 2.8.2
 * @param companyName - Company name (meaningful ID)
 * @param filters - Optional activity filters (type)
 * @returns Array of ActivityResponse
 */
export const getPartnerActivity = async (
  companyName: string,
  filters?: ActivityFilters
): Promise<ActivityResponse[]> => {
  const params: Record<string, string> = {};
  if (filters?.type) {
    params.filter = `type:${filters.type}`;
  }
  const response = await apiClient.get(`${PARTNER_API_PATH}/${companyName}/activity`, { params });
  return response.data;
};

/**
 * Get partner notes
 * Story 2.8.2
 * @param companyName - Company name (meaningful ID)
 * @returns Array of NoteResponse
 */
export const getPartnerNotes = async (companyName: string): Promise<NoteResponse[]> => {
  const response = await apiClient.get(`${PARTNER_API_PATH}/${companyName}/notes`);
  return response.data;
};

/**
 * Create a new partner note
 * Story 2.8.2
 * @param companyName - Company name (meaningful ID)
 * @param note - CreateNoteRequest payload
 * @returns Created NoteResponse
 */
export const createPartnerNote = async (
  companyName: string,
  note: CreateNoteRequest
): Promise<NoteResponse> => {
  const response = await apiClient.post(`${PARTNER_API_PATH}/${companyName}/notes`, note);
  return response.data;
};

/**
 * Update an existing partner note
 * Story 2.8.2
 * @param companyName - Company name (meaningful ID)
 * @param noteId - Note ID to update
 * @param note - UpdateNoteRequest payload
 * @returns Updated NoteResponse
 */
export const updatePartnerNote = async (
  companyName: string,
  noteId: string,
  note: UpdateNoteRequest
): Promise<NoteResponse> => {
  const response = await apiClient.patch(
    `${PARTNER_API_PATH}/${companyName}/notes/${noteId}`,
    note
  );
  return response.data;
};

/**
 * Delete a partner note
 * Story 2.8.2
 * @param companyName - Company name (meaningful ID)
 * @param noteId - Note ID to delete
 * @returns void
 */
export const deletePartnerNote = async (companyName: string, noteId: string): Promise<void> => {
  await apiClient.delete(`${PARTNER_API_PATH}/${companyName}/notes/${noteId}`);
};

// ============================================================================
// Story 2.8.3: Partner Create/Edit Modal APIs
// ============================================================================

/**
 * Create a new partnership
 * Story 2.8.3
 * @param request - CreatePartnerRequest payload
 * @returns Created PartnerResponse
 */
export const createPartner = async (_request: CreatePartnerRequest): Promise<PartnerResponse> => {
  throw new Error('Not implemented - Story 2.8.3');
};

/**
 * Update an existing partnership
 * Story 2.8.3
 * @param companyName - Company name (meaningful ID)
 * @param request - UpdatePartnerRequest payload
 * @returns Updated PartnerResponse
 */
export const updatePartner = async (
  _companyName: string,
  _request: UpdatePartnerRequest
): Promise<PartnerResponse> => {
  throw new Error('Not implemented - Story 2.8.3');
};
