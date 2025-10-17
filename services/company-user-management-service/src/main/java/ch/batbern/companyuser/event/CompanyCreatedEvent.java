package ch.batbern.companyuser.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a new company is created
 * Published to EventBridge for downstream services to consume
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCreatedEvent {

    /**
     * Unique company identifier
     */
    private UUID companyId;

    /**
     * Company name
     */
    private String name;

    /**
     * Company display name
     */
    private String displayName;

    /**
     * Swiss UID (optional)
     */
    private String swissUID;

    /**
     * Company website (optional)
     */
    private String website;

    /**
     * Company industry (optional)
     */
    private String industry;

    /**
     * Company description (optional)
     */
    private String description;

    /**
     * User ID who created the company
     */
    private String createdBy;

    /**
     * Timestamp when the company was created
     */
    private Instant createdAt;

    /**
     * Event timestamp
     */
    private Instant eventTimestamp;
}
