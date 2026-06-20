package ch.batbern.events.watch.dto;

import java.util.List;

/**
 * Story 15.1: live agenda timing snapshot, polled over REST in place of the retired
 * STOMP {@code /topic/events/{eventCode}/state} broadcast.
 *
 * Assembled entirely from persistent storage (sessions + {@code events.live_timing_version}
 * + {@code live_timing_presence}), so successive polls landing on different Fargate tasks
 * return identical results (AC3). The {@code version} drives the ETag / 304 path.
 *
 * @param eventCode           the event
 * @param version             monotonic per-event version (drives the ETag); higher = newer
 * @param organizerPresent    whether an authenticated organizer polled within the presence TTL
 * @param currentSessionSlug  slug of the currently-active session, or null
 * @param arrivedSpeakerCount distinct speakers confirmed arrived
 * @param totalSpeakerCount   total distinct speakers expected across the event
 * @param sessions            every session with scheduled-vs-actual timing (Watch sync shape)
 */
public record LiveTimingResponse(
        String eventCode,
        long version,
        boolean organizerPresent,
        String currentSessionSlug,
        int arrivedSpeakerCount,
        int totalSpeakerCount,
        List<SessionDetail> sessions
) {
}
