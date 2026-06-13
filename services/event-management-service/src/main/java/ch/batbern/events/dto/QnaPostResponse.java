package ch.batbern.events.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A single Q&A post in the thread response (Story 7.5).
 *
 * <p>The poster is shown by display name + company logo (enriched from user_profiles/companies);
 * {@code postedByUsername} is retained as a stable id and the organizer-takedown target.
 *
 * <p>When {@code removed} is true the post is an organizer-takedown tombstone: {@code body},
 * {@code postedByUsername} and the enriched display fields are nulled out (the public archive shows
 * "removed by organizer"), while the post still occupies its place so replies stay coherent.
 */
public record QnaPostResponse(
        UUID id,
        UUID parentPostId,
        String postedByUsername,
        String postedByFirstName,
        String postedByLastName,
        String postedByCompanyName,
        String postedByCompanyLogoUrl,
        String body,
        boolean removed,
        Instant createdAt) {
}
