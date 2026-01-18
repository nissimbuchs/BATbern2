package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Domain Event: SessionMaterialsUploaded
 * Published when materials are successfully uploaded and associated with a session
 *
 * Story 5.9: Session Materials Upload
 * AC8: Domain event emission for audit trail and future analytics
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SessionMaterialsUploadedEvent extends DomainEvent<String> {
    private final String sessionSlug;
    private final String eventCode;
    private final List<String> uploadIds;
    private final List<String> materialTypes;
    private final String uploadedBy;
    private final int materialCount;

    public SessionMaterialsUploadedEvent(
            String sessionSlug,
            String eventCode,
            List<String> uploadIds,
            List<String> materialTypes,
            String uploadedBy) {
        super(sessionSlug, "SessionMaterialsUploaded", uploadedBy);
        this.sessionSlug = sessionSlug;
        this.eventCode = eventCode;
        this.uploadIds = uploadIds;
        this.materialTypes = materialTypes;
        this.uploadedBy = uploadedBy;
        this.materialCount = uploadIds.size();
    }
}
