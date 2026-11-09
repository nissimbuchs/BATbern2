/**
 * useEventRegistrations Hook Tests
 *
 * The hook runs with the real eventRegistrationService and apiClient against the MSW harness
 * (17.0c2): the harness checks each request against event-registrations-api and answers with the
 * production-shaped fixture registrations (one per status, unicode names, namesakes), or with a
 * test's override. Assertions read the query that went over the wire and the transformed data.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { registrations, shapes } from '@/test/fixtures';
import { useEventRegistrations } from './useEventRegistrations';

const EVENT = 'BATbern61';

describe('useEventRegistrations', () => {
  let queryClient: QueryClient;

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
          staleTime: 0,
        },
      },
    });
  });

  afterEach(() => {
    queryClient.clear();
  });

  /** The query of the only listRegistrations call, once it was sent. */
  const sentQuery = async () => {
    await waitFor(() => expect(api.calls('listRegistrations')).toHaveLength(1));
    const [call] = api.calls('listRegistrations');
    expect(call.params.eventCode).toBe(EVENT);
    return call.query;
  };

  describe('Basic Fetching', () => {
    it('should fetch registrations for an event, in the frontend shape', async () => {
      const { result } = renderHook(() => useEventRegistrations({ eventCode: EVENT }), {
        wrapper,
      });

      expect(result.current.isLoading).toBe(true);
      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const all = registrations(EVENT);
      expect(result.current.data?.data).toHaveLength(all.length);
      expect(result.current.data?.pagination.totalItems).toBe(all.length);
      // The service maps EMS's attendee* fields; unicode names arrive unchanged.
      const vietnamese = result.current.data?.data.find((p) => p.status === 'REGISTERED');
      expect(vietnamese).toMatchObject({
        firstName: shapes.vietnamese.firstName,
        lastName: shapes.vietnamese.lastName,
        email: shapes.vietnamese.email,
      });
      // The waitlist position is the backend's (Epic 14 FR28), null for everyone else.
      const waiting = result.current.data?.data.find((p) => p.status === 'WAITLIST');
      expect(waiting?.waitlistPosition).toBe(1);
      expect(vietnamese?.waitlistPosition).toBeNull();
      // No options, no query.
      expect([...(await sentQuery()).keys()]).toEqual([]);
    });

    it('should send the status filter, repeatable, and get only those registrations', async () => {
      const { result } = renderHook(
        () =>
          useEventRegistrations({
            eventCode: EVENT,
            filters: { status: ['CONFIRMED', 'WAITLIST'] },
          }),
        { wrapper }
      );

      expect((await sentQuery()).getAll('status')).toEqual(['CONFIRMED', 'WAITLIST']);
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(new Set(result.current.data?.data.map((p) => p.status))).toEqual(
        new Set(['CONFIRMED', 'WAITLIST'])
      );
    });

    it('should send the pagination', async () => {
      const { result } = renderHook(
        () => useEventRegistrations({ eventCode: EVENT, pagination: { page: 2, limit: 1 } }),
        { wrapper }
      );

      const query = await sentQuery();
      expect(query.get('page')).toBe('2');
      expect(query.get('limit')).toBe('1');
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data?.pagination).toMatchObject({ page: 2, limit: 1, hasPrev: true });
      expect(result.current.data?.data).toHaveLength(1);
    });

    it('should send the search', async () => {
      renderHook(() => useEventRegistrations({ eventCode: EVENT, search: 'Müller' }), {
        wrapper,
      });

      expect((await sentQuery()).get('search')).toBe('Müller');
    });

    it('should send all options combined, every one declared by the spec', async () => {
      renderHook(
        () =>
          useEventRegistrations({
            eventCode: EVENT,
            filters: { status: ['CONFIRMED'], companyId: 'brunnimann' },
            pagination: { page: 1, limit: 25 },
            search: 'Müller',
          }),
        { wrapper }
      );

      const query = await sentQuery();
      expect(query.getAll('status')).toEqual(['CONFIRMED']);
      expect(query.get('companyId')).toBe('brunnimann');
      expect(query.get('page')).toBe('1');
      expect(query.get('limit')).toBe('25');
      expect(query.get('search')).toBe('Müller');
    });
  });

  describe('Enabled Flag', () => {
    it('should not fetch when enabled is false', async () => {
      const { result } = renderHook(
        () => useEventRegistrations({ eventCode: EVENT, enabled: false }),
        { wrapper }
      );

      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(result.current.isLoading).toBe(false);
      expect(result.current.isFetching).toBe(false);
      expect(api.calls('listRegistrations')).toHaveLength(0);
    });

    it('should fetch when enabled is true', async () => {
      renderHook(() => useEventRegistrations({ eventCode: EVENT, enabled: true }), { wrapper });

      await sentQuery();
    });
  });

  describe('Error Handling', () => {
    it('should handle API errors', async () => {
      api.on('listRegistrations', () =>
        HttpResponse.json(
          { error: 'INTERNAL_ERROR', message: 'Failed to fetch registrations' },
          { status: 500 }
        )
      );

      const { result } = renderHook(() => useEventRegistrations({ eventCode: EVENT }), {
        wrapper,
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error).toMatchObject({
        response: { status: 500, data: { message: 'Failed to fetch registrations' } },
      });
    });
  });

  describe('Refetch', () => {
    it('should refetch from the backend', async () => {
      const { result } = renderHook(() => useEventRegistrations({ eventCode: EVENT }), {
        wrapper,
      });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      await result.current.refetch();

      expect(api.calls('listRegistrations')).toHaveLength(2);
    });
  });

  describe('Query Key Generation', () => {
    it('should use correct query key with only eventCode', async () => {
      renderHook(() => useEventRegistrations({ eventCode: EVENT }), { wrapper });

      await waitFor(() => {
        const queries = queryClient.getQueryCache().findAll({
          queryKey: ['event-registrations', EVENT],
          exact: true,
        });
        expect(queries).toHaveLength(1);
      });
    });

    it('should include filters and pagination in query key', async () => {
      renderHook(
        () =>
          useEventRegistrations({
            eventCode: EVENT,
            filters: { status: ['CONFIRMED'] },
            pagination: { page: 2, limit: 25 },
          }),
        { wrapper }
      );

      await waitFor(() => {
        const queries = queryClient.getQueryCache().findAll({
          queryKey: [
            'event-registrations',
            EVENT,
            { filters: { status: ['CONFIRMED'] }, pagination: { page: 2, limit: 25 } },
          ],
          exact: true,
        });
        expect(queries).toHaveLength(1);
      });
    });
  });
});
