/**
 * Tests for Company Management Zustand Store
 *
 * Story 2.5.1 - Task 9a (RED Phase)
 * Test Specifications: AC14 - State Management
 *
 * Tests for:
 * - Initial state
 * - Filter state management
 * - View mode toggle (grid/list)
 * - Selected company ID management
 * - Modal state management (create/edit)
 * - State persistence (via zustand persist middleware)
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCompanyStore } from '@/stores/companyStore';
import { CompanyFilters } from '@/types/company.types';

describe('companyStore', () => {
  // Reset store and localStorage before each test
  beforeEach(() => {
    // Clear localStorage to reset persisted state
    localStorage.clear();

    // Reset store state to initial values
    const { result } = renderHook(() => useCompanyStore());
    act(() => {
      result.current.setFilters({});
      result.current.closeCreateModal();
      result.current.closeEditModal();
      result.current.setSelectedCompanyId(undefined);
      // Ensure viewMode is reset to grid
      if (result.current.viewMode === 'list') {
        result.current.toggleViewMode();
      }
    });
  });

  afterEach(() => {
    // Clean up localStorage after each test
    localStorage.clear();
  });

  describe('Initial State', () => {
    it('should_haveEmptyFilters_when_storeInitialized', () => {
      const { result } = renderHook(() => useCompanyStore());

      expect(result.current.filters).toEqual({});
    });

    it('should_haveGridViewMode_when_storeInitialized', () => {
      const { result } = renderHook(() => useCompanyStore());

      expect(result.current.viewMode).toBe('grid');
    });

    it('should_haveNoSelectedCompany_when_storeInitialized', () => {
      const { result } = renderHook(() => useCompanyStore());

      expect(result.current.selectedCompanyId).toBeUndefined();
    });

    it('should_haveModalsClosedByDefault_when_storeInitialized', () => {
      const { result } = renderHook(() => useCompanyStore());

      expect(result.current.isCreateModalOpen).toBe(false);
      expect(result.current.isEditModalOpen).toBe(false);
    });
  });

  describe('Filter Management', () => {
    it('should_updateFilters_when_setFiltersCalled', () => {
      const { result } = renderHook(() => useCompanyStore());

      const newFilters: CompanyFilters = {
        isPartner: true,
        industry: 'Cloud Computing',
      };

      act(() => {
        result.current.setFilters(newFilters);
      });

      expect(result.current.filters).toEqual(newFilters);
    });

    it('should_replaceFilters_when_setFiltersCalledMultipleTimes', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.setFilters({ isPartner: true });
      });

      expect(result.current.filters).toEqual({ isPartner: true });

      act(() => {
        result.current.setFilters({ industry: 'DevOps' });
      });

      expect(result.current.filters).toEqual({ industry: 'DevOps' });
    });

    it('should_clearFilters_when_setFiltersCalledWithEmptyObject', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.setFilters({ isPartner: true, industry: 'Cloud Computing' });
      });

      act(() => {
        result.current.setFilters({});
      });

      expect(result.current.filters).toEqual({});
    });

    it('should_handleMultipleFilters_when_allFiltersSet', () => {
      const { result } = renderHook(() => useCompanyStore());

      const filters: CompanyFilters = {
        isPartner: true,
        isVerified: true,
        industry: 'Financial Services',
        searchQuery: 'Swiss',
      };

      act(() => {
        result.current.setFilters(filters);
      });

      expect(result.current.filters).toEqual(filters);
    });
  });

  describe('View Mode Toggle', () => {
    it('should_toggleToListView_when_initiallyGrid', () => {
      const { result } = renderHook(() => useCompanyStore());

      expect(result.current.viewMode).toBe('grid');

      act(() => {
        result.current.toggleViewMode();
      });

      expect(result.current.viewMode).toBe('list');
    });

    it('should_toggleToGridView_when_currentlyList', () => {
      const { result } = renderHook(() => useCompanyStore());

      // First toggle to list
      act(() => {
        result.current.toggleViewMode();
      });

      expect(result.current.viewMode).toBe('list');

      // Toggle back to grid
      act(() => {
        result.current.toggleViewMode();
      });

      expect(result.current.viewMode).toBe('grid');
    });

    it('should_alternateViewMode_when_toggledMultipleTimes', () => {
      const { result } = renderHook(() => useCompanyStore());

      expect(result.current.viewMode).toBe('grid');

      act(() => {
        result.current.toggleViewMode();
      });
      expect(result.current.viewMode).toBe('list');

      act(() => {
        result.current.toggleViewMode();
      });
      expect(result.current.viewMode).toBe('grid');

      act(() => {
        result.current.toggleViewMode();
      });
      expect(result.current.viewMode).toBe('list');
    });
  });

  describe('Selected Company Management', () => {
    it('should_setSelectedCompanyId_when_idProvided', () => {
      const { result } = renderHook(() => useCompanyStore());

      const companyId = 'company-123';

      act(() => {
        result.current.setSelectedCompanyId(companyId);
      });

      expect(result.current.selectedCompanyId).toBe(companyId);
    });

    it('should_clearSelectedCompanyId_when_undefinedProvided', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.setSelectedCompanyId('company-123');
      });

      expect(result.current.selectedCompanyId).toBe('company-123');

      act(() => {
        result.current.setSelectedCompanyId(undefined);
      });

      expect(result.current.selectedCompanyId).toBeUndefined();
    });

    it('should_replaceSelectedCompanyId_when_differentIdProvided', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.setSelectedCompanyId('company-123');
      });

      expect(result.current.selectedCompanyId).toBe('company-123');

      act(() => {
        result.current.setSelectedCompanyId('company-456');
      });

      expect(result.current.selectedCompanyId).toBe('company-456');
    });
  });

  describe('Create Modal Management', () => {
    it('should_openCreateModal_when_openCreateModalCalled', () => {
      const { result } = renderHook(() => useCompanyStore());

      expect(result.current.isCreateModalOpen).toBe(false);

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isCreateModalOpen).toBe(true);
    });

    it('should_closeCreateModal_when_closeCreateModalCalled', () => {
      const { result } = renderHook(() => useCompanyStore());

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
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.setFilters({ isPartner: true });
        result.current.setSelectedCompanyId('company-123');
      });

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.filters).toEqual({ isPartner: true });
      expect(result.current.selectedCompanyId).toBe('company-123');
    });
  });

  describe('Edit Modal Management', () => {
    it('should_openEditModal_when_openEditModalCalledWithId', () => {
      const { result } = renderHook(() => useCompanyStore());

      const companyId = 'company-123';

      act(() => {
        result.current.openEditModal(companyId);
      });

      expect(result.current.isEditModalOpen).toBe(true);
      expect(result.current.selectedCompanyId).toBe(companyId);
    });

    it('should_closeEditModalAndClearSelection_when_closeEditModalCalled', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.openEditModal('company-123');
      });

      expect(result.current.isEditModalOpen).toBe(true);
      expect(result.current.selectedCompanyId).toBe('company-123');

      act(() => {
        result.current.closeEditModal();
      });

      expect(result.current.isEditModalOpen).toBe(false);
      expect(result.current.selectedCompanyId).toBeUndefined();
    });

    it('should_replaceSelectedCompany_when_openEditModalCalledWithDifferentId', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.openEditModal('company-123');
      });

      expect(result.current.selectedCompanyId).toBe('company-123');

      act(() => {
        result.current.openEditModal('company-456');
      });

      expect(result.current.selectedCompanyId).toBe('company-456');
    });
  });

  describe('Modal Independence', () => {
    it('should_notAffectEditModal_when_createModalOpened', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isCreateModalOpen).toBe(true);
      expect(result.current.isEditModalOpen).toBe(false);
    });

    it('should_notAffectCreateModal_when_editModalOpened', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.openEditModal('company-123');
      });

      expect(result.current.isEditModalOpen).toBe(true);
      expect(result.current.isCreateModalOpen).toBe(false);
    });
  });

  describe('State Persistence', () => {
    it('should_persistFilters_when_storeUpdated', () => {
      const { result } = renderHook(() => useCompanyStore());

      const filters: CompanyFilters = {
        isPartner: true,
        industry: 'Cloud Computing',
      };

      act(() => {
        result.current.setFilters(filters);
      });

      // Check localStorage directly
      const stored = localStorage.getItem('company-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.filters).toEqual(filters);
    });

    it('should_persistViewMode_when_toggled', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.toggleViewMode();
      });

      // Check localStorage directly
      const stored = localStorage.getItem('company-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.viewMode).toBe('list');
    });

    it('should_notPersistModalState_when_storeRemounted', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.openCreateModal();
        result.current.openEditModal('company-123');
      });

      // Check localStorage directly - modal states should not be in storage
      const stored = localStorage.getItem('company-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.isCreateModalOpen).toBeUndefined();
      expect(parsedState.state.isEditModalOpen).toBeUndefined();
    });

    it('should_notPersistSelectedCompanyId_when_storeRemounted', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.setSelectedCompanyId('company-123');
      });

      // Check localStorage directly - selectedCompanyId should not be in storage
      const stored = localStorage.getItem('company-store');
      expect(stored).toBeTruthy();
      const parsedState = JSON.parse(stored!);
      expect(parsedState.state.selectedCompanyId).toBeUndefined();
    });
  });

  describe('Complex State Scenarios', () => {
    it('should_handleCompleteWorkflow_when_filterEditCreateCombined', () => {
      const { result } = renderHook(() => useCompanyStore());

      // Set filters
      act(() => {
        result.current.setFilters({ isPartner: true, industry: 'DevOps' });
      });

      // Select company and open edit modal
      act(() => {
        result.current.openEditModal('company-123');
      });

      expect(result.current.filters).toEqual({ isPartner: true, industry: 'DevOps' });
      expect(result.current.isEditModalOpen).toBe(true);
      expect(result.current.selectedCompanyId).toBe('company-123');

      // Close edit and open create
      act(() => {
        result.current.closeEditModal();
      });

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isEditModalOpen).toBe(false);
      expect(result.current.isCreateModalOpen).toBe(true);
      expect(result.current.selectedCompanyId).toBeUndefined();
      expect(result.current.filters).toEqual({ isPartner: true, industry: 'DevOps' });
    });

    it('should_maintainStateIntegrity_when_rapidUpdates', () => {
      const { result } = renderHook(() => useCompanyStore());

      act(() => {
        result.current.setFilters({ isPartner: true });
        result.current.toggleViewMode();
        result.current.setSelectedCompanyId('company-123');
        result.current.openCreateModal();
        result.current.setFilters({ industry: 'Cloud Computing' });
        result.current.toggleViewMode();
        result.current.closeCreateModal();
      });

      expect(result.current.filters).toEqual({ industry: 'Cloud Computing' });
      expect(result.current.viewMode).toBe('grid'); // Toggled twice
      expect(result.current.selectedCompanyId).toBe('company-123');
      expect(result.current.isCreateModalOpen).toBe(false);
    });
  });
});
