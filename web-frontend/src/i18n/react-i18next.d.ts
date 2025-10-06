import 'react-i18next';

import commonDe from '../../public/locales/de/common.json';
import authDe from '../../public/locales/de/auth.json';
import validationDe from '../../public/locales/de/validation.json';

declare module 'react-i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common';
    resources: {
      common: typeof commonDe;
      auth: typeof authDe;
      validation: typeof validationDe;
    };
  }
}
