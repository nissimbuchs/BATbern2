/**
 * BaseLayout Component
 * Story 1.17, Task 6b, 11b & 12b: Role-adaptive base layout wrapper with responsive design and accessibility
 *
 * Main layout structure with AppHeader and content area.
 * Uses Material-UI Box/Container with Swiss design grid system.
 * Supports mobile (< 768px), tablet (768px-1024px), and desktop (> 1024px) breakpoints.
 * Implements WCAG 2.1 AA accessibility with skip links and semantic HTML.
 */

import { Box, Container, Link } from '@mui/material';
import AppHeader from '../Navigation/AppHeader';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import type { UserProfile } from '@/types/user';
import type { NotificationsResponse } from '@/types/notification';

interface BaseLayoutProps {
  children: React.ReactNode;
  maxWidth?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | false;
  user?: UserProfile;
  notifications?: NotificationsResponse;
}

export function BaseLayout({ children, maxWidth = 'xl', user, notifications }: BaseLayoutProps) {
  const { isDesktop } = useBreakpoints();

  const handleSkipLinkClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    const mainContent = document.getElementById('main-content');
    if (mainContent) {
      mainContent.focus();
      // scrollIntoView may not be available in test environments (jsdom)
      if (typeof mainContent.scrollIntoView === 'function') {
        mainContent.scrollIntoView({ behavior: 'smooth' });
      }
    }
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        minHeight: '100vh',
        backgroundColor: 'background.default',
      }}
    >
      {/* Skip to Main Content Link (WCAG 2.1 - Bypass Blocks) */}
      <Link
        href="#main-content"
        onClick={handleSkipLinkClick}
        sx={{
          position: 'absolute',
          left: '-9999px',
          top: '0',
          zIndex: 9999,
          padding: '0.5rem 1rem',
          backgroundColor: 'primary.main',
          color: 'primary.contrastText',
          textDecoration: 'none',
          '&:focus': {
            left: '0',
            top: '0',
            outline: '3px solid',
            outlineColor: 'primary.dark',
            outlineOffset: '2px',
          },
        }}
      >
        Skip to main content
      </Link>

      {/* App Header */}
      <AppHeader user={user} notifications={notifications} />

      {/* Main Content Area */}
      <Box
        component="main"
        id="main-content"
        tabIndex={-1}
        sx={{
          flex: 1,
          py: 3,
          maxWidth: isDesktop ? '1200px' : '100%',
          mx: 'auto',
          width: '100%',
          '&:focus': {
            outline: 'none', // Skip link will handle focus, no visual outline needed on main
          },
        }}
      >
        <Container maxWidth={maxWidth}>{children}</Container>
      </Box>
    </Box>
  );
}
