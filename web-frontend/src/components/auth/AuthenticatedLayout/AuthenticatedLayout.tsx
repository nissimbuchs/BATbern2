/**
 * AuthenticatedLayout Component
 * Story 1.2: Role-adaptive navigation and layout
 */

import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Box,
  AppBar,
  Toolbar,
  Typography,
  IconButton,
  Menu,
  MenuItem,
  Avatar,
  Drawer,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider,
} from '@mui/material';
import {
  Menu as MenuIcon,
  Dashboard,
  Event,
  People,
  Analytics,
  Search,
  Settings,
  Logout,
} from '@mui/icons-material';
import { useAuth } from '@hooks/useAuth';
import { UserRole } from '@/types/auth';
import LanguageSwitcher from '@components/shared/LanguageSwitcher/LanguageSwitcher';

interface AuthenticatedLayoutProps {
  children: React.ReactNode;
}

interface NavigationItem {
  labelKey: string;
  path: string;
  icon: React.ReactNode;
  roles: UserRole[];
}

const navigationItemsConfig: NavigationItem[] = [
  {
    labelKey: 'common:navigation.dashboard',
    path: '/dashboard',
    icon: <Dashboard />,
    roles: ['organizer', 'speaker', 'partner', 'attendee'],
  },
  {
    labelKey: 'common:navigation.events',
    path: '/events',
    icon: <Event />,
    roles: ['organizer', 'speaker', 'partner', 'attendee'],
  },
  {
    labelKey: 'common:navigation.speakers',
    path: '/speakers',
    icon: <People />,
    roles: ['organizer', 'speaker'],
  },
  {
    labelKey: 'common:navigation.partners',
    path: '/partners',
    icon: <People />,
    roles: ['organizer', 'partner'],
  },
  {
    labelKey: 'common:navigation.analytics',
    path: '/analytics',
    icon: <Analytics />,
    roles: ['organizer', 'partner'],
  },
  {
    labelKey: 'common:navigation.content',
    path: '/content',
    icon: <Search />,
    roles: ['attendee', 'organizer'],
  },
];

export const AuthenticatedLayout: React.FC<AuthenticatedLayoutProps> = ({ children }) => {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const { t } = useTranslation('common');
  const [mobileOpen, setMobileOpen] = React.useState(false);
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const handleProfileMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleSignOut = async () => {
    handleMenuClose();
    await signOut();
    navigate('/login', { replace: true });
  };

  const getVisibleNavigationItems = () => {
    if (!user) return [];

    return navigationItemsConfig.filter((item) => item.roles.includes(user.role));
  };

  const getRoleDisplayName = (role: UserRole): string => {
    return t(`role.${role}` as const);
  };

  const drawerContent = (
    <Box>
      <Toolbar sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
        <img
          src="/BATbern_color_logo.svg"
          alt={t('app.name')}
          style={{ height: 80, width: 'auto' }}
        />
      </Toolbar>
      <Divider />
      <List>
        {getVisibleNavigationItems().map((item) => (
          <ListItem
            key={item.path}
            component="a"
            href={item.path}
            sx={{
              color: 'inherit',
              textDecoration: 'none',
              '&:hover': {
                backgroundColor: 'action.hover',
              },
            }}
          >
            <ListItemIcon>{item.icon}</ListItemIcon>
            <ListItemText primary={t(item.labelKey)} />
          </ListItem>
        ))}
      </List>
    </Box>
  );

  const drawerWidth = 240;

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          ml: { sm: `${drawerWidth}px` },
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 2, display: { sm: 'none' } }}
          >
            <MenuIcon />
          </IconButton>

          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
            {t('app.title')}
          </Typography>

          {user && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <LanguageSwitcher />
              <Typography variant="body2" sx={{ display: { xs: 'none', md: 'block' } }}>
                {user.email}
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  display: { xs: 'none', md: 'block' },
                  backgroundColor: 'rgba(255,255,255,0.1)',
                  px: 1,
                  py: 0.5,
                  borderRadius: 1,
                }}
              >
                {getRoleDisplayName(user.role)}
              </Typography>
              <IconButton
                size="large"
                aria-label="account of current user"
                aria-controls="primary-search-account-menu"
                aria-haspopup="true"
                onClick={handleProfileMenuOpen}
                color="inherit"
              >
                <Avatar sx={{ width: 32, height: 32 }}>{user.email.charAt(0).toUpperCase()}</Avatar>
              </IconButton>
            </Box>
          )}
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{
            keepMounted: true,
          }}
          sx={{
            display: { xs: 'block', sm: 'none' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth },
          }}
        >
          {drawerContent}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', sm: 'block' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth },
          }}
          open
        >
          {drawerContent}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { sm: `calc(100% - ${drawerWidth}px)` },
        }}
      >
        <Toolbar />
        {children}
      </Box>

      <Menu
        anchorEl={anchorEl}
        anchorOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        keepMounted
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleMenuClose}>
          <ListItemIcon>
            <Settings fontSize="small" />
          </ListItemIcon>
          {t('menu.profileSettings')}
        </MenuItem>
        <Divider />
        <MenuItem onClick={handleSignOut}>
          <ListItemIcon>
            <Logout fontSize="small" />
          </ListItemIcon>
          {t('menu.signOut')}
        </MenuItem>
      </Menu>
    </Box>
  );
};
