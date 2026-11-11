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
import type { components, operations } from '@/types/generated/event-registrations-api.types';

/** A registration as EMS lists it (`RegistrationResponse`), from the spec. */
type BackendRegistrationItem = components['schemas']['Registration'];

type BackendPaginatedResponse =
  operations['listRegistrations']['responses'][200]['content']['application/json'];

/**
 * Transform backend registration item to frontend EventParticipant type
 */
const transformRegistration = (backendReg: BackendRegistrationItem): EventParticipant => ({
  registrationCode: backendReg.registrationCode,
  eventCode: backendReg.eventCode,
  attendeeUsername: backendReg.attendeeUsername,
  firstName: backendReg.attendeeFirstName ?? '',
  lastName: backendReg.attendeeLastName ?? '',
  email: backendReg.attendeeEmail ?? '',
  company: backendReg.attendeeCompany
    ? { id: backendReg.attendeeCompany, name: backendReg.attendeeCompany }
    : undefined,
  status: backendReg.status as EventParticipant['status'],
  registrationDate: backendReg.registrationDate ?? '',
  // Epic 14 FR28: carry the authoritative 1-based queue position so the
  // Waitlisted-filter view numbers rows by the backend, not by page index.
  waitlistPosition: backendReg.waitlistPosition ?? null,
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
 * @param registrationCode - Registration code (e.g., BATbern142-reg-A3X9K2)
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
 * @param registrationCode - Registration code (e.g., BATbern142-reg-A3X9K2)
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
 * @param registrationCode - Registration code (e.g., BATbern142-reg-A3X9K2)
 */
export const deleteRegistration = async (
  eventCode: string,
  registrationCode: string
): Promise<void> => {
  await apiClient.delete(`/events/${eventCode}/registrations/${registrationCode}`);
};

/**
 * Promote a waitlisted registration to registered (Story 10.11 — AC3, AC5)
 *
 * ORGANIZER only. Promotes the specified waitlisted registration to status=registered.
 *
 * @param eventCode - Event code
 * @param registrationCode - Registration code of the waitlisted attendee
 * @throws 404 if registration not found; 409 if not on waitlist; 403 if not ORGANIZER
 */
export const promoteFromWaitlist = async (
  eventCode: string,
  registrationCode: string
): Promise<void> => {
  await apiClient.post(`/events/${eventCode}/registrations/${registrationCode}/promote`);
};

/**
 * Resend registration confirmation email (organizer only)
 *
 * Only works for registrations in REGISTERED (pending) status.
 *
 * @param eventCode - Event code
 * @param registrationCode - Registration code
 */
export const resendConfirmationEmail = async (
  eventCode: string,
  registrationCode: string
): Promise<void> => {
  await apiClient.post(
    `/events/${eventCode}/registrations/${registrationCode}/resend-confirmation`
  );
};

export type AddParticipantsRequest = components['schemas']['AddParticipantsRequest'];
export type AddParticipantsResponse = components['schemas']['AddParticipantsResponse'];
export type ParticipantImportPerson = components['schemas']['ParticipantImportPerson'];
export type ParticipantImportResult = components['schemas']['ParticipantImportResult'];

/**
 * The one create of participants (Story 4.10c): `POST /events/{eventCode}/participants` with
 * exactly one mode, `username` (+ `force`, `notify`), `people` or `stakeholders: true`.
 * Organizer only. The helpers below build each mode's body.
 */
const postParticipants = async (
  eventCode: string,
  body: AddParticipantsRequest
): Promise<AddParticipantsResponse> => {
  const response = await apiClient.post<AddParticipantsResponse>(
    `/events/${eventCode}/participants`,
    body
  );
  return response.data;
};

/**
 * Enroll all organizers and partners as confirmed participants for an existing event.
 *
 * ORGANIZER only. Idempotent — already-registered users are counted as skipped. No mail.
 *
 * @param eventCode - Event code
 * @returns Summary of enrolled and skipped counts
 */
export const enrollStakeholders = async (
  eventCode: string
): Promise<{ enrolled: number; skipped: number }> => {
  const data = await postParticipants(eventCode, { stakeholders: true });
  return { enrolled: data.enrolled ?? 0, skipped: data.skipped ?? 0 };
};

/**
 * Add an existing user as a confirmed participant (organizer only).
 *
 * @param eventCode - Event code
 * @param params - `username` of the chosen existing user; `force` to add over capacity;
 *                 `notify` to email the attendee a "you have a confirmed spot" notice (default true)
 * @returns the created registration's code + status
 * @throws 404 unknown event/user; 409 already-registered (generic) or capacity-full
 *         (`error.response.data.details.code === 'capacity_exceeded'`); 403 if not ORGANIZER
 */
export const addParticipant = async (
  eventCode: string,
  params: { username: string; force?: boolean; notify?: boolean }
): Promise<AddParticipantsResponse> =>
  postParticipants(eventCode, {
    username: params.username,
    force: params.force ?? false,
    notify: params.notify ?? true,
  });

/**
 * Historical import for one event: at most 100 people with their status at that event. Each
 * person is looked up or created by e-mail (no login); already registered ones are skipped. No
 * mail. One failing person does not stop the others (`results[i].outcome === 'FAILED'`).
 */
export const importParticipants = async (
  eventCode: string,
  people: ParticipantImportPerson[]
): Promise<AddParticipantsResponse> => postParticipants(eventCode, { people });

export type AttendeeRegistration = components['schemas']['Registration'];
type RegistrationPage = components['schemas']['RegistrationPage'];

/** Page size for an attendee's history: the spec maximum of `GET /registrations`. */
const ATTENDEE_HISTORY_PAGE_SIZE = 100;

/**
 * All registrations of one attendee across events, newest event first (organizer view of a user).
 * `GET /registrations?filter={"attendeeUsername":…}` (Story 4.10b; was `GET /events/registrations`
 * with the username in a header). The list is paginated: this reads every page at the spec
 * maximum, so a long-time attendee's history is complete. Errors are passed through.
 *
 * @param username - the attendee
 */
export const getRegistrationsForAttendee = async (
  username: string
): Promise<AttendeeRegistration[]> => {
  const filter = JSON.stringify({ attendeeUsername: username });
  const all: AttendeeRegistration[] = [];
  for (let page = 1; ; page++) {
    const response = await apiClient.get<RegistrationPage>('/registrations', {
      params: { filter, page, limit: ATTENDEE_HISTORY_PAGE_SIZE },
    });
    all.push(...response.data.data);
    if (!response.data.pagination.hasNext) {
      return all;
    }
  }
};
