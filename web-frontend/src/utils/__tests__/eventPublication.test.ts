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
  test('true when topic object present and a published phase is active', () => {
    expect(
      canOfferSelfNomination({ topic: { name: 'Architecture' }, currentPublishedPhase: 'TOPIC' })
    ).toBe(true);
  });

  test('false when the event is unpublished (NONE) even with a topic', () => {
    expect(
      canOfferSelfNomination({ topic: { name: 'Architecture' }, currentPublishedPhase: 'NONE' })
    ).toBe(false);
  });

  test('false when no topic object is set', () => {
    expect(canOfferSelfNomination({ topic: null, currentPublishedPhase: 'SPEAKERS' })).toBe(false);
    expect(canOfferSelfNomination({ currentPublishedPhase: 'SPEAKERS' })).toBe(false);
  });
});
