/**
 * AuthPageLayout Component
 *
 * Layout wrapper for authentication pages (login, register, forgot password, etc.)
 * Applies Material-UI CssBaseline to normalize styles for these pages only.
 *
 * This component isolates Material-UI styling from public pages which use Tailwind CSS.
 *
 * Architecture Decision:
 * - Public routes → No CssBaseline → Pure Tailwind CSS
 * - Auth routes → AuthPageLayout → Material-UI + CssBaseline
 * - Authenticated routes → BaseLayout → Material-UI + CssBaseline
 */

import { Box, CssBaseline } from '@mui/material';
import { ReactNode } from 'react';

interface AuthPageLayoutProps {
  children: ReactNode;
}

export const AuthPageLayout = ({ children }: AuthPageLayoutProps) => {
  return (
    <Box>
      <CssBaseline />
      {children}
    </Box>
  );
};
