import { baseRequest, type ComposerAudience } from '../types';
import { NewsletterFields } from './NewsletterFields';

/** Newsletter: one text for every subscriber, in the language the organizer picks. */
export const newsletterAudience: ComposerAudience = {
  audience: 'NEWSLETTER',
  templateCategory: 'NEWSLETTER',
  defaultTemplateKey: 'newsletter-event',
  personalized: false,
  organizerPicksLanguage: true,
  ExtraFields: NewsletterFields,
  toRequest: (state) => ({
    ...baseRequest('NEWSLETTER', state),
    newsletterReminder: state.newsletterReminder,
  }),
};
