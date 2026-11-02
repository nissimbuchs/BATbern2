import { baseRequest, contentReady, type ComposerAudience } from '../types';

/** Registrants: a registrant notice, personalised, each registrant in their own language. */
export const registrantsAudience: ComposerAudience = {
  audience: 'REGISTRANTS',
  templateCategory: 'REGISTRANT_NOTICE',
  personalized: true,
  organizerPicksLanguage: false,
  // The backend requires a REGISTRANT_NOTICE template or own text; wait until one is there.
  toRequest: (state) => (contentReady(state) ? baseRequest('REGISTRANTS', state) : null),
};
