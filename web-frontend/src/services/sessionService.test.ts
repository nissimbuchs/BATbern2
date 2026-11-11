/**
 * Session API Client tests (`sessionService.ts`: batch import, session update)
 *
 * The client runs with the real apiClient against the MSW harness (Epic 17 test fidelity): every
 * request is checked against event-sessions-api and each test reads what went over the wire. An
 * HTTP error reaches the caller as an `Error` with the server's message (`message`, else `error`,
 * else axios' own) and the correlation id when the response carries one.
 */

import { afterEach, describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import apiClient from '@/services/api/apiClient';
import { sessionApiClient, SessionImportError } from './sessionService';
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

/**
 * Makes the next requests fail before they leave the process with `value`, a non-axios error (an
 * auth token lookup that throws, say). A request interceptor on the real apiClient: it runs first
 * and its rejection reaches the service unchanged.
 */
let ejectFailure: (() => void) | null = null;
const failBeforeSending = (value: unknown) => {
  const id = apiClient.interceptors.request.use(() => {
    throw value;
  });
  ejectFailure = () => apiClient.interceptors.request.eject(id);
};
afterEach(() => {
  ejectFailure?.();
  ejectFailure = null;
});

describe('sessionApiClient.batchImportSessions', () => {
  it('should_postSessions_when_batchImportRequested', async () => {
    api.on('createSession', () => result, { status: 200 });

    const imported = await sessionApiClient.batchImportSessions(EVENT, sessions);

    expect(imported).toEqual(result);
    const [call] = api.calls('createSession');
    expect(call.params).toEqual({ eventCode: EVENT });
    expect(call.body).toEqual({ imports: sessions });
  });

  it('should_allowTwoMinutes_when_batchImportRequested', async () => {
    // Large PDFs are fetched from the CDN during the import: the request gets a 120 s timeout.
    let timeout: number | undefined;
    const id = apiClient.interceptors.request.use((config) => {
      timeout = config.timeout;
      return config;
    });
    try {
      await sessionApiClient.batchImportSessions(EVENT, sessions);
    } finally {
      apiClient.interceptors.request.eject(id);
    }

    expect(timeout).toBe(120000);
  });

  it('should_passPlainErrorThrough_when_batchImportThrowsBeforeSending', async () => {
    const thrown = new Error('Token lookup failed');
    failBeforeSending(thrown);

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toBe(thrown);
  });

  it('should_wrapNonError_when_batchImportThrowsBeforeSending', async () => {
    failBeforeSending('raw string error');

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toEqual(
      new Error('An unexpected error occurred')
    );
  });

  it('should_sendNothing_when_noSessions', async () => {
    // The server refuses an empty `imports` (400); there is nothing to import, so nothing is sent.
    await expect(sessionApiClient.batchImportSessions(EVENT, [])).resolves.toEqual({
      totalProcessed: 0,
      successfullyCreated: 0,
      updated: 0,
      skipped: 0,
      failed: 0,
      details: [],
    });
    expect(api.calls('createSession')).toHaveLength(0);
  });

  it('should_sendChunksOf100AndSumThem_when_moreThan100Sessions', async () => {
    const many: BatchImportSessionRequest[] = Array.from({ length: 101 }, (_, i) => ({
      title: `Session ${i + 1}`,
      bat: 142,
    }));
    api.on(
      'createSession',
      ({ body }) => {
        const items = body?.imports ?? [];
        return {
          totalProcessed: items.length,
          successfullyCreated: items.length,
          updated: 0,
          skipped: 0,
          failed: 0,
          details: items.map((item) => ({
            title: item.title,
            status: 'success' as const,
            message: 'Created',
            sessionSlug: null,
          })),
        };
      },
      { status: 200 }
    );

    const imported = await sessionApiClient.batchImportSessions(EVENT, many);

    const calls = api.calls('createSession');
    expect(calls.map((c) => c.body?.imports?.length)).toEqual([100, 1]);
    expect(imported.totalProcessed).toBe(101);
    expect(imported.details).toHaveLength(101);
    expect(imported.details[100].title).toBe('Session 101');
  });

  it('should_raiseTheSavedTotals_when_aLaterChunkFails', async () => {
    const many: BatchImportSessionRequest[] = Array.from({ length: 101 }, (_, i) => ({
      title: `Session ${i + 1}`,
      bat: 142,
    }));
    api.on(
      'createSession',
      ({ body }) => {
        const items = body?.imports ?? [];
        if (items.length === 1) {
          return problem(500, { error: 'Internal Server Error', message: 'Import failed' });
        }
        return {
          totalProcessed: items.length,
          successfullyCreated: items.length,
          updated: 0,
          skipped: 0,
          failed: 0,
          details: items.map((item) => ({
            title: item.title,
            status: 'success' as const,
            message: 'Created',
            sessionSlug: null,
          })),
        };
      },
      { status: 200 }
    );

    const error = await sessionApiClient.batchImportSessions(EVENT, many).then(
      () => {
        throw new Error('expected a rejection');
      },
      (e: unknown) => e
    );

    expect(api.calls('createSession')).toHaveLength(2);
    expect(error).toBeInstanceOf(SessionImportError);
    const partial = (error as SessionImportError).partial;
    expect((error as SessionImportError).message).toBe('Import failed');
    expect(partial.successfullyCreated).toBe(100);
    expect(partial.details).toHaveLength(100);
  });

  it('should_throwServerMessage_when_responseHasMessage', async () => {
    api.on('createSession', () =>
      problem(400, { error: 'Bad Request', message: 'Sessions already exist' })
    );

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toEqual(
      new Error('Sessions already exist')
    );
  });

  it('should_throwErrorField_when_noMessage', async () => {
    api.on('createSession', () => problem(400, { error: 'Invalid event code' }));

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toEqual(
      new Error('Invalid event code')
    );
  });

  it('should_appendCorrelationId_when_headerPresent', async () => {
    api.on('createSession', () =>
      problem(500, { error: 'Internal Server Error', message: 'Server error' }, 'abc-123')
    );

    await expect(sessionApiClient.batchImportSessions(EVENT, sessions)).rejects.toEqual(
      new Error('Server error (Correlation ID: abc-123)')
    );
  });

  it('should_throwAxiosMessage_when_networkFails', async () => {
    api.on('createSession', () => HttpResponse.error());

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
