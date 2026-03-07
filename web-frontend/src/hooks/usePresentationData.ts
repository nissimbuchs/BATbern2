/**
 * usePresentationData Hook
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Loads all data for the moderator presentation page with 4 queries:
 *   1. Main event (topics, venue, sessions, speakers) — polled every 60 s
 *   2. Public organizers (Committee slide)
 *   3. Upcoming events (Upcoming Events slide)
 *   4. Presentation settings (About slide)
 *
 * Sessions and speakers are embedded in query 1 — no separate sessions call needed.
 *
 * ACs: #1, #37, #38, #42
 */

import { useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useConfig } from '@/contexts/useConfig';
import {
  getPresentationData,
  getPublicOrganizers,
  getUpcomingEvents,
  getPresentationSettings,
  type PresentationEventDetail,
  type PresentationSession,
  type PresentationSettings,
} from '@/services/presentationService';
import type { User } from '@/types/user.types';
import type { components } from '@/types/generated/events-api.types';

export interface PresentationData {
  event: PresentationEventDetail | null;
  sessions: PresentationSession[];
  organizers: User[];
  upcomingEvents: components['schemas']['Event'][];
  settings: PresentationSettings | null;
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

export function usePresentationData(eventCode: string): UsePresentationDataResult {
  const { apiBaseUrl } = useConfig();
  const queryClient = useQueryClient();
  const clientRef = useRef<Client | null>(null);

  // WebSocket sync — invalidates the event cache on any STATE_UPDATE so session
  // times (extend/delay) are reflected immediately without waiting for the 60s poll.
  // Connects anonymously: JwtStompInterceptor passes through frames with no auth header.
  useEffect(() => {
    if (!eventCode) return;

    const url = new URL(apiBaseUrl);
    const wsBase =
      url.hostname === 'localhost' || url.hostname === '127.0.0.1'
        ? `http://localhost:${parseInt(url.port || '8000', 10) + 2}`
        : `${url.protocol}//${url.host}`;

    let isMounted = true;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${wsBase}/ws`) as WebSocket,
      debug: () => {},
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/events/${eventCode}/state`, () => {
          if (!isMounted) return;
          void queryClient.invalidateQueries({ queryKey: ['presentation-event', eventCode] });
        });
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      isMounted = false;
      void client.deactivate();
      clientRef.current = null;
    };
  }, [eventCode, apiBaseUrl, queryClient]);

  // Single event call — includes topics, venue, sessions and speakers.
  // Polled every 60 s to pick up session updates (replaces separate sessions poll).
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
    queryKey: ['presentation-upcoming-events'],
    queryFn: getUpcomingEvents,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  const settingsQuery = useQuery({
    queryKey: ['presentation-settings'],
    queryFn: getPresentationSettings,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  const isLoading =
    eventQuery.isLoading ||
    organizersQuery.isLoading ||
    upcomingQuery.isLoading ||
    settingsQuery.isLoading;

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
    },
    isLoading,
    isInitialLoadError,
    refetch,
  };
}
