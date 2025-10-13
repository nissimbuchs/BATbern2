/**
 * Higher-order component for protecting routes
 */
import React from 'react';
import { ProtectedRoute } from './ProtectedRoute';
import type { ProtectedRouteProps } from './types';

export const withProtectedRoute = <P extends object>(
  Component: React.ComponentType<P>,
  options?: Omit<ProtectedRouteProps, 'children'>
) => {
  return (props: P) => (
    <ProtectedRoute {...options}>
      <Component {...props} />
    </ProtectedRoute>
  );
};
