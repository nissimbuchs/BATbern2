/**
 * Wire-shape builders for response types whose properties the API always sends (4.9b: the specs
 * mark them `required`, nullable or not). A test states what matters and gets the rest in the
 * shape the service sends: present, `null` where the service sends `null`. Typed from
 * `src/types/generated/`, so a property the spec adds or drops fails `npm run type-check` here.
 */
import type { components as sessions } from '../../types/generated/event-sessions-api.types';
import type { components as speakers } from '../../types/generated/event-speakers-api.types';
import type { components as topics } from '../../types/generated/topics-api.types';
import type { components as newsletter } from '../../types/generated/event-newsletter-api.types';
import type { components as partners } from '../../types/generated/partner-api.types';
import type { components as users } from '../../types/generated/user-api.types';
import type { components as tasks } from '../../types/generated/event-tasks-api.types';
import type { components as mail } from '../../types/generated/event-mail-api.types';
import { FIXTURE_CREATED_AT } from './people.ts';

type Session = sessions['schemas']['Session'];
type SessionResponse = sessions['schemas']['SessionResponse'];
type EventTaskResponse = tasks['schemas']['EventTaskResponse'];
type MailMessageSummary = mail['schemas']['MailMessageSummary'];
type MailConversationSummary = mail['schemas']['MailConversationSummary'];
type SessionSpeaker = sessions['schemas']['SessionSpeaker'];
type Topic = topics['schemas']['Topic'];
type TopicUsageHistory = topics['schemas']['TopicUsageHistory'];
type SubscriberResponse = newsletter['schemas']['SubscriberResponse'];
type SpeakerPoolResponse = speakers['schemas']['SpeakerPoolResponse'];
type SpeakerContentResponse = speakers['schemas']['SpeakerContentResponse'];
type PartnerResponse = partners['schemas']['PartnerResponse'];
type ProfileClaimCandidate = users['schemas']['ProfileClaimCandidate'];

/** A session as the sessions API sends it; defaults to an unscheduled talk without speakers. */
export function aSession(overrides: Partial<Session> = {}): Session {
  return {
    sessionSlug: 'cloud-native-architektur',
    eventCode: 'BATbern61',
    title: 'Cloud-native Architektur',
    description: null,
    sessionType: 'presentation',
    startTime: null,
    endTime: null,
    room: null,
    capacity: null,
    language: null,
    createdAt: FIXTURE_CREATED_AT,
    updatedAt: FIXTURE_CREATED_AT,
    speakers: [],
    materialsStatus: 'NONE',
    ...overrides,
  };
}

/** A session as the single-event detail embeds it (with its materials); defaults to none. */
export function aSessionResponse(overrides: Partial<SessionResponse> = {}): SessionResponse {
  return { ...aSession(), materials: null, materialsCount: 0, ...overrides };
}

/** An organizer task of an event; defaults to an open, unassigned task. */
export function anEventTask(overrides: Partial<EventTaskResponse> = {}): EventTaskResponse {
  return {
    id: '7d1f0c2a-5b3e-4c1d-9a8f-000000000001',
    eventId: '7e6d5c4b-3a29-4180-9f6e-5d4c3b2a1908',
    eventCode: 'BATbern61',
    templateId: null,
    taskName: 'Book the venue',
    triggerState: 'CREATED',
    dueDate: null,
    assignedOrganizerUsername: null,
    status: 'todo',
    notes: null,
    completedDate: null,
    completedByUsername: null,
    createdAt: FIXTURE_CREATED_AT,
    updatedAt: FIXTURE_CREATED_AT,
    ...overrides,
  };
}

/** A speaker of a session; defaults to a confirmed primary speaker without company. */
export function aSessionSpeaker(overrides: Partial<SessionSpeaker> = {}): SessionSpeaker {
  return {
    username: 'hongan.meier',
    firstName: 'Hông Ân',
    lastName: 'Meier',
    company: null,
    companyDisplayName: null,
    profilePictureUrl: null,
    companyLogoUrl: null,
    bio: null,
    speakerRole: 'PRIMARY_SPEAKER',
    presentationTitle: null,
    isConfirmed: true,
    ...overrides,
  };
}

/** A topic as the topics API sends it; defaults to an unused, active technical topic. */
export function aTopic(overrides: Partial<Topic> = {}): Topic {
  return {
    topicCode: 'cloud-native',
    title: 'Cloud Native',
    description: null,
    category: 'technical',
    createdDate: FIXTURE_CREATED_AT,
    lastUsedDate: null,
    usageCount: 0,
    stalenessScore: null,
    colorZone: null,
    status: null,
    similarityScores: null,
    active: true,
    createdAt: FIXTURE_CREATED_AT,
    updatedAt: FIXTURE_CREATED_AT,
    usageHistory: null,
    ...overrides,
  };
}

/** One use of a topic by an event. */
export function aTopicUsage(overrides: Partial<TopicUsageHistory> = {}): TopicUsageHistory {
  const eventNumber = overrides.eventNumber ?? 58;
  return {
    eventNumber,
    eventCode: `BATbern${eventNumber}`,
    eventDate: FIXTURE_CREATED_AT,
    usedDate: FIXTURE_CREATED_AT,
    attendance: null,
    ...overrides,
  };
}

/** A newsletter subscriber; defaults to an anonymous, active subscriber. */
export function aSubscriber(overrides: Partial<SubscriberResponse> = {}): SubscriberResponse {
  return {
    id: '0b9f8e2a-1c3d-4e5f-8a7b-9c0d1e2f3a4b',
    email: 'subscriber@example.com',
    firstName: null,
    language: 'de',
    source: 'explicit',
    username: null,
    subscribedAt: FIXTURE_CREATED_AT,
    unsubscribedAt: null,
    bounceType: null,
    bounceCount: null,
    lastBouncedAt: null,
    suppressedAt: null,
    ...overrides,
  };
}

/** A speaker pool entry; defaults to a freshly identified speaker. */
export function aSpeakerPoolEntry(
  overrides: Partial<SpeakerPoolResponse> = {}
): SpeakerPoolResponse {
  return {
    id: '3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607',
    eventId: '7e6d5c4b-3a29-4180-9f6e-5d4c3b2a1908',
    speakerName: 'Hông Ân Meier',
    company: null,
    companyDisplayName: null,
    expertise: null,
    assignedOrganizerId: null,
    status: 'IDENTIFIED',
    notes: null,
    source: 'organizer_added',
    proposedByUsername: null,
    proposedSessionTitle: null,
    proposedAbstract: null,
    createdAt: FIXTURE_CREATED_AT,
    updatedAt: FIXTURE_CREATED_AT,
    remindersDisabled: false,
    sessionId: null,
    sessionSlug: null,
    username: null,
    email: null,
    invitedAt: null,
    responseDeadline: null,
    contentDeadline: null,
    acceptedAt: null,
    declinedAt: null,
    declineReason: null,
    preferredTimeSlot: null,
    travelRequirements: null,
    technicalRequirements: null,
    initialPresentationTitle: null,
    preferenceComments: null,
    contentSubmittedAt: null,
    submittedTitle: null,
    submittedAbstract: null,
    isSlotAssigned: false,
    ...overrides,
  };
}

/** A speaker's submitted content; defaults to nothing submitted yet. */
export function aSpeakerContent(
  overrides: Partial<SpeakerContentResponse> = {}
): SpeakerContentResponse {
  return {
    speakerPoolId: '3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607',
    eventId: '7e6d5c4b-3a29-4180-9f6e-5d4c3b2a1908',
    sessionId: null,
    presentationTitle: null,
    presentationAbstract: null,
    username: null,
    speakerName: 'Hông Ân Meier',
    company: null,
    status: 'ACCEPTED',
    hasContent: false,
    warning: null,
    submittedAt: null,
    hasMaterial: false,
    materialUrl: null,
    materialFileName: null,
    ...overrides,
  };
}

/** A partner as `GET /partners` sends it, without the company and contact expansions. */
export function aPartner(overrides: Partial<PartnerResponse> = {}): PartnerResponse {
  return {
    companyName: 'buecherstube',
    partnershipLevel: 'GOLD',
    partnershipStartDate: '2025-01-01',
    partnershipEndDate: null,
    isActive: true,
    company: null,
    contacts: null,
    ...overrides,
  };
}

/** A profile a signed-in user may claim. */
export function aClaimCandidate(
  overrides: Partial<ProfileClaimCandidate> = {}
): ProfileClaimCandidate {
  return {
    candidateId: 'c0ffee00-1c3d-4e5f-8a7b-9c0d1e2f3a4b',
    maskedEmail: 'a***@example.com',
    companyName: null,
    requestPending: false,
    placeholderAddress: false,
    ...overrides,
  };
}

/** One sent mail of the outbound log; defaults to a delivered template mail to the speakers. */
export function aMailSummary(overrides: Partial<MailMessageSummary> = {}): MailMessageSummary {
  return {
    mailKey: 'BATbern61-1',
    sentAt: FIXTURE_CREATED_AT,
    senderUsername: null,
    audience: 'SPEAKERS',
    templateKey: null,
    contentSource: 'TEMPLATE',
    subject: null,
    recipientCount: 1,
    deliveredCount: 1,
    failedCount: 0,
    messageCount: 1,
    newsletterSendId: null,
    newsletterStatus: null,
    test: false,
    reference: null,
    direction: 'OUT',
    fromAddress: null,
    fromName: null,
    eventCode: null,
    conversationKey: null,
    spam: null,
    participantStatus: null,
    attachments: null,
    ...overrides,
  };
}

/** A Mail Center conversation; defaults to an open, unassigned question. */
export function aConversation(
  overrides: Partial<MailConversationSummary> = {}
): MailConversationSummary {
  return {
    conversationKey: 'conv-1',
    eventCode: null,
    audience: 'SPEAKERS',
    subject: 'Frage',
    counterpartAddress: 'success+speaker@simulator.amazonses.com',
    counterpartName: null,
    status: 'OPEN',
    assigneeUsername: null,
    via: 'info@batbern.ch',
    lastMessageAt: FIXTURE_CREATED_AT,
    messageCount: 1,
    mergeRequestId: null,
    logoRequestId: null,
    ...overrides,
  };
}
