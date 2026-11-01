import { baseRequest, type ComposerAudience } from '../types';
import { SpeakerFields } from './SpeakerFields';
import { REMINDER_KEY } from './speakerTemplates';

/**
 * Speakers: one of six reminder templates, or Automatic (each speaker gets the tier their own
 * deadline calls for). Personalised, each speaker in their own language.
 */
export const speakersAudience: ComposerAudience = {
  audience: 'SPEAKERS',
  templateCategory: 'SPEAKER',
  templateFilter: (key) => REMINDER_KEY.test(key),
  allowsAutomatic: true,
  personalized: true,
  organizerPicksLanguage: false,
  initialState: { speakerReminderType: 'RESPONSE' },
  ExtraFields: SpeakerFields,
  toRequest: (state) => {
    if (state.templateKey) {
      // An explicit template carries type and tier; the backend derives both from it.
      return { ...baseRequest('SPEAKERS', state), speakerReminderType: null };
    }
    if (!state.speakerReminderType) return null;
    return {
      ...baseRequest('SPEAKERS', state),
      templateKey: null,
      speakerReminderType: state.speakerReminderType,
    };
  },
};
