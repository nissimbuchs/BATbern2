package ch.batbern.shared.types;

/**
 * Speaker Workflow State Enum - Story 5.3
 *
 * Represents the workflow states for potential speakers during event planning.
 * This enum tracks the lifecycle of a speaker from identification through confirmation.
 *
 * Workflow Phases:
 * 1. Speaker Pool Creation (IDENTIFIED - initial state when added to speaker pool)
 * 2. Outreach (CONTACTED - organizer has made contact)
 * 3. Response (READY, ACCEPTED, DECLINED - speaker has responded to invitation)
 * 4. Content & Logistics (CONTENT_SUBMITTED, QUALITY_REVIEWED, SLOT_ASSIGNED)
 * 5. Finalization (CONFIRMED - speaker locked in final agenda)
 * 6. Special States (WITHDREW - speaker backed out, OVERFLOW - too many speakers)
 *
 * Enum Value Flow (per coding-standards.md):
 * - Java/JSON/API: CONTACTED (UPPER_CASE)
 * - Database: 'contacted' (lowercase_snake_case via AttributeConverter)
 * - Converter: SpeakerWorkflowStateConverter handles Java ↔ Database conversion
 *
 * Note: Stored in speaker_pool.status column (event-management-service database)
 *
 * @see ch.batbern.speakers.converter.SpeakerWorkflowStateConverter
 */
public enum SpeakerWorkflowState {

    /**
     * Initial state when speaker is identified and added to pool.
     * Speaker has not yet been contacted.
     * Story 5.2: Speaker Brainstorming workflow
     */
    IDENTIFIED,

    /**
     * Automated invitation email has been sent to the speaker.
     * Distinct from CONTACTED (manual outreach) - used for automated invitation flow.
     * Story 6.1b: Speaker Invitation System
     */
    INVITED,

    /**
     * Organizer has made contact with the speaker.
     * Outreach has been recorded in speaker_outreach_history.
     * Story 5.3: Speaker Outreach Tracking workflow
     */
    CONTACTED,

    /**
     * Speaker has indicated interest/availability.
     * Ready to move forward with invitation.
     * Story 5.4: Speaker Status Management workflow
     */
    READY,

    /**
     * Speaker has formally accepted the invitation.
     * Commitment to present at the event.
     * Story 5.4: Speaker Status Management workflow
     */
    ACCEPTED,

    /**
     * Speaker has declined the invitation.
     * No longer pursuing this speaker.
     * Story 5.4: Speaker Status Management workflow
     */
    DECLINED,

    /**
     * Speaker has submitted presentation materials.
     * Content is ready for quality review.
     * Story 5.5: Speaker Content Collection workflow
     */
    CONTENT_SUBMITTED,

    /**
     * Presentation content has passed quality review.
     * Ready for slot assignment.
     * Story 5.5: Speaker Content Collection workflow
     */
    QUALITY_REVIEWED,

    /**
     * Speaker has been assigned a presentation time slot.
     * Waiting for final agenda confirmation.
     * Story 5.10: Slot Assignment workflow
     */
    SLOT_ASSIGNED,

    /**
     * Speaker is confirmed in the final published agenda.
     * No more changes expected.
     * Story 5.12: Agenda Finalization workflow
     */
    CONFIRMED,

    /**
     * Speaker withdrew after initial acceptance.
     * Need to find replacement speaker.
     */
    WITHDREW,

    /**
     * Speaker accepted but event has too many speakers.
     * Moved to overflow/backup list.
     * Story 5.9: Overflow Management workflow
     */
    OVERFLOW
}
