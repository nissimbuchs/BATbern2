/**
 * "What's new" (story 2 of 2026-11-13): the features a logged-in user may not have found yet.
 *
 * The list is code, not data. Every new feature adds its entry here in its own PR, with the texts
 * `whatsNew.entries.{key}.title` and `.text` in `events.json` of all 10 locales. `releasedOn` is
 * the day the feature reached production (YYYY-MM-DD): an entry is "new" for a user when it was
 * released after the time they last closed the card (`whatsNewSeenAt` on their settings).
 *
 * The order of the list is the order on the card. `important` marks the entries a user sees when
 * they have never closed the card. A `condition` limits an entry to the users it applies to; such
 * an entry is ranked first.
 */

/** A condition an entry can require of the user. */
export type WhatsNewCondition = 'hasClaimCandidates';

export interface WhatsNewEntry {
  /** i18n key below `whatsNew.entries` in the `events` namespace. */
  key: string;
  /** Production release day, YYYY-MM-DD (start of the day, UTC). */
  releasedOn: string;
  /** Shown on a first view (nothing seen yet). */
  important: boolean;
  /** Where the feature is: an app path, optionally with a #fragment. */
  link: string;
  /** Shown only to users this applies to; ranked first. */
  condition?: WhatsNewCondition;
}

/** At most this many entries on the card. */
export const MAX_ENTRIES = 3;

/**
 * The list, in the owner's order (2026-11-13). Release days from the merge to develop, which
 * deploys to production: Epic 7 #776 (2026-06-12), SSO #737 / additional emails #745, logo request
 * #1247 (2026-11-07), profile merge #1204 (2026-11-04).
 */
export const WHATS_NEW_ENTRIES: readonly WhatsNewEntry[] = [
  {
    key: 'profileMerge',
    releasedOn: '2026-11-04',
    important: true,
    link: '/attendee/dashboard#earlier-profiles',
    condition: 'hasClaimCandidates',
  },
  { key: 'sessionQna', releasedOn: '2026-06-12', important: true, link: '/' },
  {
    key: 'suggestTopics',
    releasedOn: '2026-06-12',
    important: true,
    link: '/attendee/dashboard#suggest-topics',
  },
  { key: 'loginAndProfile', releasedOn: '2026-06-07', important: false, link: '/profile' },
  { key: 'companyLogo', releasedOn: '2026-11-07', important: true, link: '/profile' },
  { key: 'thankOrganizers', releasedOn: '2026-06-12', important: false, link: '/' },
  {
    key: 'eventHistory',
    releasedOn: '2026-06-12',
    important: false,
    link: '/attendee/dashboard#attendee-past',
  },
  { key: 'selfNomination', releasedOn: '2026-06-12', important: false, link: '/' },
];

/** What the user is known to have, for the conditions. */
export interface WhatsNewUserFacts {
  hasClaimCandidates: boolean;
}

function releasedAt(entry: WhatsNewEntry): number {
  return Date.parse(`${entry.releasedOn}T00:00:00Z`);
}

/**
 * The entries to show, at most {@link MAX_ENTRIES}:
 * - an entry whose condition the user does not meet never shows;
 * - `seenAt` null (never closed): the important entries;
 * - `seenAt` set: the entries released after it;
 * - ranked: conditional entries first, then important ones, then the list order.
 *
 * An unparsable `seenAt` counts as never closed.
 */
export function selectEntries(
  entries: readonly WhatsNewEntry[],
  seenAt: string | null | undefined,
  facts: WhatsNewUserFacts
): WhatsNewEntry[] {
  const seen = seenAt ? Date.parse(seenAt) : NaN;
  const firstView = Number.isNaN(seen);
  return entries
    .map((entry, index) => ({ entry, index }))
    .filter(({ entry }) => !entry.condition || facts[entry.condition])
    .filter(({ entry }) => (firstView ? entry.important : releasedAt(entry) > seen))
    .sort(
      (a, b) =>
        Number(!!b.entry.condition) - Number(!!a.entry.condition) ||
        Number(b.entry.important) - Number(a.entry.important) ||
        a.index - b.index
    )
    .slice(0, MAX_ENTRIES)
    .map(({ entry }) => entry);
}
