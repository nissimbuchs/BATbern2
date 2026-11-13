/**
 * "What's new" selection, one test per row of the story's I/O matrix (plus the list itself).
 */
import { describe, it, expect } from 'vitest';
import { MAX_ENTRIES, WHATS_NEW_ENTRIES, selectEntries, type WhatsNewEntry } from '../catalogue';

const NO_CANDIDATES = { hasClaimCandidates: false };
const CANDIDATES = { hasClaimCandidates: true };

const keys = (entries: WhatsNewEntry[]) => entries.map((e) => e.key);

describe('the What’s new list', () => {
  it('should_holdTheOwnersOrder_when_read', () => {
    expect(keys([...WHATS_NEW_ENTRIES])).toEqual([
      'profileMerge',
      'sessionQna',
      'suggestTopics',
      'loginAndProfile',
      'companyLogo',
      'thankOrganizers',
      'eventHistory',
      'selfNomination',
    ]);
  });

  it('should_markQnaTopicsLogoAndTheConditionalMergeImportant', () => {
    expect(keys(WHATS_NEW_ENTRIES.filter((e) => e.important))).toEqual([
      'profileMerge',
      'sessionQna',
      'suggestTopics',
      'companyLogo',
    ]);
    expect(WHATS_NEW_ENTRIES.filter((e) => e.condition).map((e) => e.key)).toEqual([
      'profileMerge',
    ]);
  });

  it('should_haveValidReleaseDaysNotInTheFuture', () => {
    for (const entry of WHATS_NEW_ENTRIES) {
      expect(entry.releasedOn).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(Date.parse(`${entry.releasedOn}T00:00:00Z`)).toBeLessThanOrEqual(Date.now());
    }
  });
});

describe('selectEntries', () => {
  it('should_showTheThreeMostImportant_when_neverSeen', () => {
    expect(keys(selectEntries(WHATS_NEW_ENTRIES, null, NO_CANDIDATES))).toEqual([
      'sessionQna',
      'suggestTopics',
      'companyLogo',
    ]);
    expect(keys(selectEntries(WHATS_NEW_ENTRIES, undefined, NO_CANDIDATES))).toHaveLength(3);
  });

  it('should_putTheMergeFirst_when_neverSeenAndItApplies', () => {
    expect(keys(selectEntries(WHATS_NEW_ENTRIES, null, CANDIDATES))).toEqual([
      'profileMerge',
      'sessionQna',
      'suggestTopics',
    ]);
  });

  it('should_showOnlyTheNewerEntry_when_seenBefore', () => {
    // Seen after everything but the logo request (2026-11-07) and the merge (2026-11-04).
    expect(keys(selectEntries(WHATS_NEW_ENTRIES, '2026-11-05T10:00:00Z', NO_CANDIDATES))).toEqual([
      'companyLogo',
    ]);
  });

  it('should_showNothing_when_upToDateAndNoCandidate', () => {
    expect(selectEntries(WHATS_NEW_ENTRIES, '2026-11-13T10:00:00Z', NO_CANDIDATES)).toEqual([]);
  });

  it('should_putTheMergeFirstAndCapAtThree_when_itAppliesAndIsNewer', () => {
    const entries: WhatsNewEntry[] = [
      { key: 'a', releasedOn: '2026-12-01', important: false, link: '/' },
      { key: 'b', releasedOn: '2026-12-01', important: false, link: '/' },
      { key: 'c', releasedOn: '2026-12-01', important: true, link: '/' },
      {
        key: 'merge',
        releasedOn: '2026-12-01',
        important: true,
        link: '/x',
        condition: 'hasClaimCandidates',
      },
    ];
    const shown = selectEntries(entries, '2026-11-30T00:00:00Z', CANDIDATES);
    expect(shown).toHaveLength(MAX_ENTRIES);
    expect(keys(shown)).toEqual(['merge', 'c', 'a']);
  });

  it('should_neverShowTheMerge_when_noCandidate', () => {
    expect(keys(selectEntries(WHATS_NEW_ENTRIES, '2026-11-01T00:00:00Z', NO_CANDIDATES))).toEqual([
      'companyLogo',
    ]);
    expect(keys(selectEntries(WHATS_NEW_ENTRIES, '2026-11-01T00:00:00Z', CANDIDATES))).toEqual([
      'profileMerge',
      'companyLogo',
    ]);
  });

  it('should_notShowTheMerge_when_seenAfterItsRelease', () => {
    expect(selectEntries(WHATS_NEW_ENTRIES, '2026-11-10T00:00:00Z', CANDIDATES)).toEqual([]);
  });

  it('should_showAnEntryReleasedTheDayAfter_when_seenTheDayBefore', () => {
    const entries: WhatsNewEntry[] = [
      { key: 'x', releasedOn: '2026-12-02', important: false, link: '/' },
    ];
    expect(keys(selectEntries(entries, '2026-12-01T23:59:00Z', NO_CANDIDATES))).toEqual(['x']);
    expect(selectEntries(entries, '2026-12-02T08:00:00Z', NO_CANDIDATES)).toEqual([]);
  });

  it('should_treatAnUnparsableTimeAsNeverSeen', () => {
    expect(keys(selectEntries(WHATS_NEW_ENTRIES, 'garbage', NO_CANDIDATES))).toEqual([
      'sessionQna',
      'suggestTopics',
      'companyLogo',
    ]);
  });
});
