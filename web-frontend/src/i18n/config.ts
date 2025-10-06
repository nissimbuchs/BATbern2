import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import commonDe from '../../public/locales/de/common.json';
import commonEn from '../../public/locales/en/common.json';
import authDe from '../../public/locales/de/auth.json';
import authEn from '../../public/locales/en/auth.json';
import validationDe from '../../public/locales/de/validation.json';
import validationEn from '../../public/locales/en/validation.json';

const resources = {
  de: {
    common: commonDe,
    auth: authDe,
    validation: validationDe,
  },
  en: {
    common: commonEn,
    auth: authEn,
    validation: validationEn,
  },
} as const;

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'de',
    defaultNS: 'common',
    ns: ['common', 'auth', 'validation'],
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
