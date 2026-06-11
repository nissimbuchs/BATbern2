package ch.batbern.partners.domain;

/**
 * Origin of a topic suggestion.
 * <ul>
 *   <li>{@code PARTNER}   — suggested by a partner company (or by an organizer on its behalf) — Story 8.2.</li>
 *   <li>{@code COMMUNITY} — suggested by a logged-in attendee ("Topics From the Floor") — Story 7.1.</li>
 * </ul>
 * Stored UPPER_CASE in the DB via {@code @Enumerated(EnumType.STRING)}, matching the
 * {@code status} column convention.
 */
public enum TopicSource {
    PARTNER,
    COMMUNITY
}
