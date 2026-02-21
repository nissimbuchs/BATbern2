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
};
