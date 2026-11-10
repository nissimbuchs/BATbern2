/**
 * usePublishing Hook Tests (Story 5.7)
 *
 * The hook runs with the real publishingService and apiClient against the MSW harness (Epic 17
 * test fidelity): every request is checked against events-core-api, the status read answers with
 * its spec example unless a test overrides it, and the assertions read what went over the wire.
 * One file for the hook (the former .test.ts and .test.tsx mocked the same service twice).
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { usePublishing } from './usePublishing';
import type { PublishingStatusResponse } from '@/types/event.types';

const EVENT = 'BATbern142';

const status: PublishingStatusResponse = {
  currentPhase: 'topic',
  publishedPhases: ['topic'],
  topic: { isValid: true, errors: [] },
  speakers: { isValid: false, errors: ['No accepted speakers yet'] },
  sessions: {
    isValid: false,
    errors: [],
    assignedCount: 0,
    totalCount: 2,
    unassignedSessions: [{ sessionSlug: 'keynote', title: 'Keynote' }],
  },
};

describe('usePublishing', () => {
  let queryClient: QueryClient;

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
    });
    api.on('getPublishingStatus', () => status);
  });

  afterEach(() => {
    queryClient.clear();
  });

  const renderLoaded = async () => {
    const view = renderHook(() => usePublishing(EVENT), { wrapper });
    await waitFor(() => expect(view.result.current.isLoadingStatus).toBe(false));
    return view;
  };

  describe('status', () => {
    it('should fetch the publishing status of the event on mount', async () => {
      const { result } = await renderLoaded();

      expect(result.current.publishingStatus).toEqual(status);
      expect(api.calls('getPublishingStatus')[0].params.eventCode).toBe(EVENT);
    });

    it('should start idle, with no validation errors', async () => {
      const { result } = await renderLoaded();

      expect(result.current.validationErrors).toEqual([]);
      expect(result.current.publishError).toBeNull();
      expect(result.current.isPublishing).toBe(false);
      expect(result.current.isUnpublishing).toBe(false);
      expect(result.current.isScheduling).toBe(false);
    });
  });

  describe('publishPhase', () => {
    it('should publish the phase and refetch the status', async () => {
      api.on('publishPhase', ({ params }) => ({
        phase: params.phase,
        published: true,
        publishedAt: '2025-01-15T10:00:00Z',
        cdnInvalidated: true,
      }));
      const { result } = await renderLoaded();
      expect(api.calls('getPublishingStatus')).toHaveLength(1);

      act(() => result.current.publishPhase('speakers'));

      await waitFor(() => expect(api.calls('publishPhase')).toHaveLength(1));
      expect(api.calls('publishPhase')[0].params).toEqual({ eventCode: EVENT, phase: 'speakers' });
      await waitFor(() => expect(result.current.isPublishing).toBe(false));
      await waitFor(() => expect(api.calls('getPublishingStatus')).toHaveLength(2));
      expect(result.current.publishError).toBeNull();
    });

    it('should expose the validation errors of a 422 answer', async () => {
      api.on('publishPhase', () =>
        HttpResponse.json(
          {
            error: 'PUBLISH_VALIDATION_FAILED',
            message: 'Content not ready for this publishing phase',
            validationErrors: [
              { sessionSlug: 'keynote', field: 'sessions.timing', message: 'No timing assigned' },
            ],
          },
          { status: 422 }
        )
      );
      const { result } = await renderLoaded();

      act(() => result.current.publishPhase('agenda'));

      await waitFor(() => expect(result.current.publishError).toBeTruthy());
      expect(result.current.validationErrors).toEqual([
        { sessionSlug: 'keynote', field: 'sessions.timing', message: 'No timing assigned' },
      ]);
    });

    it('should expose no validation errors for an error that is not a 422', async () => {
      api.on('publishPhase', () =>
        HttpResponse.json(
          { timestamp: '2026-11-09T08:00:00Z', status: 403, error: 'Forbidden', message: 'No' },
          { status: 403 }
        )
      );
      const { result } = await renderLoaded();

      act(() => result.current.publishPhase('topic'));

      await waitFor(() => expect(result.current.publishError).toBeTruthy());
      expect(result.current.validationErrors).toEqual([]);
    });
  });

  describe('unpublishPhase', () => {
    it('should unpublish the phase and refetch the status', async () => {
      api.on('unpublishPhase', ({ params }) => ({
        phase: params.phase,
        published: false,
        unpublishedAt: '2025-01-15T11:00:00Z',
        cdnInvalidated: true,
      }));
      const { result } = await renderLoaded();

      act(() => result.current.unpublishPhase('speakers'));

      await waitFor(() => expect(api.calls('unpublishPhase')).toHaveLength(1));
      expect(api.calls('unpublishPhase')[0].params).toEqual({
        eventCode: EVENT,
        phase: 'speakers',
      });
      await waitFor(() => expect(result.current.isUnpublishing).toBe(false));
      await waitFor(() => expect(api.calls('getPublishingStatus')).toHaveLength(2));
    });
  });

  describe('scheduleAutoPublish', () => {
    it('should send the schedule of both triggers', async () => {
      const schedule = {
        phase2Enabled: true,
        phase2DaysBeforeEvent: 30,
        phase3Enabled: true,
        phase3DaysBeforeEvent: 14,
      };
      api.on('configureAutoPublish', () => ({
        scheduled: true,
        ...schedule,
        phase2TriggerDate: '2025-04-15T08:00:00Z',
        phase3TriggerDate: '2025-05-01T08:00:00Z',
      }));
      const { result } = await renderLoaded();

      act(() => result.current.scheduleAutoPublish(schedule));

      await waitFor(() => expect(api.calls('configureAutoPublish')).toHaveLength(1));
      const [call] = api.calls('configureAutoPublish');
      expect(call.params.eventCode).toBe(EVENT);
      expect(call.body).toEqual(schedule);
      await waitFor(() => expect(result.current.isScheduling).toBe(false));
    });
  });
});
