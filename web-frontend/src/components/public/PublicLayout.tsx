/**
 * PublicLayout Component
 * Story 4.1.2: Public Layout & Navigation
 *
 * Layout wrapper for all public pages with dark theme,
 * navigation header, and footer.
 */

import { ReactNode } from 'react';
import { PublicNavigation } from './Navigation/PublicNavigation';
import { PublicFooter } from './Footer/PublicFooter';

interface PublicLayoutProps {
  children: ReactNode;
}

export const PublicLayout = ({ children }: PublicLayoutProps) => {
  return (
    <div className="dark min-h-screen bg-zinc-950 text-zinc-100">
      <PublicNavigation />
      <main className="min-h-[calc(100vh-theme(spacing.16)-theme(spacing.24))]">
        {children}
      </main>
      <PublicFooter />
    </div>
  );
};
