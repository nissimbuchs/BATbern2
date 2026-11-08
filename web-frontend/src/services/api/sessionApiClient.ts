/**
 * Session API Client (Story BAT-17)
 *
 * HTTP client for Session management APIs in Event Management Service
 * Features:
 * - Update session details (title, description, duration)
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type { Session, SessionMaterial, SessionSpeaker } from '@/types/event.types';
import type { FileUploadPresignedUrl, FileUploadTarget } from '@/types/fileUpload.types';

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

export interface MaterialUploadItem {
  uploadId: string;
  materialType: string; // PRESENTATION, DOCUMENT, VIDEO, ARCHIVE, OTHER
  fileName: string;
  fileExtension: string;
  fileSize: number;
  mimeType: string;
}

export interface SessionMaterialAssociationRequest {
  materials: MaterialUploadItem[];
}

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
        error instanceof AxiosError &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Associate uploaded materials with a session
   * Story 5.9: Session Materials Upload (AC5)
   *
   * POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param materials - Material association request with uploadIds and materialTypes
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
        error instanceof AxiosError &&
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
        error instanceof AxiosError &&
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
        error instanceof AxiosError &&
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
        error instanceof AxiosError &&
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
        error instanceof AxiosError &&
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
    if (error instanceof AxiosError) {
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

/**
 * Session material upload target for useFileUpload (Story 5.9): phase 1 presign
 * and phase 3 confirm. The uploads are associated with a session afterwards
 * (`associateMaterials`). Errors are passed through for the hook to report.
 *
 * POST /api/v1/materials/presigned-url, POST /api/v1/materials/{uploadId}/confirm
 */
export const materialUploadTarget: FileUploadTarget = {
  purpose: 'material',
  presign: async (request) => {
    const response = await apiClient.post<FileUploadPresignedUrl>(
      '/materials/presigned-url',
      request
    );
    return response.data;
  },
  confirm: async (fileId, request) => {
    await apiClient.post(`/materials/${fileId}/confirm`, request);
  },
};
