/**
 * Partner Detail Store Tests (RED Phase -> GREEN Phase)
 *
 * TDD tests for Partner Detail Zustand store
 * Story 2.8.2: Partner Detail View
 *
 * Test Scenarios:
 * - AC3: Tab navigation state management
 */

import { describe, test, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePartnerDetailStore } from './partnerDetailStore';

describe('Partner Detail Store', () => {
  beforeEach(() => {
    // Reset store state before each test
    const { reset } = usePartnerDetailStore.getState();
    act(() => {
      reset();
    });
  });

  describe('Initial State', () => {
    // Test: should_haveActiveTabZero_when_storeInitialized
    test('should_haveActiveTabZero_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      expect(result.current.activeTab).toBe(0);
    });

    // Test: should_haveEditModalClosedFalse_when_storeInitialized
    test('should_haveEditModalClosedFalse_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      expect(result.current.showEditModal).toBe(false);
    });

    // Test: should_haveNoteModalClosedFalse_when_storeInitialized
    test('should_haveNoteModalClosedFalse_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      expect(result.current.showNoteModal).toBe(false);
    });
  });

  describe('setActiveTab Action', () => {
    // Test 1: should_updateActiveTab_when_setActiveTabCalled
    test('should_updateActiveTab_when_setActiveTabCalled', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      act(() => {
        result.current.setActiveTab(2);
      });

      expect(result.current.activeTab).toBe(2);
    });

    // Test 2: should_handleTabIndexZero_when_overviewTabSelected
    test('should_handleTabIndexZero_when_overviewTabSelected', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      // First set to different tab
      act(() => {
        result.current.setActiveTab(3);
      });

      // Then back to overview (0)
      act(() => {
        result.current.setActiveTab(0);
      });

      expect(result.current.activeTab).toBe(0);
    });

    // Test 3: should_handleTabIndexFive_when_settingsTabSelected
    test('should_handleTabIndexFive_when_settingsTabSelected', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      act(() => {
        result.current.setActiveTab(5);
      });

      expect(result.current.activeTab).toBe(5);
    });
  });

  describe('setShowEditModal Action', () => {
    // Test 1: should_openEditModal_when_setShowEditModalCalled
    test('should_openEditModal_when_setShowEditModalCalled', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      act(() => {
        result.current.setShowEditModal(true);
      });

      expect(result.current.showEditModal).toBe(true);
    });

    // Test 2: should_closeEditModal_when_setShowEditModalFalse
    test('should_closeEditModal_when_setShowEditModalFalse', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      // First open
      act(() => {
        result.current.setShowEditModal(true);
      });

      // Then close
      act(() => {
        result.current.setShowEditModal(false);
      });

      expect(result.current.showEditModal).toBe(false);
    });
  });

  describe('setShowNoteModal Action', () => {
    // Test 1: should_openNoteModal_when_setShowNoteModalCalled
    test('should_openNoteModal_when_setShowNoteModalCalled', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      act(() => {
        result.current.setShowNoteModal(true);
      });

      expect(result.current.showNoteModal).toBe(true);
    });

    // Test 2: should_closeNoteModal_when_setShowNoteModalFalse
    test('should_closeNoteModal_when_setShowNoteModalFalse', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      // First open
      act(() => {
        result.current.setShowNoteModal(true);
      });

      // Then close
      act(() => {
        result.current.setShowNoteModal(false);
      });

      expect(result.current.showNoteModal).toBe(false);
    });
  });

  describe('reset Action', () => {
    // Test: should_resetAllState_when_resetCalled
    test('should_resetAllState_when_resetCalled', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      // Change all state values
      act(() => {
        result.current.setActiveTab(3);
        result.current.setShowEditModal(true);
        result.current.setShowNoteModal(true);
      });

      // Reset
      act(() => {
        result.current.reset();
      });

      // Verify all back to initial state
      expect(result.current.activeTab).toBe(0);
      expect(result.current.showEditModal).toBe(false);
      expect(result.current.showNoteModal).toBe(false);
    });
  });

  describe('Store Actions', () => {
    // Test: should_haveAllActions_when_storeCreated
    test('should_haveAllActions_when_storeCreated', () => {
      const { result } = renderHook(() => usePartnerDetailStore());

      expect(result.current.setActiveTab).toBeDefined();
      expect(result.current.setShowEditModal).toBeDefined();
      expect(result.current.setShowNoteModal).toBeDefined();
      expect(result.current.reset).toBeDefined();
    });
  });
});
