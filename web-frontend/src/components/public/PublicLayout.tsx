/**
 * PublicLayout Component
 * From BATbern-public design
 */

import { ReactNode, useEffect } from 'react';
import { PublicNavigation } from './Navigation/PublicNavigation';

interface PublicLayoutProps {
  children: ReactNode;
}

export const PublicLayout = ({ children }: PublicLayoutProps) => {
  // Apply dark class to html element for Tailwind dark mode
  useEffect(() => {
    document.documentElement.classList.add('dark');
    return () => {
      document.documentElement.classList.remove('dark');
    };
  }, []);

  return (
    <div className="min-h-screen w-full">
      <PublicNavigation />
      {children}
    </div>
  );
};
