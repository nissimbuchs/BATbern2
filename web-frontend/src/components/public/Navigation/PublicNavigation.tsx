/**
 * PublicNavigation Component
 * Adapted from BATbern-public Navbar with React Router integration
 * Story 5.7: Supports top offset for banners (e.g., preview mode)
 */

import { Link } from 'react-router-dom';
import { Button } from '@/components/public/ui/button';
import { useAuth } from '@/hooks/useAuth';
import { useTranslation } from 'react-i18next';

interface PublicNavigationProps {
  /** Top offset in pixels (e.g., when a banner is present above) */
  topOffset?: string;
}

export const PublicNavigation = ({ topOffset = '0px' }: PublicNavigationProps) => {
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation('common');

  const getPortalPath = () => {
    // All roles use the same /dashboard route
    // Role-based access control is handled by ProtectedRoute component
    return '/dashboard';
  };

  return (
    <nav
      style={{ top: topOffset }}
      className="fixed left-0 right-0 z-50 bg-background/80 backdrop-blur-md border-b border-border"
    >
      <div className="w-full px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <div className="flex-shrink-0">
            <Link to="/" className="text-2xl font-bold text-primary">
              {t('app.name')}
            </Link>
          </div>

          {/* Primary Navigation - Center */}
          <div className="hidden md:flex items-center space-x-8">
            <Link to="/" className="text-foreground/80 hover:text-foreground transition-colors">
              {t('navigation.home')}
            </Link>
            <Link
              to="/about"
              className="text-foreground/80 hover:text-foreground transition-colors"
            >
              {t('navigation.about')}
            </Link>
            <Link
              to="/archive"
              className="text-foreground/80 hover:text-foreground transition-colors"
            >
              {t('navigation.pastEvents')}
            </Link>
          </div>

          {/* CTA Buttons - Right */}
          <div className="flex items-center gap-3">
            {isAuthenticated ? (
              <Button asChild>
                <Link to={getPortalPath()}>{t('public.goToPortal')}</Link>
              </Button>
            ) : (
              <>
                <Button variant="secondary" asChild>
                  <Link to="/auth/login">{t('public.login')}</Link>
                </Button>
                <Button asChild>
                  <Link to="/auth/register">{t('public.joinUp')}</Link>
                </Button>
              </>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
};
