/**
 * Attendee Dashboard Service (Story 7.6)
 *
 * HTTP client for the attendee's event-history dashboard. Authenticated — the backend derives the
 * attendee from the JWT (no params). Mirrors `speakerPortalService.getDashboard()`.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-core-api.types';

export type AttendeeDashboard = components['schemas']['AttendeeDashboardResponse'];
export type AttendeeEventCard = components['schemas']['AttendeeEventCardResponse'];

export async function getDashboard(): Promise<AttendeeDashboard> {
  const response = await apiClient.get<AttendeeDashboard>('/attendee-portal/dashboard');
  return response.data;
}
