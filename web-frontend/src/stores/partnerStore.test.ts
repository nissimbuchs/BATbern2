/**
 * Tests for Partner Management Zustand Store
 *
 * Story 2.8.1 - Task 3a (RED Phase)
 * Test Specifications: AC1 - Partner Directory Screen
 *
 * Tests for:
 * - Initial state
 * - Filter state management (tier, status)
 * - View mode management (grid/list with localStorage persistence)
 * - Search query management
 * - Sort state management (sortBy, sortOrder)
 * - Page state management
 * - State persistence (only viewMode persists in localStorage)
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePartnerStore } from './partnerStore';

describe('partnerStore (RED Phase - Task 3a)', () => {
  // Reset store and localStorage before each test
  beforeEach(() => {
    // Clear localStorage to reset persisted state
    localStorage.clear();

    // Reset store state to initial values
    const { result } = renderHook(() => usePartnerStore());
    act(() => {
      result.current.resetFilters();
      result.current.setViewMode('grid');
      result.current.setSearchQuery('');
      result.current.setSortBy('engagement');
      result.current.setSortOrder('desc');
      result.current.setPage(0);
    });
  });

  afterEach(() => {
    // Clean up localStorage after each test
    localStorage.clear();
  });

  describe('Initial State', () => {
    it('should_haveDefaultFilters_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerStore());

      expect(result.current.filters).toEqual({
        tier: 'all',
        status: 'all',
      });
    });

    it('should_haveGridViewMode_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerStore());

      expect(result.current.viewMode).toBe('grid');
    });

    it('should_haveEmptySearchQuery_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerStore());

      expect(result.current.searchQuery).toBe('');
    });

    it('should_haveDefaultSort_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerStore());

      expect(result.current.sortBy).toBe('engagement');
      expect(result.current.sortOrder).toBe('desc');
    });

    it('should_havePageZero_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerStore());

      expect(result.current.page).toBe(0);
    });
  });

  describe('AC1: Test 3.1 - should_updateFilters_when_setFiltersActionCalled', () => {
    it('should update tier filter', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setFilters({ tier: 'gold' });
      });

      expect(result.current.filters.tier).toBe('gold');
    });

    it('should update status filter', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setFilters({ status: 'active' });
      });

      expect(result.current.filters.status).toBe('active');
    });

    it('should update multiple filters at once', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setFilters({ tier: 'platinum', status: 'inactive' });
      });

      expect(result.current.filters.tier).toBe('platinum');
      expect(result.current.filters.status).toBe('inactive');
    });

    it('should merge filters when called multiple times', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setFilters({ tier: 'gold' });
      });

      act(() => {
        result.current.setFilters({ status: 'active' });
      });

      expect(result.current.filters.tier).toBe('gold');
      expect(result.current.filters.status).toBe('active');
    });

    it('should reset page to 0 when filters change', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setPage(5);
      });

      expect(result.current.page).toBe(5);

      act(() => {
        result.current.setFilters({ tier: 'silver' });
      });

      expect(result.current.page).toBe(0);
    });
  });

  describe('AC1: Test 3.2 - should_updateViewMode_when_toggleViewModeActionCalled', () => {
    it('should switch to list view', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setViewMode('list');
      });

      expect(result.current.viewMode).toBe('list');
    });

    it('should switch to grid view', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setViewMode('list');
      });

      act(() => {
        result.current.setViewMode('grid');
      });

      expect(result.current.viewMode).toBe('grid');
    });

    it('should persist viewMode in localStorage', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setViewMode('list');
      });

      // Check localStorage directly
      const stored = localStorage.getItem('partner-store');
      expect(stored).toBeTruthy();
      
      if (stored) {
        const parsed = JSON.parse(stored);
        expect(parsed.state.viewMode).toBe('list');
      }
    });
  });

  describe('AC1: Test 3.3 - should_updateSearchQuery_when_setSearchQueryActionCalled', () => {
    it('should update search query', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setSearchQuery('TechCorp');
      });

      expect(result.current.searchQuery).toBe('TechCorp');
    });

    it('should clear search query', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setSearchQuery('TechCorp');
      });

      act(() => {
        result.current.setSearchQuery('');
      });

      expect(result.current.searchQuery).toBe('');
    });

    it('should reset page to 0 when search query changes', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setPage(3);
      });

      act(() => {
        result.current.setSearchQuery('CompanyName');
      });

      expect(result.current.page).toBe(0);
    });
  });

  describe('AC1: Test 3.4 - should_updateSortBy_when_setSortByActionCalled', () => {
    it('should update sortBy to name', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setSortBy('name');
      });

      expect(result.current.sortBy).toBe('name');
    });

    it('should update sortBy to tier', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setSortBy('tier');
      });

      expect(result.current.sortBy).toBe('tier');
    });

    it('should update sortBy to lastEvent', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setSortBy('lastEvent');
      });

      expect(result.current.sortBy).toBe('lastEvent');
    });

    it('should reset page to 0 when sortBy changes', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setPage(2);
      });

      act(() => {
        result.current.setSortBy('name');
      });

      expect(result.current.page).toBe(0);
    });

    it('should update sortOrder', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setSortOrder('asc');
      });

      expect(result.current.sortOrder).toBe('asc');
    });
  });

  describe('AC1: Test 3.5 - should_updatePage_when_setPageActionCalled', () => {
    it('should update page number', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setPage(5);
      });

      expect(result.current.page).toBe(5);
    });

    it('should reset page to 0', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setPage(10);
      });

      act(() => {
        result.current.setPage(0);
      });

      expect(result.current.page).toBe(0);
    });
  });

  describe('Reset Functionality', () => {
    it('should reset all filters to default', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setFilters({ tier: 'gold', status: 'active' });
        result.current.setSearchQuery('TechCorp');
        result.current.setPage(5);
      });

      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.filters).toEqual({ tier: 'all', status: 'all' });
      expect(result.current.searchQuery).toBe('');
      expect(result.current.page).toBe(0);
    });

    it('should not reset viewMode when resetFilters is called', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setViewMode('list');
        result.current.setFilters({ tier: 'gold' });
      });

      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.viewMode).toBe('list');
    });
  });

  describe('LocalStorage Persistence', () => {
    it('should only persist viewMode in localStorage', () => {
      const { result } = renderHook(() => usePartnerStore());

      act(() => {
        result.current.setViewMode('list');
        result.current.setFilters({ tier: 'gold', status: 'active' });
        result.current.setSearchQuery('Test');
        result.current.setPage(3);
      });

      // Check localStorage directly - should only contain viewMode
      const stored = localStorage.getItem('partner-store');
      expect(stored).toBeTruthy();
      
      if (stored) {
        const parsed = JSON.parse(stored);
        
        // Should have viewMode
        expect(parsed.state.viewMode).toBe('list');
        
        // Should NOT have filters, searchQuery, page, sortBy, sortOrder
        expect(parsed.state.filters).toBeUndefined();
        expect(parsed.state.searchQuery).toBeUndefined();
        expect(parsed.state.page).toBeUndefined();
        expect(parsed.state.sortBy).toBeUndefined();
        expect(parsed.state.sortOrder).toBeUndefined();
      }
    });
  });
});
