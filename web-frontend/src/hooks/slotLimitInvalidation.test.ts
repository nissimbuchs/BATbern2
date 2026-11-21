/**
 * Every write that changes an event's slot limits refreshes what shows them (#824 item 1).
 *
 * The Speakers tab reads the minimum and maximum from the speaker status summary
 * (`['speakerStatusSummary', eventCode]`), the cockpit from the agenda config
 * (`['agenda-config', eventCode]`). The limits come from the per-event agenda override, else from
 * the event-type template, so three writes change them: saving the override, editing the template,
 * and switching the event to another type. The hooks run their real services against the MSW
 * harness; only the QueryClient is observed.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useUpdateAgendaConfig } from './useAgendaConfig/useAgendaConfig';
import { useUpdateEventType } from './useEventTypes';
import { useUpdateEvent } from './useEvents';

const EVENT = 'BATbern142';

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

/** The query keys a mutation marked stale. */
const invalidatedKeys = (spy: { mock: { calls: unknown[][] } }): unknown[][] =>
  spy.mock.calls.map((call) => (call[0] as { queryKey: unknown[] }).queryKey);

let qc: QueryClient;

beforeEach(() => {
  qc = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
});

describe('slot-limit writes refresh the speaker status summary', () => {
  it('saving the per-event agenda override', async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUpdateAgendaConfig(EVENT), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        minSlots: 2,
        maxSlots: 4,
        slotDuration: 45,
        theoreticalSlotsAM: false,
        breakSlots: 1,
        lunchSlots: 0,
        defaultCapacity: 180,
        moderationStartDuration: 5,
        moderationEndDuration: 5,
        breakDuration: 20,
        lunchDuration: 60,
        aperitifSlots: 1,
        aperitifDuration: 90,
        aperitifPosition: 'end',
        typicalStartTime: '16:00',
        typicalEndTime: '21:00',
      });
    });

    expect(invalidatedKeys(spy)).toContainEqual(['speakerStatusSummary', EVENT]);
  });

  it('editing the event-type template refreshes every event summary and agenda config', async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUpdateEventType(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        type: 'EVENING',
        config: {
          minSlots: 2,
          maxSlots: 4,
          slotDuration: 45,
          theoreticalSlotsAM: false,
          breakSlots: 1,
          lunchSlots: 0,
          defaultCapacity: 180,
          typicalStartTime: '16:00',
          typicalEndTime: '21:00',
        },
      });
    });

    expect(invalidatedKeys(spy)).toContainEqual(['speakerStatusSummary']);
    expect(invalidatedKeys(spy)).toContainEqual(['agenda-config']);
  });

  it("switching the event's type", async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUpdateEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ eventCode: EVENT, data: { eventType: 'FULL_DAY' } });
    });

    expect(invalidatedKeys(spy)).toContainEqual(['speakerStatusSummary', EVENT]);
    expect(invalidatedKeys(spy)).toContainEqual(['agenda-config', EVENT]);
  });

  it('a write that leaves the type alone does not refetch the summary (positive control)', async () => {
    const spy = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUpdateEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ eventCode: EVENT, data: { title: 'Renamed' } });
    });

    expect(invalidatedKeys(spy)).toContainEqual(['events']);
    expect(invalidatedKeys(spy)).not.toContainEqual(['speakerStatusSummary', EVENT]);
  });
});
