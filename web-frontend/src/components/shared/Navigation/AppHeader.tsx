/**
 * AppHeader Component
 * Story 1.17, Task 6b, 11b & 13b: Role-adaptive application header with responsive design and performance optimization
 *
 * Top navigation bar with role-based menu, notifications, and user menu.
 * Uses Material-UI AppBar with Swiss design principles.
 * Supports mobile (< 768px), tablet (768px-1024px), and desktop (> 1024px) breakpoints.
 * Wrapped with React.memo() for performance optimization (Task 13b).
 */

import React, { useState } from 'react';
import { AppBar, Box, Toolbar, IconButton, Badge, Avatar, Tooltip } from '@mui/material';
import { Menu, Notifications, TaskAlt } from '@mui/icons-material';
import { NavigationMenu } from './NavigationMenu';
import { MobileDrawer } from './MobileDrawer';
import UserMenuDropdown from './UserMenuDropdown';
import { useUIStore } from '@/stores/uiStore';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { useAuth } from '@/hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { UserProfile } from '@/types/user';
import type { NotificationsResponse } from '@/types/notification';
import type { UserContext } from '@/types/auth';

interface AppHeaderProps {
  user?: UserProfile;
  notifications?: NotificationsResponse;
}

const AppHeader = React.memo(function AppHeader({
  user: userProp,
  notifications: notificationsProp,
}: AppHeaderProps = {}) {
  const { t } = useTranslation();
  const { isMobile, isTablet } = useBreakpoints();
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [userMenuAnchorEl, setUserMenuAnchorEl] = useState<null | HTMLElement>(null);

  // Use props if provided, otherwise fall back to stores/hooks
  const { user: storeUser, signOut } = useAuth();
  const { setUserMenuOpen: setUserMenuOpenState } = useUIStore();
  const navigate = useNavigate();

  const user = userProp || storeUser;
  // Use prop if provided (for testing), otherwise would use hook data when backend is ready
  const notificationsData = notificationsProp;
  const unreadCount = notificationsData?.unreadCount ?? 0;

  // Extract current role - handle both UserContext (role) and UserProfile (currentRole)
  const currentRole = user && ('currentRole' in user ? user.currentRole : user.role);

  const handleNotificationClick = () => {
    navigate('/organizer/notifications');
  };

  const handleTasksClick = () => {
    navigate('/organizer/tasks');
  };

  const handleUserMenuClick = (event: React.MouseEvent<HTMLElement>) => {
    setUserMenuAnchorEl(event.currentTarget);
    setUserMenuOpen(true);
    setUserMenuOpenState(true);
  };

  const handleUserMenuClose = () => {
    setUserMenuAnchorEl(null);
    setUserMenuOpen(false);
    setUserMenuOpenState(false);
  };

  const handleLogout = async () => {
    await signOut();
    navigate('/login', { replace: true });
  };

  const handleLanguageChange = (language: 'de' | 'en') => {
    // Language change is handled inside UserMenuDropdown
    console.log('[AppHeader] Language changed to:', language);
  };

  const handleMobileMenuToggle = () => {
    setMobileDrawerOpen(!mobileDrawerOpen);
  };

  if (!user) {
    return null;
  }

  return (
    <>
      <AppBar
        component="header"
        position="sticky"
        elevation={1}
        sx={{
          backgroundColor: 'background.paper',
          color: 'text.primary',
          borderBottom: 1,
          borderColor: 'divider',
          padding: isTablet ? '8px' : '0',
        }}
      >
        <Toolbar>
          {/* Mobile Menu Button */}
          {isMobile && (
            <IconButton
              edge="start"
              color="inherit"
              aria-label="menu"
              onClick={handleMobileMenuToggle}
              sx={{ mr: 2 }}
            >
              <Menu />
            </IconButton>
          )}

          {/* Logo */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mr: 4 }}>
            <img
              src="/BATbern_color_logo.svg"
              alt="BATbern"
              style={{ height: 40, width: 'auto' }}
            />
          </Box>

          {/* Desktop/Tablet Navigation */}
          {!isMobile && currentRole && (
            <Box sx={{ flex: 1 }}>
              <NavigationMenu userRole={currentRole} showText={!isTablet} />
            </Box>
          )}

          <Box sx={{ flex: 1, display: { xs: 'block', md: 'none' } }} />

          {/* Right Section: Notifications, Tasks, User Menu */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {/* Notifications */}
            <IconButton
              color="inherit"
              aria-label="notifications"
              aria-describedby={unreadCount > 0 ? 'notification-badge-description' : undefined}
              onClick={handleNotificationClick}
            >
              <Badge badgeContent={unreadCount} color="error" aria-live="polite" aria-atomic="true">
                <Notifications />
              </Badge>
              {/* Screen reader description for unread count */}
              {unreadCount > 0 && (
                <Box
                  id="notification-badge-description"
                  component="span"
                  sx={{
                    position: 'absolute',
                    width: '1px',
                    height: '1px',
                    padding: 0,
                    margin: '-1px',
                    overflow: 'hidden',
                    clip: 'rect(0, 0, 0, 0)',
                    whiteSpace: 'nowrap',
                    border: 0,
                  }}
                >
                  {unreadCount} unread notification{unreadCount !== 1 ? 's' : ''}
                </Box>
              )}
            </IconButton>

            {/* Tasks - Only show for organizers */}
            {currentRole === 'organizer' && (
              <Tooltip title={t('navigation.tasks', 'Tasks')}>
                <IconButton
                  color="inherit"
                  aria-label="tasks"
                  onClick={handleTasksClick}
                  data-testid="tasks-button"
                >
                  <TaskAlt />
                </IconButton>
              </Tooltip>
            )}

            {/* User Menu */}
            <IconButton
              onClick={handleUserMenuClick}
              sx={{ ml: 1 }}
              aria-label="user menu"
              aria-expanded={userMenuOpen}
              aria-haspopup="true"
              data-testid="user-menu-button"
            >
              <Avatar
                sx={{
                  width: 32,
                  height: 32,
                  bgcolor: 'primary.main',
                  fontSize: '0.875rem',
                }}
              >
                {user.email.charAt(0).toUpperCase()}
              </Avatar>
            </IconButton>
          </Box>
        </Toolbar>
      </AppBar>

      {/* Mobile Drawer */}
      {currentRole && (
        <MobileDrawer
          open={mobileDrawerOpen}
          onClose={() => setMobileDrawerOpen(false)}
          userRole={currentRole}
          userEmail={user.email}
        />
      )}

      {/* User Menu Dropdown */}
      {user && (
        <UserMenuDropdown
          user={user as UserContext}
          anchorEl={userMenuAnchorEl}
          open={userMenuOpen}
          onClose={handleUserMenuClose}
          onLogout={handleLogout}
          onLanguageChange={handleLanguageChange}
        />
      )}
    </>
  );
});

export default AppHeader;
