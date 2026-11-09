/**
 * Production-shaped registrations (Story 17.0d): one per status the API knows, attendees from
 * the production-shaped people. The MSW harness answers `listRegistrations` from
 * `registrations(eventCode)` when a test sets no override (`src/test/msw/fixtureDefaults.ts`).
 */
import type { components } from '../../types/generated/event-registrations-api.types';
import { daysFromNow } from './events.ts';
import { shapes } from './people.ts';
import type { UserResponse } from './people.ts';

export type Registration = components['schemas']['Registration'];
export type RegistrationStatus = Registration['status'];

/**
 * Every status of the generated union. `Record<RegistrationStatus, true>` makes a status the spec
 * adds a type error here until a fixture covers it.
 */
const STATUS_SET: Record<RegistrationStatus, true> = {
  REGISTERED: true,
  WAITLIST: true,
  CONFIRMED: true,
  CANCELLED: true,
  ATTENDED: true,
  NO_SHOW: true,
};
export const REGISTRATION_STATUSES = Object.keys(STATUS_SET) as RegistrationStatus[];

/** A registration of `attendee`; defaults to a confirmed one. Override what matters. */
export function aRegistration(
  attendee: UserResponse = shapes.mixedCaseEmail,
  overrides: Partial<Registration> = {}
): Registration {
  return {
    registrationCode: 'BATbern61-reg-A3X9K1',
    eventCode: 'BATbern61',
    attendeeUsername: attendee.id,
    attendeeFirstName: attendee.firstName,
    attendeeLastName: attendee.lastName,
    attendeeEmail: attendee.email,
    attendeeCompany: attendee.companyId ?? null,
    status: 'CONFIRMED',
    waitlistPosition: null,
    registrationDate: daysFromNow(-10),
    createdAt: daysFromNow(-10),
    updatedAt: daysFromNow(-10),
    ...overrides,
  };
}

/** One registration per status for `eventCode`, attendees with unicode names and namesakes. */
export function registrations(eventCode = 'BATbern61'): Registration[] {
  const attendees: Record<RegistrationStatus, UserResponse> = {
    REGISTERED: shapes.vietnamese,
    WAITLIST: shapes.identicalNames[0],
    CONFIRMED: shapes.mixedCaseEmail,
    CANCELLED: shapes.apostrophe,
    ATTENDED: shapes.cjk,
    NO_SHOW: shapes.idnEmail,
  };
  return REGISTRATION_STATUSES.map((status, i) =>
    aRegistration(attendees[status], {
      eventCode,
      registrationCode: `${eventCode}-reg-A3X9K${i + 1}`,
      status,
      waitlistPosition: status === 'WAITLIST' ? 1 : null,
    })
  ).concat(
    // The namesake of the waitlisted attendee is confirmed: same name, different person.
    aRegistration(shapes.identicalNames[1], {
      eventCode,
      registrationCode: `${eventCode}-reg-A3X9K${REGISTRATION_STATUSES.length + 1}`,
      status: 'CONFIRMED',
    })
  );
}
