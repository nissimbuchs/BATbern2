/**
 * useParticipantBatchImport Hook Tests
 *
 * The hook imports in parallel batches of 10 with a 100 ms pause between batches. Replaces a suite
 * written for the earlier one-by-one importer, which had been entirely skipped since the switch.
 */

import { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
import { useParticipantBatchImport } from '../useParticipantBatchImport';
import * as eventApi from '../../../services/api/eventApi';
import type {
  BatchRegistrationRequest,
  BatchRegistrationResponse,
} from '../../../types/participantImport.types';

vi.mock('../../../services/api/eventApi');

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
    vi.mocked(eventApi.batchRegisterParticipant).mockReset();
  });

  afterEach(() => {
    queryClient.clear();
    vi.useRealTimers();
  });

  it('should_reportProgressPerBatch_when_moreThanTenCandidates', async () => {
    vi.useFakeTimers();
    vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(fullSuccess);
    const requests = Array.from({ length: 12 }, (_, i) => request(i));
    const progress: Array<[number, number]> = [];
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCandidates>> | undefined;
    await act(async () => {
      const pending = result.current.importCandidates(requests, (current, total) =>
        progress.push([current, total])
      );
      // First batch of 10 settles, then the 100 ms pause before the second batch of 2.
      await vi.advanceTimersByTimeAsync(100);
      outcome = await pending;
    });

    expect(eventApi.batchRegisterParticipant).toHaveBeenCalledTimes(12);
    expect(progress).toEqual([
      [10, 12],
      [12, 12],
    ]);
    expect(outcome).toEqual({ total: 12, success: 12, failed: 0, skipped: 0 });
    expect(result.current.isImporting).toBe(false);
  });

  it('should_continueAndRecordMessages_when_requestsFail', async () => {
    const serverError = new AxiosError('Request failed', 'ERR_BAD_RESPONSE', undefined, undefined, {
      status: 409,
      statusText: 'Conflict',
      data: { message: 'Participant already registered' },
      headers: {},
      config: { headers: new AxiosHeaders() },
    });
    const networkError = new AxiosError('Network Error', 'ERR_NETWORK', undefined, {});
    vi.mocked(eventApi.batchRegisterParticipant)
      .mockRejectedValueOnce(serverError)
      .mockRejectedValueOnce(networkError)
      .mockResolvedValueOnce(fullSuccess);
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

  it('should_markSuccessWithCount_when_someRegistrationsFail', async () => {
    vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue({
      ...fullSuccess,
      totalRegistrations: 3,
      successfulRegistrations: 2,
      failedRegistrations: ['BATbern2'],
    } as BatchRegistrationResponse);
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
    vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(fullSuccess);
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
    vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(fullSuccess);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(() => useParticipantBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCandidates([request(1)]);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['registrations'] });
  });
});
