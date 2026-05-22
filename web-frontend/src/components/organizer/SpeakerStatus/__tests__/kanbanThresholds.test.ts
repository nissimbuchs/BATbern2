/**
 * Unit tests for `kanbanThresholds.ts` — Story 11.D.3 AC5 cases #1-20.
 *
 * Pure-function tests: no React, no rendering. `now` is injected at every call
 * so day arithmetic is deterministic without `vi.useFakeTimers()`.
 */
import { describe, it, expect } from 'vitest';
import {
  DEFAULT_KANBAN_THRESHOLDS,
  attentionMaxSeverity,
  classifyChipSeverity,
  countAttentionCards,
  makeAttentionPredicate,
  countInvitedSplit,
  severityToChipColor,
  getStatusChangedAt,
  type KanbanThresholdConfig,
} from '../kanbanThresholds';
import type { SpeakerPoolEntry, SpeakerWorkflowState } from '@/types/speakerPool.types';

// Fixed "now" — all tests anchor day arithmetic to this instant.
const NOW = new Date('2026-05-16T12:00:00Z');

function daysAgo(days: number): string {
  return new Date(NOW.getTime() - days * 86_400_000).toISOString();
}

function daysFromNow(days: number): string {
  return new Date(NOW.getTime() + days * 86_400_000).toISOString();
}

function makeSpeaker(
  status: SpeakerWorkflowState,
  overrides: Partial<SpeakerPoolEntry> = {}
): SpeakerPoolEntry {
  return {
    id: `speaker-${status.toLowerCase()}`,
    eventId: 'event-1',
    speakerName: `${status} Speaker`,
    status,
    createdAt: NOW.toISOString(),
    ...overrides,
  };
}

function classify(
  speaker: SpeakerPoolEntry,
  opts: { eventDate?: Date | null; thresholds?: KanbanThresholdConfig } = {}
) {
  const anchor = new Date(getStatusChangedAt(speaker) ?? speaker.createdAt);
  return classifyChipSeverity({
    speaker,
    statusChangedAt: anchor,
    eventDate: opts.eventDate ?? null,
    now: NOW,
    thresholds: opts.thresholds ?? DEFAULT_KANBAN_THRESHOLDS,
  });
}

describe('kanbanThresholds — classifyChipSeverity (AC5 #1-18)', () => {
  // #1
  it('should_returnNormal_when_speakerIsIdentified_andDaysInStateIsZero', () => {
    const speaker = makeSpeaker('IDENTIFIED', { createdAt: NOW.toISOString() });
    expect(classify(speaker)).toBe('normal');
  });

  // #2
  it('should_returnWarning_when_speakerIsIdentified_andDaysInStateIsExactly30', () => {
    const speaker = makeSpeaker('IDENTIFIED', { createdAt: daysAgo(30) });
    expect(classify(speaker)).toBe('warning');
  });

  // #3
  it('should_returnError_when_speakerIsIdentified_andDaysInStateIsExactly60', () => {
    const speaker = makeSpeaker('IDENTIFIED', { createdAt: daysAgo(60) });
    expect(classify(speaker)).toBe('error');
  });

  // #4
  it('should_returnWarning_when_speakerIsContacted_andDaysInStateIsBetween7and14', () => {
    const speaker = makeSpeaker('CONTACTED', { updatedAt: daysAgo(10) });
    expect(classify(speaker)).toBe('warning');
  });

  // #5
  it('should_returnError_when_speakerIsContacted_andDaysInStateIsAtLeast14', () => {
    const speaker = makeSpeaker('CONTACTED', { updatedAt: daysAgo(14) });
    expect(classify(speaker)).toBe('error');
  });

  // #6
  it('should_returnNormal_when_speakerIsReady_andDaysInStateIs2', () => {
    const speaker = makeSpeaker('READY', { updatedAt: daysAgo(2) });
    expect(classify(speaker)).toBe('normal');
  });

  // #7
  it('should_returnError_when_speakerIsReady_andDaysInStateIs8', () => {
    const speaker = makeSpeaker('READY', { updatedAt: daysAgo(8) });
    expect(classify(speaker)).toBe('error');
  });

  // #8
  it('should_returnWarning_when_speakerIsInvited_andResponseDeadlineIs2DaysAway', () => {
    const speaker = makeSpeaker('INVITED', {
      invitedAt: daysAgo(5),
      responseDeadline: daysFromNow(2),
    });
    expect(classify(speaker)).toBe('warning');
  });

  // #9
  it('should_returnError_when_speakerIsInvited_andResponseDeadlineHasPassed', () => {
    const speaker = makeSpeaker('INVITED', {
      invitedAt: daysAgo(10),
      responseDeadline: daysAgo(1),
    });
    expect(classify(speaker)).toBe('error');
  });

  // #10
  it('should_returnNormal_when_speakerIsInvited_andResponseDeadlineIs10DaysAway', () => {
    const speaker = makeSpeaker('INVITED', {
      invitedAt: daysAgo(2),
      responseDeadline: daysFromNow(10),
    });
    expect(classify(speaker)).toBe('normal');
  });

  // #11
  it('should_returnWarning_when_speakerIsAccepted_andDaysInStateIs15_withNoContentDeadline', () => {
    const speaker = makeSpeaker('ACCEPTED', { acceptedAt: daysAgo(15) });
    expect(classify(speaker)).toBe('warning');
  });

  // #12
  it('should_returnError_when_speakerIsAccepted_andContentDeadlineHasPassed', () => {
    const speaker = makeSpeaker('ACCEPTED', {
      acceptedAt: daysAgo(5),
      contentDeadline: daysAgo(1),
    });
    expect(classify(speaker)).toBe('error');
  });

  // #13
  it('should_returnWarning_when_speakerIsContentSubmitted_andDaysInStateIs4', () => {
    const speaker = makeSpeaker('CONTENT_SUBMITTED', { contentSubmittedAt: daysAgo(4) });
    expect(classify(speaker)).toBe('warning');
  });

  // #14
  it('should_returnError_when_speakerIsContentSubmitted_andDaysInStateIs8', () => {
    const speaker = makeSpeaker('CONTENT_SUBMITTED', { contentSubmittedAt: daysAgo(8) });
    expect(classify(speaker)).toBe('error');
  });

  // #15
  it('should_returnWarning_when_speakerIsQualityReviewed_andEventIs20DaysAway_andNoSlot', () => {
    const speaker = makeSpeaker('QUALITY_REVIEWED', {
      updatedAt: daysAgo(2),
      isSlotAssigned: false,
    });
    const eventDate = new Date(NOW.getTime() + 20 * 86_400_000);
    expect(classify(speaker, { eventDate })).toBe('warning');
  });

  // #16
  it('should_returnError_when_speakerIsQualityReviewed_andEventIs10DaysAway_andNoSlot', () => {
    const speaker = makeSpeaker('QUALITY_REVIEWED', {
      updatedAt: daysAgo(2),
      isSlotAssigned: false,
    });
    const eventDate = new Date(NOW.getTime() + 10 * 86_400_000);
    expect(classify(speaker, { eventDate })).toBe('error');
  });

  // #17
  it('should_returnNormal_when_speakerIsQualityReviewed_andSlotAssigned_regardlessOfEventDate', () => {
    const speaker = makeSpeaker('QUALITY_REVIEWED', {
      updatedAt: daysAgo(2),
      isSlotAssigned: true,
    });
    const eventDate = new Date(NOW.getTime() + 5 * 86_400_000); // very close
    expect(classify(speaker, { eventDate })).toBe('normal');
  });

  // #18
  it('should_returnNormal_when_speakerIsDeclined_regardlessOfDaysInState', () => {
    const speaker = makeSpeaker('DECLINED', { declinedAt: daysAgo(120) });
    expect(classify(speaker)).toBe('normal');
  });
});

describe('kanbanThresholds — aggregators (AC5 #19-20)', () => {
  // #19
  it('should_returnCountAttentionCards_matchingClassifyChipSeverity_forACONTACTEDColumn', () => {
    const fresh = makeSpeaker('CONTACTED', { id: 'c-fresh', updatedAt: daysAgo(2) });
    const warningCard = makeSpeaker('CONTACTED', { id: 'c-warn', updatedAt: daysAgo(10) });
    const stale1 = makeSpeaker('CONTACTED', { id: 'c-stale-1', updatedAt: daysAgo(15) });
    const stale2 = makeSpeaker('CONTACTED', { id: 'c-stale-2', updatedAt: daysAgo(20) });
    // Non-CONTACTED noise — must not contribute.
    const noise = makeSpeaker('IDENTIFIED', { id: 'noise', createdAt: daysAgo(90) });

    const speakers = [fresh, warningCard, stale1, stale2, noise];
    const count = countAttentionCards(speakers, 'CONTACTED', null, NOW, DEFAULT_KANBAN_THRESHOLDS);

    // CONTACTED subline is "stale (>14 days)" = error-only. Only stale1 + stale2 count.
    expect(count).toBe(2);

    // Each counted card must classify to 'error' — never disagrees with the chip colour.
    for (const s of [stale1, stale2]) {
      expect(classify(s)).toBe('error');
    }
    // Warning card must NOT contribute (subline is error-only for CONTACTED).
    expect(classify(warningCard)).toBe('warning');
  });

  // #20 — predicate/count parity, with explicit non-zero assertion so the test cannot
  // pass vacuously if both helpers happen to return 0 on a buggy implementation.
  it('should_buildPredicateThatMatchesCountAttentionCards', () => {
    const speakers: SpeakerPoolEntry[] = [
      makeSpeaker('CONTACTED', { id: 'c-1', updatedAt: daysAgo(2) }), // normal
      makeSpeaker('CONTACTED', { id: 'c-2', updatedAt: daysAgo(20) }), // error
      makeSpeaker('ACCEPTED', { id: 'a-1', acceptedAt: daysAgo(20) }), // warning
      makeSpeaker('ACCEPTED', {
        id: 'a-2',
        acceptedAt: daysAgo(5),
        contentDeadline: daysAgo(1),
      }), // error
      makeSpeaker('IDENTIFIED', { id: 'i-1', createdAt: daysAgo(100) }),
    ];

    const totals: Record<string, number> = {};
    for (const state of ['CONTACTED', 'ACCEPTED', 'QUALITY_REVIEWED'] as const) {
      const predicate = makeAttentionPredicate(state, null, NOW, DEFAULT_KANBAN_THRESHOLDS);
      const filtered = speakers.filter(predicate);
      const count = countAttentionCards(speakers, state, null, NOW, DEFAULT_KANBAN_THRESHOLDS);
      expect(filtered.length).toBe(count);
      totals[state] = count;
    }
    // CONTACTED has one stale card; ACCEPTED has 2 non-normal cards. At least one
    // bucket MUST be non-zero — otherwise the parity check is vacuous.
    expect(totals.CONTACTED).toBe(1);
    expect(totals.ACCEPTED).toBe(2);
    expect(totals.QUALITY_REVIEWED).toBe(0);
  });

  it('should_splitInvitedDeadlinesIntoApproachingAndPast', () => {
    const approaching = makeSpeaker('INVITED', {
      id: 'inv-approaching',
      invitedAt: daysAgo(5),
      responseDeadline: daysFromNow(1),
    });
    const past = makeSpeaker('INVITED', {
      id: 'inv-past',
      invitedAt: daysAgo(10),
      responseDeadline: daysAgo(2),
    });
    const calm = makeSpeaker('INVITED', {
      id: 'inv-calm',
      invitedAt: daysAgo(1),
      responseDeadline: daysFromNow(15),
    });
    const accepted = makeSpeaker('ACCEPTED', { id: 'a-noise', acceptedAt: daysAgo(1) });

    const split = countInvitedSplit(
      [approaching, past, calm, accepted],
      NOW,
      DEFAULT_KANBAN_THRESHOLDS
    );

    expect(split).toEqual({ approaching: 1, past: 1 });
  });

  it('should_returnZeroAttentionCount_for_IDENTIFIED_andDECLINED_andREADY', () => {
    const speakers = [
      makeSpeaker('IDENTIFIED', { id: 'i-1', createdAt: daysAgo(120) }),
      makeSpeaker('DECLINED', { id: 'd-1', declinedAt: daysAgo(120) }),
      makeSpeaker('READY', { id: 'r-1', updatedAt: daysAgo(120) }),
    ];
    for (const state of ['IDENTIFIED', 'DECLINED', 'READY'] as const) {
      expect(countAttentionCards(speakers, state, null, NOW, DEFAULT_KANBAN_THRESHOLDS)).toBe(0);
    }
  });
});

describe('kanbanThresholds — defensive guards', () => {
  it('should_returnNormal_when_statusChangedAtIsInvalidDate', () => {
    const speaker = makeSpeaker('CONTACTED', { updatedAt: 'not-a-date' });
    const anchor = new Date('not-a-date'); // Invalid Date
    const severity = classifyChipSeverity({
      speaker,
      statusChangedAt: anchor,
      eventDate: null,
      now: NOW,
      thresholds: DEFAULT_KANBAN_THRESHOLDS,
    });
    expect(severity).toBe('normal');
  });

  it('should_returnNormal_for_QUALITY_REVIEWED_when_eventDateIsNull', () => {
    const speaker = makeSpeaker('QUALITY_REVIEWED', {
      updatedAt: daysAgo(1),
      isSlotAssigned: false,
    });
    expect(classify(speaker, { eventDate: null })).toBe('normal');
  });

  it('should_mapSeverityToChipColor', () => {
    expect(severityToChipColor('normal')).toBe('default');
    expect(severityToChipColor('warning')).toBe('warning');
    expect(severityToChipColor('error')).toBe('error');
  });

  // Patch: `now = Invalid Date` shouldn't crash or silently classify everything as
  // normal-with-no-warning — the function returns 'normal' explicitly so callers see
  // a stable, audit-friendly value rather than relying on NaN propagation.
  it('should_returnNormal_when_nowIsInvalidDate', () => {
    const speaker = makeSpeaker('CONTACTED', { updatedAt: daysAgo(20) });
    const severity = classifyChipSeverity({
      speaker,
      statusChangedAt: new Date(speaker.updatedAt!),
      eventDate: null,
      now: new Date('invalid-now'),
      thresholds: DEFAULT_KANBAN_THRESHOLDS,
    });
    expect(severity).toBe('normal');
  });
});

describe('kanbanThresholds — adjacent boundary values (regression guards)', () => {
  // Boundary asymmetry guards: tests already cover exact threshold values (30/60 for
  // IDENTIFIED, 14 for CONTACTED, 7 for READY). These guard the value JUST below the
  // threshold to make sure a future flip from `>=` to `>` is caught immediately.
  it('should_returnNormal_when_speakerIsIdentified_andDaysInStateIs29', () => {
    const speaker = makeSpeaker('IDENTIFIED', { createdAt: daysAgo(29) });
    expect(classify(speaker)).toBe('normal');
  });

  it('should_returnWarning_when_speakerIsIdentified_andDaysInStateIs59', () => {
    const speaker = makeSpeaker('IDENTIFIED', { createdAt: daysAgo(59) });
    expect(classify(speaker)).toBe('warning');
  });

  it('should_returnNormal_when_speakerIsContacted_andDaysInStateIs6', () => {
    const speaker = makeSpeaker('CONTACTED', { updatedAt: daysAgo(6) });
    expect(classify(speaker)).toBe('normal');
  });

  it('should_returnWarning_when_speakerIsContacted_andDaysInStateIs13', () => {
    const speaker = makeSpeaker('CONTACTED', { updatedAt: daysAgo(13) });
    expect(classify(speaker)).toBe('warning');
  });

  it('should_returnNormal_when_speakerIsReady_andDaysInStateIs2', () => {
    const speaker = makeSpeaker('READY', { updatedAt: daysAgo(2) });
    expect(classify(speaker)).toBe('normal');
  });

  it('should_returnWarning_when_speakerIsReady_andDaysInStateIs6', () => {
    const speaker = makeSpeaker('READY', { updatedAt: daysAgo(6) });
    expect(classify(speaker)).toBe('warning');
  });
});

describe('kanbanThresholds — attentionMaxSeverity', () => {
  it('should_returnNormal_when_speakersArrayIsEmpty', () => {
    expect(
      attentionMaxSeverity([], 'CONTENT_SUBMITTED', null, NOW, DEFAULT_KANBAN_THRESHOLDS)
    ).toBe('normal');
  });

  it('should_returnNormal_when_noSpeakerInGivenStateMatchesPredicate', () => {
    const speakers = [
      makeSpeaker('CONTENT_SUBMITTED', { id: 'cs-1', contentSubmittedAt: daysAgo(1) }),
    ];
    expect(
      attentionMaxSeverity(speakers, 'CONTENT_SUBMITTED', null, NOW, DEFAULT_KANBAN_THRESHOLDS)
    ).toBe('normal');
  });

  it('should_returnWarning_when_allMatchingCardsAreWarning', () => {
    const speakers = [
      makeSpeaker('CONTENT_SUBMITTED', { id: 'cs-w-1', contentSubmittedAt: daysAgo(4) }),
      makeSpeaker('CONTENT_SUBMITTED', { id: 'cs-w-2', contentSubmittedAt: daysAgo(5) }),
    ];
    expect(
      attentionMaxSeverity(speakers, 'CONTENT_SUBMITTED', null, NOW, DEFAULT_KANBAN_THRESHOLDS)
    ).toBe('warning');
  });

  it('should_returnError_when_anyMatchingCardIsError_regardlessOfOrder', () => {
    const speakers = [
      makeSpeaker('CONTENT_SUBMITTED', { id: 'cs-w', contentSubmittedAt: daysAgo(4) }),
      makeSpeaker('CONTENT_SUBMITTED', { id: 'cs-e', contentSubmittedAt: daysAgo(10) }),
    ];
    expect(
      attentionMaxSeverity(speakers, 'CONTENT_SUBMITTED', null, NOW, DEFAULT_KANBAN_THRESHOLDS)
    ).toBe('error');
  });

  it('should_escalateToError_for_QUALITY_REVIEWED_whenAnyNoSlotCardIsError', () => {
    const eventDate = new Date(NOW.getTime() + 10 * 86_400_000); // 10 days away → error if no slot
    const speakers = [
      makeSpeaker('QUALITY_REVIEWED', {
        id: 'qr-error',
        updatedAt: daysAgo(2),
        isSlotAssigned: false,
      }),
      makeSpeaker('QUALITY_REVIEWED', {
        id: 'qr-assigned',
        updatedAt: daysAgo(2),
        isSlotAssigned: true,
      }),
    ];
    expect(
      attentionMaxSeverity(speakers, 'QUALITY_REVIEWED', eventDate, NOW, DEFAULT_KANBAN_THRESHOLDS)
    ).toBe('error');
  });
});

describe('kanbanThresholds — QUALITY_REVIEWED mixed-slot attention set', () => {
  // Guards against an inversion in `makeAttentionPredicate`'s QUALITY_REVIEWED rule
  // (`severity !== 'normal' && !speaker.isSlotAssigned`). With both kinds in one
  // input array, the slot-assigned cards MUST be excluded from the count regardless
  // of how urgent the event date is.
  it('should_excludeSlotAssignedFrom_QUALITY_REVIEWED_attentionSet_evenWhenEventIsImminent', () => {
    const eventDate = new Date(NOW.getTime() + 5 * 86_400_000); // 5 days away — error window
    const speakers: SpeakerPoolEntry[] = [
      makeSpeaker('QUALITY_REVIEWED', {
        id: 'qr-assigned-imminent',
        updatedAt: daysAgo(2),
        isSlotAssigned: true,
      }),
      makeSpeaker('QUALITY_REVIEWED', {
        id: 'qr-noslot-imminent',
        updatedAt: daysAgo(2),
        isSlotAssigned: false,
      }),
      makeSpeaker('QUALITY_REVIEWED', {
        id: 'qr-noslot-fresh',
        updatedAt: daysAgo(2),
        isSlotAssigned: false,
      }),
    ];

    const count = countAttentionCards(
      speakers,
      'QUALITY_REVIEWED',
      eventDate,
      NOW,
      DEFAULT_KANBAN_THRESHOLDS
    );
    // Two no-slot cards both classify to 'error' (within 14 days of event), one
    // slot-assigned card excluded. Count = 2.
    expect(count).toBe(2);

    const predicate = makeAttentionPredicate(
      'QUALITY_REVIEWED',
      eventDate,
      NOW,
      DEFAULT_KANBAN_THRESHOLDS
    );
    expect(speakers.filter(predicate).map((s) => s.id)).toEqual([
      'qr-noslot-imminent',
      'qr-noslot-fresh',
    ]);
  });
});
