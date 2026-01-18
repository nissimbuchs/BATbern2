/**
 * Speaker Invitation Types - Story 6.2
 *
 * TypeScript types for the speaker self-service response portal.
 */

/**
 * Preferred time slot for presenting
 */
export type PreferredTimeSlot = 'MORNING' | 'AFTERNOON' | 'NO_PREFERENCE';

/**
 * Travel/accommodation requirements
 */
export type TravelRequirement = 'LOCAL' | 'ACCOMMODATION' | 'VIRTUAL';

/**
 * Technical equipment requirements
 */
export type TechnicalRequirement = 'MAC_ADAPTER' | 'REMOTE_OPTION' | 'SPECIAL_AV';

/**
 * Response type when speaker responds to invitation
 */
export type ResponseType = 'ACCEPTED' | 'DECLINED' | 'TENTATIVE';

/**
 * Invitation status
 */
export type InvitationStatus = 'PENDING' | 'SENT' | 'OPENED' | 'RESPONDED' | 'EXPIRED';

/**
 * Speaker preferences captured when accepting an invitation
 */
export interface SpeakerResponsePreferences {
  preferredTimeSlot?: PreferredTimeSlot;
  travelRequirements?: TravelRequirement;
  technicalRequirements?: TechnicalRequirement[];
  initialPresentationTitle?: string;
  commentsForOrganizer?: string;
}

/**
 * Request body for responding to an invitation
 */
export interface RespondToInvitationRequest {
  responseType: ResponseType;
  declineReason?: string;
  notes?: string;
  preferences?: SpeakerResponsePreferences;
}

/**
 * Invitation details returned from API
 */
export interface InvitationResponse {
  id: string;
  username: string;
  eventCode: string;
  invitationStatus: InvitationStatus;
  responseType?: ResponseType;
  declineReason?: string;
  notes?: string;
  expiresAt: string;
  createdAt: string;
  respondedAt?: string;
  createdBy: string;
  /** Personalized message from organizer - "Why we chose you" */
  personalMessage?: string;
  /** Speaker preferences (populated after ACCEPTED response) */
  preferences?: SpeakerResponsePreferences;
}
