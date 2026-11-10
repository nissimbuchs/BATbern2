/**
 * useParticipantBatchImport Hook Tests
 *
 * The hook imports in parallel batches of 10 with a 100 ms pause between batches. Replaces a suite
 * written for the earlier one-by-one importer, which had been entirely skipped since the switch.
 * It runs with the real eventApi and apiClient against the MSW harness (Epic 17 test fidelity), so
 * a failed request reaches the hook as the AxiosError real axios raises.
 */

import { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { useParticipantBatchImport } from '../useParticipantBatchImport';
import type {
  BatchRegistrationRequest,
  BatchRegistrationResponse,
} from '../../../types/participantImport.types';

const request = (n: number, email = `p${n}@example.com`): BatchRegistrationRequest => ({
  participantEmail: email,
  firstName: `First${n}`,
  lastName: `Last${n}`,
  registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
});

const fullSuccess: BatchRegistrationResponse = {
  username: 'first.last',
  totalRegistrations: 1,
  successfulRegistrations: 1,
  failedRegistrations: [],
  errors: [],
};

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

  it('should_reportProgressPerBatch_when_moreThanTenCandidates', async () => {
    api.on('createBatchRegistrations', () => fullSuccess);
    const requests = Array.from({ length: 12 }, (_, i) => request(i));
    const progress: Array<[number, number]> = [];
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCandidates>> | undefined;
    await act(async () => {
      // First batch of 10 settles, then the 100 ms pause before the second batch of 2.
      outcome = await result.current.importCandidates(requests, (current, total) =>
        progress.push([current, total])
      );
    });

    expect(
      api
        .calls('createBatchRegistrations')
        .map((c) => c.body?.participantEmail)
        .sort()
    ).toEqual(requests.map((r) => r.participantEmail).sort());
    expect(progress).toEqual([
      [10, 12],
      [12, 12],
    ]);
    expect(outcome).toEqual({ total: 12, success: 12, failed: 0, skipped: 0 });
    expect(result.current.isImporting).toBe(false);
  });

  it('should_continueAndRecordMessages_when_requestsFail', async () => {
    // One answer per participant: a 409 with the server's message, a network error, a success.
    api.on('createBatchRegistrations', ({ body }) => {
      if (body?.participantEmail === 'p1@example.com') {
        return HttpResponse.json(
          { status: 409, error: 'Conflict', message: 'Participant already registered' },
          { status: 409 }
        );
      }
      if (body?.participantEmail === 'p2@example.com') return HttpResponse.error();
      return fullSuccess;
    });
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCandidates>> | undefined;
    await act(async () => {
      outcome = await result.current.importCandidates([request(1), request(2), request(3)]);
    });

    expect(outcome).toEqual({ total: 3, success: 1, failed: 2, skipped: 0 });
    expect(result.current.candidates.map((c) => [c.importStatus, c.errorMessage])).toEqual([
      ['error', 'Participant already registered'],
      ['error', 'No response from server'],
      ['success', undefined],
    ]);
  });

  it('should_useStatusText_when_errorBodyHasNoMessage', async () => {
    api.on('createBatchRegistrations', () =>
      HttpResponse.json({ status: 400 }, { status: 400, statusText: 'Bad Request' })
    );
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates([request(1)]);
    });

    expect(result.current.candidates[0]).toMatchObject({
      importStatus: 'error',
      errorMessage: 'Bad Request',
    });
  });

  it('should_markSuccessWithCount_when_someRegistrationsFail', async () => {
    api.on('createBatchRegistrations', () => ({
      ...fullSuccess,
      totalRegistrations: 3,
      successfulRegistrations: 2,
      failedRegistrations: [{ eventCode: 'BATbern2', reason: 'Event not found' }],
    }));
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates([request(1)]);
    });

    expect(result.current.candidates[0]).toMatchObject({
      importStatus: 'success',
      errorMessage: '2/3 registrations succeeded',
    });
  });

  it('should_flagSyntheticEmail_when_emailIsOnBatbernDomain', async () => {
    api.on('createBatchRegistrations', () => fullSuccess);
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates([
        request(1, 'first.last@batbern.ch'),
        request(2, 'real@example.com'),
      ]);
    });

    expect(result.current.candidates.map((c) => c.isSyntheticEmail)).toEqual([true, false]);
  });

  it('should_invalidateUserAndRegistrationCaches_when_importCompletes', async () => {
    api.on('createBatchRegistrations', () => fullSuccess);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates([request(1)]);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['registrations'] });
  });
});
