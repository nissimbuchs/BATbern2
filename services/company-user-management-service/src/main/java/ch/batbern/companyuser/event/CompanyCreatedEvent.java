package ch.batbern.companyuser.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a new company is created
 * Published to EventBridge for downstream services to consume
 *
 * Extends shared-kernel DomainEvent for consistent event handling
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CompanyCreatedEvent extends DomainEvent<UUID> {

    /**
     * Company name
     */
    private final String name;

    /**
     * Company display name
     */
    private final String displayName;

    /**
     * Swiss UID (optional)
     */
    private final String swissUID;

    /**
     * Company website (optional)
     */
    private final String website;

    /**
     * Company industry (optional)
     */
    private final String industry;

    /**
     * Company description (optional)
     */
    private final String description;

    /**
     * User ID who created the company
     */
    private final String createdBy;

    /**
     * Timestamp when the company was created
     */
    private final Instant createdAt;

    public CompanyCreatedEvent(
            UUID companyId,
            String name,
            String displayName,
            String swissUID,
            String website,
            String industry,
            String description,
            String createdBy,
            Instant createdAt,
            UserId userId) {
        super(companyId, "CompanyCreated", userId);
        this.name = name;
        this.displayName = displayName;
        this.swissUID = swissUID;
        this.website = website;
        this.industry = industry;
        this.description = description;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
