/**
 * Speaker Invitation Service - Story 6.2
 *
 * HTTP client for speaker self-service response portal.
 * Features:
 * - Get invitation details by token (GET /api/v1/invitations/respond/{token})
 * - Submit response to invitation (POST /api/v1/invitations/respond/{token})
 * - Public endpoints (no authentication required)
 */

import apiClient from '@/services/api/apiClient';
import type {
  InvitationResponse,
  RespondToInvitationRequest,
} from '@/types/speakerInvitation.types';

// API base path
const INVITATIONS_API_PATH = '/invitations';

/**
 * Speaker Invitation Service
 *
 * Handles all HTTP requests for speaker invitation responses.
 * Uses Skip-Auth header since these are public endpoints.
 */
class SpeakerInvitationService {
  /**
   * Get invitation details by response token.
   *
   * @param token 64-character cryptographic response token from email link
   * @returns Invitation details including event info and personal message
   * @throws Error if token is invalid or invitation expired
   */
  async getInvitationByToken(token: string): Promise<InvitationResponse> {
    const response = await apiClient.get<InvitationResponse>(
      `${INVITATIONS_API_PATH}/respond/${token}`,
      {
        headers: {
          'Skip-Auth': 'true', // Public endpoint - no authentication required
        },
      }
    );

    return response.data;
  }

  /**
   * Submit response to invitation (Accept, Decline, or Need More Info).
   *
   * @param token 64-character cryptographic response token
   * @param request Response data including type, preferences (if accepting), or decline reason
   * @returns Updated invitation details
   * @throws Error if token is invalid, invitation expired, or already responded
   */
  async respondToInvitation(
    token: string,
    request: RespondToInvitationRequest
  ): Promise<InvitationResponse> {
    const response = await apiClient.post<InvitationResponse>(
      `${INVITATIONS_API_PATH}/respond/${token}`,
      request,
      {
        headers: {
          'Skip-Auth': 'true', // Public endpoint - no authentication required
        },
      }
    );

    return response.data;
  }
}

// Export singleton instance
export const speakerInvitationService = new SpeakerInvitationService();

// Export class for testing
export default SpeakerInvitationService;
