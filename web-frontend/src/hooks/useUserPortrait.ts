/**
 * useUserPortrait Hook
 *
 * Lazy-loads a speaker's profile picture URL from the public speakers API.
 * Called only when the SpeakerDisplay component enters the viewport, and only
 * when the server has not already provided a profilePictureUrl (i.e. archive list).
 *
 * Uses GET /api/v1/speakers/{username} — public endpoint, no auth required.
 * 24-hour stale time: portraits change rarely; avoid repeated refetches.
 */

import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/api/apiClient';

interface SpeakerPortraitResponse {
  profilePictureUrl?: string | null;
}

async function fetchSpeakerPortrait(username: string): Promise<string | null> {
  const response = await apiClient.get<SpeakerPortraitResponse>(`/speakers/${username}`);
  return response.data.profilePictureUrl ?? null;
}

/**
 * @param username  Speaker username (from session_users)
 * @param enabled   Set to false until the component is in/near the viewport
 */
export function useUserPortrait(username: string | undefined, enabled: boolean = true) {
  return useQuery({
    queryKey: ['speaker-portrait', username],
    queryFn: () => fetchSpeakerPortrait(username!),
    enabled: !!username && enabled,
    staleTime: 24 * 60 * 60 * 1000, // 24 hours — portraits change rarely
    gcTime: 30 * 60 * 1000,
    retry: false, // Don't retry on 404 (speaker may not have a portrait)
  });
}
