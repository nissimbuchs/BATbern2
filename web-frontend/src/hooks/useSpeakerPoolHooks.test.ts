/**
 * useSpeakerPool + useSpeakerOutreach hooks against the MSW harness (moved off the service mocks
 * in Story 16.8c).
 *
 * The hooks run their real services and apiClient; the harness checks every request against
 * `docs/api/event-speakers-api.openapi.yml` and answers with the spec example or an override.
 * Covered: the pool query (enabled guard, errors), add, delete, patch, promote and invite with the
 * caches each refreshes, the outreach history (enabled guard) and recording an outreach.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import { aSpeakerPoolEntry } from '@/test/fixtures';
import {
  useSpeakerPool,
  useAddSpeakerToPool,
  useDeleteSpeakerFromPool,
  usePatchSpeakerPool,
  usePromoteSpeakerToReady,
  useSendInvitation,
} from './useSpeakerPool';
import { useSpeakerOutreachHistory, useRecordOutreach } from './useSpeakerOutreach';

const EVENT = 'BATbern142';
const POOL_ID = '3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607';

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const problem = (status: number, details?: Record<string, unknown>) =>
  HttpResponse.json(
    {
      timestamp: '2026-11-10T10:00:00Z',
      path: '/api/v1/events',
      status,
      error: 'x',
      message: 'x',
      ...(details ? { details } : {}),
    },
    { status }
  );

/** The query keys a mutation marked stale. */
const invalidatedKeys = (spy: { mock: { calls: unknown[][] } }): unknown[][] =>
  spy.mock.calls.map((call) => (call[0] as { queryKey: unknown[] }).queryKey);

let qc: QueryClient;

beforeEach(() => {
  qc = createQC();
});

describe('useSpeakerPool', () => {
  it('fetches the pool of an event', async () => {
    const entries = [aSpeakerPoolEntry({ speakerName: 'Alice Smith' })];
    api.on('getSpeakerPool', () => entries);

    const { result } = renderHook(() => useSpeakerPool(EVENT), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.calls('getSpeakerPool')[0].params.eventCode).toBe(EVENT);
    expect(result.current.data).toEqual(entries);
  });

  it('sends nothing without an event code', () => {
    const { result } = renderHook(() => useSpeakerPool(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(api.calls('getSpeakerPool')).toHaveLength(0);
  });

  it('is in error when the event is unknown (404)', async () => {
    api.on('getSpeakerPool', () => problem(404));

    const { result } = renderHook(() => useSpeakerPool(EVENT), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useAddSpeakerToPool', () => {
  it('posts the speaker and refreshes the pool and the status summary', async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: EVENT,
        request: { speakerName: 'Alice', company: 'Acme', expertise: 'Cloud' },
      });
    });

    expect(api.calls('addSpeakerToPool')[0].body).toEqual({
      speakerName: 'Alice',
      company: 'Acme',
      expertise: 'Cloud',
    });
    expect(invalidatedKeys(spy)).toContainEqual(['speakerStatusSummary', EVENT]);
    expect(invalidatedKeys(spy).some((key) => key.includes('speakerPool'))).toBe(true);
  });

  it('is in error when the server refuses', async () => {
    api.on('addSpeakerToPool', () => problem(409));
    const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: EVENT, request: { speakerName: 'Alice' } })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useDeleteSpeakerFromPool', () => {
  it('deletes the entry and refreshes the pool', async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useDeleteSpeakerFromPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ eventCode: EVENT, speakerId: POOL_ID });
    });

    expect(api.calls('deleteSpeakerFromPool')[0].params.speakerId).toBe(POOL_ID);
    expect(invalidatedKeys(spy).some((key) => key.includes('speakerPool'))).toBe(true);
  });

  it('is in error when the entry is unknown (404)', async () => {
    api.on('deleteSpeakerFromPool', () => problem(404));
    const { result } = renderHook(() => useDeleteSpeakerFromPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ eventCode: EVENT, speakerId: POOL_ID }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('usePatchSpeakerPool', () => {
  it('patches the entry and refreshes the pool', async () => {
    api.on('patchSpeakerPoolEntry', () =>
      aSpeakerPoolEntry({ assignedOrganizerId: 'carla.muster' })
    );
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => usePatchSpeakerPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: EVENT,
        speakerId: POOL_ID,
        request: { assignedOrganizerId: 'carla.muster' },
      });
    });

    expect(api.calls('patchSpeakerPoolEntry')[0].body).toEqual({
      assignedOrganizerId: 'carla.muster',
    });
    await waitFor(() => expect(result.current.data?.assignedOrganizerId).toBe('carla.muster'));
    expect(invalidatedKeys(spy).some((key) => key.includes('speakerPool'))).toBe(true);
  });

  it('is in error when the server refuses (403)', async () => {
    api.on('patchSpeakerPoolEntry', () => problem(403));
    const { result } = renderHook(() => usePatchSpeakerPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: EVENT, speakerId: POOL_ID, request: { notes: 'test' } })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('usePromoteSpeakerToReady (Story 11.D.1)', () => {
  it('promotes and refreshes the pool, the status summary and the event (its new session)', async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => usePromoteSpeakerToReady(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: EVENT,
        speakerId: POOL_ID,
        request: { email: 'alice@example.com', firstName: 'Alice', lastName: 'Smith' },
      });
    });

    expect(api.calls('promoteSpeakerToReady')[0].body).toEqual({
      email: 'alice@example.com',
      firstName: 'Alice',
      lastName: 'Smith',
    });
    expect(invalidatedKeys(spy)).toEqual(
      expect.arrayContaining([
        ['speakerPool', 'list', EVENT],
        ['speakerStatusSummary', EVENT],
        ['event', EVENT],
      ])
    );
  });

  it('exposes the 409 INVALID_PROMOTION_STATE answer', async () => {
    api.on('promoteSpeakerToReady', () =>
      problem(409, { code: 'INVALID_PROMOTION_STATE', currentState: 'INVITED' })
    );
    const { result } = renderHook(() => usePromoteSpeakerToReady(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({
          eventCode: EVENT,
          speakerId: POOL_ID,
          request: { email: 'alice@example.com', firstName: 'Alice', lastName: 'Example' },
        })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    const error = result.current.error as unknown as {
      response: { status: number; data: { details: { code: string } } };
    };
    expect(error.response.status).toBe(409);
    expect(error.response.data.details.code).toBe('INVALID_PROMOTION_STATE');
  });
});

describe('useSendInvitation', () => {
  it('posts the options and refreshes the pool and the status summary', async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useSendInvitation(EVENT), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        username: 'alice.smith',
        options: {
          responseDeadline: '2026-12-15T00:00:00Z',
          contentDeadline: '2027-01-15T00:00:00Z',
        },
      });
    });

    const [call] = api.calls('sendSpeakerInvitation');
    expect(call.params.username).toBe('alice.smith');
    expect(call.body).toEqual({
      responseDeadline: '2026-12-15T00:00:00Z',
      contentDeadline: '2027-01-15T00:00:00Z',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidatedKeys(spy)).toContainEqual(['speakerStatusSummary', EVENT]);
    expect(invalidatedKeys(spy).some((key) => key.includes('speakerPool'))).toBe(true);
  });

  it('sends an empty body without options', async () => {
    const { result } = renderHook(() => useSendInvitation(EVENT), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ username: 'alice.smith' });
    });

    expect(api.calls('sendSpeakerInvitation')[0].body).toEqual({});
  });

  it('is in error when the speaker cannot be invited (409)', async () => {
    api.on('sendSpeakerInvitation', () => problem(409));
    const { result } = renderHook(() => useSendInvitation(EVENT), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ username: 'alice.smith' }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useSpeakerOutreachHistory', () => {
  it('fetches the outreach history of a speaker', async () => {
    const { result } = renderHook(() => useSpeakerOutreachHistory(EVENT, POOL_ID), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.calls('getSpeakerOutreachHistory')[0].params).toEqual({
      eventCode: EVENT,
      speakerId: POOL_ID,
    });
    expect(Array.isArray(result.current.data)).toBe(true);
  });

  it.each([
    ['', POOL_ID],
    [EVENT, ''],
  ])('sends nothing without event code or speaker (%s, %s)', (eventCode, speakerId) => {
    const { result } = renderHook(() => useSpeakerOutreachHistory(eventCode, speakerId), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(api.calls('getSpeakerOutreachHistory')).toHaveLength(0);
  });

  it('is in error when the speaker is unknown (404)', async () => {
    api.on('getSpeakerOutreachHistory', () => problem(404));

    const { result } = renderHook(() => useSpeakerOutreachHistory(EVENT, POOL_ID), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useRecordOutreach', () => {
  it('posts the outreach and refreshes the outreach history', async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useRecordOutreach(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: EVENT,
        speakerId: POOL_ID,
        request: {
          contactMethod: 'email',
          contactDate: '2026-11-09T10:00:00Z',
          notes: 'First contact',
        },
      });
    });

    expect(api.calls('recordSpeakerOutreach')[0].body).toEqual({
      contactMethod: 'email',
      contactDate: '2026-11-09T10:00:00Z',
      notes: 'First contact',
    });
    expect(invalidatedKeys(spy).some((key) => key.includes('speakerOutreach'))).toBe(true);
  });

  it('is in error when the server refuses (400)', async () => {
    api.on('recordSpeakerOutreach', () => problem(400));
    const { result } = renderHook(() => useRecordOutreach(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({
          eventCode: EVENT,
          speakerId: POOL_ID,
          request: { contactMethod: 'email', contactDate: '2026-11-09T10:00:00Z' },
        })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
