package ch.batbern.companyuser.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a company is deleted
 * Published to EventBridge for downstream services to consume
 *
 * Extends shared-kernel DomainEvent for consistent event handling
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CompanyDeletedEvent extends DomainEvent<UUID> {

    private final String name;
    private final Instant deletedAt;

    public CompanyDeletedEvent(
            UUID companyId,
            String name,
            Instant deletedAt,
            UserId userId) {
        super(companyId, "CompanyDeleted", userId);
        this.name = name;
        this.deletedAt = deletedAt;
    }
}
