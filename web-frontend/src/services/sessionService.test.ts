/**
 * Session API Client tests (`sessionService.ts`: batch import, session update)
 *
 * The client runs with the real apiClient against the MSW harness (Epic 17 test fidelity): every
 * request is checked against event-sessions-api and each test reads what went over the wire. An
 * HTTP error reaches the caller as an `Error` with the server's message (`message`, else `error`,
 * else axios' own) and the correlation id when the response carries one.
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { sessionApiClient } from './sessionService';
import type {
  BatchImportSessionRequest,
  SessionBatchImportResult,
} from '@/types/sessionImport.types';

const EVENT = 'BATbern142';

const sessions: BatchImportSessionRequest[] = [
  { title: 'Cloud-native Architektur', abstract: 'Über Plattformen', bat: 142 },
  {
    title: 'Hông Ân über Resilienz',
    abstract: 'Ausfallsicherheit',
    pdf: 'resilienz.pdf',
    bat: 142,
    referenten: [{ name: 'Hông Ân', bio: '', company: 'Brünnimann AG', portrait: '' }],
  },
];

const result: SessionBatchImportResult = {
  totalProcessed: 2,
  successfullyCreated: 2,
  updated: 0,
  skipped: 0,
  failed: 0,
  details: [
    {
      title: 'Cloud-native Architektur',
      status: 'success',
      message: 'Created',
      sessionSlug: 'cloud-native-architektur',
    },
    {
      title: 'Hông Ân über Resilienz',
      status: 'success',
      message: 'Created',
      sessionSlug: 'hong-an-uber-resilienz',
    },
  ],
};

/** An error answer in the services' error shape, optionally with a correlation id header. */
const problem = (status: number, body: Record<string, unknown>, correlationId?: string) =>
  HttpResponse.json(
    { timestamp: '2026-11-10T08:00:00Z', status, ...body },
    { status, headers: correlationId ? { 'x-correlation-id': correlationId } : {} }
  );

describe('sessionApiClient.batchImportSessions', () => {
  it('should_postSessions_when_batchImportRequested', async () => {
    api.on('batchImportSessions', () => result);

    const imported = await sessionApiClient.batchImportSessions(EVENT, sessions);

    expect(imported).toEqual(result);
    const [call] = api.calls('batchImportSessions');
    expect(call.params).toEqual({ eventCode: EVENT });
    expect(call.body).toEqual(sessions);
  });

  it('should_postEmptyList_when_noSessions', async () => {
    const empty = { ...result, totalProcessed: 0, successfullyCreated: 0, details: [] };
    api.on('batchImportSessions', () => empty);

    await expect(sessionApiClient.batchImportSessions(EVENT, [])).resolves.toEqual(empty);
    expect(api.calls('batchImportSessions')[0].body).toEqual([]);
  });

  it('should_throwServerMessage_when_responseHasMessage', async () => {
    api.on('batchImportSessions', () =>
      problem(400, { error: 'Bad Request', message: 'Sessions already exist' })
    );

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toEqual(
      new Error('Sessions already exist')
    );
  });

  it('should_throwErrorField_when_noMessage', async () => {
    api.on('batchImportSessions', () => problem(400, { error: 'Invalid event code' }));

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toEqual(
      new Error('Invalid event code')
    );
  });

  it('should_appendCorrelationId_when_headerPresent', async () => {
    api.on('batchImportSessions', () =>
      problem(500, { error: 'Internal Server Error', message: 'Server error' }, 'abc-123')
    );

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toEqual(
      new Error('Server error (Correlation ID: abc-123)')
    );
  });

  it('should_throwAxiosMessage_when_networkFails', async () => {
    api.on('batchImportSessions', () => HttpResponse.error());

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toEqual(
      new Error('Network Error')
    );
  });
});

describe('sessionApiClient.updateSession', () => {
  it('should_patchSession_when_updated', async () => {
    await sessionApiClient.updateSession(EVENT, 'cloud-talk', {
      title: 'Cloud Security',
      durationMinutes: 45,
    });

    const [call] = api.calls('patchSession');
    expect(call.params).toEqual({ eventCode: EVENT, sessionSlug: 'cloud-talk' });
    expect(call.body).toEqual({ title: 'Cloud Security', durationMinutes: 45 });
  });

  it('should_sendOnlyGivenFields_when_partialUpdate', async () => {
    await sessionApiClient.updateSession(EVENT, 'cloud-talk', { description: 'Neu: Ausblick' });

    expect(api.calls('patchSession')[0].body).toEqual({ description: 'Neu: Ausblick' });
  });

  it('should_throwServerMessage_when_notFound', async () => {
    api.on('patchSession', () =>
      problem(404, { error: 'Not Found', message: 'Session not found: nonexistent' })
    );

    await expect(
      sessionApiClient.updateSession(EVENT, 'nonexistent', { title: 'x' })
    ).rejects.toEqual(new Error('Session not found: nonexistent'));
  });

  it('should_appendCorrelationId_when_patchFails', async () => {
    api.on('patchSession', () =>
      problem(500, { error: 'Internal Server Error', message: 'DB error' }, 'corr-456')
    );

    await expect(sessionApiClient.updateSession(EVENT, 'cloud-talk', {})).rejects.toEqual(
      new Error('DB error (Correlation ID: corr-456)')
    );
  });
});
