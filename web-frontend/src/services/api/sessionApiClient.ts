/**
 * Session API Client (Story BAT-17)
 *
 * HTTP client for Session management APIs in Event Management Service
 * Features:
 * - Update session details (title, description, duration)
 */

import apiClient from '@/services/api/apiClient';
import { isAxiosError } from 'axios';
import type { Session, SessionMaterial, SessionSpeaker } from '@/types/event.types';
import type { components as EventSessionsComponents } from '@/types/generated/event-sessions-api.types';

// API base path for session endpoints
const SESSION_API_PATH = '/events';

export interface SessionUpdateRequest {
  title?: string;
  description?: string;
  durationMinutes?: number;
}

export interface AssignSpeakerRequest {
  username: string;
  speakerRole: 'PRIMARY_SPEAKER' | 'CO_SPEAKER' | 'MODERATOR' | 'PANELIST';
}

/**
 * A confirmed upload of purpose `session-material` (Story 17.11d); materialType (PRESENTATION,
 * DOCUMENT, VIDEO, ARCHIVE or OTHER) is derived from the content type when omitted.
 */
export type MaterialUploadItem = EventSessionsComponents['schemas']['MaterialUploadItem'];
export type SessionMaterialAssociationRequest =
  EventSessionsComponents['schemas']['SessionMaterialAssociationRequest'];

/** A speaker on a session in the company view (enriched by `GET /sessions`). */
export interface CompanySessionSpeaker {
  username: string;
  firstName?: string;
  lastName?: string;
  company?: string;
  profilePictureUrl?: string;
}

/** A session with at least one speaker of a company, across events (`GET /sessions`). */
export interface CompanySession {
  sessionSlug: string;
  eventCode: string;
  eventTitle?: string;
  eventDate?: string;
  title: string;
  sessionType?: string;
  startTime?: string;
  room?: string;
  speakers: CompanySessionSpeaker[];
}

/**
 * Session API Client Class
 *
 * Handles all HTTP requests to the Session APIs
 */
class SessionApiClient {
  /**
   * Update session details (title, description, duration)
   *
   * PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param updates - Session update request
   * @returns Updated session
   */
  async updateSession(
    eventCode: string,
    sessionSlug: string,
    updates: SessionUpdateRequest
  ): Promise<Session> {
    try {
      const response = await apiClient.patch<Session>(
        `${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}`,
        updates
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve 401/403 auth errors
      if (
        isAxiosError(error) &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Attach confirmed `session-material` uploads to a session (Story 17.11d: an upload confirmed
   * before the session was saved; one for an existing session is attached by its confirm)
   *
   * POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param materials - the uploadIds, optionally with a materialType
   * @returns Created materials list
   */
  async associateMaterials(
    eventCode: string,
    sessionSlug: string,
    materials: SessionMaterialAssociationRequest
  ): Promise<SessionMaterial[]> {
    try {
      const response = await apiClient.post(
        `${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}/materials`,
        materials
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve auth errors
      if (
        isAxiosError(error) &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Get presigned download URL for a material
   * Story 5.9: Session Materials Upload (AC5)
   *
   * GET /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials/{materialId}/download
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param materialId - Material UUID
   * @returns Object with downloadUrl (presigned URL valid for 1 hour)
   */
  async getMaterialDownloadUrl(
    eventCode: string,
    sessionSlug: string,
    materialId: string
  ): Promise<{ downloadUrl: string }> {
    try {
      const response = await apiClient.get(
        `${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}/materials/${materialId}/download`
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Delete a material from a session
   *
   * DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials/{materialId}
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param materialId - Material UUID
   */
  async deleteMaterial(eventCode: string, sessionSlug: string, materialId: string): Promise<void> {
    try {
      await apiClient.delete(
        `${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}/materials/${materialId}`
      );
    } catch (error) {
      if (
        isAxiosError(error) &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Delete a session
   *
   * DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   */
  async deleteSession(eventCode: string, sessionSlug: string): Promise<void> {
    try {
      await apiClient.delete(`${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}`);
    } catch (error) {
      if (
        isAxiosError(error) &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Assign a speaker to a session
   *
   * POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param request - Speaker assignment request (username + speakerRole)
   * @returns Created session speaker
   */
  async assignSpeaker(
    eventCode: string,
    sessionSlug: string,
    request: AssignSpeakerRequest
  ): Promise<SessionSpeaker> {
    try {
      const response = await apiClient.post<SessionSpeaker>(
        `${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}/speakers`,
        request
      );
      return response.data;
    } catch (error) {
      if (
        isAxiosError(error) &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Remove a speaker from a session
   *
   * DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param username - Speaker username to remove
   */
  async removeSpeaker(eventCode: string, sessionSlug: string, username: string): Promise<void> {
    try {
      await apiClient.delete(
        `${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}/speakers/${username}`
      );
    } catch (error) {
      if (
        isAxiosError(error) &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Sessions with a speaker of the given company, across events (company detail view).
   * Errors are passed through unchanged.
   *
   * GET /api/v1/sessions?companyName={companyName}&limit={limit}
   */
  async listSessionsByCompany(companyName: string, limit = 50): Promise<CompanySession[]> {
    const response = await apiClient.get<{ data: CompanySession[] }>('/sessions', {
      params: { companyName, limit },
    });
    return response.data.data ?? [];
  }

  /**
   * Transform Axios errors into user-friendly error messages
   */
  private transformError(error: unknown): Error {
    if (isAxiosError(error)) {
      // Extract error message from response
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        error.message ||
        'An unexpected error occurred';

      // Handle specific error cases
      if (error.response?.status === 404) {
        if (message.includes('Event')) {
          return new Error('Event not found');
        }
        if (message.includes('Session')) {
          return new Error('Session not found');
        }
      }

      // Include correlation ID if available
      const correlationId = error.response?.headers?.['x-correlation-id'];
      const errorMessage = correlationId
        ? `${message} (Correlation ID: ${correlationId})`
        : message;

      return new Error(errorMessage);
    }

    return error instanceof Error ? error : new Error('An unexpected error occurred');
  }
}

// Export singleton instance
export const sessionApiClient = new SessionApiClient();
