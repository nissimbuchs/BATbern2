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
import {
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  Typography,
} from '@mui/material';
import { NavLink, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  getNavigationForRole,
  getGroupedNavigationForRoles,
  isPathActive,
} from '@/config/navigationConfig';
import type { UserRole } from '@/types/auth';

interface NavigationMenuProps {
  userRole: UserRole;
  userRoles?: UserRole[]; // Story 9.5: optional multi-role support
  onItemClick?: () => void;
  showText?: boolean;
  variant?: 'horizontal' | 'vertical';
}

export const NavigationMenu = React.memo(function NavigationMenu({
  userRole,
  userRoles,
  onItemClick,
  showText = true,
  variant = 'horizontal',
}: NavigationMenuProps) {
  const { t } = useTranslation();
  const location = useLocation();

  // Story 9.5: Use grouped navigation when user has multiple roles
  const isMultiRole = userRoles && userRoles.length > 1;
  const navigationItems = isMultiRole ? [] : getNavigationForRole(userRole);
  const groupedItems = isMultiRole ? getGroupedNavigationForRoles(userRoles) : [];

  const renderNavItem = (item: ReturnType<typeof getNavigationForRole>[0]) => {
    const Icon = item.icon;
    const active = isPathActive(item.path, location.pathname);

    return (
      <ListItem key={item.path} disablePadding>
        <ListItemButton
          component={NavLink}
          to={item.path}
          selected={active}
          onClick={onItemClick}
          data-testid={`nav-${item.path.split('/').filter(Boolean).join('-')}`}
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
              primary={t(item.labelKey)}
              primaryTypographyProps={{
                variant: 'body2',
                sx: { fontWeight: active ? 600 : 400 },
              }}
            />
          )}
        </ListItemButton>
      </ListItem>
    );
  };

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
        {isMultiRole
          ? groupedItems.map((group, groupIndex) => (
              <React.Fragment key={group.role}>
                {groupIndex > 0 && (
                  <Divider
                    orientation={variant === 'horizontal' ? 'vertical' : 'horizontal'}
                    flexItem
                    sx={{
                      mx: variant === 'horizontal' ? 1 : 0,
                      my: variant === 'vertical' ? 1 : 0,
                    }}
                  />
                )}
                {variant === 'vertical' && showText && (
                  <Typography
                    variant="overline"
                    color="text.secondary"
                    sx={{ px: 2, pt: groupIndex > 0 ? 1 : 0 }}
                    data-testid={`nav-section-${group.role}`}
                  >
                    {t(group.labelKey)}
                  </Typography>
                )}
                {group.items.map(renderNavItem)}
              </React.Fragment>
            ))
          : navigationItems.map(renderNavItem)}
      </List>
    </nav>
  );
});
