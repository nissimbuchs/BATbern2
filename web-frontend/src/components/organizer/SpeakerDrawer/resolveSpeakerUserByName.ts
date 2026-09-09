/**
 * Best-effort resolution of a `speaker_pool.speaker_name` to an existing user, shared by
 * PromoteSpeakerSubView and ContentSubmissionSubView (bug fix 2026-09-09).
 *
 * Both call sites used to do:
 *
 *     let users = await searchUsers(speaker.speakerName, 20);
 *     if (users.length === 0 && name.includes(' ')) users = await searchUsers(firstName, 20);
 *     if (users.length > 0) setSelectedUser(users[0]);          // ← silently wrong
 *
 * For "Matthias Stürmer" the full-name search returned nothing (the backend had no full-name
 * matching, and the frontend double-encoded the umlaut), so the fallback searched "Matthias",
 * got an arbitrary unordered page of 32 namesakes, and pre-selected Matthias GERMANN. Pressing
 * Promote then granted SPEAKER and a PRIMARY_SPEAKER session row to the wrong person.
 *
 * The rule now: auto-select only an UNAMBIGUOUS match. Anything else selects nobody and reports
 * `ambiguous`, so the organizer picks deliberately.
 *
 * Search order is most-selective-first: full name, then surname, then first name. A surname is a
 * far better discriminator than a first name in this dataset (32 users match "Matthias").
 */
import { searchUsers } from '@/services/api/userManagementApi';
import { normalizedFullName } from '@/utils/userDisplayName';
import type { UserSearchResponse } from '@/types/user.types';

const SEARCH_LIMIT = 20;

export interface ResolvedSpeakerUser {
  /** The unambiguously resolved user, or null when nothing matched or the match was ambiguous. */
  user: UserSearchResponse | null;
  /** True when candidates were found but none could be singled out — prompt the organizer. */
  ambiguous: boolean;
}

/**
 * Pick the single unambiguous candidate, if there is one:
 *   - exactly one result wins outright;
 *   - several results still win if exactly one matches the searched name exactly.
 */
const pickUnambiguous = (
  users: UserSearchResponse[],
  speakerName: string
): UserSearchResponse | null => {
  if (users.length === 1) {
    return users[0];
  }
  if (users.length === 0) {
    return null;
  }
  const target = speakerName.trim().toLowerCase();
  const exact = users.filter((u) => normalizedFullName(u) === target);
  return exact.length === 1 ? exact[0] : null;
};

/** Split "Matthias Stürmer" into the query ladder: full name, surname, first name. */
const queryLadder = (speakerName: string): string[] => {
  const name = speakerName.trim();
  const tokens = name.split(/\s+/).filter(Boolean);
  if (tokens.length < 2) {
    return [name].filter(Boolean);
  }
  const surname = tokens[tokens.length - 1];
  const firstName = tokens[0];
  return [name, surname, firstName];
};

export const resolveSpeakerUserByName = async (
  speakerName: string,
  options: { requireRole?: string } = {}
): Promise<ResolvedSpeakerUser> => {
  const { requireRole } = options;
  let sawCandidates = false;

  for (const query of queryLadder(speakerName)) {
    const users = await searchUsers(query, SEARCH_LIMIT);
    // A role requirement narrows the candidate set but never turns an ambiguous set into a
    // pick by position — the same `users[0]` trap in a different guise.
    const eligible = requireRole ? users.filter((u) => u.roles?.includes(requireRole)) : users;
    if (eligible.length > 0) {
      sawCandidates = true;
    }
    const match = pickUnambiguous(eligible, speakerName);
    if (match) {
      return { user: match, ambiguous: false };
    }
  }

  return { user: null, ambiguous: sawCandidates };
};
