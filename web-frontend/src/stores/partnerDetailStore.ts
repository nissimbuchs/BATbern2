/**
 * Partner Detail Store
 * Story 2.8.2: Partner Detail View - Task 3b
 *
 * Manages Partner Detail screen UI state including:
 * - Active tab index (0=Overview, 1=Contacts, 2=Meetings, 3=Activity, 4=Notes, 5=Settings)
 * - Note modal visibility
 *
 * The partner edit modal has its own store (partnerModalStore); the edit flag that lived here was
 * read by nothing (#1223 item 12) and is gone.
 *
 * AC3: Tab Navigation state management
 */

import { create } from 'zustand';

interface PartnerDetailState {
  activeTab: number;
  showNoteModal: boolean;
}

interface PartnerDetailActions {
  setActiveTab: (tab: number) => void;
  setShowNoteModal: (show: boolean) => void;
  reset: () => void;
}

type PartnerDetailStore = PartnerDetailState & PartnerDetailActions;

const initialState: PartnerDetailState = {
  activeTab: 0, // Default to Overview tab
  showNoteModal: false,
};

/**
 * usePartnerDetailStore - Zustand store for Partner Detail screen UI state
 *
 * State:
 * - activeTab: Current active tab index (0-5)
 * - showNoteModal: Add/edit note modal visibility
 *
 * Actions:
 * - setActiveTab: Update active tab index
 * - setShowNoteModal: Toggle note modal
 * - reset: Reset to initial state
 */
export const usePartnerDetailStore = create<PartnerDetailStore>()((set) => ({
  ...initialState,

  setActiveTab: (activeTab) => set(() => ({ activeTab })),

  setShowNoteModal: (showNoteModal) => set(() => ({ showNoteModal })),

  reset: () => set(() => ({ ...initialState })),
}));
