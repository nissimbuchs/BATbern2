package ch.batbern.companyuser.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain event published when a company is deleted
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
public class CompanyDeletedEvent extends DomainEvent<String> {

    private final String name;
    private final Instant deletedAt;

    public CompanyDeletedEvent(
            String companyName,
            String name,
            Instant deletedAt,
            String username) {
        super(companyName, "CompanyDeleted", username);
        this.name = name;
        this.deletedAt = deletedAt;
    }
}
