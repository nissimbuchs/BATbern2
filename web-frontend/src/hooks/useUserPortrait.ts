/**
 * useUserPortrait Hook
 *
 * Lazy-loads a user's profile picture URL from the public users API.
 * Called only when the SpeakerDisplay component enters the viewport, and only
 * when the server has not already provided a profilePictureUrl (i.e. archive list).
 *
 * Uses GET /api/v1/public/users/{username} — public endpoint, no auth required
 * (Story 11.C.1: replaces the deleted /api/v1/speakers/{username} surface).
 * 24-hour stale time: portraits change rarely; avoid repeated refetches.
 */

import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import apiClient from '@/services/api/apiClient';

interface PublicUserResponse {
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  profilePictureUrl?: string | null;
}

async function fetchUserPortrait(username: string): Promise<string | null> {
  // Skip-Auth: public endpoint — do NOT attach the Cognito token. A stale ID token
  // would otherwise produce a 401 and trigger the apiClient interceptor's /login redirect
  // (mirrors publicOrganizerService).
  const response = await apiClient.get<PublicUserResponse>(`/public/users/${username}`, {
    headers: { 'Skip-Auth': 'true' },
  });
  return response.data.profilePictureUrl ?? null;
}

/**
 * @param username  Username (from session_users)
 * @param enabled   Set to false until the component is in/near the viewport
 */
export function useUserPortrait(username: string | undefined, enabled: boolean = true) {
  return useQuery({
    queryKey: ['user-portrait', username],
    queryFn: () => fetchUserPortrait(username!),
    enabled: !!username && enabled,
    staleTime: 24 * 60 * 60 * 1000, // 24 hours — portraits change rarely
    gcTime: 30 * 60 * 1000,
    // Retry transient 5xx (e.g. Fargate-spot replacement, ~5 min windows) but not 404
    // (legitimate persistent miss — non-speakers / unknown usernames). Without this the
    // 24h staleTime would cache transient failures for the whole browser session.
    retry: (failureCount, error) => {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      return failureCount < 2;
    },
  });
}
