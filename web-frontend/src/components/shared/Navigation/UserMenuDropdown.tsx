import React, { useState } from 'react';
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
import { UserContext } from '../../../types/auth';
// import apiClient from '../../../services/api/apiClient'; // TODO: Re-enable when backend is ready

interface UserMenuDropdownProps {
  user: UserContext;
  anchorEl: HTMLElement | null;
  open: boolean;
  onClose: () => void;
  onLogout: () => void;
  onLanguageChange: (language: 'de' | 'en') => void;
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
  const [currentLanguage, setCurrentLanguage] = useState<'de' | 'en'>(
    (user.preferences.language === 'de' || user.preferences.language === 'en'
      ? user.preferences.language
      : 'de') as 'de' | 'en'
  );

  const handleProfileClick = () => {
    navigate('/profile');
    onClose();
  };

  const handleSettingsClick = () => {
    navigate('/settings');
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
    const newLang = event.target.value as 'de' | 'en';
    setCurrentLanguage(newLang);

    // Update i18n
    await i18n.changeLanguage(newLang);
    document.documentElement.lang = newLang;
    localStorage.setItem('batbern-language', newLang);

    // TODO: Re-enable when backend API is ready
    // Persist to API
    // try {
    //   await apiClient.put('/api/v1/users/me/preferences', {
    //     language: newLang,
    //   });
    // } catch (error) {
    //   console.error('Failed to persist language preference:', error);
    // }

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
      <MenuItem onClick={handleProfileClick} role="menuitem">
        <ListItemIcon>
          <PersonIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('menu.profile')}</ListItemText>
      </MenuItem>

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
            value={currentLanguage}
            onChange={handleLanguageChange}
            size="small"
            sx={{ minWidth: 100 }}
            aria-label="Language selector"
          >
            <MenuItem value="de">Deutsch</MenuItem>
            <MenuItem value="en">English</MenuItem>
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
