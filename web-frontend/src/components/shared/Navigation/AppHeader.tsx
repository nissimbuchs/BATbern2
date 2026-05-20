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
import { RoleSelector } from './RoleSelector';
import UserMenuDropdown from './UserMenuDropdown';
import { useUIStore } from '@/stores/uiStore';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { useAuth } from '@/hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getRolesWithNavEntries } from '@/config/navigationConfig';
import type { UserProfile } from '@/types/user';
import type { NotificationsResponse } from '@/types/notification';
import type { UserContext, UserRole } from '@/types/auth';

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
  const { setUserMenuOpen: setUserMenuOpenState, activeNavRole, setActiveNavRole } = useUIStore();
  const navigate = useNavigate();

  const user = userProp || storeUser;
  // Use prop if provided (for testing), otherwise would use hook data when backend is ready
  const notificationsData = notificationsProp;
  const unreadCount = notificationsData?.unreadCount ?? 0;

  // Extract current role - handle both UserContext (role) and UserProfile (currentRole)
  const currentRole = user && ('currentRole' in user ? user.currentRole : user.role);
  // 2026-05-20 (Q#1b) — only roles that have real nav entries (organizer, partner today)
  // qualify for the RoleSelector + admin NavigationMenu. Speaker and attendee live in
  // the public site, so they must not appear as chips here (clicking would render an
  // empty menu). Filter the user's roles down to the set with entries; everything
  // below — chip visibility, active-role fallback, mobile drawer — branches on the
  // filtered list.
  const rolesWithEntries = getRolesWithNavEntries();
  const allUserRoles: UserRole[] =
    user && 'roles' in user && Array.isArray(user.roles) && user.roles.length > 0
      ? (user.roles as UserRole[])
      : currentRole
        ? [currentRole as UserRole]
        : [];
  const currentRoles: UserRole[] = allUserRoles.filter((r) => rolesWithEntries.has(r));

  // Multi-role: prefer the persisted active role; fall back to the first role if
  // `activeNavRole` is unset or no longer matches any of the user's roles (e.g.
  // after a role grant/revoke). No "self-heal" effect — the stored value can stay
  // stale; it just gets ignored at render time until the user clicks a different
  // chip, which writes a fresh value via `setActiveNavRole`.
  // Single-role: always use that role (ignore stale `activeNavRole`).
  const effectiveActiveRole: UserRole | null = (() => {
    if (currentRoles.length === 0) return null;
    if (currentRoles.length === 1) return currentRoles[0];
    if (activeNavRole && currentRoles.includes(activeNavRole)) return activeNavRole;
    return currentRoles[0];
  })();

  const navRoles: UserRole[] = effectiveActiveRole ? [effectiveActiveRole] : [];

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

  const handleLanguageChange = (language: string) => {
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
              aria-label={t('navigation.openMenu')}
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

          {/* Desktop/Tablet Navigation — multi-role users see a RoleSelector chip
              row, and the nav filters down to the selected role. Single-role users
              see only their role's items, no chip. */}
          {!isMobile && currentRoles.length > 0 && effectiveActiveRole && (
            <Box sx={{ flex: 1, display: 'flex', alignItems: 'center' }}>
              {currentRoles.length > 1 && (
                <RoleSelector
                  roles={currentRoles}
                  activeRole={effectiveActiveRole}
                  onChange={(role) => setActiveNavRole(role)}
                />
              )}
              <NavigationMenu userRoles={navRoles} showText={!isTablet} />
            </Box>
          )}

          <Box sx={{ flex: 1, display: { xs: 'block', md: 'none' } }} />

          {/* Right Section: Notifications, Tasks, User Menu */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {/* Notifications */}
            <IconButton
              color="inherit"
              aria-label={t('navigation.notifications')}
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
                  {t('notifications.unreadCount', { count: unreadCount })}
                </Box>
              )}
            </IconButton>

            {/* Tasks - shown when the active nav-role chip is "organizer". */}
            {effectiveActiveRole === 'organizer' && (
              <Tooltip title={t('navigation.tasks')}>
                <IconButton
                  color="inherit"
                  aria-label={t('navigation.tasks')}
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
              aria-label={t('navigation.userMenu')}
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
      {currentRoles.length > 0 && effectiveActiveRole && (
        <MobileDrawer
          open={mobileDrawerOpen}
          onClose={() => setMobileDrawerOpen(false)}
          userRoles={currentRoles}
          activeRole={effectiveActiveRole}
          onActiveRoleChange={(role) => setActiveNavRole(role)}
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
