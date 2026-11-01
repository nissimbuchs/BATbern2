import type { ComposeAudience } from '@/services/mailMessageService';
import type { ComposerAudience } from '../types';
import { newsletterAudience } from './newsletter';
import { registrantsAudience } from './registrants';
import { speakersAudience } from './speakers';
import { venueAudience } from './venue';

/** The audiences the shared composer serves (Partners stays separate until Story 16.9). */
export const COMPOSER_AUDIENCES: Record<ComposeAudience, ComposerAudience> = {
  NEWSLETTER: newsletterAudience,
  REGISTRANTS: registrantsAudience,
  SPEAKERS: speakersAudience,
  VENUE: venueAudience,
};
