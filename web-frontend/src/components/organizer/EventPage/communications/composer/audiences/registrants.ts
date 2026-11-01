import { baseRequest, type ComposerAudience } from '../types';

/** Registrants: a registrant notice, personalised, each registrant in their own language. */
export const registrantsAudience: ComposerAudience = {
  audience: 'REGISTRANTS',
  templateCategory: 'REGISTRANT_NOTICE',
  personalized: true,
  organizerPicksLanguage: false,
  // The backend requires a REGISTRANT_NOTICE template; wait until one is chosen.
  toRequest: (state) => (state.templateKey ? baseRequest('REGISTRANTS', state) : null),
};
