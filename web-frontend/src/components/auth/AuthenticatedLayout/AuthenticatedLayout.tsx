/**
 * AuthenticatedLayout Component
 * Story 1.2: Role-adaptive navigation and layout
 */

import React from 'react'
import {
  Box,
  AppBar,
  Toolbar,
  Typography,
  IconButton,
  Menu,
  MenuItem,
  Avatar,
  Button,
  Drawer,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider
} from '@mui/material'
import {
  Menu as MenuIcon,
  AccountCircle,
  Dashboard,
  Event,
  People,
  Analytics,
  Search,
  Settings,
  Logout
} from '@mui/icons-material'
import { useAuth } from '@hooks/useAuth'
import { UserRole } from '@types/auth'

interface AuthenticatedLayoutProps {
  children: React.ReactNode
}

interface NavigationItem {
  label: string
  path: string
  icon: React.ReactNode
  roles: UserRole[]
}

const navigationItems: NavigationItem[] = [
  {
    label: 'Dashboard',
    path: '/dashboard',
    icon: <Dashboard />,
    roles: ['organizer', 'speaker', 'partner', 'attendee']
  },
  {
    label: 'Events',
    path: '/events',
    icon: <Event />,
    roles: ['organizer', 'speaker', 'partner', 'attendee']
  },
  {
    label: 'Speakers',
    path: '/speakers',
    icon: <People />,
    roles: ['organizer', 'speaker']
  },
  {
    label: 'Partners',
    path: '/partners',
    icon: <People />,
    roles: ['organizer', 'partner']
  },
  {
    label: 'Analytics',
    path: '/analytics',
    icon: <Analytics />,
    roles: ['organizer', 'partner']
  },
  {
    label: 'Content Search',
    path: '/content',
    icon: <Search />,
    roles: ['attendee', 'organizer']
  }
]

export const AuthenticatedLayout: React.FC<AuthenticatedLayoutProps> = ({ children }) => {
  const { user, signOut, hasRole } = useAuth()
  const [mobileOpen, setMobileOpen] = React.useState(false)
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null)

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen)
  }

  const handleProfileMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget)
  }

  const handleMenuClose = () => {
    setAnchorEl(null)
  }

  const handleSignOut = async () => {
    handleMenuClose()
    await signOut()
  }

  const getVisibleNavigationItems = () => {
    if (!user) return []

    return navigationItems.filter(item =>
      item.roles.includes(user.role)
    )
  }

  const getRoleDisplayName = (role: UserRole): string => {
    const roleNames = {
      organizer: 'Event Organizer',
      speaker: 'Speaker',
      partner: 'Partner',
      attendee: 'Attendee'
    }
    return roleNames[role] || role
  }

  const drawerContent = (
    <Box>
      <Toolbar>
        <Typography variant="h6" noWrap component="div">
          BATbern
        </Typography>
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
                backgroundColor: 'action.hover'
              }
            }}
          >
            <ListItemIcon>
              {item.icon}
            </ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItem>
        ))}
      </List>
    </Box>
  )

  const drawerWidth = 240

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
            BATbern Platform
          </Typography>

          {user && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
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
                  borderRadius: 1
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
                <Avatar sx={{ width: 32, height: 32 }}>
                  {user.email.charAt(0).toUpperCase()}
                </Avatar>
              </IconButton>
            </Box>
          )}
        </Toolbar>
      </AppBar>

      <Box
        component="nav"
        sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}
      >
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
          Profile Settings
        </MenuItem>
        <Divider />
        <MenuItem onClick={handleSignOut}>
          <ListItemIcon>
            <Logout fontSize="small" />
          </ListItemIcon>
          Sign Out
        </MenuItem>
      </Menu>
    </Box>
  )
}