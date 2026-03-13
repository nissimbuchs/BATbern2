/**
 * Newsletter Subscriber Store Tests
 * Story 10.28: Newsletter Subscriber Management Page
 *
 * Tests for Zustand store managing subscriber list filters, pagination, and sort state
 */

import { describe, test, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useNewsletterSubscriberStore } from './newsletterSubscriberStore';

describe('Newsletter Subscriber Store', () => {
  beforeEach(() => {
    const { reset } = useNewsletterSubscriberStore.getState();
    act(() => {
      reset();
    });
  });

  describe('Initial State', () => {
    test('should_haveDefaultFilters_when_storeInitialized', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      expect(result.current.filters).toEqual({
        searchQuery: '',
        status: 'all',
        sortBy: 'subscribedAt',
        sortDir: 'desc',
      });
    });

    test('should_haveDefaultPagination_when_storeInitialized', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      expect(result.current.pagination).toEqual({
        page: 1,
        limit: 20,
      });
    });
  });

  describe('setSearchQuery Action', () => {
    test('should_updateSearchQuery_when_setSearchQueryCalled', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setSearchQuery('test@example.com');
      });

      expect(result.current.filters.searchQuery).toBe('test@example.com');
    });

    test('should_resetPage_when_searchQueryChanged', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setPage(3);
      });

      expect(result.current.pagination.page).toBe(3);

      act(() => {
        result.current.setSearchQuery('john');
      });

      expect(result.current.pagination.page).toBe(1);
    });
  });

  describe('setSort Action', () => {
    test('should_updateSortByAndDir_when_setSortCalled', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setSort('email', 'asc');
      });

      expect(result.current.filters.sortBy).toBe('email');
      expect(result.current.filters.sortDir).toBe('asc');
    });

    test('should_resetPage_when_sortChanged', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setPage(5);
      });

      expect(result.current.pagination.page).toBe(5);

      act(() => {
        result.current.setSort('email', 'asc');
      });

      expect(result.current.pagination.page).toBe(1);
    });
  });

  describe('setFilters Action', () => {
    test('should_mergeFilters_when_setFiltersCalled', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setFilters({ status: 'active' });
      });

      expect(result.current.filters.status).toBe('active');
      // Other filters preserved
      expect(result.current.filters.sortBy).toBe('subscribedAt');
    });

    test('should_resetPage_when_filtersChanged', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setPage(4);
      });

      act(() => {
        result.current.setFilters({ status: 'unsubscribed' });
      });

      expect(result.current.pagination.page).toBe(1);
    });
  });

  describe('setLimit Action', () => {
    test('should_updateLimit_when_setLimitCalled', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setLimit(50);
      });

      expect(result.current.pagination.limit).toBe(50);
    });

    test('should_resetPage_when_limitChanged', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setPage(5);
      });

      act(() => {
        result.current.setLimit(50);
      });

      expect(result.current.pagination.page).toBe(1);
    });
  });

  describe('setPage Action', () => {
    test('should_updatePage_when_setPageCalled', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setPage(7);
      });

      expect(result.current.pagination.page).toBe(7);
    });

    test('should_keepLimit_when_setPageCalled', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setPage(3);
      });

      expect(result.current.pagination.limit).toBe(20);
    });
  });

  describe('resetFilters Action', () => {
    test('should_clearAllFilters_when_resetFiltersCalled', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setSearchQuery('test');
        result.current.setFilters({ status: 'active' });
        result.current.setSort('email', 'asc');
        result.current.setPage(5);
      });

      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.filters).toEqual({
        searchQuery: '',
        status: 'all',
        sortBy: 'subscribedAt',
        sortDir: 'desc',
      });
      expect(result.current.pagination).toEqual({
        page: 1,
        limit: 20,
      });
    });
  });

  describe('reset Action', () => {
    test('should_resetAllState_when_resetCalled', () => {
      const { result } = renderHook(() => useNewsletterSubscriberStore());

      act(() => {
        result.current.setSearchQuery('test');
        result.current.setFilters({ status: 'active' });
        result.current.setSort('email', 'asc');
        result.current.setPage(5);
        result.current.setLimit(50);
      });

      act(() => {
        result.current.reset();
      });

      expect(result.current.filters).toEqual({
        searchQuery: '',
        status: 'all',
        sortBy: 'subscribedAt',
        sortDir: 'desc',
      });
      expect(result.current.pagination).toEqual({
        page: 1,
        limit: 20,
      });
    });
  });
});
