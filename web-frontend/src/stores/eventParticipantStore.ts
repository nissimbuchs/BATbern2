/**
 * Event Participant Store
 *
 * Zustand store for client-side state management of event participants list
 * filters, pagination, and search query.
 *
 * Story 3.3: Event Participants Tab
 */

import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import type { ParticipantFilters, ParticipantPagination } from '@/types/eventParticipant.types';

interface EventParticipantState {
  // State
  filters: ParticipantFilters;
  pagination: ParticipantPagination;
  searchQuery: string;

  // Actions
  setFilters: (filters: ParticipantFilters) => void;
  setPagination: (pagination: Partial<ParticipantPagination>) => void;
  setPage: (page: number) => void;
  setLimit: (limit: number) => void;
  setSearchQuery: (query: string) => void;
  resetFilters: () => void;
  resetPagination: () => void;
  reset: () => void;
}

const DEFAULT_PAGINATION: ParticipantPagination = {
  page: 1,
  limit: 25,
};

const initialState = {
  filters: {},
  pagination: DEFAULT_PAGINATION,
  searchQuery: '',
};

export const useEventParticipantStore = create<EventParticipantState>()(
  devtools(
    (set) => ({
      ...initialState,

      setFilters: (filters: ParticipantFilters) =>
        set(
          (state) => ({
            filters,
            // Reset to page 1 when filters change
            pagination: { ...state.pagination, page: 1 },
          }),
          false,
          'setFilters'
        ),

      setPagination: (pagination: Partial<ParticipantPagination>) =>
        set(
          (state) => ({
            pagination: { ...state.pagination, ...pagination },
          }),
          false,
          'setPagination'
        ),

      setPage: (page: number) =>
        set(
          (state) => ({
            pagination: { ...state.pagination, page },
          }),
          false,
          'setPage'
        ),

      setLimit: (limit: number) =>
        set(
          (state) => ({
            pagination: { ...state.pagination, limit, page: 1 }, // Reset to page 1 on limit change
          }),
          false,
          'setLimit'
        ),

      setSearchQuery: (query: string) =>
        set(
          (state) => ({
            searchQuery: query,
            // Reset to page 1 when search query changes
            pagination: { ...state.pagination, page: 1 },
          }),
          false,
          'setSearchQuery'
        ),

      resetFilters: () =>
        set(
          (state) => ({
            filters: {},
            searchQuery: '',
            pagination: { ...state.pagination, page: 1 },
          }),
          false,
          'resetFilters'
        ),

      resetPagination: () => set({ pagination: DEFAULT_PAGINATION }, false, 'resetPagination'),

      reset: () => set(initialState, false, 'reset'),
    }),
    {
      name: 'event-participant-store',
      enabled: import.meta.env.DEV,
    }
  )
);
