/**
 * Registration Service
 * Story 10.10: Registration Status Indicator for Logged-in Users (T7)
 *
 * Handles GET /events/{eventCode}/my-registration — authenticated user's registration status.
 * CRITICAL: Always use this service layer — never call apiClient directly from components.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

export type MyRegistrationResponse = components['schemas']['MyRegistrationResponse'];

/**
 * Fetch the authenticated user's registration status for an event.
 *
 * Returns null on 404 (user not registered).
 * Throws on all other non-2xx errors.
 *
 * @param eventCode Event code (e.g., "BATbern142")
 */
export const getMyRegistration = async (
  eventCode: string
): Promise<MyRegistrationResponse | null> => {
  try {
    const response = await apiClient.get<MyRegistrationResponse>(
      `/events/${eventCode}/my-registration`
    );
    return response.data;
  } catch (error: unknown) {
    if (
      error &&
      typeof error === 'object' &&
      'response' in error &&
      (error as { response?: { status?: number } }).response?.status === 404
    ) {
      return null;
    }
    throw error;
  }
};
