/**
 * User Management Store Tests
 * Story 2.5.2: User Management Frontend - Store Testing
 *
 * Tests for Zustand store managing user list filters, pagination, and selection state
 */

import { describe, test, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useUserManagementStore } from './userManagementStore';
import type { User } from '@/types/user.types';

describe('User Management Store', () => {
  beforeEach(() => {
    const { reset } = useUserManagementStore.getState();
    act(() => {
      reset();
    });
  });

  describe('Initial State', () => {
    test('should_haveEmptyFilters_when_storeInitialized', () => {
      const { result } = renderHook(() => useUserManagementStore());

      expect(result.current.filters).toEqual({});
    });

    test('should_haveDefaultPagination_when_storeInitialized', () => {
      const { result } = renderHook(() => useUserManagementStore());

      expect(result.current.pagination).toEqual({
        page: 1,
        limit: 20,
      });
    });

    test('should_haveNoSelectedUser_when_storeInitialized', () => {
      const { result } = renderHook(() => useUserManagementStore());

      expect(result.current.selectedUser).toBeNull();
    });

    test('should_haveEmptySearchQuery_when_storeInitialized', () => {
      const { result } = renderHook(() => useUserManagementStore());

      expect(result.current.searchQuery).toBe('');
    });
  });

  describe('setFilters Action', () => {
    test('should_updateFilters_when_setFiltersCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      const newFilters = { role: 'ORGANIZER', status: 'active' };

      act(() => {
        result.current.setFilters(newFilters);
      });

      expect(result.current.filters).toEqual(newFilters);
    });

    test('should_resetToPage1_when_filtersChanged', () => {
      const { result } = renderHook(() => useUserManagementStore());

      // Set page to 3
      act(() => {
        result.current.setPage(3);
      });

      expect(result.current.pagination.page).toBe(3);

      // Change filters - should reset to page 1
      act(() => {
        result.current.setFilters({ role: 'SPEAKER' });
      });

      expect(result.current.pagination.page).toBe(1);
    });

    test('should_replaceExistingFilters_when_setFiltersCalledAgain', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setFilters({ role: 'ORGANIZER' });
      });

      act(() => {
        result.current.setFilters({ status: 'inactive' });
      });

      // Should replace, not merge
      expect(result.current.filters).toEqual({ status: 'inactive' });
    });
  });

  describe('setPagination Action', () => {
    test('should_updatePage_when_setPaginationCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setPagination({ page: 5 });
      });

      expect(result.current.pagination.page).toBe(5);
      expect(result.current.pagination.limit).toBe(20); // Should keep existing limit
    });

    test('should_updateLimit_when_setPaginationCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setPagination({ limit: 50 });
      });

      expect(result.current.pagination.limit).toBe(50);
      expect(result.current.pagination.page).toBe(1); // Should keep existing page
    });

    test('should_updateBothPageAndLimit_when_setPaginationCalledWithBoth', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setPagination({ page: 3, limit: 50 });
      });

      expect(result.current.pagination).toEqual({ page: 3, limit: 50 });
    });
  });

  describe('setPage Action', () => {
    test('should_updatePage_when_setPageCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setPage(7);
      });

      expect(result.current.pagination.page).toBe(7);
    });

    test('should_keepLimit_when_setPageCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setPage(3);
      });

      expect(result.current.pagination.limit).toBe(20);
    });
  });

  describe('setLimit Action', () => {
    test('should_updateLimit_when_setLimitCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setLimit(50);
      });

      expect(result.current.pagination.limit).toBe(50);
    });

    test('should_resetToPage1_when_limitChanged', () => {
      const { result } = renderHook(() => useUserManagementStore());

      // Set page to 5
      act(() => {
        result.current.setPage(5);
      });

      expect(result.current.pagination.page).toBe(5);

      // Change limit - should reset to page 1
      act(() => {
        result.current.setLimit(50);
      });

      expect(result.current.pagination.page).toBe(1);
    });
  });

  describe('setSelectedUser Action', () => {
    test('should_setUser_when_setSelectedUserCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      const mockUser: User = {
        id: '123',
        employeeId: 'EMP-001',
        email: 'test@example.com',
        firstName: 'John',
        lastName: 'Doe',
        role: 'ORGANIZER',
        status: 'active',
        emailVerified: true,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
      };

      act(() => {
        result.current.setSelectedUser(mockUser);
      });

      expect(result.current.selectedUser).toEqual(mockUser);
    });

    test('should_clearUser_when_setSelectedUserCalledWithNull', () => {
      const { result } = renderHook(() => useUserManagementStore());

      const mockUser: User = {
        id: '123',
        employeeId: 'EMP-001',
        email: 'test@example.com',
        firstName: 'John',
        lastName: 'Doe',
        role: 'ORGANIZER',
        status: 'active',
        emailVerified: true,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
      };

      act(() => {
        result.current.setSelectedUser(mockUser);
      });

      act(() => {
        result.current.setSelectedUser(null);
      });

      expect(result.current.selectedUser).toBeNull();
    });
  });

  describe('setSearchQuery Action', () => {
    test('should_updateSearchQuery_when_setSearchQueryCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setSearchQuery('john doe');
      });

      expect(result.current.searchQuery).toBe('john doe');
    });

    test('should_updateFiltersSearchQuery_when_setSearchQueryCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setSearchQuery('john doe');
      });

      expect(result.current.filters.searchQuery).toBe('john doe');
    });

    test('should_resetToPage1_when_searchQueryChanged', () => {
      const { result } = renderHook(() => useUserManagementStore());

      // Set page to 4
      act(() => {
        result.current.setPage(4);
      });

      expect(result.current.pagination.page).toBe(4);

      // Change search query - should reset to page 1
      act(() => {
        result.current.setSearchQuery('test');
      });

      expect(result.current.pagination.page).toBe(1);
    });

    test('should_preserveOtherFilters_when_searchQueryChanged', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setFilters({ role: 'ORGANIZER', status: 'active' });
      });

      act(() => {
        result.current.setSearchQuery('john');
      });

      expect(result.current.filters).toEqual({
        role: 'ORGANIZER',
        status: 'active',
        searchQuery: 'john',
      });
    });
  });

  describe('resetFilters Action', () => {
    test('should_clearFilters_when_resetFiltersCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setFilters({ role: 'ORGANIZER', status: 'active' });
      });

      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.filters).toEqual({});
    });

    test('should_clearSearchQuery_when_resetFiltersCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setSearchQuery('test query');
      });

      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.searchQuery).toBe('');
    });

    test('should_resetToPage1_when_resetFiltersCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setPage(5);
      });

      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.pagination.page).toBe(1);
    });

    test('should_keepLimit_when_resetFiltersCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setLimit(50);
      });

      act(() => {
        result.current.resetFilters();
      });

      expect(result.current.pagination.limit).toBe(50);
    });
  });

  describe('resetPagination Action', () => {
    test('should_resetToDefaults_when_resetPaginationCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setPagination({ page: 10, limit: 100 });
      });

      act(() => {
        result.current.resetPagination();
      });

      expect(result.current.pagination).toEqual({
        page: 1,
        limit: 20,
      });
    });

    test('should_notAffectFilters_when_resetPaginationCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      act(() => {
        result.current.setFilters({ role: 'ORGANIZER' });
      });

      act(() => {
        result.current.resetPagination();
      });

      expect(result.current.filters).toEqual({ role: 'ORGANIZER' });
    });
  });

  describe('reset Action', () => {
    test('should_resetAllState_when_resetCalled', () => {
      const { result } = renderHook(() => useUserManagementStore());

      const mockUser: User = {
        id: '123',
        employeeId: 'EMP-001',
        email: 'test@example.com',
        firstName: 'John',
        lastName: 'Doe',
        role: 'ORGANIZER',
        status: 'active',
        emailVerified: true,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
      };

      // Set various state
      act(() => {
        result.current.setFilters({ role: 'ORGANIZER', status: 'active' });
        result.current.setPagination({ page: 5, limit: 50 });
        result.current.setSearchQuery('test');
        result.current.setSelectedUser(mockUser);
      });

      // Reset everything
      act(() => {
        result.current.reset();
      });

      expect(result.current.filters).toEqual({});
      expect(result.current.pagination).toEqual({ page: 1, limit: 20 });
      expect(result.current.searchQuery).toBe('');
      expect(result.current.selectedUser).toBeNull();
    });
  });
});
