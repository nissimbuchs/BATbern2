package ch.batbern.companyuser.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a company is verified
 * Published to EventBridge for downstream services to consume
 *
 * Extends shared-kernel DomainEvent for consistent event handling
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CompanyVerifiedEvent extends DomainEvent<UUID> {

    private final String name;
    private final String swissUID;
    private final boolean isVerified;
    private final Instant verifiedAt;

    public CompanyVerifiedEvent(
            UUID companyId,
            String name,
            String swissUID,
            boolean isVerified,
            Instant verifiedAt,
            UserId userId) {
        super(companyId, "CompanyVerified", userId);
        this.name = name;
        this.swissUID = swissUID;
        this.isVerified = isVerified;
        this.verifiedAt = verifiedAt;
    }
}
