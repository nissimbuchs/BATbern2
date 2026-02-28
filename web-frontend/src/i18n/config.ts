import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import commonGswBe from '../../public/locales/gsw-BE/common.json';
import commonDe from '../../public/locales/de/common.json';
import commonEn from '../../public/locales/en/common.json';
import commonFr from '../../public/locales/fr/common.json';
import commonIt from '../../public/locales/it/common.json';
import commonRm from '../../public/locales/rm/common.json';
import commonEs from '../../public/locales/es/common.json';
import commonFi from '../../public/locales/fi/common.json';
import commonNl from '../../public/locales/nl/common.json';
import commonJa from '../../public/locales/ja/common.json';

import authGswBe from '../../public/locales/gsw-BE/auth.json';
import authDe from '../../public/locales/de/auth.json';
import authEn from '../../public/locales/en/auth.json';
import authFr from '../../public/locales/fr/auth.json';
import authIt from '../../public/locales/it/auth.json';
import authRm from '../../public/locales/rm/auth.json';
import authEs from '../../public/locales/es/auth.json';
import authFi from '../../public/locales/fi/auth.json';
import authNl from '../../public/locales/nl/auth.json';
import authJa from '../../public/locales/ja/auth.json';

import validationGswBe from '../../public/locales/gsw-BE/validation.json';
import validationDe from '../../public/locales/de/validation.json';
import validationEn from '../../public/locales/en/validation.json';
import validationFr from '../../public/locales/fr/validation.json';
import validationIt from '../../public/locales/it/validation.json';
import validationRm from '../../public/locales/rm/validation.json';
import validationEs from '../../public/locales/es/validation.json';
import validationFi from '../../public/locales/fi/validation.json';
import validationNl from '../../public/locales/nl/validation.json';
import validationJa from '../../public/locales/ja/validation.json';

import userManagementGswBe from '../../public/locales/gsw-BE/userManagement.json';
import userManagementDe from '../../public/locales/de/userManagement.json';
import userManagementEn from '../../public/locales/en/userManagement.json';
import userManagementFr from '../../public/locales/fr/userManagement.json';
import userManagementIt from '../../public/locales/it/userManagement.json';
import userManagementRm from '../../public/locales/rm/userManagement.json';
import userManagementEs from '../../public/locales/es/userManagement.json';
import userManagementFi from '../../public/locales/fi/userManagement.json';
import userManagementNl from '../../public/locales/nl/userManagement.json';
import userManagementJa from '../../public/locales/ja/userManagement.json';

import eventsGswBe from '../../public/locales/gsw-BE/events.json';
import eventsDe from '../../public/locales/de/events.json';
import eventsEn from '../../public/locales/en/events.json';
import eventsFr from '../../public/locales/fr/events.json';
import eventsIt from '../../public/locales/it/events.json';
import eventsRm from '../../public/locales/rm/events.json';
import eventsEs from '../../public/locales/es/events.json';
import eventsFi from '../../public/locales/fi/events.json';
import eventsNl from '../../public/locales/nl/events.json';
import eventsJa from '../../public/locales/ja/events.json';

import partnersGswBe from '../../public/locales/gsw-BE/partners.json';
import partnersDe from '../../public/locales/de/partners.json';
import partnersEn from '../../public/locales/en/partners.json';
import partnersFr from '../../public/locales/fr/partners.json';
import partnersIt from '../../public/locales/it/partners.json';
import partnersRm from '../../public/locales/rm/partners.json';
import partnersEs from '../../public/locales/es/partners.json';
import partnersFi from '../../public/locales/fi/partners.json';
import partnersNl from '../../public/locales/nl/partners.json';
import partnersJa from '../../public/locales/ja/partners.json';

import organizerGswBe from '../../public/locales/gsw-BE/organizer.json';
import organizerDe from '../../public/locales/de/organizer.json';
import organizerEn from '../../public/locales/en/organizer.json';
import organizerFr from '../../public/locales/fr/organizer.json';
import organizerIt from '../../public/locales/it/organizer.json';
import organizerRm from '../../public/locales/rm/organizer.json';
import organizerEs from '../../public/locales/es/organizer.json';
import organizerFi from '../../public/locales/fi/organizer.json';
import organizerNl from '../../public/locales/nl/organizer.json';
import organizerJa from '../../public/locales/ja/organizer.json';

import aboutGswBe from '../../public/locales/gsw-BE/about.json';
import aboutDe from '../../public/locales/de/about.json';
import aboutEn from '../../public/locales/en/about.json';
import aboutFr from '../../public/locales/fr/about.json';
import aboutIt from '../../public/locales/it/about.json';
import aboutRm from '../../public/locales/rm/about.json';
import aboutEs from '../../public/locales/es/about.json';
import aboutFi from '../../public/locales/fi/about.json';
import aboutNl from '../../public/locales/nl/about.json';
import aboutJa from '../../public/locales/ja/about.json';

import registrationGswBe from '../../public/locales/gsw-BE/registration.json';
import registrationDe from '../../public/locales/de/registration.json';
import registrationEn from '../../public/locales/en/registration.json';
import registrationFr from '../../public/locales/fr/registration.json';
import registrationIt from '../../public/locales/it/registration.json';
import registrationRm from '../../public/locales/rm/registration.json';
import registrationEs from '../../public/locales/es/registration.json';
import registrationFi from '../../public/locales/fi/registration.json';
import registrationNl from '../../public/locales/nl/registration.json';
import registrationJa from '../../public/locales/ja/registration.json';

const resources = {
  'gsw-BE': {
    common: commonGswBe,
    auth: authGswBe,
    validation: validationGswBe,
    userManagement: userManagementGswBe,
    events: eventsGswBe,
    partners: partnersGswBe,
    organizer: organizerGswBe,
    about: aboutGswBe,
    registration: registrationGswBe,
  },
  de: {
    common: commonDe,
    auth: authDe,
    validation: validationDe,
    userManagement: userManagementDe,
    events: eventsDe,
    partners: partnersDe,
    organizer: organizerDe,
    about: aboutDe,
    registration: registrationDe,
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
    registration: registrationEn,
  },
  fr: {
    common: commonFr,
    auth: authFr,
    validation: validationFr,
    userManagement: userManagementFr,
    events: eventsFr,
    partners: partnersFr,
    organizer: organizerFr,
    about: aboutFr,
    registration: registrationFr,
  },
  it: {
    common: commonIt,
    auth: authIt,
    validation: validationIt,
    userManagement: userManagementIt,
    events: eventsIt,
    partners: partnersIt,
    organizer: organizerIt,
    about: aboutIt,
    registration: registrationIt,
  },
  rm: {
    common: commonRm,
    auth: authRm,
    validation: validationRm,
    userManagement: userManagementRm,
    events: eventsRm,
    partners: partnersRm,
    organizer: organizerRm,
    about: aboutRm,
    registration: registrationRm,
  },
  es: {
    common: commonEs,
    auth: authEs,
    validation: validationEs,
    userManagement: userManagementEs,
    events: eventsEs,
    partners: partnersEs,
    organizer: organizerEs,
    about: aboutEs,
    registration: registrationEs,
  },
  fi: {
    common: commonFi,
    auth: authFi,
    validation: validationFi,
    userManagement: userManagementFi,
    events: eventsFi,
    partners: partnersFi,
    organizer: organizerFi,
    about: aboutFi,
    registration: registrationFi,
  },
  nl: {
    common: commonNl,
    auth: authNl,
    validation: validationNl,
    userManagement: userManagementNl,
    events: eventsNl,
    partners: partnersNl,
    organizer: organizerNl,
    about: aboutNl,
    registration: registrationNl,
  },
  ja: {
    common: commonJa,
    auth: authJa,
    validation: validationJa,
    userManagement: userManagementJa,
    events: eventsJa,
    partners: partnersJa,
    organizer: organizerJa,
    about: aboutJa,
    registration: registrationJa,
  },
} as const;

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'de', // Fallback if no language detected or not found
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
      'registration',
    ],
    detection: {
      order: ['localStorage', 'htmlTag'],
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

// Cache language changes to localStorage when changeLanguage() is called programmatically
// (i18next-browser-languagedetector only caches on initial detection by default)
i18n.on('languageChanged', (lng: string) => {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem('batbern-language', lng);
  }
});

export default i18n;
