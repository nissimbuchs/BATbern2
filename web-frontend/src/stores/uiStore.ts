/**
 * UI Store
 * Story 1.17, Task 5b: Zustand UI store implementation
 *
 * Manages UI state: sidebar, notification drawer, user menu and the nav role filter.
 * Persists sidebar state and nav role to localStorage. The UI language lives in i18next.
 */

import { create } from 'zustand';
import type { UserRole } from '@/types/auth';

interface UIState {
  sidebarCollapsed: boolean;
  notificationDrawerOpen: boolean;
  userMenuOpen: boolean;
  /** Active role filter for the nav RoleSelector; null means "no preference / single-role user". */
  activeNavRole: UserRole | null;
}

interface UIActions {
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setNotificationDrawerOpen: (open: boolean) => void;
  setUserMenuOpen: (open: boolean) => void;
  setActiveNavRole: (role: UserRole | null) => void;
  reset: () => void;
}

type UIStore = UIState & UIActions;

const initialState: UIState = {
  sidebarCollapsed: false,
  notificationDrawerOpen: false,
  userMenuOpen: false,
  activeNavRole: null,
};

const getInitialSidebarState = (): boolean => {
  const stored = localStorage.getItem('batbern-ui-sidebar');
  return stored === 'true';
};

const VALID_ROLES: ReadonlySet<UserRole> = new Set(['organizer', 'partner', 'speaker', 'attendee']);

const getInitialActiveNavRole = (): UserRole | null => {
  const stored = localStorage.getItem('batbern-ui-active-nav-role');
  if (!stored) return null;
  return VALID_ROLES.has(stored as UserRole) ? (stored as UserRole) : null;
};

export const useUIStore = create<UIStore>()((set) => ({
  sidebarCollapsed: getInitialSidebarState(),
  notificationDrawerOpen: false,
  userMenuOpen: false,
  activeNavRole: getInitialActiveNavRole(),

  toggleSidebar: () =>
    set((state) => {
      const newValue = !state.sidebarCollapsed;
      localStorage.setItem('batbern-ui-sidebar', String(newValue));
      return { sidebarCollapsed: newValue };
    }),

  setSidebarCollapsed: (sidebarCollapsed) => {
    localStorage.setItem('batbern-ui-sidebar', String(sidebarCollapsed));
    set(() => ({ sidebarCollapsed }));
  },

  setNotificationDrawerOpen: (notificationDrawerOpen) => set(() => ({ notificationDrawerOpen })),

  setUserMenuOpen: (userMenuOpen) => set(() => ({ userMenuOpen })),

  setActiveNavRole: (activeNavRole) => {
    if (activeNavRole) {
      localStorage.setItem('batbern-ui-active-nav-role', activeNavRole);
    } else {
      localStorage.removeItem('batbern-ui-active-nav-role');
    }
    set(() => ({ activeNavRole }));
  },

  reset: () => {
    localStorage.removeItem('batbern-ui-sidebar');
    localStorage.removeItem('batbern-ui-active-nav-role');
    set(() => ({ ...initialState }));
  },
}));
