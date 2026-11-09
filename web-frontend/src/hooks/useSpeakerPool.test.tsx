/**
 * useSpeakerPool Hook Tests (Story 5.2)
 *
 * The hooks run with the real speakerPoolService and apiClient against the MSW harness (Epic 17
 * test fidelity): every request is checked against event-speakers-api, and each test answers it
 * with a typed override or reads what went over the wire. A pending answer is a handler that waits
 * for a promise the test settles itself, so no answer arrives after the test has ended.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { HttpResponse } from 'msw';
import { isAxiosError } from 'axios';
import { api } from '@/test/msw/api';
import { useSpeakerPool, useAddSpeakerToPool, speakerPoolKeys } from './useSpeakerPool';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

const EVENT_ID = '7c9e6679-7425-40de-944b-e07fc1f90ae7';

const entry = (overrides: Partial<SpeakerPoolEntry>): SpeakerPoolEntry => ({
  id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  eventId: EVENT_ID,
  speakerName: 'Hông Ân Nguyễn',
  status: 'IDENTIFIED',
  createdAt: '2025-12-13T10:00:00Z',
  updatedAt: '2025-12-13T10:00:00Z',
  ...overrides,
});

const pool: SpeakerPoolEntry[] = [
  entry({
    company: 'TechCorp Solutions AG',
    expertise: 'Cloud Architecture, DevOps',
    assignedOrganizerId: 'john.doe',
    notes: 'Excellent speaker',
  }),
  entry({
    id: '9b2d6c1e-1f4a-4f8e-8a3c-2d5e6f7a8b9c',
    speakerName: 'Robert O’Neill',
    company: 'Universität Bern',
    status: 'CONTACTED',
    createdAt: '2025-12-13T11:00:00Z',
    updatedAt: '2025-12-13T12:00:00Z',
  }),
];

const problem = (status: number, message: string) =>
  HttpResponse.json(
    { timestamp: '2026-11-09T08:00:00Z', status, error: 'Error', message },
    { status }
  );

const statusOf = (error: unknown) => (isAxiosError(error) ? error.response?.status : undefined);

describe('useSpeakerPool hooks', () => {
  let queryClient: QueryClient;

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
        mutations: { retry: false },
      },
    });
  });

  afterEach(() => {
    queryClient.clear();
  });

  describe('speakerPoolKeys', () => {
    it('should generate correct query keys for all operations', () => {
      expect(speakerPoolKeys.all).toEqual(['speakerPool']);
      expect(speakerPoolKeys.lists()).toEqual(['speakerPool', 'list']);
      expect(speakerPoolKeys.list('BATbern56')).toEqual(['speakerPool', 'list', 'BATbern56']);
    });
  });

  describe('useSpeakerPool', () => {
    it('should fetch the speaker pool of the event', async () => {
      api.on('getSpeakerPool', () => pool);

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(pool);
      expect(api.calls('getSpeakerPool')[0].params.eventCode).toBe('BATbern56');
    });

    it('should return an empty list when the pool is empty', async () => {
      api.on('getSpeakerPool', () => []);

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([]);
    });

    it('should be loading until the answer arrives', async () => {
      let answer: () => void = () => {};
      const answered = new Promise<void>((resolve) => {
        answer = resolve;
      });
      api.on('getSpeakerPool', async () => {
        await answered;
        return pool;
      });

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), { wrapper });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();

      answer();
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.isLoading).toBe(false);
      expect(result.current.data).toEqual(pool);
    });

    it('should not fetch while the event code is empty, and fetch once it is set', async () => {
      api.on('getSpeakerPool', () => pool);

      const { result, rerender } = renderHook(({ code }) => useSpeakerPool(code), {
        wrapper,
        initialProps: { code: '' },
      });

      expect(result.current.isFetching).toBe(false);
      expect(api.calls('getSpeakerPool')).toHaveLength(0);

      rerender({ code: 'BATbern56' });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('getSpeakerPool')).toHaveLength(1);
    });

    it('should report 404 for an event that does not exist', async () => {
      api.on('getSpeakerPool', () => problem(404, 'Event not found: BATbern999'));

      const { result } = renderHook(() => useSpeakerPool('BATbern999'), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(statusOf(result.current.error)).toBe(404);
      expect(result.current.data).toBeUndefined();
    });

    it('should report a refused authorization', async () => {
      api.on('getSpeakerPool', () => problem(403, 'ORGANIZER role required'));

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(statusOf(result.current.error)).toBe(403);
    });

    it('should serve a second reader from the cache (2 minute stale time)', async () => {
      api.on('getSpeakerPool', () => pool);

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), { wrapper });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const { result: second } = renderHook(() => useSpeakerPool('BATbern56'), { wrapper });

      expect(second.current.data).toEqual(pool);
      expect(api.calls('getSpeakerPool')).toHaveLength(1);
    });

    it('should keep the pools of different events apart', async () => {
      const pool56 = [entry({ speakerName: 'Speaker 56' })];
      const pool57 = [entry({ speakerName: 'Speaker 57', status: 'CONTACTED' })];
      api.on('getSpeakerPool', ({ params }) =>
        params.eventCode === 'BATbern56' ? pool56 : pool57
      );

      const { result: first } = renderHook(() => useSpeakerPool('BATbern56'), { wrapper });
      await waitFor(() => expect(first.current.isSuccess).toBe(true));
      const { result: second } = renderHook(() => useSpeakerPool('BATbern57'), { wrapper });
      await waitFor(() => expect(second.current.isSuccess).toBe(true));

      expect(first.current.data).toEqual(pool56);
      expect(second.current.data).toEqual(pool57);
      expect(api.calls('getSpeakerPool').map((c) => c.params.eventCode)).toEqual([
        'BATbern56',
        'BATbern57',
      ]);
    });
  });

  describe('useAddSpeakerToPool', () => {
    const added = entry({
      speakerName: 'Dr. Michael Chen',
      company: 'AI Research Lab',
      expertise: 'Machine Learning, Neural Networks',
      assignedOrganizerId: 'john.doe',
      notes: 'Recommended by partner',
    });

    it('should add a speaker with all fields', async () => {
      api.on('addSpeakerToPool', () => added, { status: 201 });

      const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper });
      const request = {
        speakerName: 'Dr. Michael Chen',
        company: 'AI Research Lab',
        expertise: 'Machine Learning, Neural Networks',
        assignedOrganizerId: 'john.doe',
        notes: 'Recommended by partner',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      const [call] = api.calls('addSpeakerToPool');
      expect(call.params.eventCode).toBe('BATbern56');
      expect(call.body).toEqual(request);
      expect(result.current.data).toEqual(added);
    });

    it('should add a speaker with only the required name', async () => {
      api.on('addSpeakerToPool', ({ body }) => entry({ speakerName: body?.speakerName }), {
        status: 201,
      });

      const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern56', request: { speakerName: 'John Doe' } });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('addSpeakerToPool')[0].body).toEqual({ speakerName: 'John Doe' });
      expect(result.current.data?.speakerName).toBe('John Doe');
      expect(result.current.data?.company).toBeUndefined();
    });

    it('should refetch the event pool after adding a speaker', async () => {
      api.on('getSpeakerPool', () => pool);
      api.on('addSpeakerToPool', () => added, { status: 201 });

      const { result } = renderHook(
        () => ({ list: useSpeakerPool('BATbern56'), add: useAddSpeakerToPool() }),
        { wrapper }
      );
      await waitFor(() => expect(result.current.list.isSuccess).toBe(true));
      expect(api.calls('getSpeakerPool')).toHaveLength(1);

      result.current.add.mutate({
        eventCode: 'BATbern56',
        request: { speakerName: 'New Speaker' },
      });

      await waitFor(() => expect(result.current.add.isSuccess).toBe(true));
      await waitFor(() => expect(api.calls('getSpeakerPool')).toHaveLength(2));
    });

    it('should also invalidate the status summary of the event', async () => {
      api.on('addSpeakerToPool', () => added, { status: 201 });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern56', request: { speakerName: 'New Speaker' } });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: ['speakerStatusSummary', 'BATbern56'],
      });
    });

    it.each([
      [400, 'Validation failed: speakerName is required'],
      [404, 'Event not found: BATbern999'],
      [403, 'ORGANIZER role required'],
    ])('should report a %i answer', async (status, message) => {
      api.on('addSpeakerToPool', () => problem(status, message));

      const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern56', request: { speakerName: 'Jane Smith' } });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(statusOf(result.current.error)).toBe(status);
    });

    it('should report a network failure', async () => {
      api.on('addSpeakerToPool', () => HttpResponse.error());

      const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern56', request: { speakerName: 'Jane Smith' } });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(isAxiosError(result.current.error)).toBe(true);
      expect(statusOf(result.current.error)).toBeUndefined();
    });

    it('should send both of two concurrent additions', async () => {
      api.on('addSpeakerToPool', ({ body }) => entry({ speakerName: body?.speakerName }), {
        status: 201,
      });

      const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern56', request: { speakerName: 'Speaker 1' } });
      result.current.mutate({ eventCode: 'BATbern56', request: { speakerName: 'Speaker 2' } });

      await waitFor(() => expect(api.calls('addSpeakerToPool')).toHaveLength(2));
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('addSpeakerToPool').map((c) => c.body?.speakerName)).toEqual([
        'Speaker 1',
        'Speaker 2',
      ]);
    });
  });
});
