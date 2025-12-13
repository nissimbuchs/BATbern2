/**
 * Event Management Zustand Store
 *
 * Story 2.5.3 - Task 6b (GREEN Phase)
 * AC18: State Management
 *
 * Manages UI state for event management feature:
 * - Filter state (persisted in localStorage)
 * - Selected event code (transient, not persisted) - Story 1.16.2: uses eventCode as identifier
 * - Modal states (transient, not persisted)
 *
 * Uses Zustand with persist middleware for filter persistence only.
 * Modal states and selected event are NOT persisted (transient UI state).
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { EventFilters } from '@/types/event.types';

export interface EventPagination {
  page: number;
  limit: number;
}

export interface EventStore {
  // State
  filters: EventFilters;
  pagination: EventPagination;
  selectedEventCode?: string;
  isCreateModalOpen: boolean;
  isEditModalOpen: boolean;

  // Actions
  setFilters: (filters: EventFilters) => void;
  setPagination: (pagination: Partial<EventPagination>) => void;
  setPage: (page: number) => void;
  setLimit: (limit: number) => void;
  setSelectedEventCode: (eventCode?: string) => void;
  openCreateModal: () => void;
  closeCreateModal: () => void;
  openEditModal: (eventCode: string) => void;
  closeEditModal: () => void;
}

const DEFAULT_PAGINATION: EventPagination = {
  page: 1,
  limit: 20,
};

export const useEventStore = create<EventStore>()(
  persist(
    (set) => ({
      // Initial state
      filters: {},
      pagination: DEFAULT_PAGINATION,
      selectedEventCode: undefined,
      isCreateModalOpen: false,
      isEditModalOpen: false,

      // Filter management
      setFilters: (filters: EventFilters) =>
        set((state) => ({
          filters,
          // Reset to page 1 when filters change
          pagination: { ...state.pagination, page: 1 },
        })),

      // Pagination management
      setPagination: (pagination: Partial<EventPagination>) =>
        set((state) => ({
          pagination: { ...state.pagination, ...pagination },
        })),

      setPage: (page: number) =>
        set((state) => ({
          pagination: { ...state.pagination, page },
        })),

      setLimit: (limit: number) =>
        set((state) => ({
          pagination: { ...state.pagination, limit, page: 1 }, // Reset to page 1 when limit changes
        })),

      // Selected event management (Story 1.16.2: uses eventCode as identifier)
      setSelectedEventCode: (eventCode?: string) => set({ selectedEventCode: eventCode }),

      // Create modal management
      openCreateModal: () => set({ isCreateModalOpen: true }),
      closeCreateModal: () => set({ isCreateModalOpen: false }),

      // Edit modal management
      openEditModal: (eventCode: string) =>
        set({
          selectedEventCode: eventCode,
          isEditModalOpen: true,
        }),
      closeEditModal: () =>
        set({
          isEditModalOpen: false,
          selectedEventCode: undefined,
        }),
    }),
    {
      name: 'event-store', // localStorage key
      // Partial persist: only persist filters (NOT modals, pagination, or selectedEventCode)
      partialize: (state) => ({
        filters: state.filters,
      }),
    }
  )
);
