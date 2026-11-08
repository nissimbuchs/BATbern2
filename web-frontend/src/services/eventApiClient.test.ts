/**
 * Event API client against the MSW harness (Story 17.0c1; first written in Story 2.5.3).
 *
 * This file used to expect a network error from every call ("no backend in tests"), so it proved
 * nothing about the requests. The harness now answers every call with the spec example, checks it
 * against docs/api, and records it: these tests assert what is sent (method, path, query, body)
 * and how answers and errors come back. The client-side validations are unchanged.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { eventApiClient } from '@/services/eventApiClient';
import type {
  CreateEventRequest,
  UpdateEventRequest,
  PatchEventRequest,
} from '@/types/event.types';

const inDays = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString();
};

const validEvent = (overrides: Partial<CreateEventRequest> = {}): CreateEventRequest => ({
  title: 'Test Event',
  eventNumber: 56,
  date: inDays(60),
  registrationDeadline: inDays(50),
  venueName: 'Test Venue',
  venueAddress: 'Test Address',
  venueCapacity: 100,
  organizerUsername: 'test.organizer',
  eventType: 'EVENING',
  currentAttendeeCount: 0,
  ...overrides,
});

/** The query of the only listEvents call, as a plain object. */
const listQuery = () => {
  const calls = api.calls('listEvents');
  expect(calls).toHaveLength(1);
  return Object.fromEntries(calls[0].query);
};

describe('Event API client', () => {
  describe('client-side validation (nothing is sent)', () => {
    it('rejects an event date in the past', async () => {
      await expect(
        eventApiClient.createEvent(
          validEvent({ date: '2020-01-01T10:00:00Z', registrationDeadline: '2019-12-15T23:59:59Z' })
        )
      ).rejects.toThrow(/Event date must be in the future/);
      expect(api.allCalls()).toEqual([]);
    });

    it('rejects a registration deadline after the event date', async () => {
      await expect(
        eventApiClient.createEvent(validEvent({ registrationDeadline: inDays(70) }))
      ).rejects.toThrow(/Registration deadline must be before event date/);
      expect(api.allCalls()).toEqual([]);
    });

    it('rejects a negative capacity', async () => {
      await expect(eventApiClient.createEvent(validEvent({ venueCapacity: -100 }))).rejects.toThrow(
        /Venue capacity must be positive/
      );
      expect(api.allCalls()).toEqual([]);
    });

    it('rejects an invalid deadline on update', async () => {
      const updates: UpdateEventRequest = {
        title: 'Test Event',
        date: inDays(60),
        registrationDeadline: inDays(70),
      };
      await expect(eventApiClient.updateEvent('BATbern56', updates)).rejects.toThrow(
        /Registration deadline must be before event date/
      );
      expect(api.allCalls()).toEqual([]);
    });
  });

  describe('create, update, patch, delete', () => {
    it('POSTs a valid event as it is and returns the created event', async () => {
      const event = validEvent();

      const created = await eventApiClient.createEvent(event);

      const [call] = api.calls('createEvent');
      expect(call.body).toEqual(event);
      expect(created.eventCode).toBeTruthy();
    });

    it('accepts a past date for an ARCHIVED event', async () => {
      await eventApiClient.createEvent(
        validEvent({
          date: '2020-01-01T10:00:00Z',
          registrationDeadline: '2019-12-15T23:59:59Z',
          workflowState: 'ARCHIVED',
        })
      );

      expect(api.calls('createEvent')).toHaveLength(1);
    });

    it('PUTs a full update to /events/{eventCode}', async () => {
      const update: UpdateEventRequest = { title: 'New title', date: inDays(60) };

      await eventApiClient.updateEvent('BATbern56', update);

      const [call] = api.calls('updateEvent');
      expect(call.params).toEqual({ eventCode: 'BATbern56' });
      expect(call.body).toEqual(update);
    });

    it('PATCHes only the changed fields', async () => {
      const partial: PatchEventRequest = { description: 'Updated description only' };

      await eventApiClient.patchEvent('BATbern56', partial);

      const [call] = api.calls('patchEvent');
      expect(call.method).toBe('PATCH');
      expect(call.body).toEqual({ description: 'Updated description only' });
    });

    it('DELETEs /events/{eventCode}', async () => {
      await eventApiClient.deleteEvent('BATbern56');

      expect(api.calls('deleteEvent')[0].params).toEqual({ eventCode: 'BATbern56' });
    });
  });

  describe('getEvent', () => {
    it('sends include for the expansions and returns the event', async () => {
      const event = await eventApiClient.getEvent('BATbern56', {
        expand: ['speakers', 'sessions', 'venue'],
      });

      const [call] = api.calls('getEvent');
      expect(call.params).toEqual({ eventCode: 'BATbern56' });
      expect(call.query.get('include')).toBe('speakers,sessions,venue');
      expect(event.eventCode).toBeTruthy();
    });

    it('sends no include without expansions', async () => {
      await eventApiClient.getEvent('BATbern56');

      expect(api.calls('getEvent')[0].url.search).toBe('');
    });
  });

  describe('getEvents', () => {
    it('sends page and limit, and nothing else by default', async () => {
      const result = await eventApiClient.getEvents({ page: 2, limit: 10 });

      expect(listQuery()).toEqual({ page: '2', limit: '10' });
      expect(result.data.length).toBeGreaterThan(0);
    });

    it('builds the JSON filter from workflow states, year, search and topic codes', async () => {
      await eventApiClient.getEvents(
        { page: 1, limit: 20 },
        {
          workflowState: ['ARCHIVED', 'EVENT_COMPLETED'],
          year: 2024,
          search: 'C++ & Java (Advanced)',
          topicCode: ['cloud', 'devops'],
        }
      );

      expect(JSON.parse(listQuery().filter)).toEqual({
        workflowState: { $in: ['ARCHIVED', 'EVENT_COMPLETED'] },
        year: 2024,
        title: { $contains: 'C++ & Java (Advanced)' },
        topicCode: { $in: ['cloud', 'devops'] },
      });
    });

    it('sends no filter for empty filters', async () => {
      await eventApiClient.getEvents({ page: 1, limit: 20 }, { search: '', topicCode: [] });

      expect(listQuery()).not.toHaveProperty('filter');
    });

    it('sends include and sort', async () => {
      await eventApiClient.getEvents(
        { page: 1, limit: 20 },
        {},
        { expand: ['topics', 'sessions', 'speakers'], sort: '-date' }
      );

      expect(listQuery()).toMatchObject({ include: 'topics,sessions,speakers', sort: '-date' });
    });

    it('sends no include for an empty expansion list', async () => {
      await eventApiClient.getEvents({ page: 1, limit: 20 }, {}, { expand: [] });

      expect(listQuery()).not.toHaveProperty('include');
    });

    it('sends includeArchived only when it is set', async () => {
      await eventApiClient.getEvents({ page: 1, limit: 20 }, { includeArchived: true });
      await eventApiClient.getEvents({ page: 1, limit: 20 }, { includeArchived: false });
      await eventApiClient.getEvents({ page: 1, limit: 20 });

      expect(api.calls('listEvents').map((c) => c.query.get('includeArchived'))).toEqual([
        'true',
        'false',
        null,
      ]);
    });
  });

  describe('registrations and topics', () => {
    it('POSTs a registration to /events/{eventCode}/registrations', async () => {
      await eventApiClient.createRegistration('BATbern56', {
        firstName: 'Anna',
        lastName: 'Muster',
        email: 'anna.muster@example.com',
        company: 'ACME Corp',
        termsAccepted: true,
      });

      const [call] = api.calls('createRegistration');
      expect(call.params).toEqual({ eventCode: 'BATbern56' });
      expect(call.body).toMatchObject({ email: 'anna.muster@example.com' });
    });

    it('confirms a registration with the token as query parameter', async () => {
      await eventApiClient.confirmRegistration('BATbern56', 'test-jwt-token');

      expect(api.calls('confirmRegistration')[0].query.get('token')).toBe('test-jwt-token');
    });

    it('reads a registration by its code', async () => {
      await eventApiClient.getRegistration('BATbern56', 'BATbern56-reg-ABC123');

      expect(api.calls('getRegistration')[0].params).toEqual({
        eventCode: 'BATbern56',
        registrationCode: 'BATbern56-reg-ABC123',
      });
    });

    it('assigns a topic with its code in the body', async () => {
      await eventApiClient.assignTopicToEvent('BATbern56', 'cloud-native');

      expect(api.calls('selectTopicForEvent')[0].body).toEqual({ topicCode: 'cloud-native' });
    });

    it('returns null when no current event is featured (404)', async () => {
      api.on('getPublicCurrentEvent', () => new HttpResponse(null, { status: 404 }));

      expect(await eventApiClient.getPublicCurrentEvent()).toBeNull();
    });
  });

  describe('error transformation', () => {
    const failWith = (status: number, body: Record<string, unknown> = {}, headers = {}) =>
      api.on('getEvent', () => HttpResponse.json(body, { status, headers }));

    it.each([
      [401, {}, 'Unauthorized: Please log in'],
      [403, { error: 'turnstile_required' }, 'turnstile_required'],
      [404, {}, 'Not Found: The requested event was not found'],
      [409, { message: 'Already registered' }, 'Already registered'],
      [500, {}, 'Server Error: Please try again later'],
    ])('maps %i to a readable message', async (status, body, message) => {
      failWith(status, body);

      await expect(eventApiClient.getEvent('BATbern56')).rejects.toThrow(message);
    });

    it('appends the correlation id and keeps the status', async () => {
      failWith(500, {}, { 'X-Correlation-ID': 'corr-123' });

      await expect(eventApiClient.getEvent('BATbern56')).rejects.toMatchObject({
        message: 'Server Error: Please try again later (ID: corr-123)',
        status: 500,
      });
    });
  });
});
