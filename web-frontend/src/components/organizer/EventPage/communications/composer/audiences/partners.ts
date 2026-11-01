import { PartnerMeetingsCompose } from '../../PartnerMeetingsCompose';
import { baseRequest, type ComposerAudience } from '../types';

/** Pseudo-template key of the partner meeting invite; never sent to the composer API. */
export const PARTNER_MEETING_INVITE = '__partner-meeting-invite__';

/**
 * Partners (Story 16.9): ONE mail with every partner on the To: line, in the organizer's language,
 * not personalised. The template list starts with the partner meeting invite (calendar entry, fixed
 * text, its own confirmation), which keeps its own body: this event's meetings with the invite
 * action. Then the PARTNER templates of the store, with preview, test send and send.
 */
export const partnersAudience: ComposerAudience = {
  audience: 'PARTNERS',
  templateCategory: 'PARTNER',
  personalized: false,
  organizerPicksLanguage: true,
  defaultTemplateKey: PARTNER_MEETING_INVITE,
  pseudoTemplates: [
    {
      key: PARTNER_MEETING_INVITE,
      labelKey: 'eventPage.mail.composer.partners.meetingInvite',
      hintKey: 'eventPage.mail.composer.partners.fixedText',
      Body: PartnerMeetingsCompose,
    },
  ],
  toRequest: (state) =>
    state.templateKey && state.templateKey !== PARTNER_MEETING_INVITE
      ? baseRequest('PARTNERS', state)
      : null,
};
