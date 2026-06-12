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
  test('true ONLY during the TOPIC phase (topic published, speakers not yet)', () => {
    expect(
      canOfferSelfNomination({ topic: { name: 'Architecture' }, currentPublishedPhase: 'TOPIC' })
    ).toBe(true);
  });

  test('false once the speaker lineup is published (SPEAKERS / AGENDA) — window closed', () => {
    expect(
      canOfferSelfNomination({ topic: { name: 'Architecture' }, currentPublishedPhase: 'SPEAKERS' })
    ).toBe(false);
    expect(
      canOfferSelfNomination({ topic: { name: 'Architecture' }, currentPublishedPhase: 'AGENDA' })
    ).toBe(false);
  });

  test('false when the event is unpublished (NONE) even with a topic', () => {
    expect(
      canOfferSelfNomination({ topic: { name: 'Architecture' }, currentPublishedPhase: 'NONE' })
    ).toBe(false);
  });

  test('false when no topic object is set', () => {
    expect(canOfferSelfNomination({ topic: null, currentPublishedPhase: 'TOPIC' })).toBe(false);
    expect(canOfferSelfNomination({ currentPublishedPhase: 'TOPIC' })).toBe(false);
  });
});
