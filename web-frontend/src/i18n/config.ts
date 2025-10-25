import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import commonDe from '../../public/locales/de/common.json';
import commonEn from '../../public/locales/en/common.json';
import authDe from '../../public/locales/de/auth.json';
import authEn from '../../public/locales/en/auth.json';
import validationDe from '../../public/locales/de/validation.json';
import validationEn from '../../public/locales/en/validation.json';
import userManagementDe from '../../public/locales/de/userManagement.json';
import userManagementEn from '../../public/locales/en/userManagement.json';

const resources = {
  de: {
    common: commonDe,
    auth: authDe,
    validation: validationDe,
    userManagement: userManagementDe,
  },
  en: {
    common: commonEn,
    auth: authEn,
    validation: validationEn,
    userManagement: userManagementEn,
  },
} as const;

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    lng: 'de', // Default language for first access
    fallbackLng: 'de', // Fallback if language not found
    defaultNS: 'common',
    ns: ['common', 'auth', 'validation', 'userManagement'],
    detection: {
      order: ['localStorage', 'navigator', 'htmlTag'],
      caches: ['localStorage'],
      lookupLocalStorage: 'batbern-language',
    },
    interpolation: {
      escapeValue: false, // React already escapes
    },
    react: {
      useSuspense: typeof window !== 'undefined' && !import.meta.env.VITEST,
    },
  });

export default i18n;
