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
 * Returns null when the user has no registration (registered=false).
 * Returns the full response when registered=true.
 * Throws on non-2xx errors.
 *
 * @param eventCode Event code (e.g., "BATbern142")
 */
export const getMyRegistration = async (
  eventCode: string
): Promise<MyRegistrationResponse | null> => {
  const response = await apiClient.get<MyRegistrationResponse>(
    `/events/${eventCode}/my-registration`
  );
  return response.data.registered ? response.data : null;
};
