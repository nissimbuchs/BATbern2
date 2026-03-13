/**
 * Newsletter Subscriber Store
 *
 * Zustand store for client-side state management of subscriber list filters,
 * pagination, and sort state.
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { create } from 'zustand';
import { devtools } from 'zustand/middleware';

export interface NewsletterSubscriberFilters {
  searchQuery?: string;
  status?: 'active' | 'unsubscribed' | 'all';
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

interface NewsletterSubscriberPagination {
  page: number;
  limit: number;
}

interface NewsletterSubscriberState {
  // State
  filters: NewsletterSubscriberFilters;
  pagination: NewsletterSubscriberPagination;

  // Actions
  setFilters: (filters: NewsletterSubscriberFilters) => void;
  setSearchQuery: (query: string) => void;
  setSort: (field: string, dir: 'asc' | 'desc') => void;
  setPage: (page: number) => void;
  setLimit: (limit: number) => void;
  resetFilters: () => void;
  reset: () => void;
}

const DEFAULT_FILTERS: NewsletterSubscriberFilters = {
  searchQuery: '',
  status: 'all',
  sortBy: 'subscribedAt',
  sortDir: 'desc',
};

const DEFAULT_PAGINATION: NewsletterSubscriberPagination = {
  page: 1,
  limit: 20,
};

const initialState = {
  filters: DEFAULT_FILTERS,
  pagination: DEFAULT_PAGINATION,
};

export const useNewsletterSubscriberStore = create<NewsletterSubscriberState>()(
  devtools(
    (set) => ({
      ...initialState,

      setFilters: (filters: NewsletterSubscriberFilters) =>
        set(
          (state) => ({
            filters: { ...state.filters, ...filters },
            pagination: { ...state.pagination, page: 1 },
          }),
          false,
          'setFilters'
        ),

      setSearchQuery: (query: string) =>
        set(
          (state) => ({
            filters: { ...state.filters, searchQuery: query },
            pagination: { ...state.pagination, page: 1 },
          }),
          false,
          'setSearchQuery'
        ),

      setSort: (field: string, dir: 'asc' | 'desc') =>
        set(
          (state) => ({
            filters: { ...state.filters, sortBy: field, sortDir: dir },
            pagination: { ...state.pagination, page: 1 },
          }),
          false,
          'setSort'
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
            pagination: { ...state.pagination, limit, page: 1 },
          }),
          false,
          'setLimit'
        ),

      resetFilters: () =>
        set(
          () => ({
            filters: DEFAULT_FILTERS,
            pagination: DEFAULT_PAGINATION,
          }),
          false,
          'resetFilters'
        ),

      reset: () => set(initialState, false, 'reset'),
    }),
    {
      name: 'newsletter-subscriber-store',
      enabled: import.meta.env.DEV,
    }
  )
);
