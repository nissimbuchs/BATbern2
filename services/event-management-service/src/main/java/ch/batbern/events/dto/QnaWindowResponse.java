package ch.batbern.events.dto;

import java.time.Instant;
import java.util.List;

/**
 * The Q&A thread for a session (Story 7.5).
 *
 * <p>{@code status} is {@code OPEN} or {@code FROZEN}. Posts are returned oldest-first; the client
 * nests answers under their {@code parentPostId}. Public read returns the same shape whether open
 * or frozen — the difference is whether the posting UI is enabled.
 */
public record QnaWindowResponse(
        String status,
        Instant opensAt,
        Instant closesAt,
        List<QnaPostResponse> posts) {
}
