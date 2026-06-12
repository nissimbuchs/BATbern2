package ch.batbern.events.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A single Q&A post in the thread response (Story 7.5).
 *
 * <p>When {@code removed} is true the post is an organizer-takedown tombstone: {@code body} and
 * {@code postedByUsername} are nulled out (the public archive shows "removed by organizer"), while
 * the post still occupies its place so replies stay coherent.
 */
public record QnaPostResponse(
        UUID id,
        UUID parentPostId,
        String postedByUsername,
        String body,
        boolean removed,
        Instant createdAt) {
}
