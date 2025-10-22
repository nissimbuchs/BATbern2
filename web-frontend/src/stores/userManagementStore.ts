/**
 * User Management Store
 *
 * Zustand store for client-side state management of user list filters,
 * pagination, and selected user.
 *
 * Story 2.5.2: User Management Frontend
 */

import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import type { User, UserFilters, UserPagination } from '@/types/user.types';

interface UserManagementState {
  // State
  filters: UserFilters;
  pagination: UserPagination;
  selectedUser: User | null;
  searchQuery: string;

  // Actions
  setFilters: (filters: UserFilters) => void;
  setPagination: (pagination: Partial<UserPagination>) => void;
  setPage: (page: number) => void;
  setLimit: (limit: number) => void;
  setSelectedUser: (user: User | null) => void;
  setSearchQuery: (query: string) => void;
  resetFilters: () => void;
  resetPagination: () => void;
  reset: () => void;
}

const DEFAULT_PAGINATION_CONST: UserPagination = {
  page: 1,
  limit: 20,
};

const initialState = {
  filters: {},
  pagination: DEFAULT_PAGINATION_CONST,
  selectedUser: null,
  searchQuery: '',
};

export const useUserManagementStore = create<UserManagementState>()(
  devtools(
    (set) => ({
      ...initialState,

      setFilters: (filters: UserFilters) =>
        set(
          (state) => ({
            filters,
            // Reset to page 1 when filters change
            pagination: { ...state.pagination, page: 1 },
          }),
          false,
          'setFilters'
        ),

      setPagination: (pagination: Partial<UserPagination>) =>
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

      setSelectedUser: (user: User | null) => set({ selectedUser: user }, false, 'setSelectedUser'),

      setSearchQuery: (query: string) =>
        set(
          (state) => ({
            searchQuery: query,
            // Also update filters.search so it gets passed to API
            filters: { ...state.filters, search: query },
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

      resetPagination: () =>
        set({ pagination: DEFAULT_PAGINATION_CONST }, false, 'resetPagination'),

      reset: () => set(initialState, false, 'reset'),
    }),
    {
      name: 'user-management-store',
      enabled: import.meta.env.DEV,
    }
  )
);
