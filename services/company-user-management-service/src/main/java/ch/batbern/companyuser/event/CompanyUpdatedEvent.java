package ch.batbern.companyuser.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain event published when a company is updated
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
public class CompanyUpdatedEvent extends DomainEvent<String> {

    private final String name;
    private final String displayName;
    private final String swissUID;
    private final String website;
    private final String industry;
    private final String description;
    private final Instant updatedAt;

    public CompanyUpdatedEvent(
            String companyName,
            String name,
            String displayName,
            String swissUID,
            String website,
            String industry,
            String description,
            Instant updatedAt,
            String username) {
        super(companyName, "CompanyUpdated", username);
        this.name = name;
        this.displayName = displayName;
        this.swissUID = swissUID;
        this.website = website;
        this.industry = industry;
        this.description = description;
        this.updatedAt = updatedAt;
    }
}
