import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { I18nextProvider, useTranslation } from 'react-i18next';
import i18n from '@/i18n/config';
import LanguageSwitcher from './LanguageSwitcher';

// Mock useAuth hook
const mockUseAuth = {
  isAuthenticated: false,
  user: null,
  signIn: vi.fn(),
  signOut: vi.fn(),
  isLoading: false,
  error: null,
  clearError: vi.fn(),
};

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => mockUseAuth,
}));

describe('LanguageSwitcher Component', () => {
  beforeEach(() => {
    localStorage.clear();
    i18n.changeLanguage('de');
  });

  describe('Test Group 2: Language Switcher Component', () => {
    it('should_renderLanguageSelector_when_componentMounted', () => {
      render(
        <I18nextProvider i18n={i18n}>
          <LanguageSwitcher />
        </I18nextProvider>
      );

      expect(screen.getByTestId('language-selector')).toBeInTheDocument();
    });

    it('should_showGermanSelected_when_defaultLanguage', async () => {
      await i18n.changeLanguage('de');

      render(
        <I18nextProvider i18n={i18n}>
          <LanguageSwitcher />
        </I18nextProvider>
      );

      const select = screen.getByTestId('language-selector');
      expect(select).toHaveTextContent('DE');
    });

    it('should_changeLanguage_when_dropdownChanged', async () => {
      render(
        <I18nextProvider i18n={i18n}>
          <LanguageSwitcher />
        </I18nextProvider>
      );

      const select = screen.getByTestId('language-selector');

      // Select English from the native <select>
      fireEvent.change(select, { target: { value: 'en' } });

      await waitFor(() => {
        expect(i18n.language).toBe('en');
      });
    });

    it('should_persistLanguage_when_selectionMade', async () => {
      render(
        <I18nextProvider i18n={i18n}>
          <LanguageSwitcher />
        </I18nextProvider>
      );

      const select = screen.getByTestId('language-selector');

      // Change to English
      fireEvent.change(select, { target: { value: 'en' } });

      await waitFor(() => {
        expect(localStorage.getItem('batbern-language')).toBe('en');
      });
    });

    it('should_updateAllText_when_languageChanged', async () => {
      const TestComponent = () => {
        const { t } = useTranslation('auth');
        return (
          <div>
            <LanguageSwitcher />
            <div>{t('login.title')}</div>
          </div>
        );
      };

      render(
        <I18nextProvider i18n={i18n}>
          <TestComponent />
        </I18nextProvider>
      );

      // Initially should show German text
      expect(screen.getByText('Willkommen zurück')).toBeInTheDocument();

      // Change to English
      const select = screen.getByTestId('language-selector');
      fireEvent.change(select, { target: { value: 'en' } });

      // Should now show English text
      await waitFor(() => {
        expect(screen.getByText('Welcome Back')).toBeInTheDocument();
      });
    });

    it('should_updateHtmlLangAttribute_when_languageChanged', async () => {
      render(
        <I18nextProvider i18n={i18n}>
          <LanguageSwitcher />
        </I18nextProvider>
      );

      const select = screen.getByTestId('language-selector');

      // Change to English
      fireEvent.change(select, { target: { value: 'en' } });

      await waitFor(() => {
        expect(document.documentElement.lang).toBe('en');
      });
    });
  });
});
