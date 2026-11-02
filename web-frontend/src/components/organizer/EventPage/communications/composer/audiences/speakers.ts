import { baseRequest, isOwnText, ownTextReady, type ComposerAudience } from '../types';
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
  // Template mode sends the six reminders only; a saved speaker template could never be sent.
  canSaveAsTemplate: false,
  initialState: { speakerReminderType: 'RESPONSE' },
  ExtraFields: SpeakerFields,
  toRequest: (state) => {
    if (isOwnText(state)) {
      // Own text goes to the chosen statuses, at least one (AC11).
      if (!ownTextReady(state) || state.speakerStatuses.length === 0) return null;
      return {
        ...baseRequest('SPEAKERS', state),
        speakerReminderType: null,
        speakerStatuses: state.speakerStatuses,
      };
    }
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
