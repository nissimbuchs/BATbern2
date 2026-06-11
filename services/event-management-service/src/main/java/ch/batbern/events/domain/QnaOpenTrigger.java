package ch.batbern.events.domain;

/**
 * When per-session Q&A windows open for an event (Story 7.5 rework). Configured per event in the
 * organizer Settings tab.
 */
public enum QnaOpenTrigger {
    /** Open when the event reaches EVENT_COMPLETED — the "digital afterglow" (Epic 7 NFR6 default). */
    EVENT_COMPLETED,
    /**
     * Open as soon as the speakers publishing phase is published — a deliberate, organizer-chosen
     * reversal of the afterglow guardrail (pre-event discussion). Opt-in per event.
     */
    SPEAKERS_PUBLISHED
}
