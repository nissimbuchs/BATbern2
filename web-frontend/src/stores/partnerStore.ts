/**
 * Partner Management Zustand Store
 *
 * Story 2.8.1 - Task 3b (GREEN Phase)
 * AC1: Partner Directory Screen - State Management
 *
 * Manages UI state for partner management feature:
 * - Filter state (tier, status) - transient, not persisted
 * - View mode (grid/list) - persisted in localStorage
 * - Search query - transient, not persisted
 * - Sort state (sortBy, sortOrder) - transient, not persisted
 * - Page state - transient, not persisted
 *
 * Uses Zustand with persist middleware for viewMode persistence only.
 * Filter, search, sort, and page states are NOT persisted (transient UI state).
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { PartnershipLevel } from '@/services/api/partnerApi';

/**
 * Filter state interface
 */
export interface PartnerFilters {
  tier: PartnershipLevel | 'all';
  status: 'all' | 'active' | 'inactive';
}

/**
 * Partner store interface
 */
export interface PartnerStore {
  // State
  filters: PartnerFilters;
  viewMode: 'grid' | 'list';
  searchQuery: string;
  sortBy: 'engagement' | 'name' | 'tier' | 'lastEvent';
  sortOrder: 'asc' | 'desc';
  page: number;

  // Actions
  setFilters: (filters: Partial<PartnerFilters>) => void;
  setViewMode: (mode: 'grid' | 'list') => void;
  setSearchQuery: (query: string) => void;
  setSortBy: (sortBy: PartnerStore['sortBy']) => void;
  setSortOrder: (order: 'asc' | 'desc') => void;
  setPage: (page: number) => void;
  resetFilters: () => void;
}

/**
 * Partner Management Store
 *
 * Persists only viewMode to localStorage.
 * All other state (filters, search, sort, page) is transient.
 */
export const usePartnerStore = create<PartnerStore>()(
  persist(
    (set) => ({
      // Initial state
      filters: { tier: 'all', status: 'all' },
      viewMode: 'grid',
      searchQuery: '',
      sortBy: 'engagement',
      sortOrder: 'desc',
      page: 0,

      // Filter management (resets page to 0)
      setFilters: (newFilters: Partial<PartnerFilters>) =>
        set((state) => ({
          filters: { ...state.filters, ...newFilters },
          page: 0, // Reset to first page on filter change
        })),

      // View mode management (persisted)
      setViewMode: (mode: 'grid' | 'list') => set({ viewMode: mode }),

      // Search query management (resets page to 0)
      setSearchQuery: (query: string) => set({ searchQuery: query, page: 0 }),

      // Sort management (resets page to 0)
      setSortBy: (sortBy: PartnerStore['sortBy']) => set({ sortBy, page: 0 }),
      setSortOrder: (order: 'asc' | 'desc') => set({ sortOrder: order }),

      // Page management
      setPage: (page: number) => set({ page }),

      // Reset filters and search (does not reset viewMode)
      resetFilters: () =>
        set({
          filters: { tier: 'all', status: 'all' },
          searchQuery: '',
          page: 0,
        }),
    }),
    {
      name: 'partner-store', // localStorage key
      // Partial persist: only persist viewMode (NOT filters, search, sort, or page)
      partialize: (state) => ({
        viewMode: state.viewMode,
      }),
    }
  )
);
