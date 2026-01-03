/**
 * Types for Event Participants view
 *
 * These types support the Participants tab in EventPage where organizers
 * can view and manage event registrations.
 */

/**
 * Registration status values
 */
export type RegistrationStatus =
  | 'REGISTERED'
  | 'CONFIRMED'
  | 'ATTENDED'
  | 'CANCELLED'
  | 'WAITLISTED';

/**
 * Company information for a participant
 */
export interface ParticipantCompany {
  id: string;
  name: string;
  logo?: string;
}

/**
 * Event participant (registration) data
 *
 * Represents a single participant's registration for an event.
 * This is the frontend view model for event registrations.
 */
export interface EventParticipant {
  registrationCode: string;
  eventCode: string;
  attendeeUsername: string;
  firstName: string;
  lastName: string;
  email: string;
  company?: ParticipantCompany;
  status: RegistrationStatus;
  registrationDate: string; // ISO 8601 format
}

/**
 * Filters for event participants list
 */
export interface ParticipantFilters {
  status?: RegistrationStatus[];
  companyId?: string;
}

/**
 * Pagination options for event participants
 */
export interface ParticipantPagination {
  page: number;
  limit: number;
}

/**
 * Pagination metadata from backend
 */
export interface PaginationMetadata {
  page: number; // 1-indexed
  limit: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrev: boolean;
}

/**
 * API response for event registrations endpoint (paginated)
 */
export interface EventRegistrationsResponse {
  data: EventParticipant[];
  pagination: PaginationMetadata;
}

/**
 * Options for fetching event registrations
 */
export interface FetchEventRegistrationsOptions {
  eventCode: string;
  filters?: ParticipantFilters;
  pagination?: ParticipantPagination;
  search?: string;
}
