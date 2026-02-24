/**
 * PublicNavigation Component
 * Adapted from BATbern-public Navbar with React Router integration
 * Story 5.7: Supports top offset for banners (e.g., preview mode)
 */

import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Menu } from 'lucide-react';
import { Button } from '@/components/public/ui/button';
import { useAuth } from '@/hooks/useAuth';
import { useTranslation } from 'react-i18next';

interface PublicNavigationProps {
  /** Top offset in pixels (e.g., when a banner is present above) */
  topOffset?: string;
}

export const PublicNavigation = ({ topOffset = '0px' }: PublicNavigationProps) => {
  const { isAuthenticated, user } = useAuth();
  const { t } = useTranslation('common');

  const initials = user
    ? user.username
        .split(/[.\s_-]/)
        .map((p) => p[0] ?? '')
        .join('')
        .toUpperCase()
        .slice(0, 2) || '?'
    : '';
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const getPortalPath = () => {
    // All roles use the same /dashboard route
    // Role-based access control is handled by ProtectedRoute component
    return '/dashboard';
  };

  const closeMobileMenu = () => setMobileMenuOpen(false);

  return (
    <>
      <nav
        style={{ top: topOffset }}
        className="fixed left-0 right-0 z-50 bg-background/80 backdrop-blur-md border-b border-border"
      >
        <div className="w-full px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-24">
            {/* Logo */}
            <div className="flex-shrink-0">
              <Link to="/" className="flex items-center">
                <img src="/BATbern_color_logo.svg" alt="BATbern" className="h-20 w-auto" />
              </Link>
            </div>

            {/* Primary Navigation - Center (desktop) */}
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

            {/* CTA Buttons - Right (desktop) */}
            <div className="hidden md:flex items-center gap-3">
              {isAuthenticated ? (
                <Button asChild>
                  <Link to={getPortalPath()} className="flex items-center gap-2">
                    <span className="flex h-6 w-6 items-center justify-center rounded-full bg-white/20 text-xs font-semibold leading-none">
                      {initials}
                    </span>
                    {t('public.goToPortal')}
                  </Link>
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

            {/* Hamburger Button - Mobile only */}
            <button
              className="md:hidden p-2 text-foreground/80 hover:text-foreground transition-colors"
              onClick={() => setMobileMenuOpen((prev) => !prev)}
              aria-label={t('navigation.openMenu')}
            >
              <Menu className="h-6 w-6" />
            </button>
          </div>
        </div>
      </nav>

      {/* Overlay */}
      <div
        className={`fixed inset-0 z-40 bg-black/50 transition-opacity duration-300 ${
          mobileMenuOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'
        }`}
        onClick={closeMobileMenu}
        aria-hidden="true"
      />

      {/* Slide-in panel — starts below navbar, height fits content */}
      <div
        className={`fixed top-24 right-0 z-50 w-72 bg-background border border-border rounded-bl-lg shadow-xl flex flex-col transition-transform duration-300 ease-in-out ${
          mobileMenuOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
        aria-hidden={!mobileMenuOpen}
      >
        {/* Nav links */}
        <nav className="flex flex-col gap-1 p-4">
          <Link
            to="/"
            onClick={closeMobileMenu}
            className="px-3 py-3 rounded-md text-foreground/80 hover:text-foreground hover:bg-accent transition-colors"
          >
            {t('navigation.home')}
          </Link>
          <Link
            to="/about"
            onClick={closeMobileMenu}
            className="px-3 py-3 rounded-md text-foreground/80 hover:text-foreground hover:bg-accent transition-colors"
          >
            {t('navigation.about')}
          </Link>
          <Link
            to="/archive"
            onClick={closeMobileMenu}
            className="px-3 py-3 rounded-md text-foreground/80 hover:text-foreground hover:bg-accent transition-colors"
          >
            {t('navigation.pastEvents')}
          </Link>
        </nav>

        {/* CTA buttons */}
        <div className="px-4 pb-4 flex flex-col gap-3">
          {isAuthenticated ? (
            <Button asChild className="w-full">
              <Link
                to={getPortalPath()}
                onClick={closeMobileMenu}
                className="flex items-center gap-2"
              >
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-white/20 text-xs font-semibold leading-none">
                  {initials}
                </span>
                {t('public.goToPortal')}
              </Link>
            </Button>
          ) : (
            <>
              <Button variant="secondary" asChild className="w-full">
                <Link to="/auth/login" onClick={closeMobileMenu}>
                  {t('public.login')}
                </Link>
              </Button>
              <Button asChild className="w-full">
                <Link to="/auth/register" onClick={closeMobileMenu}>
                  {t('public.joinUp')}
                </Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </>
  );
};
