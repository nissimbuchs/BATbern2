package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for speaker content operations (Story 5.5 AC6-10).
 *
 * Returns the created session details and updated speaker pool status
 * after content submission or when retrieving existing content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerContentResponse {

    /**
     * Speaker pool entry ID
     */
    private UUID speakerPoolId;

    /**
     * Event ID
     */
    private UUID eventId;

    /**
     * Created session ID (AC7)
     */
    private UUID sessionId;

    /**
     * Presentation title (from session.title)
     */
    private String presentationTitle;

    /**
     * Presentation abstract (from session.description)
     */
    private String presentationAbstract;

    /**
     * Speaker username (AC8)
     */
    private String username;

    /**
     * Speaker name
     */
    private String speakerName;

    /**
     * Speaker company
     */
    private String company;

    /**
     * Updated speaker workflow status (AC10)
     * Should be CONTENT_SUBMITTED after submission
     */
    private SpeakerWorkflowState status;

    /**
     * Whether content exists for this speaker
     * AC34: False if session was deleted (orphaned FK)
     */
    private boolean hasContent;

    /**
     * Warning message if any issues detected
     * AC34: "Content was lost. Please resubmit." if session deleted
     */
    private String warning;

    /**
     * When content was submitted
     */
    private Instant submittedAt;

    /**
     * Whether material has been uploaded (AC7)
     */
    private boolean hasMaterial;

    /**
     * Material URL (CloudFront CDN URL)
     */
    private String materialUrl;

    /**
     * Material filename for display
     */
    private String materialFileName;
}
