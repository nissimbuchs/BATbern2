package ch.batbern.shared.types;

/**
 * Event Workflow State Enum - 8-State Model
 *
 * Represents the 8-step event workflow state machine for the BATbern platform.
 * This enum tracks the complete lifecycle of an event from creation to archival.
 *
 * Workflow Phases:
 * 1. Event Creation (CREATED)
 * 2. Topic Selection (TOPIC_SELECTION)
 * 3. Speaker Identification (SPEAKER_IDENTIFICATION) - Consolidated phase for speaker management
 * 4. Slot Assignment (SLOT_ASSIGNMENT)
 * 5. Agenda Publishing (AGENDA_PUBLISHED)
 * 6. Event Execution (EVENT_LIVE, EVENT_COMPLETED)
 * 7. Archival (ARCHIVED)
 *
 * State Consolidation (from 16-state model via V30, then V82):
 * - SPEAKER_IDENTIFICATION consolidates: SPEAKER_BRAINSTORMING, SPEAKER_OUTREACH,
 *   SPEAKER_CONFIRMATION, CONTENT_COLLECTION, QUALITY_REVIEW, THRESHOLD_CHECK, OVERFLOW_MANAGEMENT
 * - ARCHIVED consolidates: PARTNER_MEETING_COMPLETE
 * - AGENDA_FINALIZED removed (V82): was a manual gate that blocked automated lifecycle;
 *   scheduler now transitions AGENDA_PUBLISHED directly to EVENT_LIVE on event day
 *
 * Automatic Transitions:
 * - Topic selection → SPEAKER_IDENTIFICATION (via TopicService)
 * - Speaker added to pool → SPEAKER_IDENTIFICATION (via SpeakerAddedToPoolEventListener)
 * - Speaker accepted → SLOT_ASSIGNMENT (via SpeakerAcceptedEventListener)
 * - All sessions timed + phase ready → AGENDA_PUBLISHED (via SessionTimingAssignedEventListener)
 * - Event date reached → EVENT_LIVE (via cron job, daily 00:01 Bern time)
 * - Event date passed → EVENT_COMPLETED (via cron job, daily 23:59 Bern time)
 *
 * Enum Value Flow (per coding-standards.md):
 * - Java/JSON/API: SPEAKER_IDENTIFICATION (UPPER_CASE with underscores)
 * - Database: 'speaker_identification' (lowercase_snake_case via AttributeConverter)
 * - Converter: EventWorkflowStateConverter handles Java ↔ Database conversion
 *
 * @see ch.batbern.events.converter.EventWorkflowStateConverter
 */
public enum EventWorkflowState {

    /**
     * Initial state when event is created.
     * New events start in this state.
     *
     * Next States:
     * - TOPIC_SELECTION (manual transition)
     * - SPEAKER_IDENTIFICATION (automatic when speaker added to pool)
     */
    CREATED,

    /**
     * Organizers are selecting topics for the event.
     *
     * Consolidates: Topic selection phase
     *
     * Next States:
     * - SPEAKER_IDENTIFICATION (automatic when topic selected OR speaker added)
     */
    TOPIC_SELECTION,

    /**
     * Building speaker pool and managing speaker workflow.
     *
     * Consolidates previous states:
     * - SPEAKER_BRAINSTORMING (brainstorming potential speakers)
     * - SPEAKER_OUTREACH (reaching out to identified speakers)
     * - SPEAKER_CONFIRMATION (confirming speaker participation)
     * - CONTENT_COLLECTION (collecting presentation materials)
     * - QUALITY_REVIEW (reviewing submitted content)
     * - THRESHOLD_CHECK (checking minimum speaker threshold)
     * - OVERFLOW_MANAGEMENT (managing speaker overflow)
     *
     * Speaker workflow states (tracked in speaker_pool table):
     * IDENTIFIED → CONTACTED → READY → ACCEPTED → CONTENT_SUBMITTED →
     * QUALITY_REVIEWED → CONFIRMED
     *
     * Next States:
     * - SLOT_ASSIGNMENT (automatic when 1+ speakers ACCEPTED)
     */
    SPEAKER_IDENTIFICATION,

    /**
     * Assigning speakers to presentation time slots.
     *
     * Next States:
     * - AGENDA_PUBLISHED (automatic when all sessions have timing AND publishing phase ready)
     */
    SLOT_ASSIGNMENT,

    /**
     * Agenda has been published to attendees.
     * Accepting registrations, agenda is public.
     *
     * Next States:
     * - EVENT_LIVE (automatic when event date reached via cron, daily 00:01 Bern time)
     */
    AGENDA_PUBLISHED,

    /**
     * Event is currently happening.
     * Active during the event day.
     *
     * Triggered: Automatically via cron job when event date is reached
     *
     * Next States:
     * - EVENT_COMPLETED (automatic when event date passed via cron)
     */
    EVENT_LIVE,

    /**
     * Event has completed, post-event processing.
     * Handles post-event activities and partner meetings.
     *
     * Triggered: Automatically via cron job when event date has passed
     *
     * Next States:
     * - ARCHIVED (manual archival)
     */
    EVENT_COMPLETED,

    /**
     * Event is archived and no longer active.
     * Terminal state in the workflow.
     *
     * Consolidates:
     * - PARTNER_MEETING_COMPLETE (partner meeting task)
     *
     * Next States: None (terminal state)
     */
    ARCHIVED;
}
