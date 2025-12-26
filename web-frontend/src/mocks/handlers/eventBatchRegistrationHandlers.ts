import { http, HttpResponse, delay } from 'msw';
import type { components } from '../../types/generated/events-api.types';

type BatchRegistrationRequest = components['schemas']['BatchRegistrationRequest'];
type BatchRegistrationResponse = components['schemas']['BatchRegistrationResponse'];

export const eventBatchRegistrationHandlers = [
  http.post('/api/v1/events/batch_registrations', async ({ request }) => {
    const body = (await request.json()) as BatchRegistrationRequest;

    // Simulate network delay
    await delay(100);

    // Simulate successful batch registration
    const username = `${body.firstName.toLowerCase()}.${body.lastName.toLowerCase()}`;

    const response: BatchRegistrationResponse = {
      username,
      totalRegistrations: body.registrations.length,
      successfulRegistrations: body.registrations.length,
      failedRegistrations: [],
      errors: [],
    };

    return HttpResponse.json(response, { status: 200 });
  }),

  // Mock partial success scenario (for testing)
  http.post('/api/v1/events/batch_registrations/partial', async ({ request }) => {
    const body = (await request.json()) as BatchRegistrationRequest;
    await delay(100);

    const username = `${body.firstName.toLowerCase()}.${body.lastName.toLowerCase()}`;
    const totalRegs = body.registrations.length;
    const failedCount = Math.min(1, totalRegs); // Fail 1 registration

    const response: BatchRegistrationResponse = {
      username,
      totalRegistrations: totalRegs,
      successfulRegistrations: totalRegs - failedCount,
      failedRegistrations:
        failedCount > 0
          ? [
              {
                eventCode: body.registrations[0].eventCode,
                reason: 'Event not found',
              },
            ]
          : [],
      errors: failedCount > 0 ? [`Event ${body.registrations[0].eventCode} not found`] : [],
    };

    return HttpResponse.json(response, { status: 200 });
  }),

  // Mock 400 Bad Request
  http.post('/api/v1/events/batch_registrations/invalid', async () => {
    await delay(100);

    return HttpResponse.json(
      {
        error: 'Bad Request',
        message: 'Invalid request data',
        details: ['participantEmail must be a valid email format'],
      },
      { status: 400 }
    );
  }),

  // Mock 401 Unauthorized
  http.post('/api/v1/events/batch_registrations/unauthorized', async () => {
    await delay(100);

    return HttpResponse.json(
      {
        error: 'Unauthorized',
        message: 'Missing or invalid authentication token',
      },
      { status: 401 }
    );
  }),

  // Mock 403 Forbidden
  http.post('/api/v1/events/batch_registrations/forbidden', async () => {
    await delay(100);

    return HttpResponse.json(
      {
        error: 'Forbidden',
        message: 'Insufficient permissions - ORGANIZER role required',
      },
      { status: 403 }
    );
  }),

  // Mock 500 Internal Server Error
  http.post('/api/v1/events/batch_registrations/error', async () => {
    await delay(100);

    return HttpResponse.json(
      {
        error: 'Internal Server Error',
        message: 'An unexpected error occurred',
      },
      { status: 500 }
    );
  }),
];
