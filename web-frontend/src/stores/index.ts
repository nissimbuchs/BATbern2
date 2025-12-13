/**
 * Store exports
 * Story 1.17: Zustand stores
 *
 * Note: Authentication state is managed via useAuth hook (@/hooks/useAuth),
 * not via Zustand store, to ensure consistency with authService integration.
 */

export { useUIStore } from './uiStore';
export { useCompanyStore } from './companyStore';
