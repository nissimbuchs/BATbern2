import { describe, test, expect } from 'vitest';
import { isPublishedPhase, canOfferSelfNomination } from '../eventPublication';

describe('isPublishedPhase', () => {
  test.each(['TOPIC', 'SPEAKERS', 'AGENDA'])('%s is published', (phase) => {
    expect(isPublishedPhase(phase)).toBe(true);
  });

  test.each([['NONE'], [''], [null], [undefined]])('%s is not published', (phase) => {
    expect(isPublishedPhase(phase as string | null | undefined)).toBe(false);
  });
});

describe('canOfferSelfNomination', () => {
  test('true when the server reports self-nomination open', () => {
    expect(canOfferSelfNomination({ selfNominationOpen: true })).toBe(true);
  });

  test('false when the server reports it closed', () => {
    expect(canOfferSelfNomination({ selfNominationOpen: false })).toBe(false);
  });

  test('false when the flag is absent (organizer endpoints, preview)', () => {
    expect(canOfferSelfNomination({})).toBe(false);
  });
});
