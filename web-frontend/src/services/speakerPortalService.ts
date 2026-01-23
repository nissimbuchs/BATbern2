/**
 * Speaker Portal Service (Story 6.2a - Task 6)
 *
 * API client for Speaker Portal endpoints.
 * These are PUBLIC endpoints authenticated via magic link token.
 * Features:
 * - Token validation
 * - Response submission (Accept/Decline/Tentative)
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
