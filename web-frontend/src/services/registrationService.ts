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

/**
 * Quick registration for an authenticated attendee.
 * No form data required — profile is read from the JWT session on the server.
 * Returns the server message and the attendee's email.
 */
export const createMyRegistration = async (
  eventCode: string
): Promise<{ message: string; email: string }> => {
  const response = await apiClient.post<{ message: string; email: string }>(
    `/events/${eventCode}/my-registration`
  );
  return response.data;
};

/**
 * Immediately cancel the authenticated user's registration for an event.
 * No email is sent. Triggers waitlist promotion on the server.
 */
export const deleteMyRegistration = async (eventCode: string): Promise<void> => {
  await apiClient.delete(`/events/${eventCode}/my-registration`);
};
