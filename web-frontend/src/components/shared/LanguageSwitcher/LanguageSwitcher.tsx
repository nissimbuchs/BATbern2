import React from 'react';
import { useTranslation } from 'react-i18next';
import { Globe } from 'lucide-react';
import { updateUserPreferences } from '../../../services/api/userApi';
import { useAuth } from '../../../hooks/useAuth';
import type { UpdatePreferencesRequest } from '../../../types/user';

/**
 * Language selector. Tailwind + a native <select> (no MUI): this control sits in the
 * public navigation, which is part of the eager homepage graph, so keeping it MUI-free
 * is what stops @mui/material from being pulled into the public entry chunk. Styling is
 * neutral (transparent bg, inherited text colour) so it reads correctly on both the
 * dark public nav and the light auth pages where it is also used.
 */
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

  const handleLanguageChange = async (event: React.ChangeEvent<HTMLSelectElement>) => {
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
    <div className="flex items-center gap-2">
      <Globe className="h-4 w-4 flex-shrink-0" aria-hidden="true" />
      <select
        value={i18n.language}
        onChange={handleLanguageChange}
        aria-label="Language selector"
        data-testid="language-selector"
        className="min-w-[160px] cursor-pointer rounded-md border border-current/20 bg-transparent px-3 py-1.5 text-sm text-inherit focus:outline-none focus:ring-2 focus:ring-ring/50"
      >
        {languages.map(({ code, label }) => (
          <option key={code} value={code} className="bg-zinc-800 text-zinc-100">
            {label}
          </option>
        ))}
      </select>
    </div>
  );
};

export default LanguageSwitcher;
