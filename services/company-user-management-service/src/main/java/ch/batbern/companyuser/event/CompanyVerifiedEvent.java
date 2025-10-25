package ch.batbern.companyuser.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain event published when a company is verified
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
public class CompanyVerifiedEvent extends DomainEvent<String> {

    private final String name;
    private final String swissUID;
    private final boolean isVerified;
    private final Instant verifiedAt;

    public CompanyVerifiedEvent(
            String companyName,
            String name,
            String swissUID,
            boolean isVerified,
            Instant verifiedAt,
            String username) {
        super(companyName, "CompanyVerified", username);
        this.name = name;
        this.swissUID = swissUID;
        this.isVerified = isVerified;
        this.verifiedAt = verifiedAt;
    }
}
