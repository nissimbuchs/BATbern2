/**
 * LanguageSync Component
 *
 * Synchronizes user language preferences from backend to localStorage and i18n
 * on authentication/page load. Ensures user's saved preference overrides browser default.
 */

import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { getUserProfile } from '@/services/api/userApi';

export const LanguageSync: React.FC = () => {
  const { i18n } = useTranslation();
  const { isAuthenticated, isLoading } = useAuth();

  useEffect(() => {
    // Only sync if user is authenticated and auth is not loading
    if (!isAuthenticated || isLoading) {
      return;
    }

    const syncLanguage = async () => {
      try {
        // Fetch user profile with preferences
        const profile = await getUserProfile(['preferences']);

        // Extract language preference
        const userLanguage = profile.preferences?.language;

        if (!userLanguage) {
          console.log('[LanguageSync] No language preference found in user profile');
          return;
        }

        if (userLanguage === i18n.language) {
          return; // Already in sync
        }

        console.log(
          '[LanguageSync] Syncing user language preference:',
          userLanguage,
          '(current:',
          i18n.language,
          ')'
        );

        // Update localStorage
        localStorage.setItem('batbern-language', userLanguage);

        // Update i18n
        await i18n.changeLanguage(userLanguage);

        // Update document lang attribute
        document.documentElement.lang = userLanguage;

        console.log('[LanguageSync] Language synchronized successfully to:', userLanguage);
      } catch (error) {
        console.error('[LanguageSync] Failed to sync language preference:', error);
        // Don't throw - fallback to browser/localStorage language
      }
    };

    syncLanguage();
  }, [isAuthenticated, isLoading, i18n]);

  // This component doesn't render anything
  return null;
};
