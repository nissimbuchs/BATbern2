/**
 * useActiveRole Hook Tests
 *
 * Tests for multi-role active role selection with localStorage persistence.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useActiveRole } from './useActiveRole';
import type { UserRole } from '@/types/auth';

describe('useActiveRole', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('should return primary role when user has only one role', () => {
    const { result } = renderHook(() => useActiveRole(['organizer']));

    const [activeRole] = result.current;
    expect(activeRole).toBe('organizer');
  });

  it('should return primary role as default for multi-role users when no stored preference', () => {
    const { result } = renderHook(() => useActiveRole(['organizer', 'speaker', 'partner']));

    const [activeRole] = result.current;
    expect(activeRole).toBe('organizer');
  });

  it('should restore stored role from localStorage for multi-role users', () => {
    localStorage.setItem('batbern:active-role', 'speaker');

    const { result } = renderHook(() => useActiveRole(['organizer', 'speaker', 'partner']));

    const [activeRole] = result.current;
    expect(activeRole).toBe('speaker');
  });

  it('should fall back to primary role when stored role not in user roles', () => {
    localStorage.setItem('batbern:active-role', 'partner');

    const { result } = renderHook(
      () => useActiveRole(['organizer', 'speaker']) // PARTNER not in roles
    );

    const [activeRole] = result.current;
    expect(activeRole).toBe('organizer');
  });

  it('should update active role and persist to localStorage', () => {
    const { result } = renderHook(() => useActiveRole(['organizer', 'speaker', 'partner']));

    act(() => {
      const [, setActiveRole] = result.current;
      setActiveRole('partner' as UserRole);
    });

    const [activeRole] = result.current;
    expect(activeRole).toBe('partner');
    expect(localStorage.getItem('batbern:active-role')).toBe('partner');
  });

  it('should handle localStorage errors gracefully on read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('localStorage unavailable');
    });

    const { result } = renderHook(() => useActiveRole(['organizer', 'speaker']));

    const [activeRole] = result.current;
    // Falls back to primary role
    expect(activeRole).toBe('organizer');
  });

  it('should handle localStorage errors gracefully on write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('localStorage full');
    });

    const { result } = renderHook(() => useActiveRole(['organizer', 'speaker']));

    // Should not throw when setting role with broken localStorage
    expect(() => {
      act(() => {
        const [, setActiveRole] = result.current;
        setActiveRole('speaker' as UserRole);
      });
    }).not.toThrow();

    const [activeRole] = result.current;
    expect(activeRole).toBe('speaker');
  });
});
