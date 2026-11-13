/**
 * "What's new" for the logged-in user: the entries to show, whether there is anything new (the
 * dot on the user menu) and `dismiss()`, which stores "seen now" on the user's settings
 * (`PATCH /users/me/settings`, `whatsNewSeenAt`), so every browser of the account agrees.
 *
 * Reads the settings with `GET /users/me/settings` and the claim candidates through the query the
 * dashboard offer uses, both without the 401 redirect to /login (the dot is in the header of
 * public pages too). Nothing is fetched when logged out. Settings that cannot be read show
 * nothing; candidates that cannot be read only drop the conditional merge entry.
 */
import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/hooks/useAuth';
import { useProfileClaimCandidates } from '@/hooks/useProfileClaimCandidates';
import { getUserSettings, updateUserSettings } from '@/services/api/userApi';
import {
  WHATS_NEW_ENTRIES,
  listAllEntries,
  selectEntries,
  type ListedEntry,
  type WhatsNewEntry,
} from './catalogue';

export interface WhatsNew {
  entries: WhatsNewEntry[];
  /** Everything that applies to the user, the new ones first and marked (the menu item). */
  all: ListedEntry[];
  hasNew: boolean;
  dismiss: () => void;
  dismissing: boolean;
}

/** The settings read of "What's new". */
export const WHATS_NEW_SETTINGS_KEY = ['users', 'me', 'settings', 'whatsNew'] as const;
/** The shared profile query (`useUserProfile`), which carries the settings too. */
const USER_PROFILE_KEY = ['userProfile'] as const;

export function useWhatsNew(): WhatsNew {
  const { isAuthenticated } = useAuth();
  const queryClient = useQueryClient();
  const settingsQuery = useQuery({
    queryKey: WHATS_NEW_SETTINGS_KEY,
    queryFn: () => getUserSettings({ skipAuthRedirect: true }),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
  const candidates = useProfileClaimCandidates({ enabled: isAuthenticated });

  const settings = isAuthenticated ? settingsQuery.data : undefined;
  const hasClaimCandidates = (candidates.data?.length ?? 0) > 0;
  const seenAt = settings?.whatsNewSeenAt;

  const entries = useMemo(
    () => (settings ? selectEntries(WHATS_NEW_ENTRIES, seenAt, { hasClaimCandidates }) : []),
    [settings, seenAt, hasClaimCandidates]
  );

  const all = useMemo(
    () => (settings ? listAllEntries(WHATS_NEW_ENTRIES, seenAt, { hasClaimCandidates }) : []),
    [settings, seenAt, hasClaimCandidates]
  );

  const mutation = useMutation({
    mutationFn: () => updateUserSettings({ whatsNewSeenAt: new Date().toISOString() }),
    onSuccess: (updated) => {
      // Hide at once; the profile query carries the settings too.
      queryClient.setQueryData(WHATS_NEW_SETTINGS_KEY, updated);
      void queryClient.invalidateQueries({ queryKey: USER_PROFILE_KEY });
    },
    onError: (error) => {
      // The card stays; the user can close it again. Not a page error.
      console.warn('[whatsNew] could not store the seen time', error);
    },
  });

  return {
    entries,
    all,
    hasNew: entries.length > 0,
    dismiss: () => mutation.mutate(),
    dismissing: mutation.isPending,
  };
}
