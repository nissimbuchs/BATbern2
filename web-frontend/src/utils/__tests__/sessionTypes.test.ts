import { describe, it, expect } from 'vitest';
import { isStructuralSession, STRUCTURAL_SESSION_TYPES } from '../sessionTypes';

describe('isStructuralSession', () => {
  it.each(['moderation', 'break', 'lunch', 'aperitif', 'BREAK', 'Aperitif'])(
    'should treat %s as structural',
    (type) => expect(isStructuralSession(type)).toBe(true)
  );

  it.each(['presentation', 'keynote', 'workshop', 'panel_discussion', 'networking'])(
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
      'aperitif',
      'break',
      'lunch',
      'moderation',
    ]);
  });
});
