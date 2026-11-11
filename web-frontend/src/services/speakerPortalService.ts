/**
 * Speaker Portal Service
 *
 * API client for Speaker Portal endpoints.
 *
 * Story 11.E.3 (ADR-009 §Decision 3): the portal is now Cognito-secured. Every endpoint
 * runs through {@code @PreAuthorize("hasRole('SPEAKER')")} and reads the username from the
 * JWT; the magic-link token bridge is gone. The `eventCode` is a path parameter on every
 * per-event endpoint (Q#1 — matches Story 11.C.2's organizer endpoint shape).
 *
 * Authentication is handled automatically by {@code apiClient} (Cognito Bearer header
 * attached by the axios interceptor); per-call `Skip-Auth` headers are gone.
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type { components } from '@/types/generated/event-speakers-api.types';

// API base path for speaker portal endpoints
const SPEAKER_PORTAL_API_PATH = '/speaker-portal';

/**
 * Speaker Response Types.
 * Story 11.B.1 + 11.E.3 (Q#6): TENTATIVE was removed from the shared-kernel enum and the
 * backend; the frontend type is cleaned up here to match.
 */
export type SpeakerResponseType = components['schemas']['SpeakerResponseType'];

/**
 * Speaker preferences submitted with ACCEPT response.
 */
export type SpeakerResponsePreferences = components['schemas']['SpeakerResponsePreferences'];

/**
 * Request body for {@code POST /speaker-portal/events/{eventCode}/respond}.
 * Story 11.E.3: the magic-link `token` field is gone; the `eventCode` is in the URL path.
 */
export type SpeakerResponseRequest = components['schemas']['SpeakerResponseRequest'];

/**
 * Result of a successful response submission.
 */
export type SpeakerResponseResult = components['schemas']['SpeakerResponseResult'];

/**
 * Error response structure for speaker portal.
 */
export interface SpeakerPortalError {
  errorCode: string;
  message: string;
  previousResponse?: string;
  respondedAt?: string;
}

// ============================================================================
// Story 6.4 / 11.E.3: Dashboard Types
// ============================================================================

export interface DashboardUpcomingEvent {
  eventCode: string;
  eventTitle: string;
  eventDate: string;
  eventLocation: string;
  sessionTitle: string | null;
  workflowState: string;
  workflowStateLabel: string;
  // 2026-05-20 (Q#D) — contentStatus / contentStatusLabel dropped end-to-end.
  // The workflow state + per-field hasTitle/hasAbstract/hasMaterial checkmarks
  // cover the same information without the parallel-status confusion.
  hasTitle: boolean;
  hasAbstract: boolean;
  hasMaterial: boolean;
  materialFileName: string | null;
  responseDeadline: string | null;
  contentDeadline: string | null;
  reviewerFeedback: string | null;
  organizerName: string | null;
  organizerEmail: string | null;
  respondUrl: string | null;
  contentUrl: string | null;
}

export interface DashboardPastEvent {
  eventCode: string;
  eventTitle: string;
  eventDate: string;
  sessionTitle: string | null;
  hasMaterial: boolean;
  materialFileName: string | null;
}

export interface SpeakerDashboard {
  speakerName: string;
  profilePictureUrl: string | null;
  profileCompleteness: number;
  upcomingEvents: DashboardUpcomingEvent[];
  pastEvents: DashboardPastEvent[];
}

// ============================================================================
// Content Submission Types — Story 6.3 / 11.E.3
// ============================================================================

// 2026-05-20 (Q#E): no `contentStatus`. The page reads `needsRevision` + `reviewerFeedback`
// directly; the speaker_pool workflow status is the canonical "where am I" signal everywhere.
export type SpeakerContentInfo = components['schemas']['SpeakerContentInfo'];

// Story 11.E.8 §2.9: backend draft endpoint removed — drafts live in localStorage
// (see ContentSubmissionPage.tsx auto-save). The single backend write path for
// title/abstract is submitContent below.

export type ContentSubmitRequest = components['schemas']['ContentSubmitRequest'];
export type ContentSubmitResponse = components['schemas']['ContentSubmitResponse'];
export type SpeakerMaterialUploadRequest = components['schemas']['SpeakerMaterialUploadRequest'];

export type MaterialUploadResponse = components['schemas']['SpeakerMaterialUploadResponse'];
export type MaterialConfirmRequest = components['schemas']['SpeakerMaterialConfirmRequest'];
export type MaterialConfirmResponse = components['schemas']['SpeakerMaterialConfirmResponse'];

/**
 * Speaker Portal Service Class — Story 11.E.3.
 *
 * Cognito-authenticated. The {@code apiClient} axios instance attaches the Bearer JWT
 * automatically; per-method `Skip-Auth` overrides are removed.
 */
class SpeakerPortalService {
  // ==========================================================================
  // Invitation response — Story 6.2a / 11.E.3
  // ==========================================================================

  /**
   * Submit a response to a speaker invitation for a specific event.
   * Story 11.E.3: {@code POST /api/v1/speaker-portal/events/{eventCode}/respond}.
   */
  async respond(
    eventCode: string,
    request: SpeakerResponseRequest
  ): Promise<SpeakerResponseResult> {
    try {
      const response = await apiClient.post<SpeakerResponseResult>(
        `${SPEAKER_PORTAL_API_PATH}/events/${encodeURIComponent(eventCode)}/respond`,
        request
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  // ==========================================================================
  // Dashboard — Story 6.4 / 11.E.3
  // ==========================================================================

  /**
   * Get speaker dashboard summary across all events the authenticated speaker is in.
   * Story 11.E.3: {@code GET /api/v1/speaker-portal/dashboard} (no eventCode — it
   * aggregates across the speaker's pool rows).
   */
  async getDashboard(): Promise<SpeakerDashboard> {
    try {
      const response = await apiClient.get<SpeakerDashboard>(
        `${SPEAKER_PORTAL_API_PATH}/dashboard`
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  // Code review 2026-05-18 (D1): the per-event profile endpoints (getProfile / updateProfile /
  // getPhotoPresignedUrl / confirmPhotoUpload / uploadProfilePhoto) were removed. Story 11.C.1
  // deleted the backend SpeakerPortalProfileController + SpeakerProfileService, which left
  // these client methods pointing at non-existent routes. The new direction (PM decision
  // 2026-05-18): profile editing uses the CUMS user endpoints in @/services/api/userAccountApi
  // (`GET /api/v1/users/me`, `PATCH /api/v1/users/{username}`, `/users/{username}/picture/*`) since
  // every speaker is a User. The speaker-portal namespace no longer carries a /profile resource.

  // ==========================================================================
  // Content Submission — Story 6.3 / 11.E.3
  // ==========================================================================

  async getContentInfo(eventCode: string): Promise<SpeakerContentInfo> {
    try {
      const response = await apiClient.get<SpeakerContentInfo>(
        `${SPEAKER_PORTAL_API_PATH}/events/${encodeURIComponent(eventCode)}/content`
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  // Story 11.E.8 §2.9 — saveDraft removed; ContentSubmissionPage stores drafts in
  // localStorage keyed by event + speaker.

  async submitContent(
    eventCode: string,
    request: ContentSubmitRequest
  ): Promise<ContentSubmitResponse> {
    try {
      const response = await apiClient.post<ContentSubmitResponse>(
        `${SPEAKER_PORTAL_API_PATH}/events/${encodeURIComponent(eventCode)}/content/submit`,
        request
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async getMaterialPresignedUrl(
    eventCode: string,
    request: SpeakerMaterialUploadRequest
  ): Promise<MaterialUploadResponse> {
    try {
      const response = await apiClient.post<MaterialUploadResponse>(
        `${SPEAKER_PORTAL_API_PATH}/events/${encodeURIComponent(eventCode)}/materials/presigned-url`,
        request
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async confirmMaterialUpload(
    eventCode: string,
    request: MaterialConfirmRequest
  ): Promise<MaterialConfirmResponse> {
    try {
      const response = await apiClient.post<MaterialConfirmResponse>(
        `${SPEAKER_PORTAL_API_PATH}/events/${encodeURIComponent(eventCode)}/materials/confirm`,
        request
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async uploadMaterial(
    eventCode: string,
    file: File,
    onProgress?: (progress: number) => void
  ): Promise<MaterialConfirmResponse> {
    const { uploadUrl, uploadId, requiredHeaders } = await this.getMaterialPresignedUrl(eventCode, {
      fileName: file.name,
      fileSize: file.size,
      mimeType: file.type,
    });
    if (!uploadUrl || !uploadId) {
      throw new Error('Upload failed: the server returned no upload URL');
    }

    await new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable && onProgress) {
          const percentComplete = Math.round((event.loaded / event.total) * 100);
          onProgress(percentComplete);
        }
      });
      xhr.addEventListener('load', () => {
        if (xhr.status === 200) {
          resolve();
        } else {
          reject(new Error(`S3 upload failed with status ${xhr.status}`));
        }
      });
      xhr.addEventListener('error', () => reject(new Error('S3 upload failed')));
      xhr.open('PUT', uploadUrl);
      Object.entries(requiredHeaders ?? {}).forEach(([key, value]) => {
        xhr.setRequestHeader(key, value);
      });
      xhr.send(file);
    });

    const fileExtension = file.name.split('.').pop()?.toLowerCase() || '';
    return this.confirmMaterialUpload(eventCode, {
      uploadId,
      fileName: file.name,
      fileExtension,
      fileSize: file.size,
      mimeType: file.type,
      materialType: 'PRESENTATION',
    });
  }

  /**
   * Type guard for Axios errors.
   */
  private isAxiosError(error: unknown): error is AxiosError {
    return (error as AxiosError).isAxiosError === true;
  }

  /**
   * Transform Axios errors to application errors. Preserves specific error codes for UI handling.
   */
  private transformError(error: unknown): Error {
    if (error instanceof Error && !this.isAxiosError(error)) {
      return error;
    }

    const axiosError = error as AxiosError<SpeakerPortalError>;

    if (!axiosError.response) {
      return new Error('Network Error: Unable to connect to server');
    }

    const status = axiosError.response.status;
    const errorData = axiosError.response.data;
    const correlationId = axiosError.response.headers['x-correlation-id'];

    const appError = new Error(errorData?.message || 'An error occurred') as Error & {
      status?: number;
      errorCode?: string;
      previousResponse?: string;
      respondedAt?: string;
    };

    appError.status = status;
    appError.errorCode = errorData?.errorCode;

    if (status === 409 && errorData) {
      appError.previousResponse = errorData.previousResponse;
      appError.respondedAt = errorData.respondedAt;
    }

    if (correlationId) {
      appError.message += ` (ID: ${correlationId})`;
    }

    return appError;
  }
}

// Export singleton instance
export const speakerPortalService = new SpeakerPortalService();
