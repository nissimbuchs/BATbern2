import { describe, it, expect, beforeEach } from 'vitest';
import i18n from './config';

describe('i18n Configuration', () => {
  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear();
    // Reset language to default
    i18n.changeLanguage('de');
  });

  describe('Test Group 1: Translation Infrastructure', () => {
    it('should_loadTranslations_when_appInitializes', () => {
      expect(i18n.isInitialized).toBe(true);
      expect(i18n.options.resources).toBeDefined();
      expect(i18n.options.resources?.de).toBeDefined();
      expect(i18n.options.resources?.en).toBeDefined();
    });

    it('should_detectBrowserLanguage_when_noPreferenceStored', () => {
      // Clear any stored language preference
      localStorage.removeItem('batbern-language');

      // Reinitialize i18n to test detection
      const detectionOrder = i18n.options.detection?.order;
      expect(detectionOrder).toContain('localStorage');
      expect(detectionOrder).toContain('navigator');
    });

    it('should_useStoredLanguage_when_preferenceExists', async () => {
      // Store a language preference
      localStorage.setItem('batbern-language', 'en');

      // Change language and verify it uses localStorage
      await i18n.changeLanguage('en');
      expect(i18n.language).toBe('en');
      expect(localStorage.getItem('batbern-language')).toBe('en');
    });

    it('should_updateLocalStorage_when_languageChanged', async () => {
      await i18n.changeLanguage('en');
      expect(localStorage.getItem('batbern-language')).toBe('en');

      await i18n.changeLanguage('de');
      expect(localStorage.getItem('batbern-language')).toBe('de');
    });

    it('should_loadAllNamespaces_when_configured', () => {
      const namespaces = i18n.options.ns;
      expect(namespaces).toContain('common');
      expect(namespaces).toContain('auth');
      expect(namespaces).toContain('validation');
    });

    it('should_useGermanAsFallback_when_translationMissing', () => {
      const fallback = i18n.options.fallbackLng;
      expect(fallback === 'de' || (Array.isArray(fallback) && fallback[0] === 'de')).toBe(true);
    });

    it('should_haveCommonAsDefaultNamespace', () => {
      expect(i18n.options.defaultNS).toBe('common');
    });

    it('should_translateKeys_when_validKeyProvided', () => {
      const translation = i18n.t('common:app.name');
      expect(translation).toBe('BATbern');
    });

    it('should_translateAuthKeys_when_germanLanguage', async () => {
      await i18n.changeLanguage('de');
      const translation = i18n.t('auth:login.title');
      expect(translation).toBe('Willkommen zurück');
    });

    it('should_translateAuthKeys_when_englishLanguage', async () => {
      await i18n.changeLanguage('en');
      const translation = i18n.t('auth:login.title');
      expect(translation).toBe('Welcome Back');
    });

    it('should_translateValidationKeys_when_accessed', () => {
      const translation = i18n.t('validation:email.required');
      expect(translation).toBe('E-Mail ist erforderlich');
    });

    it('should_disableSuspense_when_inTestMode', () => {
      // Suspense is disabled in test mode to avoid React 19 test rendering issues
      expect(i18n.options.react?.useSuspense).toBe(false);
    });
  });
});
