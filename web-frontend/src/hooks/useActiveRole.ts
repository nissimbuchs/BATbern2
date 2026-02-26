/**
 * useActiveRole hook
 * For multi-role users: tracks which role's portal is currently active.
 * Persists selection to localStorage so it survives page refreshes.
 * Single-role users always get their one role returned; no localStorage interaction.
 */

import { useState } from 'react';
import type { UserRole } from '@/types/auth';

const STORAGE_KEY = 'batbern:active-role';

export function useActiveRole(userRoles: UserRole[]): [UserRole, (role: UserRole) => void] {
  const primaryRole = userRoles[0];

  const resolveInitial = (): UserRole => {
    if (userRoles.length <= 1) return primaryRole;
    try {
      const stored = localStorage.getItem(STORAGE_KEY) as UserRole | null;
      if (stored && userRoles.includes(stored)) return stored;
    } catch {
      // localStorage unavailable (e.g. private browsing)
    }
    return primaryRole;
  };

  const [activeRole, setActiveRoleState] = useState<UserRole>(resolveInitial);

  const setActiveRole = (role: UserRole) => {
    setActiveRoleState(role);
    try {
      localStorage.setItem(STORAGE_KEY, role);
    } catch {
      // ignore
    }
  };

  return [activeRole, setActiveRole];
}
