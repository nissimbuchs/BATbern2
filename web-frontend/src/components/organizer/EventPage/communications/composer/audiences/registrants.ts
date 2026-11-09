import { baseRequest, contentReady, isOwnText, type ComposerAudience } from '../types';
import { RegistrantFields } from './RegistrantFields';

/** Registrants: a registrant notice, personalised, each registrant in their own language. */
export const registrantsAudience: ComposerAudience = {
  audience: 'REGISTRANTS',
  templateCategory: 'REGISTRANT_NOTICE',
  personalized: true,
  organizerPicksLanguage: false,
  ExtraFields: RegistrantFields,
  // The backend requires a REGISTRANT_NOTICE template or own text; wait until one is there.
  toRequest: (state) => {
    if (!contentReady(state)) return null;
    if (!isOwnText(state)) return baseRequest('REGISTRANTS', state);
    // Own text goes to the chosen registration statuses, e.g. the no-shows only.
    if (state.registrantStatuses.length === 0) return null;
    return { ...baseRequest('REGISTRANTS', state), registrantStatuses: state.registrantStatuses };
  },
};
