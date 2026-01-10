import React from 'react';
import { useTranslation } from 'react-i18next';
import { Select, MenuItem, Box, SelectChangeEvent } from '@mui/material';
import LanguageIcon from '@mui/icons-material/Language';
import { updateUserPreferences } from '../../../services/api/userApi';

const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();

  const handleLanguageChange = async (event: SelectChangeEvent<string>) => {
    const newLang = event.target.value as 'de' | 'en';
    await i18n.changeLanguage(newLang);
    document.documentElement.lang = newLang;
    localStorage.setItem('batbern-language', newLang);

    // Persist to API (backend is ready, Story 2.6)
    try {
      await updateUserPreferences({ language: newLang });
    } catch (error) {
      console.error('Failed to persist language preference:', error);
      // Continue anyway - localStorage update was successful
    }
  };

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <LanguageIcon fontSize="small" />
      <Select
        value={i18n.language}
        onChange={handleLanguageChange}
        size="small"
        sx={{ minWidth: 100 }}
        aria-label="Language selector"
      >
        <MenuItem value="de">DE</MenuItem>
        <MenuItem value="en">EN</MenuItem>
      </Select>
    </Box>
  );
};

export default LanguageSwitcher;
