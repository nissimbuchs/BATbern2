/**
 * MobileDrawer Component
 * Story 1.17, Task 6b: Responsive mobile navigation drawer
 *
 * Slide-in navigation drawer for mobile devices (< 768px).
 * Uses Material-UI Drawer with Swiss design principles.
 */

import {
  Drawer,
  Box,
  IconButton,
  Divider,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
} from '@mui/material';
import { Close, Logout } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { NavigationMenu } from './NavigationMenu';
import { RoleSelector } from './RoleSelector';
import type { UserRole } from '@/types/auth';

interface MobileDrawerProps {
  open: boolean;
  onClose: () => void;
  userRoles: UserRole[];
  userEmail?: string;
  /** Only provided for multi-role users */
  allRoles?: UserRole[];
  activeRole?: UserRole;
  onRoleChange?: (role: UserRole) => void;
}

export function MobileDrawer({
  open,
  onClose,
  userRoles,
  userEmail,
  allRoles,
  activeRole,
  onRoleChange,
}: MobileDrawerProps) {
  const { t } = useTranslation();
  const handleLogout = () => {
    // Logout will be handled by parent component
    onClose();
  };

  return (
    <Drawer
      anchor="left"
      open={open}
      onClose={onClose}
      aria-label={t('navigation.mobileNav')}
      ModalProps={{
        keepMounted: false, // Unmount content when closed for better performance and cleaner DOM
      }}
      sx={{
        '& .MuiDrawer-paper': {
          width: 280,
          boxSizing: 'border-box',
        },
      }}
    >
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Header with Logo and Close Button */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            p: 2,
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <img
              src="/BATbern_color_logo.svg"
              alt="BATbern"
              style={{ height: 36, width: 'auto' }}
            />
          </Box>
          <IconButton onClick={onClose} edge="end" aria-label={t('actions.close')}>
            <Close />
          </IconButton>
        </Box>

        <Divider />

        {/* User Profile Section */}
        {userEmail && (
          <>
            <Box sx={{ p: 2 }}>
              <Typography variant="body2" color="text.secondary">
                Signed in as
              </Typography>
              <Typography variant="body1" sx={{ fontWeight: 500, mt: 0.5 }}>
                {userEmail}
              </Typography>
            </Box>
            <Divider />
          </>
        )}

        {/* Role selector — only for multi-role users */}
        {allRoles && allRoles.length > 1 && activeRole && onRoleChange && (
          <>
            <Box sx={{ px: 2, py: 1.5, display: 'flex', justifyContent: 'center' }}>
              <RoleSelector roles={allRoles} activeRole={activeRole} onChange={onRoleChange} />
            </Box>
            <Divider />
          </>
        )}

        {/* Navigation Menu */}
        <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
          <NavigationMenu userRoles={userRoles} onItemClick={onClose} variant="vertical" />
        </Box>

        <Divider />

        {/* Logout Section */}
        <List>
          <ListItem disablePadding>
            <ListItemButton onClick={handleLogout}>
              <ListItemIcon>
                <Logout />
              </ListItemIcon>
              <ListItemText primary="Logout" />
            </ListItemButton>
          </ListItem>
        </List>
      </Box>
    </Drawer>
  );
}
