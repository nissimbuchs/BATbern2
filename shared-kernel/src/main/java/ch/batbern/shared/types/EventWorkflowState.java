package ch.batbern.shared.types;

/**
 * Event Workflow State Enum - Story 5.1a AC1
 *
 * Represents the 16-step event workflow state machine for the BATbern platform.
 * This enum tracks the complete lifecycle of an event from creation to archival.
 *
 * Workflow Phases:
 * 1. Event Creation (CREATED)
 * 2. Topic Selection (TOPIC_SELECTION)
 * 3. Speaker Identification (SPEAKER_BRAINSTORMING, SPEAKER_OUTREACH, SPEAKER_CONFIRMATION)
 * 4. Content Collection (CONTENT_COLLECTION, QUALITY_REVIEW)
 * 5. Threshold & Overflow (THRESHOLD_CHECK, OVERFLOW_MANAGEMENT)
 * 6. Slot Assignment (SLOT_ASSIGNMENT)
 * 7. Agenda Finalization (AGENDA_PUBLISHED, AGENDA_FINALIZED)
 * 8. Pre-Event (NEWSLETTER_SENT, EVENT_READY)
 * 9. Post-Event (PARTNER_MEETING_COMPLETE, ARCHIVED)
 *
 * Database Storage Pattern (per coding-standards.md):
 * - Java: EventWorkflowState.SPEAKER_OUTREACH (UPPER_CASE with underscores)
 * - Database: 'speaker_outreach' (lowercase with underscores)
 * - Converter: EventWorkflowStateConverter handles automatic conversion
 *
 * @see ch.batbern.events.converter.EventWorkflowStateConverter
 */
public enum EventWorkflowState {

    /**
     * Initial state when event is created.
     * New events start in this state.
     */
    CREATED,

    /**
     * Organizers are selecting topics for the event.
     * Story 5.2: Topic Selection workflow
     */
    TOPIC_SELECTION,

    /**
     * Organizers are brainstorming potential speakers.
     * Story 5.3: Speaker Brainstorming workflow
     */
    SPEAKER_BRAINSTORMING,

    /**
     * Organizers are reaching out to identified speakers.
     * Story 5.4: Speaker Outreach workflow
     */
    SPEAKER_OUTREACH,

    /**
     * Speakers have been confirmed for the event.
     * Story 5.5: Speaker Confirmation workflow
     */
    SPEAKER_CONFIRMATION,

    /**
     * Collecting presentation materials from speakers.
     * Story 5.6: Content Collection workflow
     */
    CONTENT_COLLECTION,

    /**
     * Content is undergoing quality review.
     * Story 5.7: Quality Review workflow
     */
    QUALITY_REVIEW,

    /**
     * Checking if minimum speaker threshold is met.
     * Story 5.8: Threshold Check workflow
     */
    THRESHOLD_CHECK,

    /**
     * Managing speaker overflow situation.
     * Story 5.9: Overflow Management workflow
     */
    OVERFLOW_MANAGEMENT,

    /**
     * Assigning speakers to presentation slots.
     * Story 5.10: Slot Assignment workflow
     */
    SLOT_ASSIGNMENT,

    /**
     * Agenda has been published to attendees.
     * Story 5.11: Agenda Publishing workflow
     */
    AGENDA_PUBLISHED,

    /**
     * Agenda is finalized, no more changes allowed.
     * Story 5.12: Agenda Finalization workflow
     */
    AGENDA_FINALIZED,

    /**
     * Newsletter has been sent to attendees.
     * Story 5.13: Newsletter Distribution workflow
     */
    NEWSLETTER_SENT,

    /**
     * Event is ready to begin.
     * Story 5.14: Event Readiness workflow
     */
    EVENT_READY,

    /**
     * Post-event partner meeting completed.
     * Story 5.15: Partner Meeting workflow
     */
    PARTNER_MEETING_COMPLETE,

    /**
     * Event is archived and no longer active.
     * Final state in the workflow.
     */
    ARCHIVED
}
