/**
 * Partner Meetings API Client
 * Story 8.3: Partner Meeting Coordination — AC1–6, 8
 * Story 10.27: iCal RSVP Tracking — AC7, AC8
 *
 * All endpoints served by partner-coordination-service via API Gateway.
 * Path is relative to baseURL (which already includes /api/v1).
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/partner-meetings-api.types';

// ─── Re-export generated types ───────────────────────────────────────────────

export type PartnerMeetingDTO = components['schemas']['PartnerMeetingDTO'];
export type CreateMeetingRequest = components['schemas']['CreateMeetingRequest'];
export type UpdateMeetingRequest = components['schemas']['UpdateMeetingRequest'];
export type SendInviteResponse = components['schemas']['SendInviteResponse'];

// ─── Story 10.27: RSVP types ─────────────────────────────────────────────────

export interface RsvpDTO {
  attendeeEmail: string;
  status: 'ACCEPTED' | 'DECLINED' | 'TENTATIVE';
  respondedAt: string;
}

export interface RsvpSummary {
  accepted: number;
  declined: number;
  tentative: number;
}

export interface MeetingRsvpListResponse {
  meetingId: string;
  inviteSentAt: string | null;
  rsvps: RsvpDTO[];
  summary: RsvpSummary;
}

// ─── API functions ────────────────────────────────────────────────────────────

/**
 * List all partner meetings sorted by meeting date descending (ORGANIZER only).
 */
export const getMeetings = async (): Promise<PartnerMeetingDTO[]> => {
  const response = await apiClient.get<PartnerMeetingDTO[]>('/partner-meetings');
  return response.data;
};

/**
 * Create a new partner meeting linked to a BATbern event (ORGANIZER only).
 */
export const createMeeting = async (req: CreateMeetingRequest): Promise<PartnerMeetingDTO> => {
  const response = await apiClient.post<PartnerMeetingDTO>('/partner-meetings', req);
  return response.data;
};

/**
 * Get a single partner meeting (ORGANIZER only).
 */
export const getMeeting = async (meetingId: string): Promise<PartnerMeetingDTO> => {
  const response = await apiClient.get<PartnerMeetingDTO>(`/partner-meetings/${meetingId}`);
  return response.data;
};

/**
 * Update meeting agenda or notes — all fields optional (ORGANIZER only).
 */
export const updateMeeting = async (
  meetingId: string,
  req: UpdateMeetingRequest
): Promise<PartnerMeetingDTO> => {
  const response = await apiClient.patch<PartnerMeetingDTO>(`/partner-meetings/${meetingId}`, req);
  return response.data;
};

/**
 * Send ICS calendar invite to all partner contacts (ORGANIZER only).
 * Returns 202 Accepted immediately — email is dispatched asynchronously (AC8).
 */
export const sendInvite = async (meetingId: string): Promise<SendInviteResponse> => {
  const response = await apiClient.post<SendInviteResponse>(
    `/partner-meetings/${meetingId}/send-invite`
  );
  return response.data;
};

/**
 * Get RSVP responses for a partner meeting (ORGANIZER only).
 * Story 10.27 (AC7).
 */
export const getRsvps = async (meetingId: string): Promise<MeetingRsvpListResponse> => {
  const response = await apiClient.get<MeetingRsvpListResponse>(
    `/partner-meetings/${meetingId}/rsvps`
  );
  return response.data;
};
