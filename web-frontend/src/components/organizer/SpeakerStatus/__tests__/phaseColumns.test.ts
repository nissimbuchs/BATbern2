/**
 * phaseColumns pure-model tests (Epic 14 — Story 14.C.2 / 14.C.3).
 *
 * Covers the 4-phase grouping (FR15) and the workflow-safe cross-column drag
 * geometry (FR19/FR20): a forward column drop advances exactly one transition;
 * same/earlier-phase drops are no-ops (snap-back).
 */
import { describe, it, expect } from 'vitest';
import {
  PHASE_COLUMNS,
  PHASE_COLUMN_STATES,
  phaseForState,
  forwardSuccessor,
  resolveColumnDrop,
} from '../phaseColumns';

describe('phaseColumns — 4-phase model (Story 14.C.2)', () => {
  it('should_groupEightStatesIntoFourPhases_excludingDeclined', () => {
    expect(PHASE_COLUMNS.map((c) => c.key)).toEqual([
      'sourcing',
      'inviting',
      'content',
      'confirmed',
    ]);
    expect(PHASE_COLUMNS.find((c) => c.key === 'sourcing')?.states).toEqual([
      'IDENTIFIED',
      'CONTACTED',
    ]);
    expect(PHASE_COLUMNS.find((c) => c.key === 'inviting')?.states).toEqual(['READY', 'INVITED']);
    expect(PHASE_COLUMNS.find((c) => c.key === 'content')?.states).toEqual([
      'ACCEPTED',
      'CONTENT_SUBMITTED',
    ]);
    expect(PHASE_COLUMNS.find((c) => c.key === 'confirmed')?.states).toEqual(['QUALITY_REVIEWED']);
  });

  it('should_excludeDeclinedFromAnyColumn', () => {
    expect(PHASE_COLUMN_STATES).not.toContain('DECLINED');
    expect(phaseForState('DECLINED')).toBeNull();
  });

  it('should_mapEachNonTerminalStateToItsPhase', () => {
    expect(phaseForState('IDENTIFIED')).toBe('sourcing');
    expect(phaseForState('CONTACTED')).toBe('sourcing');
    expect(phaseForState('READY')).toBe('inviting');
    expect(phaseForState('INVITED')).toBe('inviting');
    expect(phaseForState('ACCEPTED')).toBe('content');
    expect(phaseForState('CONTENT_SUBMITTED')).toBe('content');
    expect(phaseForState('QUALITY_REVIEWED')).toBe('confirmed');
  });
});

describe('phaseColumns — forward successor + cross-column drop (Story 14.C.3)', () => {
  it('should_returnSingleForwardSuccessor_perState', () => {
    expect(forwardSuccessor('IDENTIFIED')).toBe('CONTACTED');
    expect(forwardSuccessor('CONTACTED')).toBe('READY');
    expect(forwardSuccessor('READY')).toBe('INVITED');
    expect(forwardSuccessor('INVITED')).toBe('ACCEPTED');
    expect(forwardSuccessor('ACCEPTED')).toBe('CONTENT_SUBMITTED');
    expect(forwardSuccessor('CONTENT_SUBMITTED')).toBe('QUALITY_REVIEWED');
    expect(forwardSuccessor('QUALITY_REVIEWED')).toBeNull();
    expect(forwardSuccessor('DECLINED')).toBeNull();
  });

  it('should_advanceExactlyOneStep_when_droppingIdentifiedOnContentColumn', () => {
    // FR19 — dropping IDENTIFIED on the *Content* column must NOT fire three steps.
    const drop = resolveColumnDrop('IDENTIFIED', 'content');
    expect(drop).toEqual({ kind: 'forward', from: 'IDENTIFIED', to: 'CONTACTED' });
  });

  it('should_advanceOneStep_when_droppingOnAdjacentForwardColumn', () => {
    expect(resolveColumnDrop('CONTACTED', 'inviting')).toEqual({
      kind: 'forward',
      from: 'CONTACTED',
      to: 'READY',
    });
    expect(resolveColumnDrop('INVITED', 'content')).toEqual({
      kind: 'forward',
      from: 'INVITED',
      to: 'ACCEPTED',
    });
  });

  it('should_noop_when_droppingOnSamePhase', () => {
    // IDENTIFIED and CONTACTED share the Sourcing phase — within-pair advances use the card button.
    expect(resolveColumnDrop('IDENTIFIED', 'sourcing')).toEqual({ kind: 'noop' });
    expect(resolveColumnDrop('READY', 'inviting')).toEqual({ kind: 'noop' });
  });

  it('should_noop_when_droppingBackward', () => {
    // FR20 — backward drag snaps back (the one legal back-transition lives on the drawer/card).
    expect(resolveColumnDrop('QUALITY_REVIEWED', 'content')).toEqual({ kind: 'noop' });
    expect(resolveColumnDrop('ACCEPTED', 'sourcing')).toEqual({ kind: 'noop' });
    expect(resolveColumnDrop('INVITED', 'inviting')).toEqual({ kind: 'noop' });
  });

  it('should_noop_when_confirmedCardHasNoForwardColumn', () => {
    // QUALITY_REVIEWED is the last column; nothing forward of it.
    expect(resolveColumnDrop('QUALITY_REVIEWED', 'confirmed')).toEqual({ kind: 'noop' });
  });
});
