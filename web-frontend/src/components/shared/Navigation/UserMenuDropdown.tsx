import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Menu,
  MenuItem,
  Divider,
  Box,
  Typography,
  Select,
  SelectChangeEvent,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import PersonIcon from '@mui/icons-material/Person';
import SettingsIcon from '@mui/icons-material/Settings';
import HelpIcon from '@mui/icons-material/Help';
import LogoutIcon from '@mui/icons-material/Logout';
import LanguageIcon from '@mui/icons-material/Language';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import { UserContext } from '../../../types/auth';
import { updateUserPreferences } from '../../../services/api/userApi';
import type { UpdatePreferencesRequest } from '../../../types/user';

interface UserMenuDropdownProps {
  user: UserContext;
  anchorEl: HTMLElement | null;
  open: boolean;
  onClose: () => void;
  onLogout: () => void;
  onLanguageChange: (language: string) => void;
}

const UserMenuDropdown: React.FC<UserMenuDropdownProps> = ({
  user,
  anchorEl,
  open,
  onClose,
  onLogout,
  onLanguageChange,
}) => {
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();

  const handleProfileClick = () => {
    console.log('[UserMenuDropdown] Profile clicked, navigating to /account');
    console.log('[UserMenuDropdown] navigate function:', navigate);
    console.log('[UserMenuDropdown] Current location:', window.location.pathname);
    try {
      navigate('/account');
      console.log('[UserMenuDropdown] Navigation called successfully');
    } catch (error) {
      console.error('[UserMenuDropdown] Navigation error:', error);
    }
    onClose();
  };

  const handleAdministrationClick = () => {
    navigate('/organizer/admin');
    onClose();
  };

  const handleSettingsClick = () => {
    console.log('[UserMenuDropdown] Settings clicked, navigating to /account');
    console.log('[UserMenuDropdown] navigate function:', navigate);
    try {
      navigate('/account');
      console.log('[UserMenuDropdown] Navigation called successfully');
    } catch (error) {
      console.error('[UserMenuDropdown] Navigation error:', error);
    }
    onClose();
  };

  const handleHelpClick = () => {
    navigate('/help');
    onClose();
  };

  const handleLogoutClick = () => {
    onLogout();
    onClose();
  };

  const handleLanguageChange = async (event: SelectChangeEvent<string>) => {
    const newLang = event.target.value;

    // Update i18n
    await i18n.changeLanguage(newLang);
    document.documentElement.lang = newLang;
    localStorage.setItem('batbern-language', newLang);

    // Persist to API (backend is ready, Story 2.6)
    try {
      await updateUserPreferences({ language: newLang as UpdatePreferencesRequest['language'] });
      console.log('[UserMenuDropdown] Language preference persisted to backend:', newLang);
    } catch (error) {
      console.error('[UserMenuDropdown] Failed to persist language preference:', error);
      // Continue anyway - localStorage update was successful
    }

    // Notify parent
    onLanguageChange(newLang);
  };

  return (
    <Menu
      anchorEl={anchorEl}
      open={open}
      onClose={onClose}
      role="menu"
      aria-label="User menu"
      MenuListProps={{
        'aria-labelledby': 'user-menu-button',
      }}
    >
      {/* User Info Section */}
      <Box sx={{ px: 2, py: 1 }}>
        <Typography variant="body2" fontWeight="medium">
          {user.email}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {t(`role.${user.role}`)}
        </Typography>
      </Box>

      <Divider />

      {/* Profile Menu Item */}
      <MenuItem onClick={handleProfileClick} role="menuitem" data-testid="my-account-link">
        <ListItemIcon>
          <PersonIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('menu.profile')}</ListItemText>
      </MenuItem>

      {/* Administration Menu Item - organizer only (Story 10.1) */}
      {user.role === 'organizer' && (
        <MenuItem
          onClick={handleAdministrationClick}
          role="menuitem"
          data-testid="administration-link"
        >
          <ListItemIcon>
            <AdminPanelSettingsIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('menu.administration')}</ListItemText>
        </MenuItem>
      )}

      {/* Settings Menu Item */}
      <MenuItem onClick={handleSettingsClick} role="menuitem">
        <ListItemIcon>
          <SettingsIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('menu.settings')}</ListItemText>
      </MenuItem>

      {/* Language Switcher */}
      <Box sx={{ px: 2, py: 1.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <LanguageIcon fontSize="small" />
          <Select
            value={i18n.language}
            onChange={handleLanguageChange}
            size="small"
            sx={{ minWidth: 160 }}
            aria-label="Language selector"
          >
            {Object.keys(i18n.options?.resources ?? {}).map((code) => (
              <MenuItem key={code} value={code}>
                {code.toUpperCase()} — {t(`language.${code}`)}
              </MenuItem>
            ))}
          </Select>
        </Box>
      </Box>

      <Divider />

      {/* Help Menu Item */}
      <MenuItem onClick={handleHelpClick} role="menuitem">
        <ListItemIcon>
          <HelpIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('menu.help')}</ListItemText>
      </MenuItem>

      <Divider />

      {/* Logout Menu Item */}
      <MenuItem onClick={handleLogoutClick} role="menuitem">
        <ListItemIcon>
          <LogoutIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('menu.logout')}</ListItemText>
      </MenuItem>
    </Menu>
  );
};

export default UserMenuDropdown;
