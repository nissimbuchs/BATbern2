/**
 * PublicNavigation Component
 * Adapted from BATbern-public Navbar with React Router integration
 * Story 5.7: Supports top offset for banners (e.g., preview mode)
 */

import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Menu, ChevronDown, LogOut, UserCircle } from 'lucide-react';
import { Button } from '@/components/public/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/public/ui/popover';
import LanguageSwitcher from '@/components/shared/LanguageSwitcher/LanguageSwitcher';
import { useAuth } from '@/hooks/useAuth';
import { useTranslation } from 'react-i18next';

interface PublicNavigationProps {
  /** Top offset in pixels (e.g., when a banner is present above) */
  topOffset?: string;
}

export const PublicNavigation = ({ topOffset = '0px' }: PublicNavigationProps) => {
  const { isAuthenticated, user, signOut } = useAuth();
  const { t } = useTranslation('common');
  const navigate = useNavigate();

  // 2026-05-20 (Q#2 / Q#2b) — "Portal" and "My Sessions" live in the main horizontal
  // nav bar (next to Home / About / Past Events), not the user dropdown. Visibility:
  //   - "Portal" → /dashboard → role-router (Dashboard.tsx) → organizer / partner /
  //     attendee landing. Shown only when the user holds an *admin* role (organizer or
  //     partner); for a speaker-only user, Portal would redirect to /speaker-portal/
  //     dashboard which is the same target as "My Sessions" — redundant, so hidden.
  //   - "My Sessions" → /speaker-portal/dashboard (the public-styled speaker dashboard).
  //     Shown only when the user holds the SPEAKER role.
  //   - Multi-role user (e.g. organizer + speaker) sees BOTH: Portal routes to the
  //     organizer dashboard (logged-in MUI chrome), My Sessions stays on the public
  //     view at /speaker-portal/dashboard.
  // The route-side fix that lets organizer+speaker users actually reach
  // /speaker-portal/dashboard lives in AuthContext.canAccess (Q#2c — flatMap over
  // user.roles instead of consulting only the primary role).
  // Tolerate legacy callers that supply only the primary `user.role` and not the
  // `user.roles` array — fall back to the singular when the array is unset.
  const userRoles = user?.roles ?? (user?.role ? [user.role] : []);
  const isSpeaker = userRoles.includes('speaker');
  const hasAdminRole = userRoles.includes('organizer') || userRoles.includes('partner');

  const initials = user
    ? (user.username ?? user.email ?? '')
        .split(/[.\s_-]/)
        .map((p) => p[0] ?? '')
        .join('')
        .toUpperCase()
        .slice(0, 2) || '?'
    : '';
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const closeMobileMenu = () => setMobileMenuOpen(false);

  const handleLogout = async () => {
    closeMobileMenu();
    await signOut();
    navigate('/', { replace: true });
  };

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
              {/* 2026-05-20 (Q#2b) — role-aware nav items hoisted out of the user dropdown
                  so they live in the main nav bar alongside Home / About / Past Events. */}
              {isAuthenticated && hasAdminRole && (
                <Link
                  to="/dashboard"
                  className="text-foreground/80 hover:text-foreground transition-colors"
                  data-testid="public-nav-portal"
                >
                  {t('public.goToPortal')}
                </Link>
              )}
              {isAuthenticated && isSpeaker && (
                <Link
                  to="/speaker-portal/dashboard"
                  className="text-foreground/80 hover:text-foreground transition-colors"
                  data-testid="public-nav-my-sessions"
                >
                  {t('navigation.mySessions', 'My Sessions')}
                </Link>
              )}
            </div>

            {/* CTA Buttons - Right (desktop) */}
            <div className="hidden md:flex items-center gap-3">
              {isAuthenticated ? (
                <Popover>
                  <PopoverTrigger asChild>
                    <Button variant="outline" className="flex items-center gap-2 px-3">
                      <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs font-semibold leading-none">
                        {initials}
                      </span>
                      <ChevronDown className="h-4 w-4 text-foreground/60" />
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent align="end" className="w-64 p-2">
                    <div className="flex flex-col gap-1">
                      {/* 2026-05-20 (Q#2b) — Portal + My Sessions moved out to the main
                          nav bar above. The dropdown is now secondary actions only:
                          My Profile (speakers), language switcher, logout. */}
                      {isSpeaker && (
                        <Link
                          to="/speaker-portal/profile"
                          className="flex items-center gap-2 rounded-md px-3 py-2 text-sm hover:bg-accent transition-colors"
                        >
                          <UserCircle className="h-4 w-4" />
                          {t('navigation.myProfile', 'My Profile')}
                        </Link>
                      )}
                      <div className="py-1 px-1">
                        <LanguageSwitcher />
                      </div>
                      <button
                        onClick={handleLogout}
                        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-red-400 hover:bg-accent hover:text-red-300 transition-colors w-full text-left"
                      >
                        <LogOut className="h-4 w-4" />
                        {t('menu.logout')}
                      </button>
                    </div>
                  </PopoverContent>
                </Popover>
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
        {/* Nav links — Portal + My Sessions live here (2026-05-20 Q#2b) so the mobile
            slide-in matches the desktop bar layout. Visibility rules mirror the desktop
            block: Portal for admin roles, My Sessions for the speaker role. */}
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
          {isAuthenticated && hasAdminRole && (
            <Link
              to="/dashboard"
              onClick={closeMobileMenu}
              className="px-3 py-3 rounded-md text-foreground/80 hover:text-foreground hover:bg-accent transition-colors"
            >
              {t('public.goToPortal')}
            </Link>
          )}
          {isAuthenticated && isSpeaker && (
            <Link
              to="/speaker-portal/dashboard"
              onClick={closeMobileMenu}
              className="px-3 py-3 rounded-md text-foreground/80 hover:text-foreground hover:bg-accent transition-colors"
            >
              {t('navigation.mySessions', 'My Sessions')}
            </Link>
          )}
        </nav>

        {/* CTA buttons */}
        <div className="px-4 pb-4 flex flex-col gap-3">
          {isAuthenticated ? (
            <>
              {/* 2026-05-20 (Q#2b) — Portal/My Sessions moved up into the nav links block.
                  CTA buttons retain only the user-identity strip, My Profile (speakers),
                  language switcher, and logout. */}
              <Button asChild variant="secondary" className="w-full">
                <Link
                  to={
                    hasAdminRole
                      ? '/dashboard'
                      : isSpeaker
                        ? '/speaker-portal/dashboard'
                        : '/dashboard'
                  }
                  onClick={closeMobileMenu}
                  className="flex items-center gap-2"
                >
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-white/20 text-xs font-semibold leading-none">
                    {initials}
                  </span>
                  {user?.email ?? ''}
                </Link>
              </Button>
              {isSpeaker && (
                <Button asChild variant="secondary" className="w-full">
                  <Link
                    to="/speaker-portal/profile"
                    onClick={closeMobileMenu}
                    className="flex items-center gap-2"
                  >
                    <UserCircle className="h-4 w-4" />
                    {t('navigation.myProfile', 'My Profile')}
                  </Link>
                </Button>
              )}
              <div className="px-1">
                <LanguageSwitcher />
              </div>
              <Button
                variant="ghost"
                className="w-full justify-start text-red-400 hover:text-red-300"
                onClick={handleLogout}
              >
                <LogOut className="h-4 w-4 mr-2" />
                {t('menu.logout')}
              </Button>
            </>
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
