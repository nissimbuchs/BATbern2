/**
 * "What's new" selection, one test per row of the story's I/O matrix, on fixture entries: the
 * live list grows with every feature, so it is checked only for what must always hold.
 */
import { describe, it, expect } from 'vitest';
import i18n from '@/i18n/config';
import { MAX_ENTRIES, WHATS_NEW_ENTRIES, selectEntries, type WhatsNewEntry } from '../catalogue';

const NO_CANDIDATES = { hasClaimCandidates: false };
const CANDIDATES = { hasClaimCandidates: true };
const LOCALES = ['de', 'en', 'fr', 'it', 'rm', 'es', 'fi', 'nl', 'ja', 'gsw-BE'];

const keys = (entries: WhatsNewEntry[]) => entries.map((e) => e.key);

describe('the live What’s new list', () => {
  it('should_haveUniqueKeys', () => {
    const all = keys([...WHATS_NEW_ENTRIES]);
    expect(new Set(all).size).toBe(all.length);
  });

  it('should_haveValidReleaseDaysNotInTheFuture', () => {
    for (const entry of WHATS_NEW_ENTRIES) {
      expect(entry.releasedOn).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      const released = Date.parse(`${entry.releasedOn}T00:00:00Z`);
      expect(Number.isNaN(released)).toBe(false);
      expect(released).toBeLessThanOrEqual(Date.now());
    }
  });

  it.each(LOCALES)('should_haveATitleAndATextForEveryEntry_in_%s', (locale) => {
    const bundle = i18n.getResourceBundle(locale, 'events') as {
      whatsNew?: {
        title?: string;
        close?: string;
        entries?: Record<string, Record<string, string>>;
      };
    };
    expect(bundle?.whatsNew?.title).toBeTruthy();
    expect(bundle?.whatsNew?.close).toBeTruthy();
    for (const { key } of WHATS_NEW_ENTRIES) {
      expect(bundle.whatsNew?.entries?.[key]?.title, `${locale} ${key}.title`).toBeTruthy();
      expect(bundle.whatsNew?.entries?.[key]?.text, `${locale} ${key}.text`).toBeTruthy();
    }
  });

  it('should_keepTheOwnersFirstSevenInTheirOrder', () => {
    const owner = [
      'sessionQna',
      'suggestTopics',
      'loginAndProfile',
      'companyLogo',
      'thankOrganizers',
      'eventHistory',
      'selfNomination',
    ];
    const all = keys([...WHATS_NEW_ENTRIES]);
    const positions = owner.map((key) => all.indexOf(key));
    expect(positions.every((p) => p >= 0)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });
});

/** A list shaped like the live one: a conditional entry, important and other entries. */
const ENTRIES: WhatsNewEntry[] = [
  {
    key: 'merge',
    releasedOn: '2026-03-04',
    important: true,
    link: '/m',
    condition: 'hasClaimCandidates',
  },
  { key: 'qna', releasedOn: '2026-01-10', important: true, link: '/' },
  { key: 'topics', releasedOn: '2026-01-10', important: true, link: '/t' },
  { key: 'login', releasedOn: '2026-01-05', important: false, link: '/p' },
  { key: 'logo', releasedOn: '2026-03-07', important: true, link: '/p' },
  { key: 'thanks', releasedOn: '2026-01-10', important: false, link: '/' },
];

describe('selectEntries', () => {
  it('should_showTheThreeMostImportant_when_neverSeen', () => {
    expect(keys(selectEntries(ENTRIES, null, NO_CANDIDATES))).toEqual(['qna', 'topics', 'logo']);
    expect(keys(selectEntries(ENTRIES, undefined, NO_CANDIDATES))).toHaveLength(MAX_ENTRIES);
  });

  it('should_putTheMergeFirst_when_neverSeenAndItApplies', () => {
    expect(keys(selectEntries(ENTRIES, null, CANDIDATES))).toEqual(['merge', 'qna', 'topics']);
  });

  it('should_showOnlyTheNewerEntry_when_seenBefore', () => {
    expect(keys(selectEntries(ENTRIES, '2026-03-05T10:00:00Z', NO_CANDIDATES))).toEqual(['logo']);
  });

  it('should_showNothing_when_upToDateAndNoCandidate', () => {
    expect(selectEntries(ENTRIES, '2026-03-08T00:00:00Z', NO_CANDIDATES)).toEqual([]);
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
    expect(keys(selectEntries(ENTRIES, '2026-03-01T00:00:00Z', NO_CANDIDATES))).toEqual(['logo']);
    expect(keys(selectEntries(ENTRIES, '2026-03-01T00:00:00Z', CANDIDATES))).toEqual([
      'merge',
      'logo',
    ]);
  });

  it('should_notShowTheMerge_when_seenAfterItsRelease', () => {
    expect(selectEntries(ENTRIES, '2026-03-10T00:00:00Z', CANDIDATES)).toEqual([]);
  });

  it('should_showAnEntryReleasedTheDayAfter_when_seenTheDayBefore', () => {
    const entries: WhatsNewEntry[] = [
      { key: 'x', releasedOn: '2026-12-02', important: false, link: '/' },
    ];
    expect(keys(selectEntries(entries, '2026-12-01T23:59:00Z', NO_CANDIDATES))).toEqual(['x']);
    expect(selectEntries(entries, '2026-12-02T08:00:00Z', NO_CANDIDATES)).toEqual([]);
  });

  it('should_treatAnUnparsableTimeAsNeverSeen', () => {
    expect(keys(selectEntries(ENTRIES, 'garbage', NO_CANDIDATES))).toEqual([
      'qna',
      'topics',
      'logo',
    ]);
  });
});
