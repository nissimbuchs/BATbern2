/**
 * Speaker Portal Service (Story 6.2a/6.2b/6.3)
 *
 * API client for Speaker Portal endpoints.
 * These are PUBLIC endpoints authenticated via magic link token.
 * Features:
 * - Token validation (6.1a)
 * - Response submission (6.2a)
 * - Profile management (6.2b)
 * - Content submission (6.3)
 * - Error handling with correlation IDs
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';

// API base path for speaker portal endpoints
const SPEAKER_PORTAL_API_PATH = '/speaker-portal';

/**
 * Speaker Response Types
 */
export type SpeakerResponseType = 'ACCEPT' | 'DECLINE' | 'TENTATIVE';

/**
 * Token validation request
 */
export interface ValidateTokenRequest {
  token: string;
}

/**
 * Token validation result returned from the API
 */
export interface TokenValidationResult {
  valid: boolean;
  speakerName: string;
  eventCode: string;
  eventTitle: string;
  eventDate: string;
  sessionTitle?: string;
  invitationMessage?: string;
  responseDeadline?: string;
  alreadyResponded: boolean;
  previousResponse?: string;
  previousResponseDate?: string;
  error?: string;
}

/**
 * Speaker preferences submitted with ACCEPT response
 */
export interface SpeakerResponsePreferences {
  timeSlot?: 'morning' | 'afternoon' | 'no_preference';
  travelRequirements?: 'local' | 'accommodation' | 'virtual';
  technicalRequirements?: string;
  initialTitle?: string;
  comments?: string;
}

/**
 * Request to submit a speaker response
 */
export interface SpeakerResponseRequest {
  token: string;
  response: SpeakerResponseType;
  reason?: string;
  preferences?: SpeakerResponsePreferences;
}

/**
 * Result of a successful response submission
 */
export interface SpeakerResponseResult {
  success: boolean;
  speakerName: string;
  eventName: string;
  eventDate?: string;
  sessionTitle?: string;
  nextSteps: string[];
  contentDeadline?: string;
  dashboardUrl?: string;
  profileUrl?: string;
  message?: string;
}

/**
 * Error response structure for speaker portal
 */
export interface SpeakerPortalError {
  errorCode: string;
  message: string;
  previousResponse?: string;
  respondedAt?: string;
}

// ============================================================================
// Story 6.4: Dashboard Types
// ============================================================================

/**
 * Upcoming event in the speaker dashboard (AC2)
 */
export interface DashboardUpcomingEvent {
  eventCode: string;
  eventTitle: string;
  eventDate: string;
  eventLocation: string;
  sessionTitle: string | null;
  workflowState: string;
  workflowStateLabel: string;
  contentStatus: string | null;
  contentStatusLabel: string | null;
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
  profileUrl: string;
  contentUrl: string | null;
}

/**
 * Past event in the speaker dashboard (AC3)
 */
export interface DashboardPastEvent {
  eventCode: string;
  eventTitle: string;
  eventDate: string;
  sessionTitle: string | null;
  hasMaterial: boolean;
  materialFileName: string | null;
}

/**
 * Speaker dashboard summary (AC1-AC5)
 */
export interface SpeakerDashboard {
  speakerName: string;
  profilePictureUrl: string | null;
  profileCompleteness: number;
  upcomingEvents: DashboardUpcomingEvent[];
  pastEvents: DashboardPastEvent[];
}

// ============================================================================
// Story 6.2b: Profile Management Types
// ============================================================================

/**
 * Combined speaker profile (User + Speaker data)
 */
export interface SpeakerProfile {
  // User fields (from Company Service)
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  bio: string | null;
  profilePictureUrl: string | null;
  // Speaker fields (from Event Service)
  expertiseAreas: string[];
  speakingTopics: string[];
  linkedInUrl: string | null;
  languages: string[];
  // Computed fields
  profileCompleteness: number;
  missingFields: string[];
  // Navigation context (Story 6.3 AC10)
  hasSessionAssigned?: boolean;
  sessionTitle?: string | null;
  eventCode?: string | null;
}

/**
 * Profile update request
 */
export interface ProfileUpdateRequest {
  token: string;
  firstName?: string;
  lastName?: string;
  bio?: string;
  expertiseAreas?: string[];
  speakingTopics?: string[];
  linkedInUrl?: string;
  languages?: string[];
}

// ============================================================================
// Story 6.2b: Photo Upload Types (AC7)
// ============================================================================

/**
 * Request to get presigned URL for photo upload
 */
export interface PhotoUploadRequest {
  token: string;
  fileName: string;
  fileSize: number;
  contentType: string;
}

/**
 * Response from presigned URL endpoint
 */
export interface PresignedPhotoUploadResponse {
  uploadUrl: string;
  uploadId: string;
  s3Key: string;
  expiresIn: number;
  maxSizeBytes: number;
}

/**
 * Request to confirm photo upload
 */
export interface PhotoConfirmRequest {
  token: string;
  uploadId: string;
  s3Key: string;
}

/**
 * Response from photo confirm endpoint
 */
export interface PhotoConfirmResponse {
  profilePictureUrl: string;
}

// ============================================================================
// Story 6.3: Content Submission Types
// ============================================================================

/**
 * Content info returned from the API (AC1, AC4, AC7, AC8)
 */
export interface SpeakerContentInfo {
  speakerName: string;
  eventCode: string;
  eventTitle: string;
  hasSessionAssigned: boolean;
  sessionTitle: string | null;
  canSubmitContent: boolean;
  contentStatus: string | null;
  hasDraft: boolean;
  draftTitle: string | null;
  draftAbstract: string | null;
  draftVersion: number | null;
  lastSavedAt: string | null;
  needsRevision: boolean;
  reviewerFeedback: string | null;
  reviewedAt: string | null;
  reviewedBy: string | null;
  // AC7: Material upload
  hasMaterial: boolean;
  materialUrl: string | null;
  materialFileName: string | null;
}

/**
 * Request to save content draft (AC4)
 */
export interface ContentDraftRequest {
  token: string;
  title: string | null;
  contentAbstract: string | null;
}

/**
 * Response from draft save endpoint (AC4)
 */
export interface ContentDraftResponse {
  draftId: string;
  savedAt: string;
}

/**
 * Request to submit content (AC5)
 */
export interface ContentSubmitRequest {
  token: string;
  title: string;
  contentAbstract: string;
}

/**
 * Response from content submit endpoint (AC5)
 */
export interface ContentSubmitResponse {
  submissionId: string;
  version: number;
  status: string;
  sessionTitle: string;
}

/**
 * Request to get presigned URL for material upload (AC7)
 */
export interface MaterialUploadRequest {
  token: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
}

/**
 * Response from material presigned URL endpoint (AC7)
 */
export interface MaterialUploadResponse {
  uploadUrl: string;
  uploadId: string;
  s3Key: string;
  fileExtension: string;
  expiresInMinutes: number;
  requiredHeaders: Record<string, string>;
}

/**
 * Request to confirm material upload (AC7)
 */
export interface MaterialConfirmRequest {
  token: string;
  uploadId: string;
  fileName: string;
  fileExtension: string;
  fileSize: number;
  mimeType: string;
  materialType: string;
}

/**
 * Response from material confirm endpoint (AC7)
 */
export interface MaterialConfirmResponse {
  materialId: string;
  uploadId: string;
  fileName: string;
  cloudFrontUrl: string;
  materialType: string;
  uploadedAt: string;
}

/**
 * Speaker Portal Service Class
 *
 * Handles all HTTP requests to the Speaker Portal endpoints.
 * Note: These are PUBLIC endpoints - no JWT auth required.
 */
class SpeakerPortalService {
  /**
   * Validate a magic link token and retrieve invitation details.
   * Story 6.1a: Token validation endpoint
   *
   * @param token Magic link token from email
   * @returns Token validation result with invitation details
   */
  async validateToken(token: string): Promise<TokenValidationResult> {
    try {
      const response = await apiClient.post<TokenValidationResult>(
        `${SPEAKER_PORTAL_API_PATH}/validate-token`,
        { token },
        {
          headers: {
            // Public endpoint - skip auth header
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Submit a response to a speaker invitation.
   * Story 6.2a: Response submission endpoint
   *
   * @param request Response request with token, response type, and optional preferences
   * @returns Response result with next steps
   */
  async respond(request: SpeakerResponseRequest): Promise<SpeakerResponseResult> {
    try {
      const response = await apiClient.post<SpeakerResponseResult>(
        `${SPEAKER_PORTAL_API_PATH}/respond`,
        request,
        {
          headers: {
            // Public endpoint - skip auth header
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  // ==========================================================================
  // Story 6.4: Dashboard
  // ==========================================================================

  /**
   * Get speaker dashboard summary.
   * Story 6.4: Speaker Dashboard (View-Only)
   *
   * @param token Magic link token
   * @returns Dashboard summary with upcoming and past events
   */
  async getDashboard(token: string): Promise<SpeakerDashboard> {
    try {
      const response = await apiClient.get<SpeakerDashboard>(
        `${SPEAKER_PORTAL_API_PATH}/dashboard`,
        {
          params: { token },
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  // ==========================================================================
  // Story 6.2b: Profile Management
  // ==========================================================================

  /**
   * Get speaker profile.
   * Story 6.2b: Profile view endpoint
   *
   * @param token Magic link token
   * @returns Combined speaker profile (User + Speaker data)
   */
  async getProfile(token: string): Promise<SpeakerProfile> {
    try {
      const response = await apiClient.get<SpeakerProfile>(`${SPEAKER_PORTAL_API_PATH}/profile`, {
        params: { token },
        headers: {
          'Skip-Auth': 'true',
        },
      });
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Update speaker profile.
   * Story 6.2b: Profile update endpoint
   *
   * @param request Profile update request with token and fields to update
   * @returns Updated speaker profile
   */
  async updateProfile(request: ProfileUpdateRequest): Promise<SpeakerProfile> {
    try {
      const response = await apiClient.patch<SpeakerProfile>(
        `${SPEAKER_PORTAL_API_PATH}/profile`,
        request,
        {
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  // ==========================================================================
  // Story 6.2b: Photo Upload (AC7)
  // ==========================================================================

  /**
   * Get presigned URL for profile photo upload.
   * Story 6.2b AC7: Photo upload via presigned URL
   *
   * @param request Photo upload request with token and file metadata
   * @returns Presigned URL response with upload details
   */
  async getPhotoPresignedUrl(request: PhotoUploadRequest): Promise<PresignedPhotoUploadResponse> {
    try {
      const response = await apiClient.post<PresignedPhotoUploadResponse>(
        `${SPEAKER_PORTAL_API_PATH}/profile/photo/presigned-url`,
        request,
        {
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Confirm profile photo upload and update User profile.
   * Story 6.2b AC7: Upload confirmation
   *
   * @param request Confirm request with token, uploadId, and s3Key
   * @returns CloudFront URL of the uploaded photo
   */
  async confirmPhotoUpload(request: PhotoConfirmRequest): Promise<PhotoConfirmResponse> {
    try {
      const response = await apiClient.post<PhotoConfirmResponse>(
        `${SPEAKER_PORTAL_API_PATH}/profile/photo/confirm`,
        request,
        {
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Upload profile photo using the 3-phase presigned URL flow.
   * This is a convenience method that handles the full upload flow.
   *
   * @param token Magic link token
   * @param file File to upload
   * @param onProgress Optional progress callback (0-100)
   * @returns CloudFront URL of the uploaded photo
   */
  async uploadProfilePhoto(
    token: string,
    file: File,
    onProgress?: (progress: number) => void
  ): Promise<string> {
    // Phase 1: Get presigned URL
    const presignedResponse = await this.getPhotoPresignedUrl({
      token,
      fileName: file.name,
      fileSize: file.size,
      contentType: file.type,
    });

    // Phase 2: Upload directly to S3
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

      xhr.addEventListener('error', () => {
        reject(new Error('S3 upload failed'));
      });

      xhr.open('PUT', presignedResponse.uploadUrl);
      xhr.setRequestHeader('Content-Type', file.type);
      xhr.send(file);
    });

    // Phase 3: Confirm upload
    const confirmResponse = await this.confirmPhotoUpload({
      token,
      uploadId: presignedResponse.uploadId,
      s3Key: presignedResponse.s3Key,
    });

    return confirmResponse.profilePictureUrl;
  }

  // ==========================================================================
  // Story 6.3: Content Submission
  // ==========================================================================

  /**
   * Get content info for the speaker portal.
   * Story 6.3 AC1: Session assignment check
   * Story 6.3 AC4: Draft restoration
   * Story 6.3 AC8: Revision feedback display
   *
   * @param token Magic link token
   * @returns Content info including session status, draft, and revision feedback
   */
  async getContentInfo(token: string): Promise<SpeakerContentInfo> {
    try {
      const response = await apiClient.get<SpeakerContentInfo>(
        `${SPEAKER_PORTAL_API_PATH}/content`,
        {
          params: { token },
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Save content draft.
   * Story 6.3 AC4: Draft auto-save every 30 seconds
   *
   * @param request Draft request with token, title, and abstract
   * @returns Draft response with save timestamp
   */
  async saveDraft(request: ContentDraftRequest): Promise<ContentDraftResponse> {
    try {
      const response = await apiClient.post<ContentDraftResponse>(
        `${SPEAKER_PORTAL_API_PATH}/content/draft`,
        request,
        {
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Submit content for organizer review.
   * Story 6.3 AC5: Content submission with validation
   *
   * @param request Submit request with token, title, and abstract
   * @returns Submit response with submission ID and version
   */
  async submitContent(request: ContentSubmitRequest): Promise<ContentSubmitResponse> {
    try {
      const response = await apiClient.post<ContentSubmitResponse>(
        `${SPEAKER_PORTAL_API_PATH}/content/submit`,
        request,
        {
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get presigned URL for material upload.
   * Story 6.3 AC7: File upload with 50MB limit
   *
   * @param request Upload request with token and file metadata
   * @returns Presigned URL response with upload details
   */
  async getMaterialPresignedUrl(request: MaterialUploadRequest): Promise<MaterialUploadResponse> {
    try {
      const response = await apiClient.post<MaterialUploadResponse>(
        `${SPEAKER_PORTAL_API_PATH}/materials/presigned-url`,
        request,
        {
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Confirm material upload and associate with session.
   * Story 6.3 AC7: Material association after upload
   *
   * @param request Confirm request with token and upload details
   * @returns Confirm response with material info
   */
  async confirmMaterialUpload(request: MaterialConfirmRequest): Promise<MaterialConfirmResponse> {
    try {
      const response = await apiClient.post<MaterialConfirmResponse>(
        `${SPEAKER_PORTAL_API_PATH}/materials/confirm`,
        request,
        {
          headers: {
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Upload presentation material using the 3-phase presigned URL flow.
   * Story 6.3 AC7: Material upload convenience method
   *
   * @param token Magic link token
   * @param file File to upload
   * @param onProgress Optional progress callback (0-100)
   * @returns Material confirm response with CloudFront URL
   */
  async uploadMaterial(
    token: string,
    file: File,
    onProgress?: (progress: number) => void
  ): Promise<MaterialConfirmResponse> {
    // Phase 1: Get presigned URL
    const presignedResponse = await this.getMaterialPresignedUrl({
      token,
      fileName: file.name,
      fileSize: file.size,
      mimeType: file.type,
    });

    // Phase 2: Upload directly to S3
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

      xhr.addEventListener('error', () => {
        reject(new Error('S3 upload failed'));
      });

      xhr.open('PUT', presignedResponse.uploadUrl);
      // Set required headers from response
      Object.entries(presignedResponse.requiredHeaders).forEach(([key, value]) => {
        xhr.setRequestHeader(key, value);
      });
      xhr.send(file);
    });

    // Phase 3: Confirm upload
    const fileExtension = file.name.split('.').pop()?.toLowerCase() || '';
    const confirmResponse = await this.confirmMaterialUpload({
      token,
      uploadId: presignedResponse.uploadId,
      fileName: file.name,
      fileExtension,
      fileSize: file.size,
      mimeType: file.type,
      materialType: 'PRESENTATION',
    });

    return confirmResponse;
  }

  /**
   * Type guard for Axios errors
   */
  private isAxiosError(error: unknown): error is AxiosError {
    return (error as AxiosError).isAxiosError === true;
  }

  /**
   * Transform Axios errors to application errors
   * Preserves specific error codes for UI handling
   */
  private transformError(error: unknown): Error {
    if (error instanceof Error && !this.isAxiosError(error)) {
      return error;
    }

    const axiosError = error as AxiosError<SpeakerPortalError>;

    // Network errors
    if (!axiosError.response) {
      return new Error('Network Error: Unable to connect to server');
    }

    const status = axiosError.response.status;
    const errorData = axiosError.response.data;
    const correlationId = axiosError.response.headers['x-correlation-id'];

    // Create error with specific error code for UI handling
    const appError = new Error(errorData?.message || 'An error occurred') as Error & {
      status?: number;
      errorCode?: string;
      previousResponse?: string;
      respondedAt?: string;
    };

    appError.status = status;
    appError.errorCode = errorData?.errorCode;

    // Include previous response info for 409 conflicts
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
