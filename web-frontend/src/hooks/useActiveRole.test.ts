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
    const { result } = renderHook(() => useActiveRole(['ORGANIZER']));

    const [activeRole] = result.current;
    expect(activeRole).toBe('ORGANIZER');
  });

  it('should return primary role as default for multi-role users when no stored preference', () => {
    const { result } = renderHook(() => useActiveRole(['ORGANIZER', 'SPEAKER', 'PARTNER']));

    const [activeRole] = result.current;
    expect(activeRole).toBe('ORGANIZER');
  });

  it('should restore stored role from localStorage for multi-role users', () => {
    localStorage.setItem('batbern:active-role', 'SPEAKER');

    const { result } = renderHook(() => useActiveRole(['ORGANIZER', 'SPEAKER', 'PARTNER']));

    const [activeRole] = result.current;
    expect(activeRole).toBe('SPEAKER');
  });

  it('should fall back to primary role when stored role not in user roles', () => {
    localStorage.setItem('batbern:active-role', 'PARTNER');

    const { result } = renderHook(
      () => useActiveRole(['ORGANIZER', 'SPEAKER']) // PARTNER not in roles
    );

    const [activeRole] = result.current;
    expect(activeRole).toBe('ORGANIZER');
  });

  it('should update active role and persist to localStorage', () => {
    const { result } = renderHook(() => useActiveRole(['ORGANIZER', 'SPEAKER', 'PARTNER']));

    act(() => {
      const [, setActiveRole] = result.current;
      setActiveRole('PARTNER' as UserRole);
    });

    const [activeRole] = result.current;
    expect(activeRole).toBe('PARTNER');
    expect(localStorage.getItem('batbern:active-role')).toBe('PARTNER');
  });

  it('should handle localStorage errors gracefully on read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('localStorage unavailable');
    });

    const { result } = renderHook(() => useActiveRole(['ORGANIZER', 'SPEAKER']));

    const [activeRole] = result.current;
    // Falls back to primary role
    expect(activeRole).toBe('ORGANIZER');
  });

  it('should handle localStorage errors gracefully on write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('localStorage full');
    });

    const { result } = renderHook(() => useActiveRole(['ORGANIZER', 'SPEAKER']));

    // Should not throw when setting role with broken localStorage
    expect(() => {
      act(() => {
        const [, setActiveRole] = result.current;
        setActiveRole('SPEAKER' as UserRole);
      });
    }).not.toThrow();

    const [activeRole] = result.current;
    expect(activeRole).toBe('SPEAKER');
  });
});
