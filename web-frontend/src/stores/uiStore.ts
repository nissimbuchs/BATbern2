/**
 * UI Store
 * Story 1.17, Task 5b: Zustand UI store implementation
 *
 * Manages UI state including locale, sidebar state, notification drawer, and user menu.
 * Persists locale and sidebar state to localStorage.
 */

import { create } from 'zustand';

type Locale = 'de' | 'en';

interface UIState {
  locale: Locale;
  sidebarCollapsed: boolean;
  notificationDrawerOpen: boolean;
  userMenuOpen: boolean;
}

interface UIActions {
  setLocale: (locale: Locale) => void;
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setNotificationDrawerOpen: (open: boolean) => void;
  setUserMenuOpen: (open: boolean) => void;
  reset: () => void;
}

type UIStore = UIState & UIActions;

const initialState: UIState = {
  locale: 'de', // German as default
  sidebarCollapsed: false,
  notificationDrawerOpen: false,
  userMenuOpen: false,
};

// Initialize locale from localStorage if available
const getInitialLocale = (): Locale => {
  const stored = localStorage.getItem('batbern-ui-locale');
  if (stored) {
    try {
      const parsed = JSON.parse(stored);
      return parsed === 'en' ? 'en' : 'de';
    } catch {
      return 'de';
    }
  }
  return 'de';
};

const getInitialSidebarState = (): boolean => {
  const stored = localStorage.getItem('batbern-ui-sidebar');
  return stored === 'true';
};

export const useUIStore = create<UIStore>()((set) => ({
  locale: getInitialLocale(),
  sidebarCollapsed: getInitialSidebarState(),
  notificationDrawerOpen: false,
  userMenuOpen: false,

  setLocale: (locale) => {
    localStorage.setItem('batbern-ui-locale', JSON.stringify(locale));
    set(() => ({ locale }));
  },

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

  reset: () => {
    localStorage.removeItem('batbern-ui-locale');
    localStorage.removeItem('batbern-ui-sidebar');
    set(() => ({ ...initialState }));
  },
}));
