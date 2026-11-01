/**
 * The composer's audience contract (Story 16.8b).
 *
 * `MailComposer` holds the steps that are the same for every audience; an audience only says which
 * templates it offers, whether the organizer picks the language, which extra fields it has and how
 * its state becomes a request. The backend checks every request again (16.8a AC10), so these are
 * conveniences, not the rules.
 */
import type React from 'react';
import type { ComposeAudience, MailComposeRequest } from '@/services/mailMessageService';

export type ComposerLocale = MailComposeRequest['locale'];
export type SpeakerReminderType = NonNullable<MailComposeRequest['speakerReminderType']>;
export type VenueRole = NonNullable<MailComposeRequest['venueRecipients']>[number];

/** Everything the organizer has chosen so far. One shape for all audiences. */
export interface ComposerState {
  /** null = the audience's automatic choice (speakers) or its default template. */
  templateKey: string | null;
  locale: ComposerLocale;
  recipientIndex: number;
  newsletterReminder: boolean;
  speakerReminderType: SpeakerReminderType | null;
  venueRecipients: VenueRole[];
  venueNotes: string;
}

export interface ExtraFieldsProps {
  state: ComposerState;
  onChange: (patch: Partial<ComposerState>) => void;
}

export interface ComposerAudience {
  audience: ComposeAudience;
  /** Admin template store category whose templates are offered. */
  templateCategory: string;
  /** Narrows the category, e.g. speakers: only the six reminder templates. */
  templateFilter?: (templateKey: string) => boolean;
  /** Preselected when present; otherwise the first template. */
  defaultTemplateKey?: string;
  /** Speakers: "Automatic" (templateKey null) is offered and preselected. */
  allowsAutomatic?: boolean;
  /** False when everyone gets the same text; the preview then does not step. */
  personalized: boolean;
  /** False when each recipient gets their own language (registrants, speakers). */
  organizerPicksLanguage: boolean;
  initialState?: Partial<ComposerState>;
  ExtraFields?: React.FC<ExtraFieldsProps>;
  /** The request for this state, or null while something required is missing. */
  toRequest: (state: ComposerState) => MailComposeRequest | null;
}

export const INITIAL_STATE: ComposerState = {
  templateKey: null,
  locale: 'de',
  recipientIndex: 0,
  newsletterReminder: false,
  speakerReminderType: null,
  venueRecipients: [],
  venueNotes: '',
};

/** The fields every audience sends. */
export function baseRequest(audience: ComposeAudience, state: ComposerState): MailComposeRequest {
  return {
    audience,
    contentSource: 'TEMPLATE',
    templateKey: state.templateKey,
    locale: state.locale,
    recipientIndex: state.recipientIndex,
    newsletterReminder: false,
  };
}
