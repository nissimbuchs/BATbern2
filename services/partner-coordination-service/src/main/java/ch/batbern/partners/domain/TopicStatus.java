package ch.batbern.partners.domain;

/**
 * Lifecycle statuses for partner topic suggestions — Story 8.2.
 * PROPOSED → SELECTED | DECLINED (organizer decides)
 */
public enum TopicStatus {
    PROPOSED,
    SELECTED,
    DECLINED
}
