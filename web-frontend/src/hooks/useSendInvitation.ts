/**
 * Send Invitation React Query Hook - Story 6.3
 *
 * Custom hook for organizers to send speaker invitations.
 * Features:
 * - React Query mutation for sending invitations
 * - Automatic cache invalidation on success
 * - Error handling for validation failures
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/api/apiClient';
import { speakerPoolKeys } from './useSpeakerPool';
import type { SendInvitationRequest, InvitationResponse } from '@/types/speakerInvitation.types';

/**
 * Query key factory for invitations
 */
export const invitationKeys = {
  all: ['invitations'] as const,
  byEvent: (eventCode: string) => [...invitationKeys.all, eventCode] as const,
  bySpeaker: (username: string) => [...invitationKeys.all, 'speaker', username] as const,
};

/**
 * Send a speaker invitation
 */
async function sendInvitation(
  eventCode: string,
  request: SendInvitationRequest
): Promise<InvitationResponse> {
  const response = await apiClient.post<InvitationResponse>(
    `/events/${eventCode}/invitations`,
    request
  );
  return response.data;
}

/**
 * Hook to send a speaker invitation (ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const sendMutation = useSendInvitation();
 * sendMutation.mutate({
 *   eventCode: 'BATbern2026',
 *   request: {
 *     username: 'john.doe',
 *     personalMessage: 'We would love to have you speak!',
 *     expirationDays: 14
 *   }
 * });
 */
export function useSendInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ eventCode, request }: { eventCode: string; request: SendInvitationRequest }) =>
      sendInvitation(eventCode, request),
    onSuccess: (_data, variables) => {
      // Invalidate speaker pool to update status
      queryClient.invalidateQueries({
        queryKey: speakerPoolKeys.list(variables.eventCode),
      });

      // Invalidate invitations list for this event
      queryClient.invalidateQueries({
        queryKey: invitationKeys.byEvent(variables.eventCode),
      });

      // Invalidate speaker status summary
      queryClient.invalidateQueries({
        queryKey: ['speakerStatusSummary', variables.eventCode],
      });
    },
  });
}

export default useSendInvitation;
