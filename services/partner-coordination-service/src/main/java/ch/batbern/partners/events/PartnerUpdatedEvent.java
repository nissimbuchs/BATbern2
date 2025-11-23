package ch.batbern.partners.events;

import ch.batbern.shared.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Domain event published when a partner is updated.
 */
@Getter
public class PartnerUpdatedEvent extends DomainEvent<UUID> {

    private final UUID partnerId;
    private final String companyName;
    private final String partnershipLevel;
    private final boolean isActive;

    public PartnerUpdatedEvent(UUID partnerId, String companyName, String partnershipLevel,
                               boolean isActive, String userId) {
        super(partnerId, "PartnerUpdated", userId);
        this.partnerId = partnerId;
        this.companyName = companyName;
        this.partnershipLevel = partnershipLevel;
        this.isActive = isActive;
    }
}
