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
  Typography,
  Divider,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import { Close, Logout } from '@mui/icons-material';
import { NavigationMenu } from './NavigationMenu';
import type { UserRole } from '@/types/auth';

interface MobileDrawerProps {
  open: boolean;
  onClose: () => void;
  userRole: UserRole;
  userEmail?: string;
}

export function MobileDrawer({ open, onClose, userRole, userEmail }: MobileDrawerProps) {
  const handleLogout = () => {
    // Logout will be handled by parent component
    onClose();
  };

  return (
    <Drawer
      anchor="left"
      open={open}
      onClose={onClose}
      aria-label="mobile navigation"
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
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <img
              src="/logo.svg"
              alt="BATbern"
              style={{ height: 32, width: 32 }}
              onError={(e) => {
                // Fallback if logo doesn't exist
                e.currentTarget.style.display = 'none';
              }}
            />
            <Typography variant="h6" component="div" sx={{ fontWeight: 700 }}>
              BATbern
            </Typography>
          </Box>
          <IconButton onClick={onClose} edge="end" aria-label="close">
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

        {/* Navigation Menu */}
        <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
          <NavigationMenu userRole={userRole} onItemClick={onClose} variant="vertical" />
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
