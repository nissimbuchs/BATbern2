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
export type ContentSource = MailComposeRequest['contentSource'];
export type SpeakerStatus = NonNullable<MailComposeRequest['speakerStatuses']>[number];

/** Story 16.5: the speaker statuses own text can go to, in the order they are shown. */
export const SPEAKER_STATUSES: SpeakerStatus[] = [
  'INVITED',
  'ACCEPTED',
  'CONTENT_SUBMITTED',
  'QUALITY_REVIEWED',
];

/** Everything the organizer has chosen so far. One shape for all audiences. */
export interface ComposerState {
  /** Story 16.5: a stored template, or the organizer's own subject and body. */
  contentSource: ContentSource;
  /** null = the audience's automatic choice (speakers) or its default template. */
  templateKey: string | null;
  locale: ComposerLocale;
  recipientIndex: number;
  newsletterReminder: boolean;
  speakerReminderType: SpeakerReminderType | null;
  venueRecipients: VenueRole[];
  venueNotes: string;
  /** OWN_TEXT only. One line, at most 500 characters. */
  ownSubject: string;
  /** OWN_TEXT only. HTML from the editor; the BATbern layout is added by the server. */
  ownBodyHtml: string;
  /** SPEAKERS with OWN_TEXT only, at least one. */
  speakerStatuses: SpeakerStatus[];
}

export interface ExtraFieldsProps {
  state: ComposerState;
  onChange: (patch: Partial<ComposerState>) => void;
}

/**
 * Story 16.9: a choice in the template list that is not a template of the store, e.g. the partner
 * meeting invite with its fixed text. Selecting it shows its own body in place of the preview and
 * the footer: no preview, no test send.
 */
export interface ComposerPseudoTemplate {
  key: string;
  labelKey: string;
  /** Shown above the body, e.g. that the text is still fixed. */
  hintKey: string;
  Body: React.FC<{ eventCode: string; onSent: () => void }>;
}

export interface ComposerAudience {
  audience: ComposeAudience;
  /** Admin template store category whose templates are offered. */
  templateCategory: string;
  /** Narrows the category, e.g. speakers: only the six reminder templates. */
  templateFilter?: (templateKey: string) => boolean;
  /** Preselected when present; otherwise the first template. */
  defaultTemplateKey?: string;
  /** Listed before the store's templates (Story 16.9). */
  pseudoTemplates?: ComposerPseudoTemplate[];
  /** Speakers: "Automatic" (templateKey null) is offered and preselected. */
  allowsAutomatic?: boolean;
  /** False when everyone gets the same text; the preview then does not step. */
  personalized: boolean;
  /** False when each recipient gets their own language (registrants, speakers). */
  organizerPicksLanguage: boolean;
  /**
   * Story 16.5: false when a template saved from own text could not be sent from this audience's
   * template mode (speakers: the six reminder templates only). Default true.
   */
  canSaveAsTemplate?: boolean;
  /** Story 16.5: a hint shown with own text, e.g. the newsletter's automatic unsubscribe link. */
  ownTextHintKey?: string;
  initialState?: Partial<ComposerState>;
  ExtraFields?: React.FC<ExtraFieldsProps>;
  /** The request for this state, or null while something required is missing. */
  toRequest: (state: ComposerState) => MailComposeRequest | null;
}

export const INITIAL_STATE: ComposerState = {
  contentSource: 'TEMPLATE',
  templateKey: null,
  locale: 'de',
  recipientIndex: 0,
  newsletterReminder: false,
  speakerReminderType: null,
  venueRecipients: [],
  venueNotes: '',
  ownSubject: '',
  ownBodyHtml: '',
  speakerStatuses: [...SPEAKER_STATUSES],
};

export const isOwnText = (state: ComposerState): boolean => state.contentSource === 'OWN_TEXT';

/** Own text with a subject and a body; empty own text is not previewed (AC13). */
export const ownTextReady = (state: ComposerState): boolean =>
  state.ownSubject.trim().length > 0 && state.ownBodyHtml.trim().length > 0;

/**
 * The fields every audience sends: the template, or (Story 16.5) the own subject and body without
 * a template. `confirmResend` is added by the send only, never by the preview.
 */
export function baseRequest(audience: ComposeAudience, state: ComposerState): MailComposeRequest {
  const common = {
    audience,
    locale: state.locale,
    recipientIndex: state.recipientIndex,
    newsletterReminder: false,
    confirmResend: false,
  };
  if (isOwnText(state)) {
    return {
      ...common,
      contentSource: 'OWN_TEXT',
      templateKey: null,
      ownSubject: state.ownSubject,
      ownBodyHtml: state.ownBodyHtml,
    };
  }
  return { ...common, contentSource: 'TEMPLATE', templateKey: state.templateKey };
}

/**
 * The request for an audience whose template path needs a chosen template: own text needs a
 * subject and a body instead.
 */
export function contentReady(state: ComposerState): boolean {
  return isOwnText(state) ? ownTextReady(state) : Boolean(state.templateKey);
}
