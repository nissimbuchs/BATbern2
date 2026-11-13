import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
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
  Badge,
} from '@mui/material';
import PersonIcon from '@mui/icons-material/Person';
import SettingsIcon from '@mui/icons-material/Settings';
import HelpIcon from '@mui/icons-material/Help';
import LogoutIcon from '@mui/icons-material/Logout';
import LanguageIcon from '@mui/icons-material/Language';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import { UserContext } from '../../../types/auth';
import { updateUserSettings, type UserLanguage } from '../../../services/api/userApi';
import { USER_GUIDE_URL } from '@/config/navigationConfig';
import { scrollToFragment } from '@/features/whatsNew/scrollToFragment';

interface UserMenuDropdownProps {
  user: UserContext;
  anchorEl: HTMLElement | null;
  open: boolean;
  onClose: () => void;
  onLogout: () => void;
  onLanguageChange: (language: string) => void;
  /** Something is new on the "What's new" card: the item shows a dot. */
  whatsNewHasNew?: boolean;
}

/** The "What's new" card on the attendee dashboard (every logged-in user reaches it). */
const WHATS_NEW_PATH = '/attendee/dashboard';
const WHATS_NEW_LINK = `${WHATS_NEW_PATH}#whats-new`;

const UserMenuDropdown: React.FC<UserMenuDropdownProps> = ({
  user,
  anchorEl,
  open,
  onClose,
  onLogout,
  onLanguageChange,
  whatsNewHasNew = false,
}) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { t, i18n } = useTranslation();

  const handleProfileClick = () => {
    navigate('/account/profile');
    onClose();
  };

  const handleAdministrationClick = () => {
    navigate('/organizer/admin');
    onClose();
  };

  const handleWhatsNewClick = () => {
    navigate(WHATS_NEW_LINK);
    // Already on the dashboard (perhaps with #whats-new): the router does not scroll again.
    if (pathname === WHATS_NEW_PATH) scrollToFragment('whats-new');
    onClose();
  };

  const handleSettingsClick = () => {
    navigate('/account/settings');
    onClose();
  };

  const handleHelpClick = () => {
    window.open(USER_GUIDE_URL, '_blank', 'noopener,noreferrer');
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
      await updateUserSettings({ language: newLang as UserLanguage });
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
      aria-label={t('navigation.userMenu')}
      MenuListProps={{
        'aria-labelledby': 'user-menu-button',
      }}
    >
      {/* User Info Section */}
      <Box sx={{ px: 2, py: 1 }}>
        <Typography variant="body2" fontWeight="medium">
          {user.email}
        </Typography>
        <Typography variant="caption" color="text.secondary" data-testid="user-menu-roles">
          {/* Story 11.E.3 (cherry-pick 73d94688 + 396a9045): list every role the user holds,
              null-safe in case a legacy auth shape predates the multi-role array. */}
          {(user.roles ?? (user.role ? [user.role] : [])).map((r) => t(`role.${r}`)).join(', ')}
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

      {/* "What's new" (story 2 of 2026-11-13): every logged-in user, dot when something is new */}
      <MenuItem onClick={handleWhatsNewClick} role="menuitem" data-testid="whats-new-link">
        <ListItemIcon>
          <Badge
            variant="dot"
            color="error"
            invisible={!whatsNewHasNew}
            data-testid="whats-new-link-dot"
            data-visible={whatsNewHasNew ? 'true' : 'false'}
          >
            <AutoAwesomeIcon fontSize="small" />
          </Badge>
        </ListItemIcon>
        <ListItemText>{t('menu.whatsNew')}</ListItemText>
      </MenuItem>

      {/* Settings Menu Item */}
      <MenuItem onClick={handleSettingsClick} role="menuitem">
        <ListItemIcon>
          <SettingsIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('menu.settings')}</ListItemText>
      </MenuItem>

      {/* Administration Menu Item - organizer only (Story 10.1; Story 11.E.3 multi-role aware) */}
      {(user.roles ?? (user.role ? [user.role] : [])).includes('organizer') && (
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

      {/* Language Switcher */}
      <Box sx={{ px: 2, py: 1.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <LanguageIcon fontSize="small" />
          <Select
            value={i18n.language}
            onChange={handleLanguageChange}
            size="small"
            sx={{ minWidth: 160 }}
            aria-label={t('language.select')}
            data-testid="language-select"
          >
            {Object.keys(i18n.options?.resources ?? {}).map((code) => (
              <MenuItem key={code} value={code} data-testid={`language-option-${code}`}>
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
