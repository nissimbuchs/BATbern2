package ch.batbern.events.domain;

/**
 * Speaker invitation response type - Story 6.1.
 *
 * The three possible responses a speaker can give to an invitation.
 */
public enum ResponseType {
    ACCEPTED,   // Speaker accepts the invitation
    DECLINED,   // Speaker declines the invitation
    TENTATIVE   // Speaker needs more information before deciding
}
