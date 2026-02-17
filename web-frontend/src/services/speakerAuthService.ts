/**
 * Speaker Auth Service (Story 9.1)
 *
 * API client for JWT-based magic link authentication.
 * Handles the new-style JWT magic link flow introduced in Epic 9.
 */

import apiClient from '@/services/api/apiClient';

/**
 * Response from the magic login endpoint
 */
export interface SpeakerAuthResponse {
  speakerPoolId: string;
  speakerName: string;
  eventCode: string;
  eventTitle: string;
  /** Opaque session token for use with existing speaker portal endpoints */
  sessionToken: string;
}

export const speakerAuthService = {
  /**
   * Validate a JWT magic link token and obtain a session token.
   * POSTs the JWT to /api/v1/auth/speaker-magic-login.
   * On success, backend sets speaker_jwt HTTP-only cookie and returns speaker context.
   *
   * @param jwtToken - RS256-signed JWT from magic link URL (?jwt=...)
   */
  async validateMagicLink(jwtToken: string): Promise<SpeakerAuthResponse> {
    const response = await apiClient.post<SpeakerAuthResponse>('/api/v1/auth/speaker-magic-login', {
      jwtToken,
    });
    return response.data;
  },

  /**
   * Story 9.3 Task 7.1: Authenticate speaker with email + password via Cognito.
   * POSTs to /api/v1/auth/speaker-password-login.
   * On success, backend sets speaker_jwt HTTP-only cookie and returns speaker context.
   *
   * @param email    Speaker email address
   * @param password Speaker password
   */
  async loginWithPassword(email: string, password: string): Promise<SpeakerAuthResponse> {
    const response = await apiClient.post<SpeakerAuthResponse>(
      '/api/v1/auth/speaker-password-login',
      {
        email,
        password,
      }
    );
    return response.data;
  },

  /**
   * Story 9.3 Task 7.2: Request a new magic link to be sent to the given email.
   * POSTs to /api/v1/auth/speaker-request-magic-link.
   * Always resolves (backend returns 200 regardless of whether email exists).
   *
   * @param email Speaker email address
   */
  async requestMagicLink(email: string): Promise<void> {
    await apiClient.post('/api/v1/auth/speaker-request-magic-link', { email });
  },

  /**
   * Story 9.3 Task 7.3: Initiate speaker password reset — triggers Cognito code email.
   * POSTs to /api/v1/auth/speaker-forgot-password.
   * Always resolves (backend returns 200 regardless of whether email exists).
   *
   * @param email Speaker email address
   */
  async forgotPassword(email: string): Promise<void> {
    await apiClient.post('/api/v1/auth/speaker-forgot-password', { email });
  },

  /**
   * Story 9.3 Task 7.4: Confirm speaker password reset with code + new password.
   * POSTs to /api/v1/auth/speaker-confirm-reset.
   *
   * @param email           Speaker email address
   * @param code            Confirmation code received by email
   * @param newPassword     New password (min 8 chars)
   */
  async confirmPasswordReset(email: string, code: string, newPassword: string): Promise<void> {
    await apiClient.post('/api/v1/auth/speaker-confirm-reset', {
      email,
      confirmationCode: code,
      newPassword,
    });
  },
};
