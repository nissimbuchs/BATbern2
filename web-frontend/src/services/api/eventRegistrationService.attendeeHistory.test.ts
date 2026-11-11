/**
 * getRegistrationsForAttendee on the MSW harness (Story 4.10b): `GET /registrations` with the
 * attendee in `filter`, read page by page. The request is checked against
 * event-registrations-api, so the old `/events/registrations` path or the header would fail.
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { getRegistrationsForAttendee, type AttendeeRegistration } from './eventRegistrationService';

const registration = (n: number): AttendeeRegistration => ({
  registrationCode: `BATbern${n}-reg-A1B2C3`,
  eventCode: `BATbern${n}`,
  eventTitle: `Abend ${n}`,
  eventDate: '2026-03-10T18:00:00Z',
  attendeeUsername: 'hongan.nguyen',
  attendeeFirstName: 'Hông Ân',
  attendeeLastName: 'Nguyễn',
  attendeeEmail: 'Hongan.Nguyen@example.com',
  attendeeCompany: null,
  status: 'ATTENDED',
  waitlistPosition: null,
  registrationDate: '2026-02-10T18:00:00Z',
  createdAt: null,
  updatedAt: null,
});

describe('getRegistrationsForAttendee', () => {
  it('should_filterByAttendee_when_listingTheHistory', async () => {
    api.on('listUserRegistrations', () => ({
      data: [registration(57), registration(56)],
      pagination: {
        page: 1,
        limit: 100,
        totalItems: 2,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    }));

    const result = await getRegistrationsForAttendee('hongan.nguyen');

    expect(result.map((r) => r.eventCode)).toEqual(['BATbern57', 'BATbern56']);
    const [call] = api.calls('listUserRegistrations');
    expect(JSON.parse(call.query.get('filter') ?? '')).toEqual({
      attendeeUsername: 'hongan.nguyen',
    });
    expect(call.query.get('page')).toBe('1');
    expect(call.query.get('limit')).toBe('100');
    expect(call.request.headers.has('X-Attendee-Username')).toBe(false);
  });

  it('should_readEveryPage_when_historyIsLongerThanOnePage', async () => {
    const all = Array.from({ length: 130 }, (_, i) => registration(200 - i));
    api.on('listUserRegistrations', ({ query }) => {
      const page = Number(query.get('page'));
      const limit = Number(query.get('limit'));
      return {
        data: all.slice((page - 1) * limit, page * limit),
        pagination: {
          page,
          limit,
          totalItems: all.length,
          totalPages: 2,
          hasNext: page * limit < all.length,
          hasPrev: page > 1,
        },
      };
    });

    const result = await getRegistrationsForAttendee('hongan.nguyen');

    expect(result).toHaveLength(130);
    expect(api.calls('listUserRegistrations').map((c) => c.query.get('page'))).toEqual(['1', '2']);
  });

  it('should_passErrorThrough_when_requestFails', async () => {
    api.on('listUserRegistrations', () => HttpResponse.json({ message: 'boom' }, { status: 500 }));

    await expect(getRegistrationsForAttendee('hongan.nguyen')).rejects.toMatchObject({
      response: { status: 500 },
    });
  });
});
