/**
 * Session Speaker React Query Hooks
 *
 * Custom hooks for assigning and removing speakers from sessions.
 * Speakers are stored in the session_users table (entity SessionUser, exposed in the
 * API as SessionSpeaker). speaker_pool owns the workflow state machine only; from the
 * CONTACTED → READY transition onward, session_users is the canonical record for
 * speaker meta (confirmation, role, presentation title).
 *
 * On success, both mutations invalidate two query keys:
 * - ['event', eventCode] — so session.speakers[] is refreshed in the parent session list
 * - ['speakerPool', 'list', eventCode] — so the organizer drawer / kanban refetches
 *   the pool list. The server-side response derives the speaker's identity from the
 *   session-overlay (Phase A); without this invalidation the FE keeps showing the
 *   pre-reassign identity even though the server has the new value.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { sessionApiClient, type AssignSpeakerRequest } from '@/services/api/sessionApiClient';
import { speakerPoolKeys } from '@/hooks/useSpeakerPool';

/**
 * Hook to assign a speaker to a session (ORGANIZER only)
 *
 * @example
 * const assign = useAssignSpeaker();
 * assign.mutate({ eventCode: 'BATbern142', sessionSlug: 'cloud-talk', request: { username: 'john.doe', speakerRole: 'PRIMARY_SPEAKER' } });
 */
export function useAssignSpeaker() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      eventCode,
      sessionSlug,
      request,
    }: {
      eventCode: string;
      sessionSlug: string;
      request: AssignSpeakerRequest;
    }) => sessionApiClient.assignSpeaker(eventCode, sessionSlug, request),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['event', variables.eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(variables.eventCode) });
    },
  });
}

/**
 * Hook to remove a speaker from a session (ORGANIZER only)
 *
 * @example
 * const remove = useRemoveSpeaker();
 * remove.mutate({ eventCode: 'BATbern142', sessionSlug: 'cloud-talk', username: 'john.doe' });
 */
export function useRemoveSpeaker() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      eventCode,
      sessionSlug,
      username,
    }: {
      eventCode: string;
      sessionSlug: string;
      username: string;
    }) => sessionApiClient.removeSpeaker(eventCode, sessionSlug, username),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['event', variables.eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(variables.eventCode) });
    },
  });
}
