import { baseRequest, type ComposerAudience } from '../types';
import { VenueFields } from './VenueFields';

/**
 * Venue & Catering: one mail for the chosen roles (first To, the other Cc), in the organizer's
 * language. Not personalised.
 */
export const venueAudience: ComposerAudience = {
  audience: 'VENUE',
  templateCategory: 'VENUE_COORDINATION',
  personalized: false,
  organizerPicksLanguage: true,
  initialState: { venueRecipients: ['VENUE', 'CATERING'] },
  ExtraFields: VenueFields,
  toRequest: (state) =>
    state.templateKey && state.venueRecipients.length > 0
      ? {
          ...baseRequest('VENUE', state),
          venueRecipients: state.venueRecipients,
          venueNotes: state.venueNotes.trim() || null,
        }
      : null,
};
