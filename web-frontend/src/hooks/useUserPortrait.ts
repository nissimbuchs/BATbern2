/**
 * Public-user lookup hooks.
 *
 * Lazy-loads the public projection of a user from the CUMS public endpoint:
 *   GET /api/v1/public/users/{username}
 *
 * Skip-Auth: public endpoint — does NOT attach the Cognito token. A stale ID
 * token would otherwise produce a 401 and trigger the apiClient interceptor's
 * `/login` redirect (mirrors publicOrganizerService).
 *
 * 24-hour stale time: public-user projections change rarely; avoid repeated refetches.
 *
 * Story 11.C.1 originally introduced this for portrait lookup (`useUserPortrait`).
 * Epic 11 bug fix 2026-05-19 generalised to `usePublicUser` so the kanban card +
 * drawer header can resolve the linked User (firstName/lastName/portrait) once
 * `speaker_pool.username` is populated by the CONTACTED→READY promote flow.
 */

import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import apiClient from '@/services/api/apiClient';

export interface PublicUserResponse {
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  profilePictureUrl?: string | null;
}

async function fetchPublicUser(username: string): Promise<PublicUserResponse> {
  const response = await apiClient.get<PublicUserResponse>(`/public/users/${username}`, {
    headers: { 'Skip-Auth': 'true' },
  });
  return response.data;
}

/**
 * Fetch the full public projection (firstName, lastName, profilePictureUrl) for a
 * user by username. Returns React Query state so callers can show a loading
 * placeholder while the user resolves.
 *
 * @param username  Username (the meaningful ID on `speaker_pool.username` /
 *                  `session_users.username`)
 * @param enabled   Set to false until the component is in/near the viewport
 */
export function usePublicUser(username: string | undefined, enabled: boolean = true) {
  return useQuery({
    queryKey: ['public-user', username],
    queryFn: () => fetchPublicUser(username!),
    enabled: !!username && enabled,
    staleTime: 24 * 60 * 60 * 1000, // 24 hours — public projections change rarely
    gcTime: 30 * 60 * 1000,
    // Retry transient 5xx (e.g. Fargate-spot replacement, ~5 min windows) but not 404
    // (legitimate persistent miss — non-existent users). Without this the 24h
    // staleTime would cache transient failures for the whole browser session.
    retry: (failureCount, error) => {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      return failureCount < 2;
    },
  });
}

/**
 * Backward-compat thin wrapper: returns only the profile picture URL. Prefer
 * `usePublicUser` for new code that also needs firstName/lastName.
 *
 * @param username  Username (from session_users)
 * @param enabled   Set to false until the component is in/near the viewport
 */
export function useUserPortrait(username: string | undefined, enabled: boolean = true) {
  const query = usePublicUser(username, enabled);
  // Re-projection: callers (e.g. SpeakerDisplay) destructure `{ data }`. Returning a
  // narrowed object instead of `{ ...query, data: ... }` avoids cast issues with React
  // Query's discriminated-union result types.
  return {
    data: query.data?.profilePictureUrl ?? null,
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
  };
}
