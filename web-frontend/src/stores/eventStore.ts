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

export interface EventStore {
  // State
  filters: EventFilters;
  selectedEventCode?: string;
  isCreateModalOpen: boolean;
  isEditModalOpen: boolean;

  // Actions
  setFilters: (filters: EventFilters) => void;
  setSelectedEventCode: (eventCode?: string) => void;
  openCreateModal: () => void;
  closeCreateModal: () => void;
  openEditModal: (eventCode: string) => void;
  closeEditModal: () => void;
}

export const useEventStore = create<EventStore>()(
  persist(
    (set) => ({
      // Initial state
      filters: {},
      selectedEventCode: undefined,
      isCreateModalOpen: false,
      isEditModalOpen: false,

      // Filter management
      setFilters: (filters: EventFilters) => set({ filters }),

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
      // Partial persist: only persist filters (NOT modals or selectedEventCode)
      partialize: (state) => ({
        filters: state.filters,
      }),
    }
  )
);
