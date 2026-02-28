import React from 'react';
import { useTranslation } from 'react-i18next';
import { Select, MenuItem, Box, SelectChangeEvent } from '@mui/material';
import LanguageIcon from '@mui/icons-material/Language';
import { updateUserPreferences } from '../../../services/api/userApi';
import { useAuth } from '../../../hooks/useAuth';
import type { UpdatePreferencesRequest } from '../../../types/user';

const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();
  const { isAuthenticated } = useAuth();

  const languages = [
    { code: 'de', label: 'DE — Deutsch' },
    { code: 'en', label: 'EN — English' },
    { code: 'fr', label: 'FR — Français' },
    { code: 'it', label: 'IT — Italiano' },
    { code: 'rm', label: 'RM — Rumantsch' },
    { code: 'es', label: 'ES — Español' },
    { code: 'fi', label: 'FI — Suomi' },
    { code: 'nl', label: 'NL — Nederlands' },
    { code: 'ja', label: 'JA — 日本語' },
  ];

  const handleLanguageChange = async (event: SelectChangeEvent<string>) => {
    const newLang = event.target.value;
    await i18n.changeLanguage(newLang);
    document.documentElement.lang = newLang;
    localStorage.setItem('batbern-language', newLang);

    // Persist to API only if authenticated (backend is ready, Story 2.6)
    if (isAuthenticated) {
      try {
        await updateUserPreferences({ language: newLang as UpdatePreferencesRequest['language'] });
        console.log('[LanguageSwitcher] Language preference saved to backend');
      } catch (error) {
        console.error('[LanguageSwitcher] Failed to persist language preference:', error);
        // Continue anyway - localStorage update was successful
      }
    } else {
      console.log('[LanguageSwitcher] Skipping backend save - user not authenticated');
    }
  };

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <LanguageIcon fontSize="small" />
      <Select
        value={i18n.language}
        onChange={handleLanguageChange}
        size="small"
        sx={{ minWidth: 160 }}
        inputProps={{ 'aria-label': 'Language selector' }}
      >
        {languages.map(({ code, label }) => (
          <MenuItem key={code} value={code}>
            {label}
          </MenuItem>
        ))}
      </Select>
    </Box>
  );
};

export default LanguageSwitcher;
