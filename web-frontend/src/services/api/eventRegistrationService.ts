/**
 * Event Registration Service
 *
 * API service for fetching event registrations (participants).
 * Story 3.3: Event Participants Tab
 */

import apiClient from './apiClient';
import type {
  EventParticipant,
  EventRegistrationsResponse,
  FetchEventRegistrationsOptions,
} from '@/types/eventParticipant.types';

/**
 * Backend API response format (field names differ from frontend)
 */
interface BackendRegistrationItem {
  registrationCode: string;
  eventCode: string;
  status: string;
  registrationDate: string;
  attendeeUsername: string;
  attendeeFirstName: string; // Backend uses attendeeFirstName
  attendeeLastName: string; // Backend uses attendeeLastName
  attendeeEmail: string; // Backend uses attendeeEmail
  attendeeCompany?: string; // Company ID (optional)
}

interface BackendPaginatedResponse {
  data: BackendRegistrationItem[];
  pagination: {
    page: number;
    limit: number;
    totalItems: number;
    totalPages: number;
    hasNext: boolean;
    hasPrev: boolean;
  };
}

/**
 * Transform backend registration item to frontend EventParticipant type
 */
const transformRegistration = (backendReg: BackendRegistrationItem): EventParticipant => ({
  registrationCode: backendReg.registrationCode,
  eventCode: backendReg.eventCode,
  attendeeUsername: backendReg.attendeeUsername,
  firstName: backendReg.attendeeFirstName, // Transform from attendeeFirstName
  lastName: backendReg.attendeeLastName, // Transform from attendeeLastName
  email: backendReg.attendeeEmail, // Transform from attendeeEmail
  company: backendReg.attendeeCompany
    ? { id: backendReg.attendeeCompany, name: backendReg.attendeeCompany }
    : undefined,
  status: backendReg.status as EventParticipant['status'],
  registrationDate: backendReg.registrationDate,
});

/**
 * Fetch event registrations (participants) for a specific event
 *
 * @param eventCode - Event code to fetch registrations for
 * @param options - Optional filters, pagination, and search
 * @returns Registrations list and total count
 */
export const getEventRegistrations = async (
  eventCode: string,
  options?: Omit<FetchEventRegistrationsOptions, 'eventCode'>
): Promise<EventRegistrationsResponse> => {
  // Build query parameters
  const params: Record<string, unknown> = {};

  // Add pagination
  if (options?.pagination) {
    params.page = options.pagination.page;
    params.limit = options.pagination.limit;
  }

  // Add filters
  if (options?.filters) {
    if (options.filters.status && options.filters.status.length > 0) {
      params.status = options.filters.status;
    }
    if (options.filters.companyId) {
      params.companyId = options.filters.companyId;
    }
  }

  // Add search
  if (options?.search) {
    params.search = options.search;
  }

  // Backend returns paginated response: { data: [...], pagination: {...} }
  const response = await apiClient.get<BackendPaginatedResponse>(
    `/events/${eventCode}/registrations`,
    { params }
  );

  // Transform backend field names to frontend format
  const data = response.data.data.map(transformRegistration);

  return {
    data,
    pagination: response.data.pagination,
  };
};

/**
 * Update registration status
 *
 * @param eventCode - Event code
 * @param registrationCode - Registration code (e.g., BAT-2025-000123)
 * @param status - New registration status
 * @returns Updated registration
 */
export const updateRegistrationStatus = async (
  eventCode: string,
  registrationCode: string,
  status: EventParticipant['status']
): Promise<EventParticipant> => {
  // Backend expects lowercase status values to match database constraint
  const lowercaseStatus = status.toLowerCase();

  const response = await apiClient.patch<BackendRegistrationItem>(
    `/events/${eventCode}/registrations/${registrationCode}`,
    { status: lowercaseStatus }
  );

  return transformRegistration(response.data);
};

/**
 * Cancel registration (set status to CANCELLED)
 *
 * @param eventCode - Event code
 * @param registrationCode - Registration code (e.g., BAT-2025-000123)
 * @returns Updated registration with CANCELLED status
 */
export const cancelRegistration = async (
  eventCode: string,
  registrationCode: string
): Promise<EventParticipant> => {
  return updateRegistrationStatus(eventCode, registrationCode, 'CANCELLED');
};

/**
 * Delete registration (permanent removal)
 *
 * @param eventCode - Event code
 * @param registrationCode - Registration code (e.g., BAT-2025-000123)
 */
export const deleteRegistration = async (
  eventCode: string,
  registrationCode: string
): Promise<void> => {
  await apiClient.delete(`/events/${eventCode}/registrations/${registrationCode}`);
};
