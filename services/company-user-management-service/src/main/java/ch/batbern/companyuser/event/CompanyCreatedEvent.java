package ch.batbern.companyuser.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain event published when a new company is created
 * Published to EventBridge for downstream services to consume
 *
 * Extends shared-kernel DomainEvent for consistent event handling
 *
 * Story 1.16.2: Eliminate UUIDs from API
 * Uses String companyName instead of UUID as aggregate ID
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CompanyCreatedEvent extends DomainEvent<String> {

    /**
     * Company name (public identifier)
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
     * Username who created the company
     */
    private final String createdBy;

    /**
     * Timestamp when the company was created
     */
    private final Instant createdAt;

    public CompanyCreatedEvent(
            String companyName,
            String name,
            String displayName,
            String swissUID,
            String website,
            String industry,
            String description,
            String createdBy,
            Instant createdAt,
            String username) {
        super(companyName, "CompanyCreated", username);
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
