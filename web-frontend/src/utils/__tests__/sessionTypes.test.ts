import { describe, it, expect } from 'vitest';
import { isStructuralSession, STRUCTURAL_SESSION_TYPES } from '../sessionTypes';

describe('isStructuralSession', () => {
  it.each(['moderation', 'break', 'lunch', 'networking', 'BREAK', 'Networking'])(
    'should treat %s as structural',
    (type) => expect(isStructuralSession(type)).toBe(true)
  );

  // aperitif was folded into networking by V127 (#1123)
  it.each(['presentation', 'keynote', 'workshop', 'panel_discussion', 'aperitif'])(
    'should treat %s as a speaker session',
    (type) => expect(isStructuralSession(type)).toBe(false)
  );

  it('should treat a missing type as a speaker session', () => {
    expect(isStructuralSession(undefined)).toBe(false);
    expect(isStructuralSession(null)).toBe(false);
    expect(isStructuralSession('')).toBe(false);
  });

  it('should mirror the backend set (Session.STRUCTURAL_SESSION_TYPES)', () => {
    expect([...STRUCTURAL_SESSION_TYPES].sort()).toEqual([
      'break',
      'lunch',
      'moderation',
      'networking',
    ]);
  });
});
