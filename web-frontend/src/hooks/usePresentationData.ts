/**
 * usePresentationData Hook
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Loads all data for the moderator presentation page with 5 queries:
 *   1. Main event (topics, venue, sessions, speakers) — polled every 60 s
 *   2. Public organizers (Committee slide)
 *   3. Upcoming events (Upcoming Events slide)
 *   4. Presentation settings (About slide)
 *   5. Global teaser images (shown on all events)
 *
 * Sessions and speakers are embedded in query 1 — no separate sessions call needed.
 *
 * ACs: #1, #37, #38, #42
 */

import { useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { liveTimingService } from '@/services/liveTimingService';
import {
  getPresentationData,
  getPublicOrganizers,
  getUpcomingEvents,
  getPresentationSettings,
  getGlobalTeaserImages,
  type PresentationEventDetail,
  type PresentationSession,
  type PresentationSettings,
} from '@/services/presentationService';
import type { User } from '@/types/user.types';
import type { components } from '@/types/generated/events-core-api.types';

export interface PresentationData {
  event: PresentationEventDetail | null;
  sessions: PresentationSession[];
  organizers: User[];
  upcomingEvents: components['schemas']['Event'][];
  settings: PresentationSettings | null;
  globalTeaserImages: components['schemas']['TeaserImageItem'][];
}

export interface UsePresentationDataResult {
  data: PresentationData;
  isLoading: boolean;
  /** True only when the *initial* load of all sources has failed */
  isInitialLoadError: boolean;
  refetch: () => void;
}

const DEFAULT_ABOUT_TEXT =
  'BATbern ist eine unabhängige Plattform, die Berner Architekten und Ingenieure vernetzt.';

/** Adaptive live-timing poll cadence (ms). */
const LIVE_TIMING_POLL_ACTIVE = 5000;
const LIVE_TIMING_POLL_HIDDEN = 30000;

export function usePresentationData(eventCode: string): UsePresentationDataResult {
  const queryClient = useQueryClient();
  const etagRef = useRef<string | null>(null);
  const lastVersionRef = useRef<number | null>(null);

  // Story 15.1: REST polling replaces the STOMP STATE_UPDATE subscription. Poll the cheap
  // live-timing endpoint anonymously with If-None-Match (304 when unchanged); when the
  // monotonic version advances (an organizer ended/extended/delayed a session), invalidate
  // the event query so the presenter reflects new session times — exactly what the WS
  // cache-invalidation did, without per-task in-memory broker state.
  useEffect(() => {
    if (!eventCode) return;

    let cancelled = false;
    let timeoutId: ReturnType<typeof setTimeout> | null = null;

    const poll = async () => {
      if (cancelled) return;
      try {
        const result = await liveTimingService.getLiveTiming(eventCode, etagRef.current, true);
        if (cancelled) return;
        etagRef.current = result.etag;
        if (result.status === 200 && result.data) {
          const version = result.data.version;
          if (lastVersionRef.current !== null && version !== lastVersionRef.current) {
            void queryClient.invalidateQueries({ queryKey: ['presentation-event', eventCode] });
          }
          lastVersionRef.current = version;
        }
      } catch {
        // Transient — next tick retries; the 60s event poll is the safety net.
      } finally {
        if (!cancelled) {
          const interval =
            typeof document !== 'undefined' && document.hidden
              ? LIVE_TIMING_POLL_HIDDEN
              : LIVE_TIMING_POLL_ACTIVE;
          timeoutId = setTimeout(poll, interval);
        }
      }
    };

    void poll();

    return () => {
      cancelled = true;
      if (timeoutId) clearTimeout(timeoutId);
    };
  }, [eventCode, queryClient]);

  // Single event call — includes topics, venue, sessions and speakers.
  // Polled every 60 s as a safety net; the live-timing poll above drives prompt refresh.
  const eventQuery = useQuery({
    queryKey: ['presentation-event', eventCode],
    queryFn: () => getPresentationData(eventCode),
    refetchInterval: 60_000,
    refetchIntervalInBackground: true,
    retry: 1,
    staleTime: 30_000,
  });

  const organizersQuery = useQuery({
    queryKey: ['presentation-organizers'],
    queryFn: getPublicOrganizers,
    staleTime: 10 * 60 * 1000,
    retry: 1,
  });

  const upcomingQuery = useQuery({
    queryKey: ['presentation-upcoming-events', eventCode],
    queryFn: () => getUpcomingEvents(eventCode),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  const settingsQuery = useQuery({
    queryKey: ['presentation-settings'],
    queryFn: getPresentationSettings,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  const globalTeaserImagesQuery = useQuery({
    queryKey: ['presentation-global-teaser-images'],
    queryFn: getGlobalTeaserImages,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  const isLoading =
    eventQuery.isLoading ||
    organizersQuery.isLoading ||
    upcomingQuery.isLoading ||
    settingsQuery.isLoading ||
    globalTeaserImagesQuery.isLoading;

  // AC #42: surface error only when initial load (event) fails
  const isInitialLoadError =
    !isLoading && (eventQuery.isError || settingsQuery.isError) && eventQuery.data == null;

  // Sessions are embedded in the event response — guard against paginated shape
  const rawSessions = eventQuery.data?.sessions;
  const sessions: PresentationSession[] = Array.isArray(rawSessions) ? rawSessions : [];

  const refetch = () => {
    void eventQuery.refetch();
    void organizersQuery.refetch();
    void upcomingQuery.refetch();
    void settingsQuery.refetch();
    void globalTeaserImagesQuery.refetch();
  };

  return {
    data: {
      event: eventQuery.data ?? null,
      sessions,
      organizers: organizersQuery.data ?? [],
      upcomingEvents: upcomingQuery.data ?? [],
      settings: settingsQuery.data ?? {
        aboutText: DEFAULT_ABOUT_TEXT,
        partnerCount: 9,
      },
      globalTeaserImages: globalTeaserImagesQuery.data ?? [],
    },
    isLoading,
    isInitialLoadError,
    refetch,
  };
}
