package ch.batbern.partners.events;

import ch.batbern.shared.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Domain event published when a new partner is created.
 */
@Getter
public class PartnerCreatedEvent extends DomainEvent<UUID> {

    private final UUID partnerId;
    private final String companyName;
    private final String partnershipLevel;

    public PartnerCreatedEvent(UUID partnerId, String companyName, String partnershipLevel, String userId) {
        super(partnerId, "PartnerCreated", userId);
        this.partnerId = partnerId;
        this.companyName = companyName;
        this.partnershipLevel = partnershipLevel;
    }
}
