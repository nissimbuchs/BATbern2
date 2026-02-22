/**
 * NavigationMenu Component
 * Story 1.17, Task 6b, 11b & 13b: Role-adaptive navigation menu with responsive design and performance optimization
 *
 * Renders navigation menu items based on user role.
 * Uses Material-UI components with Swiss design principles.
 * Supports icon-only mode for tablet layouts.
 * Wrapped with React.memo() for performance optimization (Task 13b).
 *
 * Items with `children` render as a dropdown (horizontal) or collapsible section (vertical).
 */

import React from 'react';
import {
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Collapse,
} from '@mui/material';
import { ExpandLess, ExpandMore, ArrowDropDown } from '@mui/icons-material';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getNavigationForRole, isPathActive } from '@/config/navigationConfig';
import type { NavigationItem, UserRole } from '@/config/navigationConfig';

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
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const navigationItems = getNavigationForRole(userRole);

  // Horizontal: track which group's popover menu is open
  const [menuAnchor, setMenuAnchor] = React.useState<HTMLElement | null>(null);
  const [openGroupPath, setOpenGroupPath] = React.useState<string | null>(null);

  // Vertical: track which groups are expanded
  const [expandedPaths, setExpandedPaths] = React.useState<Set<string>>(new Set());

  const handleGroupClick = (event: React.MouseEvent<HTMLElement>, path: string) => {
    if (variant === 'horizontal') {
      setMenuAnchor(event.currentTarget);
      setOpenGroupPath(path);
    } else {
      setExpandedPaths((prev) => {
        const next = new Set(prev);
        if (next.has(path)) {
          next.delete(path);
        } else {
          next.add(path);
        }
        return next;
      });
    }
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
    setOpenGroupPath(null);
  };

  const handleChildClick = (path: string) => {
    handleMenuClose();
    navigate(path);
    onItemClick?.();
  };

  /** True if any child of this group is the active route */
  const isGroupActive = (item: NavigationItem) =>
    item.children?.some((child) => isPathActive(child.path, location.pathname)) ?? false;

  const itemSx = {
    borderRadius: 1,
    minWidth: showText ? 'auto' : '48px',
    justifyContent: showText ? 'flex-start' : 'center',
    px: showText ? 2 : 1,
    mb: variant === 'vertical' ? 0.5 : 0,
    '&.Mui-selected': {
      backgroundColor: 'primary.main',
      color: 'primary.contrastText',
      '&:hover': { backgroundColor: 'primary.dark' },
      '& .MuiListItemIcon-root': { color: 'primary.contrastText' },
    },
    '&:hover': { backgroundColor: 'action.hover' },
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
        {navigationItems.map((item) => {
          const Icon = item.icon;

          // ── Grouped item (has children) ──────────────────────────────────
          if (item.children && item.children.length > 0) {
            const groupActive = isGroupActive(item);
            const isOpen =
              variant === 'horizontal' ? openGroupPath === item.path : expandedPaths.has(item.path);

            return (
              <React.Fragment key={item.path}>
                <ListItem disablePadding>
                  <ListItemButton
                    selected={groupActive}
                    onClick={(e) => handleGroupClick(e, item.path)}
                    data-testid={`nav-group-${item.path.split('/').filter(Boolean).join('-')}`}
                    aria-haspopup={variant === 'horizontal' ? 'true' : undefined}
                    aria-expanded={isOpen}
                    sx={itemSx}
                  >
                    <ListItemIcon sx={{ minWidth: showText ? 40 : 'auto', color: 'inherit' }}>
                      <Icon />
                    </ListItemIcon>
                    {showText && (
                      <ListItemText
                        primary={t(item.labelKey)}
                        primaryTypographyProps={{
                          variant: 'body2',
                          sx: { fontWeight: groupActive ? 600 : 400 },
                        }}
                      />
                    )}
                    {showText &&
                      (variant === 'horizontal' ? (
                        <ArrowDropDown sx={{ ml: 0.5, fontSize: 20 }} />
                      ) : isOpen ? (
                        <ExpandLess />
                      ) : (
                        <ExpandMore />
                      ))}
                  </ListItemButton>
                </ListItem>

                {/* Horizontal: popover Menu */}
                {variant === 'horizontal' && (
                  <Menu
                    anchorEl={menuAnchor}
                    open={openGroupPath === item.path}
                    onClose={handleMenuClose}
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
                    transformOrigin={{ vertical: 'top', horizontal: 'left' }}
                  >
                    {item.children.map((child) => {
                      const ChildIcon = child.icon;
                      const childActive = isPathActive(child.path, location.pathname);
                      return (
                        <MenuItem
                          key={child.path}
                          selected={childActive}
                          onClick={() => handleChildClick(child.path)}
                          data-testid={`nav-${child.path.split('/').filter(Boolean).join('-')}`}
                          sx={{ gap: 1.5 }}
                        >
                          <ChildIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                          {t(child.labelKey)}
                        </MenuItem>
                      );
                    })}
                  </Menu>
                )}

                {/* Vertical: inline Collapse */}
                {variant === 'vertical' && (
                  <Collapse in={isOpen} unmountOnExit>
                    <List disablePadding>
                      {item.children.map((child) => {
                        const ChildIcon = child.icon;
                        const childActive = isPathActive(child.path, location.pathname);
                        return (
                          <ListItem key={child.path} disablePadding>
                            <ListItemButton
                              component={NavLink}
                              to={child.path}
                              selected={childActive}
                              onClick={onItemClick}
                              data-testid={`nav-${child.path.split('/').filter(Boolean).join('-')}`}
                              sx={{ ...itemSx, pl: 4 }}
                            >
                              <ListItemIcon sx={{ minWidth: 40, color: 'inherit' }}>
                                <ChildIcon />
                              </ListItemIcon>
                              <ListItemText
                                primary={t(child.labelKey)}
                                primaryTypographyProps={{
                                  variant: 'body2',
                                  sx: { fontWeight: childActive ? 600 : 400 },
                                }}
                              />
                            </ListItemButton>
                          </ListItem>
                        );
                      })}
                    </List>
                  </Collapse>
                )}
              </React.Fragment>
            );
          }

          // ── Leaf item (no children) ──────────────────────────────────────
          const active = isPathActive(item.path, location.pathname);
          return (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                component={NavLink}
                to={item.path}
                selected={active}
                onClick={onItemClick}
                data-testid={`nav-${item.path.split('/').filter(Boolean).join('-')}`}
                sx={itemSx}
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
        })}
      </List>
    </nav>
  );
});
