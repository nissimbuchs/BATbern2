/**
 * LanguageSync Component
 *
 * Synchronizes user language preferences from backend to localStorage and i18n
 * on authentication/page load. Ensures user's saved preference overrides browser default.
 */

import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { getUserProfile } from '@/services/api/userApi';

export const LanguageSync: React.FC = () => {
  const { i18n } = useTranslation();
  const { isAuthenticated, isLoading } = useAuth();
  // Prevent re-running the sync on every render / React Strict Mode double-invoke.
  // The sync only needs to happen once per authenticated session (on login).
  const hasSynced = useRef(false);

  useEffect(() => {
    // Reset so the next login re-syncs.
    if (!isAuthenticated || isLoading) {
      hasSynced.current = false;
      return;
    }

    // Already synced for this session — don't interfere with user's active language choice.
    if (hasSynced.current) return;
    hasSynced.current = true;

    const syncLanguage = async () => {
      try {
        // Fetch user profile with preferences
        const profile = await getUserProfile(['preferences']);

        // Extract language preference
        const userLanguage = profile.preferences?.language;

        if (!userLanguage || userLanguage === i18n.language) {
          return; // No preference saved or already in sync
        }

        // Update localStorage
        localStorage.setItem('batbern-language', userLanguage);

        // Update i18n
        await i18n.changeLanguage(userLanguage);

        // Update document lang attribute
        document.documentElement.lang = userLanguage;
      } catch (error) {
        console.error('[LanguageSync] Failed to sync language preference:', error);
        hasSynced.current = false; // Allow retry on next render if fetch failed
      }
    };

    syncLanguage();
    // Intentionally omitting `i18n` from deps: LanguageSync must only run on auth
    // state changes (login/logout), never on language changes. Including `i18n` would
    // cause it to re-fetch the OLD backend preference after the user picks a new language
    // (before the PUT completes), reverting the UI choice and causing a flicker.
  }, [isAuthenticated, isLoading]);

  // This component doesn't render anything
  return null;
};
