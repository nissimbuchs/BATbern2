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
import eventsDe from '../../public/locales/de/events.json';
import eventsEn from '../../public/locales/en/events.json';
import partnersDe from '../../public/locales/de/partners.json';
import partnersEn from '../../public/locales/en/partners.json';
import organizerDe from '../../public/locales/de/organizer.json';
import organizerEn from '../../public/locales/en/organizer.json';
import aboutDe from '../../public/locales/de/about.json';
import aboutEn from '../../public/locales/en/about.json';

const resources = {
  de: {
    common: commonDe,
    auth: authDe,
    validation: validationDe,
    userManagement: userManagementDe,
    events: eventsDe,
    partners: partnersDe,
    organizer: organizerDe,
    about: aboutDe,
  },
  en: {
    common: commonEn,
    auth: authEn,
    validation: validationEn,
    userManagement: userManagementEn,
    events: eventsEn,
    partners: partnersEn,
    organizer: organizerEn,
    about: aboutEn,
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
    ns: [
      'common',
      'auth',
      'validation',
      'userManagement',
      'events',
      'partners',
      'organizer',
      'about',
    ],
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
