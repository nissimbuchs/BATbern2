/**
 * PublicNavigation Component
 * Story 4.1.2: Public Layout & Navigation
 *
 * Sticky header navigation for public pages with:
 * - Desktop: Horizontal navigation links
 * - Mobile: Hamburger menu with drawer
 * - Auth indicator when logged in
 */

import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Menu } from 'lucide-react';
import { Button } from '@/components/public/ui/button';
import { Sheet, SheetContent, SheetTrigger } from '@/components/public/ui/sheet';
import { useAuth } from '@/hooks/useAuth';

export const PublicNavigation = () => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { user, isAuthenticated } = useAuth();

  const navLinks = [
    { label: 'Next Event', href: '/current-event' },
    { label: 'About', href: '/about' },
    { label: 'Past Events', href: '/archive' },
  ];

  const getPortalPath = () => {
    // Redirect to appropriate portal based on user role
    if (user?.role === 'organizer') return '/organizer/dashboard';
    if (user?.role === 'speaker') return '/speaker/dashboard';
    if (user?.role === 'partner') return '/partner/dashboard';
    return '/attendee/dashboard';
  };

  return (
    <header className="sticky top-0 z-50 w-full border-b border-zinc-800 bg-zinc-950/80 backdrop-blur supports-[backdrop-filter]:bg-zinc-950/60">
      <nav className="container mx-auto flex h-16 items-center justify-between px-4">
        {/* Logo */}
        <Link to="/" className="text-2xl font-light tracking-wide">
          BATbern
        </Link>

        {/* Desktop Navigation */}
        <div className="hidden md:flex md:items-center md:gap-6">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              to={link.href}
              className="text-sm font-light text-zinc-300 transition-colors hover:text-blue-400"
            >
              {link.label}
            </Link>
          ))}

          {/* Auth Indicator */}
          {isAuthenticated ? (
            <Button
              asChild
              variant="outline"
              size="sm"
              className="ml-4"
            >
              <Link to={getPortalPath()}>Go to Portal</Link>
            </Button>
          ) : (
            <Button
              asChild
              variant="default"
              size="sm"
              className="ml-4"
            >
              <Link to="/auth/login">Login</Link>
            </Button>
          )}
        </div>

        {/* Mobile Menu */}
        <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
          <SheetTrigger asChild className="md:hidden">
            <Button variant="ghost" size="icon">
              <Menu className="h-6 w-6" />
              <span className="sr-only">Toggle menu</span>
            </Button>
          </SheetTrigger>
          <SheetContent side="right" className="w-[300px] bg-zinc-900">
            <div className="flex flex-col gap-4 mt-8">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  to={link.href}
                  onClick={() => setMobileMenuOpen(false)}
                  className="text-lg font-light text-zinc-300 hover:text-blue-400"
                >
                  {link.label}
                </Link>
              ))}
              {isAuthenticated ? (
                <Button asChild className="mt-4">
                  <Link to={getPortalPath()}>Go to Portal</Link>
                </Button>
              ) : (
                <Button asChild className="mt-4">
                  <Link to="/auth/login">Login</Link>
                </Button>
              )}
            </div>
          </SheetContent>
        </Sheet>
      </nav>
    </header>
  );
};
