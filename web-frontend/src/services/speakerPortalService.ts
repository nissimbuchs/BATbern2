/**
 * Speaker Portal Service (Story 6.2a/6.2b)
 *
 * API client for Speaker Portal endpoints.
 * These are PUBLIC endpoints authenticated via magic link token.
 * Features:
 * - Token validation (6.1a)
 * - Response submission (6.2a)
 * - Profile management (6.2b)
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
  preferredTimeSlot?: 'morning' | 'afternoon' | 'no_preference';
  travelRequirements?: 'local' | 'accommodation' | 'virtual';
  technicalRequirements?: string;
  initialPresentationTitle?: string;
  preferenceComments?: string;
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
