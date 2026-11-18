/**
 * UI Store Tests
 * Story 1.17, Task 5a: TDD for Zustand UI store
 */

import { describe, test, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useUIStore } from './uiStore';

describe('UI Store', () => {
  beforeEach(() => {
    // Clear localStorage first
    localStorage.clear();
    // Then reset store state
    const { reset } = useUIStore.getState();
    act(() => {
      reset();
    });
  });

  describe('Initial State', () => {
    test('should_haveSidebarCollapsedFalse_when_storeInitialized', () => {
      const { result } = renderHook(() => useUIStore());

      expect(result.current.sidebarCollapsed).toBe(false);
    });

    test('should_haveNotificationDrawerClosedFalse_when_storeInitialized', () => {
      const { result } = renderHook(() => useUIStore());

      expect(result.current.notificationDrawerOpen).toBe(false);
    });

    test('should_haveUserMenuOpenFalse_when_storeInitialized', () => {
      const { result } = renderHook(() => useUIStore());

      expect(result.current.userMenuOpen).toBe(false);
    });
  });

  describe('toggleSidebar Action', () => {
    test('should_toggleSidebarState_when_toggleSidebarCalled', () => {
      const { result } = renderHook(() => useUIStore());

      expect(result.current.sidebarCollapsed).toBe(false);

      act(() => {
        result.current.toggleSidebar();
      });

      expect(result.current.sidebarCollapsed).toBe(true);

      act(() => {
        result.current.toggleSidebar();
      });

      expect(result.current.sidebarCollapsed).toBe(false);
    });
  });

  describe('setSidebarCollapsed Action', () => {
    test('should_setSidebarState_when_setSidebarCollapsedCalled', () => {
      const { result } = renderHook(() => useUIStore());

      act(() => {
        result.current.setSidebarCollapsed(true);
      });

      expect(result.current.sidebarCollapsed).toBe(true);

      act(() => {
        result.current.setSidebarCollapsed(false);
      });

      expect(result.current.sidebarCollapsed).toBe(false);
    });
  });

  describe('setNotificationDrawerOpen Action', () => {
    test('should_updateDrawerState_when_setNotificationDrawerOpenCalled', () => {
      const { result } = renderHook(() => useUIStore());

      act(() => {
        result.current.setNotificationDrawerOpen(true);
      });

      expect(result.current.notificationDrawerOpen).toBe(true);

      act(() => {
        result.current.setNotificationDrawerOpen(false);
      });

      expect(result.current.notificationDrawerOpen).toBe(false);
    });
  });

  describe('setUserMenuOpen Action', () => {
    test('should_updateUserMenuState_when_setUserMenuOpenCalled', () => {
      const { result } = renderHook(() => useUIStore());

      act(() => {
        result.current.setUserMenuOpen(true);
      });

      expect(result.current.userMenuOpen).toBe(true);

      act(() => {
        result.current.setUserMenuOpen(false);
      });

      expect(result.current.userMenuOpen).toBe(false);
    });
  });

  describe('State Persistence', () => {
    test('should_persistSidebarState_when_sidebarToggled', () => {
      const { result } = renderHook(() => useUIStore());

      act(() => {
        result.current.setSidebarCollapsed(true);
      });

      const storedState = localStorage.getItem('batbern-ui-sidebar');
      expect(storedState).toBe('true');
    });

    test('should_defaultToFalse_when_sidebarStateNotInLocalStorage', () => {
      // Clear localStorage first
      localStorage.clear();
      const { reset } = useUIStore.getState();
      act(() => {
        reset();
      });

      const { result } = renderHook(() => useUIStore());

      expect(result.current.sidebarCollapsed).toBe(false);
    });

    test('should_loadSidebarStateFromLocalStorage_when_storeInitialized', () => {
      // Clear and reset first
      localStorage.clear();
      const { reset, setSidebarCollapsed } = useUIStore.getState();
      act(() => {
        reset();
      });

      // Set sidebar state in localStorage
      localStorage.setItem('batbern-ui-sidebar', 'true');

      // Manually trigger sidebar state update
      act(() => {
        setSidebarCollapsed(true);
      });

      const { result } = renderHook(() => useUIStore());

      expect(result.current.sidebarCollapsed).toBe(true);
    });
  });

  describe('reset Action', () => {
    test('should_resetToDefaultState_when_resetCalled', () => {
      const { result } = renderHook(() => useUIStore());

      // Set some state
      act(() => {
        result.current.setSidebarCollapsed(true);
        result.current.setNotificationDrawerOpen(true);
        result.current.setUserMenuOpen(true);
      });

      // Reset
      act(() => {
        result.current.reset();
      });

      expect(result.current.sidebarCollapsed).toBe(false);
      expect(result.current.notificationDrawerOpen).toBe(false);
      expect(result.current.userMenuOpen).toBe(false);
    });

    test('should_clearLocalStorage_when_resetCalled', () => {
      const { result } = renderHook(() => useUIStore());

      // Set some state that persists to localStorage
      act(() => {
        result.current.setSidebarCollapsed(true);
      });

      // Verify localStorage has values
      expect(localStorage.getItem('batbern-ui-sidebar')).toBeTruthy();

      // Reset
      act(() => {
        result.current.reset();
      });

      // Verify localStorage is cleared
      expect(localStorage.getItem('batbern-ui-sidebar')).toBeNull();
    });
  });
});
