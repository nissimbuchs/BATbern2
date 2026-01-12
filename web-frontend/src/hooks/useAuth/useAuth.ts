/**
 * useAuth Hook Implementation
 * Story 1.2: Authentication State Management
 *
 * Consumes global AuthContext to share auth state across all components
 */

import { useContext } from 'react';
import { AuthContext } from '@/contexts/AuthContext';

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }

  return context;
}
