/**
 * PublicLayout Component
 * From BATbern-public design
 * Story 5.7: Supports optional top banner (e.g., preview mode notice)
 */

import { ReactNode, useEffect } from 'react';
import { PublicNavigation } from './Navigation/PublicNavigation';
import { PublicFooter } from './Footer/PublicFooter';

interface PublicLayoutProps {
  children: ReactNode;
  /** Optional banner to display above navigation (e.g., preview mode notice) */
  topBanner?: ReactNode;
}

export const PublicLayout = ({ children, topBanner }: PublicLayoutProps) => {
  // Apply dark class to html element for Tailwind dark mode
  useEffect(() => {
    document.documentElement.classList.add('dark');
    return () => {
      document.documentElement.classList.remove('dark');
    };
  }, []);

  // Calculate top offset for navigation when banner is present
  // Banner height is approximately 52px (py-3 padding + text content)
  const hasBanner = !!topBanner;
  const navTopOffset = hasBanner ? '52px' : '0px';

  return (
    <div className="min-h-screen w-full flex flex-col">
      {/* Top banner (e.g., preview mode notice) */}
      {topBanner}
      <PublicNavigation topOffset={navTopOffset} />
      {/* Add padding to prevent content from going under fixed nav and banner */}
      <div className={hasBanner ? 'pt-[116px]' : 'pt-16'}>
        <main className="flex-1">{children}</main>
      </div>
      <PublicFooter />
    </div>
  );
};
