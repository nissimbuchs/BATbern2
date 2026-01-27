package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Domain Event: SpeakerContentSubmitted
 * Published when a speaker submits their presentation content via the self-service portal.
 *
 * Story 6.3: Speaker Content Self-Submission Portal
 * AC6: Triggers notification to organizers when content is submitted
 *
 * This event enables:
 * - Organizer notification (email/dashboard)
 * - Audit trail for content submissions
 * - Analytics on submission timing
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SpeakerContentSubmittedEvent extends DomainEvent<UUID> {
    private final UUID submissionId;
    private final UUID speakerPoolId;
    private final String speakerName;
    private final String eventCode;
    private final String eventTitle;
    private final String sessionTitle;
    private final String presentationTitle;
    private final int submissionVersion;
    private final boolean isResubmission;

    public SpeakerContentSubmittedEvent(
            UUID submissionId,
            UUID speakerPoolId,
            String speakerName,
            String eventCode,
            String eventTitle,
            String sessionTitle,
            String presentationTitle,
            int submissionVersion) {
        super(submissionId, "SpeakerContentSubmitted", speakerName);
        this.submissionId = submissionId;
        this.speakerPoolId = speakerPoolId;
        this.speakerName = speakerName;
        this.eventCode = eventCode;
        this.eventTitle = eventTitle;
        this.sessionTitle = sessionTitle;
        this.presentationTitle = presentationTitle;
        this.submissionVersion = submissionVersion;
        this.isResubmission = submissionVersion > 1;
    }
}
