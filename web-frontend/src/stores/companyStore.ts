/**
 * Company Management Zustand Store
 *
 * Story 2.5.1 - Task 9b (GREEN Phase)
 * AC14: State Management
 *
 * Manages UI state for company management feature:
 * - Filter state (persisted in localStorage)
 * - View mode toggle (grid/list, persisted)
 * - Selected company name (transient, not persisted) - Story 1.16.2: uses company name as identifier
 * - Modal states (transient, not persisted)
 *
 * Uses Zustand with persist middleware for filter and view mode persistence
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { CompanyStore, CompanyFilters } from '@/types/company.types';

export const useCompanyStore = create<CompanyStore>()(
  persist(
    (set) => ({
      // Initial state
      filters: {},
      viewMode: 'grid',
      selectedCompanyName: undefined,
      isCreateModalOpen: false,
      isEditModalOpen: false,

      // Filter management
      setFilters: (filters: CompanyFilters) => set({ filters }),

      // View mode toggle
      toggleViewMode: () =>
        set((state) => ({
          viewMode: state.viewMode === 'grid' ? 'list' : 'grid',
        })),

      // Selected company management (Story 1.16.2: uses company name as identifier)
      setSelectedCompanyName: (name?: string) => set({ selectedCompanyName: name }),

      // Create modal management
      openCreateModal: () => set({ isCreateModalOpen: true }),
      closeCreateModal: () => set({ isCreateModalOpen: false }),

      // Edit modal management
      openEditModal: (name: string) =>
        set({
          selectedCompanyName: name,
          isEditModalOpen: true,
        }),
      closeEditModal: () =>
        set({
          isEditModalOpen: false,
          selectedCompanyName: undefined,
        }),
    }),
    {
      name: 'company-store', // localStorage key
      // Partial persist: only persist filters and viewMode
      partialize: (state) => ({
        filters: state.filters,
        viewMode: state.viewMode,
      }),
    }
  )
);
