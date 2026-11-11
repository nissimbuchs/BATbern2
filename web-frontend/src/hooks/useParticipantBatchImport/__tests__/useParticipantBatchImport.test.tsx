/**
 * useParticipantBatchImport Hook Tests
 *
 * The hook regroups CSV rows (one per person) per event and sends each event's people to
 * `POST /events/{eventCode}/participants` (Story 4.10c, operationId `addParticipants`), in chunks
 * of 20, one request after the other. A person's status is aggregated over their events. It runs
 * with the real service and apiClient against the MSW harness (Epic 17 test fidelity), so a failed
 * request reaches the hook as the AxiosError real axios raises.
 */

import { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { useParticipantBatchImport } from '../useParticipantBatchImport';
import type {
  ParticipantImportRow,
  ParticipantImportPerson,
  ParticipantImportResult,
} from '../../../types/participantImport.types';

const row = (
  n: number,
  eventCodes: string[],
  email = `p${n}@example.com`
): ParticipantImportRow => ({
  email,
  firstName: `First${n}`,
  lastName: `Last${n}`,
  eventCodes,
});

/** Answers every person of the request as registered. */
const allRegistered = ({ body }: { body?: { people?: ParticipantImportPerson[] } }) => ({
  enrolled: body?.people?.length ?? 0,
  skipped: 0,
  failed: 0,
  results: (body?.people ?? []).map((p): ParticipantImportResult => ({
    email: p.email ?? '',
    outcome: 'REGISTERED',
  })),
});

describe('useParticipantBatchImport', () => {
  let queryClient: QueryClient;

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  });

  afterEach(() => {
    queryClient.clear();
  });

  it('should_sendOneRequestPerEvent_when_personAttendedTwoEvents', async () => {
    api.on('addParticipants', allRegistered);
    const rows = [row(1, ['BATbern1', 'BATbern2']), row(2, ['BATbern2'])];
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCandidates>> | undefined;
    await act(async () => {
      outcome = await result.current.importCandidates(rows);
    });

    const calls = api.calls('addParticipants');
    expect(calls.map((c) => c.params.eventCode)).toEqual(['BATbern1', 'BATbern2']);
    expect(calls[0].body).toEqual({
      people: [
        { email: 'p1@example.com', firstName: 'First1', lastName: 'Last1', status: 'ATTENDED' },
      ],
    });
    expect(calls[1].body?.people?.map((p) => p.email)).toEqual([
      'p1@example.com',
      'p2@example.com',
    ]);
    expect(outcome).toEqual({ total: 2, success: 2, failed: 0, skipped: 0 });
    expect(result.current.candidates.map((c) => c.importStatus)).toEqual(['success', 'success']);
    expect(result.current.isImporting).toBe(false);
  });

  it('should_chunkBy20AndReportProgressPerRequest_when_eventHasMoreThan20People', async () => {
    api.on('addParticipants', allRegistered);
    const rows = Array.from({ length: 41 }, (_, i) => row(i, ['BATbern7']));
    const progress: Array<[number, number]> = [];
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates(rows, (current, total) =>
        progress.push([current, total])
      );
    });

    expect(api.calls('addParticipants').map((c) => c.body?.people?.length)).toEqual([20, 20, 1]);
    expect(progress).toEqual([
      [1, 3],
      [2, 3],
      [3, 3],
    ]);
  });

  it('should_markPersonFailed_when_oneOfTheirEventsFails', async () => {
    // BATbern1 registers everyone; BATbern2 reports p1 as FAILED; BATbern3 is a network error.
    api.on('addParticipants', (call) => {
      const event = call.params.eventCode;
      if (event === 'BATbern3') return HttpResponse.error();
      const answer = allRegistered(call);
      if (event === 'BATbern2') {
        answer.results[0] = {
          email: 'p1@example.com',
          outcome: 'FAILED',
          reason: 'linked to another account',
        };
      }
      return answer;
    });
    const rows = [row(1, ['BATbern1', 'BATbern2']), row(2, ['BATbern1']), row(3, ['BATbern3'])];
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCandidates>> | undefined;
    await act(async () => {
      outcome = await result.current.importCandidates(rows);
    });

    expect(outcome).toEqual({ total: 3, success: 1, failed: 2, skipped: 0 });
    expect(result.current.candidates.map((c) => [c.importStatus, c.errorMessage])).toEqual([
      ['error', '1/2 events failed: BATbern2: linked to another account'],
      ['success', undefined],
      ['error', '1/1 events failed: BATbern3: No response from server'],
    ]);
  });

  it('should_useServerMessageOrStatusText_when_requestIsRefused', async () => {
    api.on('addParticipants', (call) =>
      call.params.eventCode === 'BATbern1'
        ? HttpResponse.json({ status: 400, message: 'Give exactly one mode' }, { status: 400 })
        : HttpResponse.json({ status: 400 }, { status: 400, statusText: 'Bad Request' })
    );
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates([row(1, ['BATbern1']), row(2, ['BATbern2'])]);
    });

    expect(result.current.candidates.map((c) => c.errorMessage)).toEqual([
      '1/1 events failed: BATbern1: Give exactly one mode',
      '1/1 events failed: BATbern2: Bad Request',
    ]);
  });

  it('should_skipPersonWithoutEvents_and_sendNothingForThem', async () => {
    api.on('addParticipants', allRegistered);
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCandidates>> | undefined;
    await act(async () => {
      outcome = await result.current.importCandidates([row(1, []), row(2, ['BATbern4'])]);
    });

    expect(outcome).toEqual({ total: 2, success: 1, failed: 0, skipped: 1 });
    expect(
      api.calls('addParticipants').flatMap((c) => c.body?.people?.map((p) => p.email))
    ).toEqual(['p2@example.com']);
    expect(result.current.candidates[0].importStatus).toBe('skipped');
  });

  it('should_flagSyntheticEmail_when_emailIsOnBatbernDomain', async () => {
    api.on('addParticipants', allRegistered);
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates([
        row(1, ['BATbern1'], 'first.last@batbern.ch'),
        row(2, ['BATbern1'], 'real@example.com'),
      ]);
    });

    expect(result.current.candidates.map((c) => c.isSyntheticEmail)).toEqual([true, false]);
  });

  it('should_invalidateUserAndRegistrationCaches_when_importCompletes', async () => {
    api.on('addParticipants', allRegistered);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates([row(1, ['BATbern1'])]);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['registrations'] });
  });
});
