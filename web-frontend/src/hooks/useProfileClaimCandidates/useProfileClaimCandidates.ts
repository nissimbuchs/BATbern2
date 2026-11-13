/**
 * #1183 D: earlier profiles without a login under the caller's name. One query, shared by the
 * dashboard offer (`EarlierProfilesSection`) and the "What's new" card, which shows the merge
 * entry only to a user who has a candidate.
 */
import { useQuery } from '@tanstack/react-query';
import { listProfileClaimCandidates } from '@/services/api/userAccountApi';

export const PROFILE_CLAIM_CANDIDATES_KEY = ['users', 'me', 'profile-claims'] as const;

export function useProfileClaimCandidates({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: PROFILE_CLAIM_CANDIDATES_KEY,
    queryFn: listProfileClaimCandidates,
    staleTime: 5 * 60 * 1000,
    retry: false,
    enabled,
  });
}
