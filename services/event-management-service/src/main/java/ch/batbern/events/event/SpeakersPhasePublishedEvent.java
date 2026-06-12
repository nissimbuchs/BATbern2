package ch.batbern.events.event;

/**
 * Published by {@code PublishingService} when an event's SPEAKERS publishing phase goes live.
 *
 * <p>Story 7.5 rework: lets the Q&A open on the speakers phase (for events whose
 * {@code qnaOpenTrigger == SPEAKERS_PUBLISHED}) via {@code SessionQnaWindowListener}, mirroring the
 * EVENT_COMPLETED path. A plain in-service application event (Spring publishes any object).
 */
public record SpeakersPhasePublishedEvent(String eventCode) {
}
