/**
 * Session Speaker React Query Hooks
 *
 * Custom hooks for assigning and removing speakers from sessions.
 * Speakers are stored in the session_speaker table, entirely separate from the speaker pool.
 *
 * On success, both mutations invalidate ['event', eventCode] so session.speakers[]
 * is refreshed in the parent session list.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { sessionApiClient, type AssignSpeakerRequest } from '@/services/api/sessionApiClient';

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
    },
  });
}
