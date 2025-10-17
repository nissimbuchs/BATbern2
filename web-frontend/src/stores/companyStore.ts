/**
 * Company Management Zustand Store
 *
 * Story 2.5.1 - Task 9b (GREEN Phase)
 * AC14: State Management
 *
 * Manages UI state for company management feature:
 * - Filter state (persisted in localStorage)
 * - View mode toggle (grid/list, persisted)
 * - Selected company ID (transient, not persisted)
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
      selectedCompanyId: undefined,
      isCreateModalOpen: false,
      isEditModalOpen: false,

      // Filter management
      setFilters: (filters: CompanyFilters) => set({ filters }),

      // View mode toggle
      toggleViewMode: () =>
        set((state) => ({
          viewMode: state.viewMode === 'grid' ? 'list' : 'grid',
        })),

      // Selected company management
      setSelectedCompanyId: (id?: string) => set({ selectedCompanyId: id }),

      // Create modal management
      openCreateModal: () => set({ isCreateModalOpen: true }),
      closeCreateModal: () => set({ isCreateModalOpen: false }),

      // Edit modal management
      openEditModal: (id: string) =>
        set({
          selectedCompanyId: id,
          isEditModalOpen: true,
        }),
      closeEditModal: () =>
        set({
          isEditModalOpen: false,
          selectedCompanyId: undefined,
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
