/**
 * usePresentationData Hook
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Loads all 4 data sources for the moderator presentation page and sets up
 * a 60-second poll for sessions to reflect Watch cascade updates.
 *
 * ACs: #1, #37, #38, #42
 */

import { useQuery } from '@tanstack/react-query';
import {
  getPresentationData,
  getPresentationSessions,
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
  const eventQuery = useQuery({
    queryKey: ['presentation-event', eventCode],
    queryFn: () => getPresentationData(eventCode),
    retry: 1,
    staleTime: 5 * 60 * 1000,
  });

  // 60-second poll — sessions only (Watch cascade updates)
  const sessionsQuery = useQuery({
    queryKey: ['presentation-sessions', eventCode],
    queryFn: () => getPresentationSessions(eventCode),
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

  // Use polled sessions if available, fall back to event.sessions
  // Guard: event.sessions may be a paginated object rather than a plain array
  const rawEventSessions = eventQuery.data?.sessions;
  const eventSessions: PresentationSession[] = Array.isArray(rawEventSessions)
    ? rawEventSessions
    : [];

  const sessions: PresentationSession[] = sessionsQuery.data ?? eventSessions;

  const refetch = () => {
    void eventQuery.refetch();
    void sessionsQuery.refetch();
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
