/**
 * Unit tests for `speakerTransitions.ts` (Story 11.D.4 — AC1 + AC10 cases 1-20).
 *
 * Pure-function tests — no rendering, no React, no mocks. Pinned to ADR-009 §0.2 and
 * `docs/architecture/06a-workflow-state-machines.md` lines 296-306.
 */
import { describe, it, expect } from 'vitest';
import {
  ALLOWED_TRANSITIONS,
  classifyDrop,
  getRejectionExplanation,
  isLegalTransition,
  type KanbanState,
} from '../speakerTransitions';

const NON_TERMINAL_KANBAN_STATES: KanbanState[] = [
  'IDENTIFIED',
  'CONTACTED',
  'READY',
  'INVITED',
  'ACCEPTED',
  'CONTENT_SUBMITTED',
  'QUALITY_REVIEWED',
];

describe('speakerTransitions — isLegalTransition (AC10 cases 1-13)', () => {
  // Case 1
  it('should_returnLegalTrue_when_identifiedToContacted', () => {
    expect(isLegalTransition('IDENTIFIED', 'CONTACTED')).toBe(true);
  });

  // Case 2
  it('should_returnLegalTrue_when_identifiedToDeclined', () => {
    expect(isLegalTransition('IDENTIFIED', 'DECLINED')).toBe(true);
  });

  // Case 3 — covers identifiedToAccepted/Invited/ContentSubmitted/QualityReviewed
  it.each(['ACCEPTED', 'INVITED', 'CONTENT_SUBMITTED', 'QUALITY_REVIEWED'] as const)(
    'should_returnLegalFalse_when_identifiedTo%s',
    (to) => {
      expect(isLegalTransition('IDENTIFIED', to)).toBe(false);
    }
  );

  // Case 4
  it('should_returnLegalTrue_when_contactedToReady_andContactedToDeclined', () => {
    expect(isLegalTransition('CONTACTED', 'READY')).toBe(true);
    expect(isLegalTransition('CONTACTED', 'DECLINED')).toBe(true);
  });

  // Case 5 — covers all CONTACTED skip-ahead variants
  it.each(['INVITED', 'ACCEPTED', 'CONTENT_SUBMITTED', 'QUALITY_REVIEWED'] as const)(
    'should_returnLegalFalse_when_contactedTo%s',
    (to) => {
      expect(isLegalTransition('CONTACTED', to)).toBe(false);
    }
  );

  // Case 6
  it('should_returnLegalTrue_when_readyToInvited_andReadyToDeclined', () => {
    expect(isLegalTransition('READY', 'INVITED')).toBe(true);
    expect(isLegalTransition('READY', 'DECLINED')).toBe(true);
  });

  // Case 7
  it('should_returnLegalFalse_when_readyToAccepted', () => {
    expect(isLegalTransition('READY', 'ACCEPTED')).toBe(false);
    expect(isLegalTransition('READY', 'CONTENT_SUBMITTED')).toBe(false);
    expect(isLegalTransition('READY', 'QUALITY_REVIEWED')).toBe(false);
  });

  // Case 8
  it('should_returnLegalTrue_when_invitedToAccepted_andInvitedToDeclined', () => {
    expect(isLegalTransition('INVITED', 'ACCEPTED')).toBe(true);
    expect(isLegalTransition('INVITED', 'DECLINED')).toBe(true);
  });

  // Case 9
  it('should_returnLegalTrue_when_acceptedToContentSubmitted_andAcceptedToDeclined', () => {
    expect(isLegalTransition('ACCEPTED', 'CONTENT_SUBMITTED')).toBe(true);
    expect(isLegalTransition('ACCEPTED', 'DECLINED')).toBe(true);
  });

  // Case 10
  it('should_returnLegalTrue_when_contentSubmittedToQualityReviewed_andContentSubmittedToDeclined', () => {
    expect(isLegalTransition('CONTENT_SUBMITTED', 'QUALITY_REVIEWED')).toBe(true);
    expect(isLegalTransition('CONTENT_SUBMITTED', 'DECLINED')).toBe(true);
  });

  // Case 11
  it('should_returnLegalTrue_when_qualityReviewedToDeclined', () => {
    expect(isLegalTransition('QUALITY_REVIEWED', 'DECLINED')).toBe(true);
  });

  // Case 12 — covers all backwards moves from QUALITY_REVIEWED
  it.each([
    'CONTENT_SUBMITTED',
    'ACCEPTED',
    'INVITED',
    'READY',
    'CONTACTED',
    'IDENTIFIED',
  ] as const)('should_returnLegalFalse_when_qualityReviewedTo%s', (to) => {
    expect(isLegalTransition('QUALITY_REVIEWED', to)).toBe(false);
  });

  // Case 13 — DECLINED is terminal; cannot transition to anything (including itself).
  it.each([
    'IDENTIFIED',
    'CONTACTED',
    'READY',
    'INVITED',
    'ACCEPTED',
    'CONTENT_SUBMITTED',
    'QUALITY_REVIEWED',
    'DECLINED',
  ] as const)('should_returnLegalFalse_when_declinedTo%s', (to) => {
    expect(isLegalTransition('DECLINED', to)).toBe(false);
  });

  // Sanity: ALLOWED_TRANSITIONS map shape mirrors the function above.
  it('should_exposeAllowedTransitionsAsReadonly', () => {
    expect(ALLOWED_TRANSITIONS.DECLINED.size).toBe(0);
    expect(ALLOWED_TRANSITIONS.IDENTIFIED.has('CONTACTED')).toBe(true);
    expect(ALLOWED_TRANSITIONS.QUALITY_REVIEWED.has('DECLINED')).toBe(true);
  });
});

describe('speakerTransitions — classifyDrop (AC10 cases 14-20)', () => {
  // Case 14
  it('should_classifyAsLegalInputPromote_when_contactedToReady', () => {
    expect(classifyDrop('CONTACTED', 'READY', false)).toEqual({
      kind: 'legal-input',
      modal: 'promote',
    });
  });

  // Case 15
  it('should_classifyAsLegalInputInvitation_when_readyToInvited_andSlotCapacityNotReached', () => {
    expect(classifyDrop('READY', 'INVITED', false)).toEqual({
      kind: 'legal-input',
      modal: 'invitation',
    });
  });

  // Case 16
  it('should_classifyAsLegalBlockedSlot_when_readyToInvited_andSlotCapacityReached', () => {
    expect(classifyDrop('READY', 'INVITED', true)).toEqual({ kind: 'legal-blocked-slot' });
  });

  // Case 17
  it('should_classifyAsLegalInputContentForm_when_acceptedToContentSubmitted', () => {
    expect(classifyDrop('ACCEPTED', 'CONTENT_SUBMITTED', false)).toEqual({
      kind: 'legal-input',
      modal: 'content-form',
    });
  });

  // Case 18
  it('should_classifyAsLegalInputQualityReview_when_contentSubmittedToQualityReviewed', () => {
    expect(classifyDrop('CONTENT_SUBMITTED', 'QUALITY_REVIEWED', false)).toEqual({
      kind: 'legal-input',
      modal: 'quality-review',
    });
  });

  // Case 19 — parameterised across the 7 non-terminal source states.
  it.each(NON_TERMINAL_KANBAN_STATES)('should_classifyAsLegalDecline_when_%sToDeclined', (from) => {
    expect(classifyDrop(from, 'DECLINED', false)).toEqual({ kind: 'legal-decline' });
  });

  // Case 20 — parameterised skip-ahead matrix.
  it.each([
    ['IDENTIFIED', 'ACCEPTED'],
    ['IDENTIFIED', 'INVITED'],
    ['IDENTIFIED', 'CONTENT_SUBMITTED'],
    ['IDENTIFIED', 'QUALITY_REVIEWED'],
    ['CONTACTED', 'INVITED'],
    ['CONTACTED', 'ACCEPTED'],
    ['CONTACTED', 'CONTENT_SUBMITTED'],
    ['READY', 'ACCEPTED'],
    ['READY', 'CONTENT_SUBMITTED'],
    ['INVITED', 'CONTENT_SUBMITTED'],
    ['INVITED', 'QUALITY_REVIEWED'],
    ['ACCEPTED', 'QUALITY_REVIEWED'],
    ['QUALITY_REVIEWED', 'ACCEPTED'],
    ['DECLINED', 'IDENTIFIED'],
  ] as const)('should_classifyAsIllegal_when_%sTo%s', (from, to) => {
    expect(classifyDrop(from, to, false)).toEqual({ kind: 'illegal' });
  });

  // Defensive: legal-direct currently only fires for INVITED → ACCEPTED.
  it('should_classifyAsLegalDirect_when_invitedToAccepted', () => {
    expect(classifyDrop('INVITED', 'ACCEPTED', false)).toEqual({ kind: 'legal-direct' });
  });
});

describe('speakerTransitions — getRejectionExplanation (AC3)', () => {
  // Identity i18n stub: returns the key plus a JSON-encoded options blob so we can
  // assert exact placeholder substitution.
  const fakeT = ((key: string, options?: Record<string, unknown>) => {
    if (options) {
      return `${key}|${JSON.stringify(options)}`;
    }
    return key;
  }) as unknown as Parameters<typeof getRejectionExplanation>[2];

  it('should_returnMustPromoteFirstKey_when_identifiedToAccepted', () => {
    const msg = getRejectionExplanation('IDENTIFIED', 'ACCEPTED', fakeT);
    expect(msg).toContain('organizer:kanbanDrag.rejection.template');
    expect(msg).toContain('organizer:kanbanDrag.rejection.mustPromoteFirst');
  });

  it('should_returnMustPromoteFirstKey_when_contactedToInvited', () => {
    const msg = getRejectionExplanation('CONTACTED', 'INVITED', fakeT);
    expect(msg).toContain('organizer:kanbanDrag.rejection.mustPromoteFirst');
  });

  it('should_returnMustInviteFirstKey_when_readyToAccepted', () => {
    const msg = getRejectionExplanation('READY', 'ACCEPTED', fakeT);
    expect(msg).toContain('organizer:kanbanDrag.rejection.mustInviteFirst');
  });

  it('should_returnMustAcceptFirstKey_when_invitedToContentSubmitted', () => {
    const msg = getRejectionExplanation('INVITED', 'CONTENT_SUBMITTED', fakeT);
    expect(msg).toContain('organizer:kanbanDrag.rejection.mustAcceptFirst');
  });

  it('should_returnMustSubmitContentFirstKey_when_acceptedToQualityReviewed', () => {
    const msg = getRejectionExplanation('ACCEPTED', 'QUALITY_REVIEWED', fakeT);
    expect(msg).toContain('organizer:kanbanDrag.rejection.mustSubmitContentFirst');
  });

  it('should_returnCannotMoveBackwardsKey_when_qualityReviewedToContentSubmitted', () => {
    const msg = getRejectionExplanation('QUALITY_REVIEWED', 'CONTENT_SUBMITTED', fakeT);
    expect(msg).toContain('organizer:kanbanDrag.rejection.cannotMoveBackwards');
  });

  it('should_returnCannotMoveBackwardsKey_when_acceptedToInvited', () => {
    const msg = getRejectionExplanation('ACCEPTED', 'INVITED', fakeT);
    expect(msg).toContain('organizer:kanbanDrag.rejection.cannotMoveBackwards');
  });

  it('should_substituteFromAndToPlaceholders', () => {
    const msg = getRejectionExplanation('IDENTIFIED', 'ACCEPTED', fakeT);
    expect(msg).toContain('"from":"organizer:speakerStatus.IDENTIFIED"');
    expect(msg).toContain('"to":"organizer:speakerStatus.ACCEPTED"');
  });
});
