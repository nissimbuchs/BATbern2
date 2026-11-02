import { baseRequest, isOwnText, ownTextReady, type ComposerAudience } from '../types';
import { NewsletterFields } from './NewsletterFields';

/** Newsletter: one text for every subscriber, in the language the organizer picks. */
export const newsletterAudience: ComposerAudience = {
  audience: 'NEWSLETTER',
  templateCategory: 'NEWSLETTER',
  defaultTemplateKey: 'newsletter-event',
  personalized: false,
  organizerPicksLanguage: true,
  ownTextHintKey: 'eventPage.mail.composer.ownText.unsubscribeAdded',
  ExtraFields: NewsletterFields,
  toRequest: (state) =>
    isOwnText(state) && !ownTextReady(state)
      ? null
      : { ...baseRequest('NEWSLETTER', state), newsletterReminder: state.newsletterReminder },
};
