/**
 * Partner Modal Zustand Store (Story 2.8.3)
 *
 * Task 3b: GREEN Phase Implementation
 * AC1: Create Partner Modal
 * AC2: Edit Partner Modal
 *
 * Manages UI state for partner create/edit modal:
 * - Modal open/close state
 * - Mode tracking (create vs edit)
 * - Partner data for editing
 *
 * State is transient (not persisted) - resets on page reload
 */

import { create } from 'zustand';
import type { PartnerResponse } from '@/services/api/partnerApi';

interface PartnerModalStore {
  // State
  isOpen: boolean;
  mode: 'create' | 'edit';
  partnerToEdit: PartnerResponse | null;

  // Actions
  openCreateModal: () => void;
  openEditModal: (partner: PartnerResponse) => void;
  closeModal: () => void;
}

export const usePartnerModalStore = create<PartnerModalStore>((set) => ({
  // Initial state
  isOpen: false,
  mode: 'create',
  partnerToEdit: null,

  // Open create modal
  openCreateModal: () =>
    set({
      isOpen: true,
      mode: 'create',
      partnerToEdit: null,
    }),

  // Open edit modal with partner data
  openEditModal: (partner: PartnerResponse) =>
    set({
      isOpen: true,
      mode: 'edit',
      partnerToEdit: partner,
    }),

  // Close modal and reset state
  closeModal: () =>
    set({
      isOpen: false,
      mode: 'create',
      partnerToEdit: null,
    }),
}));
