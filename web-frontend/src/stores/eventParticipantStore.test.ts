/**
 * Event Participant Store Tests
 *
 * TDD Tests for Zustand store managing event participants list state
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useEventParticipantStore } from './eventParticipantStore';
import type { RegistrationStatus } from '@/types/eventParticipant.types';

describe('eventParticipantStore', () => {
  beforeEach(() => {
    // Reset store to initial state before each test
    const { result } = renderHook(() => useEventParticipantStore());
    act(() => {
      result.current.reset();
    });
  });

  describe('Initial State', () => {
    it('should have default initial state', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      expect(result.current.filters).toEqual({});
      expect(result.current.pagination).toEqual({ page: 1, limit: 25 });
      expect(result.current.searchQuery).toBe('');
    });
  });

  describe('setFilters', () => {
    it('should update filters', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      act(() => {
        result.current.setFilters({ status: ['CONFIRMED'] });
      });

      expect(result.current.filters).toEqual({ status: ['CONFIRMED'] });
    });

    it('should reset to page 1 when filters change', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Set to page 2
      act(() => {
        result.current.setPage(2);
      });

      expect(result.current.pagination.page).toBe(2);

      // Change filters - should reset to page 1
      act(() => {
        result.current.setFilters({ status: ['ATTENDED'] });
      });

      expect(result.current.pagination.page).toBe(1);
      expect(result.current.filters).toEqual({ status: ['ATTENDED'] });
    });

    it('should handle multiple status filters', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      const statuses: RegistrationStatus[] = ['CONFIRMED', 'ATTENDED'];

      act(() => {
        result.current.setFilters({ status: statuses });
      });

      expect(result.current.filters.status).toEqual(statuses);
    });

    it('should handle company filter', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      act(() => {
        result.current.setFilters({ companyId: 'centris-ag' });
      });

      expect(result.current.filters.companyId).toBe('centris-ag');
    });
  });

  describe('setPagination', () => {
    it('should update pagination', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      act(() => {
        result.current.setPagination({ page: 3, limit: 50 });
      });

      expect(result.current.pagination).toEqual({ page: 3, limit: 50 });
    });

    it('should merge partial pagination updates', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Set initial pagination
      act(() => {
        result.current.setPagination({ page: 2, limit: 50 });
      });

      // Update only page
      act(() => {
        result.current.setPagination({ page: 4 });
      });

      expect(result.current.pagination).toEqual({ page: 4, limit: 50 });
    });
  });

  describe('setPage', () => {
    it('should update page number', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      act(() => {
        result.current.setPage(5);
      });

      expect(result.current.pagination.page).toBe(5);
      expect(result.current.pagination.limit).toBe(25); // Should preserve limit
    });
  });

  describe('setLimit', () => {
    it('should update limit', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      act(() => {
        result.current.setLimit(100);
      });

      expect(result.current.pagination.limit).toBe(100);
    });

    it('should reset to page 1 when limit changes', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Go to page 3
      act(() => {
        result.current.setPage(3);
      });

      expect(result.current.pagination.page).toBe(3);

      // Change limit - should reset to page 1
      act(() => {
        result.current.setLimit(50);
      });

      expect(result.current.pagination).toEqual({ page: 1, limit: 50 });
    });
  });

  describe('setSearchQuery', () => {
    it('should update search query', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      act(() => {
        result.current.setSearchQuery('john doe');
      });

      expect(result.current.searchQuery).toBe('john doe');
    });

    it('should reset to page 1 when search query changes', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Go to page 2
      act(() => {
        result.current.setPage(2);
      });

      // Change search query - should reset to page 1
      act(() => {
        result.current.setSearchQuery('alice');
      });

      expect(result.current.pagination.page).toBe(1);
      expect(result.current.searchQuery).toBe('alice');
    });
  });

  describe('resetFilters', () => {
    it('should reset filters to empty object', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Set some filters
      act(() => {
        result.current.setFilters({ status: ['CONFIRMED'], companyId: 'test' });
        result.current.setSearchQuery('test query');
      });

      expect(result.current.filters).toEqual({ status: ['CONFIRMED'], companyId: 'test' });
      expect(result.current.searchQuery).toBe('test query');

      // Reset filters
      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.filters).toEqual({});
      expect(result.current.searchQuery).toBe('');
    });

    it('should reset to page 1 when filters are reset', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Set filters and go to page 3
      act(() => {
        result.current.setFilters({ status: ['ATTENDED'] });
        result.current.setPage(3);
      });

      expect(result.current.pagination.page).toBe(3);

      // Reset filters
      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.pagination.page).toBe(1);
    });

    it('should preserve pagination limit when resetting filters', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Set custom limit
      act(() => {
        result.current.setLimit(100);
        result.current.setFilters({ status: ['CONFIRMED'] });
      });

      // Reset filters
      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.pagination.limit).toBe(100);
      expect(result.current.filters).toEqual({});
    });
  });

  describe('resetPagination', () => {
    it('should reset pagination to defaults', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Change pagination
      act(() => {
        result.current.setPagination({ page: 5, limit: 100 });
      });

      expect(result.current.pagination).toEqual({ page: 5, limit: 100 });

      // Reset pagination
      act(() => {
        result.current.resetPagination();
      });

      expect(result.current.pagination).toEqual({ page: 1, limit: 25 });
    });

    it('should preserve filters when resetting pagination', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Set filters
      act(() => {
        result.current.setFilters({ status: ['CONFIRMED'] });
        result.current.setPagination({ page: 3, limit: 50 });
      });

      // Reset pagination
      act(() => {
        result.current.resetPagination();
      });

      expect(result.current.filters).toEqual({ status: ['CONFIRMED'] });
      expect(result.current.pagination).toEqual({ page: 1, limit: 25 });
    });
  });

  describe('reset', () => {
    it('should reset entire store to initial state', () => {
      const { result } = renderHook(() => useEventParticipantStore());

      // Set various state (set search query first to avoid auto-reset)
      act(() => {
        result.current.setSearchQuery('test search');
        result.current.setFilters({ status: ['ATTENDED'], companyId: 'test' });
        result.current.setPagination({ page: 4, limit: 100 });
      });

      expect(result.current.filters).toEqual({ status: ['ATTENDED'], companyId: 'test' });
      expect(result.current.pagination).toEqual({ page: 4, limit: 100 });
      expect(result.current.searchQuery).toBe('test search');

      // Reset all
      act(() => {
        result.current.reset();
      });

      expect(result.current.filters).toEqual({});
      expect(result.current.pagination).toEqual({ page: 1, limit: 25 });
      expect(result.current.searchQuery).toBe('');
    });
  });
});
