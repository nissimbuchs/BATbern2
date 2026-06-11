package ch.batbern.events.domain;

/**
 * Lifecycle of a per-session Q&A window (Story 7.5).
 *
 * <ul>
 *   <li>{@code OPEN} — accepting posts until {@code closesAt}.</li>
 *   <li>{@code FROZEN} — read-only; no further posts. Set by the scheduled freeze job when
 *       {@code closesAt} passes, or immediately by an organizer closing early.</li>
 * </ul>
 *
 * Enum value flow: UPPER_CASE in Java/JSON, {@code lowercase} in the DB via
 * {@link ch.batbern.events.converter.QnaWindowStatusConverter}.
 */
public enum QnaWindowStatus {
    OPEN,
    FROZEN
}
