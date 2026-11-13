/**
 * "What's new" for the logged-in user: the entries to show, whether there is anything new (the
 * dot on the user menu) and `dismiss()`, which stores "seen now" on the user's settings
 * (`PATCH /users/me/settings`, `whatsNewSeenAt`), so every browser of the account agrees.
 *
 * Reads the settings through the shared profile query (`['userProfile']`) and the claim
 * candidates through the query the dashboard offer uses. Nothing is fetched when logged out.
 * Settings that cannot be read show nothing; candidates that cannot be read only drop the
 * conditional merge entry.
 */
import { useMemo } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/hooks/useAuth';
import { useUserProfile } from '@/hooks/useUserProfile';
import { useProfileClaimCandidates } from '@/hooks/useProfileClaimCandidates';
import { updateUserSettings, type CurrentUserResponse } from '@/services/api/userApi';
import { WHATS_NEW_ENTRIES, selectEntries, type WhatsNewEntry } from './catalogue';

export interface WhatsNew {
  entries: WhatsNewEntry[];
  hasNew: boolean;
  dismiss: () => void;
  dismissing: boolean;
}

export const USER_PROFILE_KEY = ['userProfile'] as const;

export function useWhatsNew(): WhatsNew {
  const { isAuthenticated } = useAuth();
  const queryClient = useQueryClient();
  const profile = useUserProfile({ enabled: isAuthenticated });
  const candidates = useProfileClaimCandidates({ enabled: isAuthenticated });

  const settings = isAuthenticated ? profile.data?.settings : undefined;
  const hasClaimCandidates = (candidates.data?.length ?? 0) > 0;
  const seenAt = settings?.whatsNewSeenAt;

  const entries = useMemo(
    () => (settings ? selectEntries(WHATS_NEW_ENTRIES, seenAt, { hasClaimCandidates }) : []),
    [settings, seenAt, hasClaimCandidates]
  );

  const mutation = useMutation({
    mutationFn: () => updateUserSettings({ whatsNewSeenAt: new Date().toISOString() }),
    onSuccess: (updated) => {
      // Hide at once, then re-read the profile so every reader agrees with the server.
      queryClient.setQueryData<CurrentUserResponse>(USER_PROFILE_KEY, (old) =>
        old ? { ...old, settings: updated } : old
      );
      void queryClient.invalidateQueries({ queryKey: USER_PROFILE_KEY });
    },
    onError: (error) => {
      // The card stays; the user can close it again. Not a page error.
      console.warn('[whatsNew] could not store the seen time', error);
    },
  });

  return {
    entries,
    hasNew: entries.length > 0,
    dismiss: () => mutation.mutate(),
    dismissing: mutation.isPending,
  };
}
