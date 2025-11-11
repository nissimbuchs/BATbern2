/**
 * Tests for Partner Modal Zustand Store (RED Phase - Story 2.8.3)
 *
 * Task 3a: RED Phase Tests
 * Test Specifications: AC1, AC2 - Modal State Management
 *
 * Tests for:
 * - Initial state
 * - Create modal open/close
 * - Edit modal open/close with partner data
 * - Mode tracking (create vs edit)
 * - Partner to edit state management
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePartnerModalStore } from '@/stores/partnerModalStore';
import type { PartnerResponse } from '@/services/api/partnerApi';

describe('partnerModalStore - Story 2.8.3', () => {
  // Reset store before each test
  beforeEach(() => {
    const { result } = renderHook(() => usePartnerModalStore());
    act(() => {
      result.current.closeModal();
    });
  });

  describe('Initial State', () => {
    it('should_haveModalClosed_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      expect(result.current.isOpen).toBe(false);
    });

    it('should_haveCreateMode_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      expect(result.current.mode).toBe('create');
    });

    it('should_haveNoPartnerToEdit_when_storeInitialized', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      expect(result.current.partnerToEdit).toBeNull();
    });
  });

  describe('Create Modal Management (AC1)', () => {
    it('should_openCreateModal_when_openCreateModalCalled', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isOpen).toBe(true);
      expect(result.current.mode).toBe('create');
      expect(result.current.partnerToEdit).toBeNull();
    });

    it('should_clearPartnerData_when_openCreateModalCalledAfterEdit', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      const mockPartner: PartnerResponse = {
        id: 'partner-123',
        companyName: 'test-company',
        partnershipLevel: 'GOLD',
        partnershipStartDate: '2025-01-01',
        isActive: true,
        engagementScore: 75,
        lastActivityDate: '2025-01-15',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-15T00:00:00Z',
      };

      // First open edit modal
      act(() => {
        result.current.openEditModal(mockPartner);
      });

      // Then open create modal - should clear partner data
      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isOpen).toBe(true);
      expect(result.current.mode).toBe('create');
      expect(result.current.partnerToEdit).toBeNull();
    });
  });

  describe('Edit Modal Management (AC2)', () => {
    it('should_openEditModal_when_openEditModalCalled', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      const mockPartner: PartnerResponse = {
        id: 'partner-456',
        companyName: 'edit-company',
        partnershipLevel: 'PLATINUM',
        partnershipStartDate: '2025-02-01',
        partnershipEndDate: '2025-12-31',
        isActive: true,
        engagementScore: 90,
        lastActivityDate: '2025-02-15',
        createdAt: '2025-02-01T00:00:00Z',
        updatedAt: '2025-02-15T00:00:00Z',
      };

      act(() => {
        result.current.openEditModal(mockPartner);
      });

      expect(result.current.isOpen).toBe(true);
      expect(result.current.mode).toBe('edit');
      expect(result.current.partnerToEdit).toEqual(mockPartner);
    });

    it('should_storePartnerData_when_openEditModalCalled', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      const mockPartner: PartnerResponse = {
        id: 'partner-789',
        companyName: 'strategic-company',
        partnershipLevel: 'STRATEGIC',
        partnershipStartDate: '2025-01-01',
        isActive: true,
        engagementScore: 95,
        lastActivityDate: '2025-03-01',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-03-01T00:00:00Z',
      };

      act(() => {
        result.current.openEditModal(mockPartner);
      });

      expect(result.current.partnerToEdit?.companyName).toBe('strategic-company');
      expect(result.current.partnerToEdit?.partnershipLevel).toBe('STRATEGIC');
      expect(result.current.partnerToEdit?.engagementScore).toBe(95);
    });
  });

  describe('Close Modal Management', () => {
    it('should_closeModal_when_closeModalCalled', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      // Open create modal first
      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.isOpen).toBe(true);

      // Close modal
      act(() => {
        result.current.closeModal();
      });

      expect(result.current.isOpen).toBe(false);
    });

    it('should_clearPartnerData_when_closeModalCalled', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      const mockPartner: PartnerResponse = {
        id: 'partner-clear-123',
        companyName: 'clear-company',
        partnershipLevel: 'SILVER',
        partnershipStartDate: '2025-01-01',
        isActive: true,
        engagementScore: 60,
        lastActivityDate: '2025-01-10',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-10T00:00:00Z',
      };

      // Open edit modal
      act(() => {
        result.current.openEditModal(mockPartner);
      });

      expect(result.current.partnerToEdit).toEqual(mockPartner);

      // Close modal - should clear partner data
      act(() => {
        result.current.closeModal();
      });

      expect(result.current.isOpen).toBe(false);
      expect(result.current.partnerToEdit).toBeNull();
      expect(result.current.mode).toBe('create'); // Reset to create mode
    });
  });

  describe('Mode Switching', () => {
    it('should_switchToEditMode_when_openEditModalCalledAfterCreate', () => {
      const { result } = renderHook(() => usePartnerModalStore());

      // Open create modal first
      act(() => {
        result.current.openCreateModal();
      });

      expect(result.current.mode).toBe('create');

      const mockPartner: PartnerResponse = {
        id: 'partner-switch-123',
        companyName: 'switch-company',
        partnershipLevel: 'BRONZE',
        partnershipStartDate: '2025-01-01',
        isActive: true,
        engagementScore: 40,
        lastActivityDate: '2025-01-05',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-05T00:00:00Z',
      };

      // Open edit modal - should switch mode
      act(() => {
        result.current.openEditModal(mockPartner);
      });

      expect(result.current.mode).toBe('edit');
      expect(result.current.partnerToEdit).toEqual(mockPartner);
    });
  });
});
