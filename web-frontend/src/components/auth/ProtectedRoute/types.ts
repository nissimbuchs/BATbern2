/**
 * ProtectedRoute Types
 */
import { UserRole } from '@/types/auth';

export interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: UserRole[];
  requiresAuth?: boolean;
  requiresVerification?: boolean;
  fallbackPath?: string;
}
