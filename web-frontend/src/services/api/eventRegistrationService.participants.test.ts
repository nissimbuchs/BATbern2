/**
 * The participants create (Story 4.10c) against the MSW harness: every helper posts to the one
 * `POST /events/{eventCode}/participants` (operationId `addParticipants`) with exactly one mode.
 * The harness validates each body against `docs/api/event-registrations-api.openapi.yml`.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { addParticipant, enrollStakeholders, importParticipants } from './eventRegistrationService';

const problem = (status: number, details?: Record<string, unknown>) =>
  HttpResponse.json(
    {
      timestamp: '2026-11-11T10:00:00Z',
      path: '/api/v1/events/BATbern73/participants',
      status,
      error: 'x',
      message: 'x',
      ...(details ? { details } : {}),
    },
    { status }
  );

describe('participants create', () => {
  it('enrollStakeholders sends only stakeholders: true and returns the counts', async () => {
    api.on('addParticipants', () => ({ enrolled: 5, skipped: 2 }));

    expect(await enrollStakeholders('BATbern73')).toEqual({ enrolled: 5, skipped: 2 });
    const [call] = api.calls('addParticipants');
    expect(call.body).toEqual({ stakeholders: true });
    expect(call.params.eventCode).toBe('BATbern73');
  });

  it('addParticipant sends username with the force and notify defaults', async () => {
    api.on(
      'addParticipants',
      () => ({
        registrationCode: 'BATbern73-reg-X',
        status: 'confirmed',
        attendeeUsername: 'jane.doe',
      }),
      { status: 201 }
    );

    const result = await addParticipant('BATbern73', { username: 'jane.doe' });

    expect(result.registrationCode).toBe('BATbern73-reg-X');
    expect(api.calls('addParticipants')[0].body).toEqual({
      username: 'jane.doe',
      force: false,
      notify: true,
    });
  });

  it('addParticipant passes a capacity 409 through to the caller', async () => {
    api.on('addParticipants', () => problem(409, { code: 'capacity_exceeded' }));

    await expect(addParticipant('BATbern73', { username: 'jane.doe' })).rejects.toMatchObject({
      response: { status: 409, data: { details: { code: 'capacity_exceeded' } } },
    });
  });

  it('importParticipants sends the people of one event and returns the result per person', async () => {
    api.on('addParticipants', () => ({
      enrolled: 1,
      skipped: 0,
      failed: 1,
      results: [
        {
          email: 'hugo.muster@example.com',
          username: 'hugo.muster',
          outcome: 'REGISTERED' as const,
        },
        { email: 'linked.person@example.com', outcome: 'FAILED' as const, reason: 'linked' },
      ],
    }));

    const people = [
      {
        email: 'hugo.muster@example.com',
        firstName: 'Hugo',
        lastName: 'Müster',
        status: 'ATTENDED' as const,
      },
      {
        email: 'linked.person@example.com',
        firstName: 'Linked',
        lastName: 'Person',
        status: 'ATTENDED' as const,
      },
    ];
    const result = await importParticipants('BATbern12', people);

    expect(result.results?.map((r) => r.outcome)).toEqual(['REGISTERED', 'FAILED']);
    expect(api.calls('addParticipants')[0].body).toEqual({ people });
  });
});
