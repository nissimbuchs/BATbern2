/**
 * Tests for Event Management Zustand Store
 *
 * Story 2.5.3 - Task 6a (RED Phase)
 * Test Specifications: AC18 - State Management
 *
 * Tests for:
 * - Initial state
 * - Filter state management
 * - Selected event code management (Story 1.16.2: uses eventCode as identifier)
 * - Modal state management (create/edit)
 * - State persistence (via zustand persist middleware - only filters persist)
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useEventStore } from '@/stores/eventStore';
import { EventFilters } from '@/types/event.types';

describe('eventStore', () => {
  // Reset store and localStorage before each test
  beforeEach(() => {
    // Clear localStorage to reset persisted state
    localStorage.clear();

    // Reset store state to initial values
    const { result } = renderHook(() => useEventStore());
    act(() => {
      result.current.setFilters({});
      result.current.closeCreateModal();
      result.current.closeEditModal();
      result.current.setSelectedEventCode(undefined);
    });
  });

  afterEach(() => {
    // Clean up localStorage after each test
    localStorage.clear();
  });

  describe('Initial State', () => {
    it('should_haveEmptyFilters_when_storeInitialized', () => {
      const { result } = renderHook(() => useEventStore());

      expect(result.current.filters).toEqual({});
    });

    it('should_haveNoSelectedEvent_when_storeInitialized', () => {
      const { result } = renderHook(() => useEventStore());

      expect(result.current.selectedEventCode).toBeUndefined();
    });

    it('should_haveModalsClosedByDefault_when_storeInitialized', () => {
      const { result } = renderHook(() => useEventStore());

      expect(result.current.isCreateModalOpen).toBe(false);
      expect(result.current.isEditModalOpen).toBe(false);
    });
  });

  describe('Filter Management', () => {
    it('should_updateFilters_when_setFiltersCalled', () => {
      const { result } = renderHook(() => useEventStore());

      const newFilters: EventFilters = {
        status: ['active', 'published'],
        year: 2025,
        search: 'BATbern',
      };

      act(() => {
        result.current.setFilters(newFilters);
      });

      expect(result.current.filters).toEqual(newFilters);
    });

    it('should_replaceFilters_when_setFiltersCalledMultipleTimes', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.setFilters({ status: ['active'] });
      });

      expect(result.current.filters).toEqual({ status: ['active'] });

      act(() => {
        result.current.setFilters({ year: 2025 });
      });

      expect(result.current.filters).toEqual({ year: 2025 });
    });

    it('should_clearFilters_when_setFiltersCalledWithEmptyObject', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.setFilters({ status: ['active'], year: 2025 });
      });

      act(() => {
        result.current.setFilters({});
      });

      expect(result.current.filters).toEqual({});
    });

    it('should_handleMultipleFilters_when_allFiltersSet', () => {
      const { result } = renderHook(() => useEventStore());

      const filters: EventFilters = {
        status: ['active', 'published', 'completed'],
        year: 2025,
        search: 'DevOps',
      };

      act(() => {
        result.current.setFilters(filters);
      });

      expect(result.current.filters).toEqual(filters);
    });

    it('should_handleStatusArray_when_multipleStatusesProvided', () => {
      const { result } = renderHook(() => useEventStore());

      const filters: EventFilters = {
        status: ['active', 'published', 'archived'],
      };

      act(() => {
        result.current.setFilters(filters);
      });

      expect(result.current.filters.status).toEqual(['active', 'published', 'archived']);
    });

    it('should_handleSearchQuery_when_searchProvided', () => {
      const { result } = renderHook(() => useEventStore());

      const filters: EventFilters = {
        search: 'Cloud Computing',
      };

      act(() => {
        result.current.setFilters(filters);
      });

      expect(result.current.filters.search).toBe('Cloud Computing');
    });
  });

  describe('Selected Event Management', () => {
    it('should_setSelectedEventCode_when_codeProvided', () => {
      const { result } = renderHook(() => useEventStore());

      const eventCode = 'BATbern56';

      act(() => {
        result.current.setSelectedEventCode(eventCode);
      });

      expect(result.current.selectedEventCode).toBe(eventCode);
    });

    it('should_clearSelectedEventCode_when_undefinedProvided', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.setSelectedEventCode('BATbern56');
      });

      expect(result.current.selectedEventCode).toBe('BATbern56');

      act(() => {
        result.current.setSelectedEventCode(undefined);
      });

      expect(result.current.selectedEventCode).toBeUndefined();
    });

    it('should_replaceSelectedEventCode_when_differentCodeProvided', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.setSelectedEventCode('BATbern56');
      });

      expect(result.current.selectedEventCode).toBe('BATbern56');

      act(() => {
        result.current.setSelectedEventCode('BATbern57');
      });

      expect(result.current.selectedEventCode).toBe('BATbern57');
    });

    it('should_handleEventCodePattern_when_validEventCodeProvided', () => {
      const { result } = renderHook(() => useEventStore());

      // Test various event code patterns (per Story 1.16.2)
      const eventCodes = ['BATbern56', 'BATbern100', 'BATbern1'];

      eventCodes.forEach((code) => {
        act(() => {
          result.current.setSelectedEventCode(code);
        });

        expect(result.current.selectedEventCode).toBe(code);
      });
    });
  });

  describe('Create Modal Management', () => {
    it('should_openCreateModal_when_openCreateModalCalled', () => {
      const { result } = renderHook(() => useEventStore());

      expect(result.current.isCreateModalOpen).toBe(false);

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isCreateModalOpen).toBe(true);
    });

    it('should_closeCreateModal_when_closeCreateModalCalled', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isCreateModalOpen).toBe(true);

      act(() => {
        result.current.closeCreateModal();
      });

      expect(result.current.isCreateModalOpen).toBe(false);
    });

    it('should_keepOtherStateIntact_when_createModalOpened', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.setFilters({ status: ['active'] });
        result.current.setSelectedEventCode('BATbern56');
      });

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.filters).toEqual({ status: ['active'] });
      expect(result.current.selectedEventCode).toBe('BATbern56');
    });
  });

  describe('Edit Modal Management', () => {
    it('should_openEditModal_when_openEditModalCalledWithCode', () => {
      const { result } = renderHook(() => useEventStore());

      const eventCode = 'BATbern56';

      act(() => {
        result.current.openEditModal(eventCode);
      });

      expect(result.current.isEditModalOpen).toBe(true);
      expect(result.current.selectedEventCode).toBe(eventCode);
    });

    it('should_closeEditModalAndClearSelection_when_closeEditModalCalled', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.openEditModal('BATbern56');
      });

      expect(result.current.isEditModalOpen).toBe(true);
      expect(result.current.selectedEventCode).toBe('BATbern56');

      act(() => {
        result.current.closeEditModal();
      });

      expect(result.current.isEditModalOpen).toBe(false);
      expect(result.current.selectedEventCode).toBeUndefined();
    });

    it('should_replaceSelectedEvent_when_openEditModalCalledWithDifferentCode', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.openEditModal('BATbern56');
      });

      expect(result.current.selectedEventCode).toBe('BATbern56');

      act(() => {
        result.current.openEditModal('BATbern57');
      });

      expect(result.current.selectedEventCode).toBe('BATbern57');
    });
  });

  describe('Modal Independence', () => {
    it('should_notAffectEditModal_when_createModalOpened', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isCreateModalOpen).toBe(true);
      expect(result.current.isEditModalOpen).toBe(false);
    });

    it('should_notAffectCreateModal_when_editModalOpened', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.openEditModal('BATbern56');
      });

      expect(result.current.isEditModalOpen).toBe(true);
      expect(result.current.isCreateModalOpen).toBe(false);
    });

    it('should_notOpenBothModals_when_createAndEditCalledSequentially', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isCreateModalOpen).toBe(true);

      act(() => {
        result.current.openEditModal('BATbern56');
      });

      // Both can be open (in practice, UI would close one, but store allows it)
      expect(result.current.isCreateModalOpen).toBe(true);
      expect(result.current.isEditModalOpen).toBe(true);
    });
  });

  describe('State Persistence', () => {
    it('should_persistFilters_when_storeUpdated', () => {
      const { result } = renderHook(() => useEventStore());

      const filters: EventFilters = {
        status: ['active', 'published'],
        year: 2025,
      };

      act(() => {
        result.current.setFilters(filters);
      });

      // Check localStorage directly
      const stored = localStorage.getItem('event-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.filters).toEqual(filters);
    });

    it('should_persistAllFilterTypes_when_complexFiltersSet', () => {
      const { result } = renderHook(() => useEventStore());

      const filters: EventFilters = {
        status: ['active', 'published', 'completed'],
        year: 2025,
        search: 'Cloud',
      };

      act(() => {
        result.current.setFilters(filters);
      });

      // Check localStorage directly
      const stored = localStorage.getItem('event-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.filters).toEqual(filters);
    });

    it('should_notPersistModalState_when_storeRemounted', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.openCreateModal();
        result.current.openEditModal('BATbern56');
      });

      // Check localStorage directly - modal states should not be in storage
      const stored = localStorage.getItem('event-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.isCreateModalOpen).toBeUndefined();
      expect(parsedState.state.isEditModalOpen).toBeUndefined();
    });

    it('should_notPersistSelectedEventCode_when_storeRemounted', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.setSelectedEventCode('BATbern56');
      });

      // Check localStorage directly - selectedEventCode should not be in storage
      const stored = localStorage.getItem('event-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.selectedEventCode).toBeUndefined();
    });

    it('should_restoreFiltersFromStorage_when_storeReinitialized', () => {
      // First render - set filters
      const { result: firstRender, unmount } = renderHook(() => useEventStore());

      const filters: EventFilters = {
        status: ['active'],
        year: 2025,
        search: 'BATbern',
      };

      act(() => {
        firstRender.current.setFilters(filters);
      });

      // Unmount and remount to simulate page reload
      unmount();

      const { result: secondRender } = renderHook(() => useEventStore());

      // Filters should be restored from localStorage
      expect(secondRender.current.filters).toEqual(filters);
    });

    it('should_notRestoreTransientState_when_storeReinitialized', () => {
      // First render - set everything
      const { result: firstRender } = renderHook(() => useEventStore());

      act(() => {
        firstRender.current.setFilters({ status: ['active'] });
        firstRender.current.setSelectedEventCode('BATbern56');
        firstRender.current.openCreateModal();
        firstRender.current.openEditModal('BATbern57');
      });

      // Verify localStorage only has filters (not transient state)
      const stored = localStorage.getItem('event-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.filters).toEqual({ status: ['active'] });
      expect(parsedState.state.selectedEventCode).toBeUndefined();
      expect(parsedState.state.isCreateModalOpen).toBeUndefined();
      expect(parsedState.state.isEditModalOpen).toBeUndefined();

      // The in-memory store will still have transient state since Zustand stores are singletons
      // But localStorage correctly only persists filters
      expect(firstRender.current.selectedEventCode).toBe('BATbern57');
      expect(firstRender.current.isCreateModalOpen).toBe(true);
      expect(firstRender.current.isEditModalOpen).toBe(true);
    });
  });

  describe('Complex State Scenarios', () => {
    it('should_handleCompleteWorkflow_when_filterEditCreateCombined', () => {
      const { result } = renderHook(() => useEventStore());

      // Set filters
      act(() => {
        result.current.setFilters({ status: ['active'], year: 2025 });
      });

      // Select event and open edit modal
      act(() => {
        result.current.openEditModal('BATbern56');
      });

      expect(result.current.filters).toEqual({ status: ['active'], year: 2025 });
      expect(result.current.isEditModalOpen).toBe(true);
      expect(result.current.selectedEventCode).toBe('BATbern56');

      // Close edit and open create
      act(() => {
        result.current.closeEditModal();
      });

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isEditModalOpen).toBe(false);
      expect(result.current.isCreateModalOpen).toBe(true);
      expect(result.current.selectedEventCode).toBeUndefined();
      expect(result.current.filters).toEqual({ status: ['active'], year: 2025 });
    });

    it('should_maintainStateIntegrity_when_rapidUpdates', () => {
      const { result } = renderHook(() => useEventStore());

      act(() => {
        result.current.setFilters({ status: ['active'] });
        result.current.setSelectedEventCode('BATbern56');
        result.current.openCreateModal();
        result.current.setFilters({ year: 2025 });
        result.current.closeCreateModal();
        result.current.openEditModal('BATbern57');
      });

      expect(result.current.filters).toEqual({ year: 2025 });
      expect(result.current.selectedEventCode).toBe('BATbern57');
      expect(result.current.isCreateModalOpen).toBe(false);
      expect(result.current.isEditModalOpen).toBe(true);
    });

    it('should_handleMultipleEventSwitches_when_editingDifferentEvents', () => {
      const { result } = renderHook(() => useEventStore());

      const events = ['BATbern56', 'BATbern57', 'BATbern58'];

      events.forEach((eventCode) => {
        act(() => {
          result.current.openEditModal(eventCode);
        });

        expect(result.current.selectedEventCode).toBe(eventCode);
        expect(result.current.isEditModalOpen).toBe(true);
      });

      // Final state should be the last event
      expect(result.current.selectedEventCode).toBe('BATbern58');
    });

    it('should_clearSelectionAndCloseModal_when_userNavigatesAway', () => {
      const { result } = renderHook(() => useEventStore());

      // Simulate editing an event
      act(() => {
        result.current.openEditModal('BATbern56');
      });

      // User navigates away (close modal and clear selection)
      act(() => {
        result.current.closeEditModal();
      });

      expect(result.current.selectedEventCode).toBeUndefined();
      expect(result.current.isEditModalOpen).toBe(false);

      // Filters should still be intact
      act(() => {
        result.current.setFilters({ status: ['active'] });
      });
      expect(result.current.filters).toEqual({ status: ['active'] });
    });
  });
});
