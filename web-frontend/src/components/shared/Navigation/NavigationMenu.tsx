/**
 * NavigationMenu Component
 * Story 1.17, Task 6b, 11b & 13b: Role-adaptive navigation menu with responsive design and performance optimization
 *
 * Renders navigation menu items based on user role.
 * Uses Material-UI components with Swiss design principles.
 * Supports icon-only mode for tablet layouts.
 * Wrapped with React.memo() for performance optimization (Task 13b).
 */

import React from 'react';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { NavLink, useLocation } from 'react-router-dom';
import { getNavigationForRole, isPathActive } from '@/config/navigationConfig';
import type { UserRole } from '@/types/auth';

interface NavigationMenuProps {
  userRole: UserRole;
  onItemClick?: () => void;
  showText?: boolean;
  variant?: 'horizontal' | 'vertical';
}

export const NavigationMenu = React.memo(function NavigationMenu({
  userRole,
  onItemClick,
  showText = true,
  variant = 'horizontal',
}: NavigationMenuProps) {
  const location = useLocation();
  const navigationItems = getNavigationForRole(userRole);

  return (
    <nav aria-label="main navigation">
      <List
        sx={{
          display: 'flex',
          flexDirection: variant === 'horizontal' ? 'row' : 'column',
          gap: variant === 'horizontal' ? 1 : 0,
          p: 0,
        }}
      >
        {navigationItems.map((item) => {
          const Icon = item.icon;
          const active = isPathActive(item.path, location.pathname);

          return (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                component={NavLink}
                to={item.path}
                selected={active}
                onClick={onItemClick}
                sx={{
                  borderRadius: 1,
                  minWidth: showText ? 'auto' : '48px',
                  justifyContent: showText ? 'flex-start' : 'center',
                  px: showText ? 2 : 1,
                  mb: variant === 'vertical' ? 0.5 : 0,
                  '&.Mui-selected': {
                    backgroundColor: 'primary.main',
                    color: 'primary.contrastText',
                    '&:hover': {
                      backgroundColor: 'primary.dark',
                    },
                    '& .MuiListItemIcon-root': {
                      color: 'primary.contrastText',
                    },
                  },
                  '&:hover': {
                    backgroundColor: 'action.hover',
                  },
                }}
              >
                <ListItemIcon sx={{ minWidth: showText ? 40 : 'auto', color: 'inherit' }}>
                  <Icon />
                </ListItemIcon>
                {showText && (
                  <ListItemText
                    primary={item.label}
                    primaryTypographyProps={{
                      variant: 'body2',
                      sx: { fontWeight: active ? 600 : 400 },
                    }}
                  />
                )}
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>
    </nav>
  );
});
