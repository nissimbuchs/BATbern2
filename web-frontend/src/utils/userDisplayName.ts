/**
 * How a user is shown to a human.
 *
 * Added 2026-09-09: `UserAutocomplete` used `option.id` (the username) as its display label, so
 * selecting "Matthias Stürmer" put `matthias.stuermer` in the field. Lives in its own module
 * because exporting a non-component from a component file breaks react-refresh.
 */
import type { UserSearchResponse } from '@/types/user.types';

type NamedUser = Pick<UserSearchResponse, 'id' | 'firstName' | 'lastName'>;

/**
 * The user's real name ("Matthias Stürmer"), falling back to the username when the projection
 * carries no name at all — never an empty string, since it is used as an input label.
 */
export const userDisplayName = (user: NamedUser): string => {
  const name = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
  return name || user.id;
};

/** Lower-cased full name, for comparing a stored display name against a user. Empty when unnamed. */
export const normalizedFullName = (user: NamedUser): string =>
  [user.firstName, user.lastName].filter(Boolean).join(' ').trim().toLowerCase();
