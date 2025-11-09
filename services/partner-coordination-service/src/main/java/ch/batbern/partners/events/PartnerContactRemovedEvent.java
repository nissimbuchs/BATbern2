package ch.batbern.partners.events;

import ch.batbern.shared.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Domain event published when a contact is removed from a partner.
 */
@Getter
public class PartnerContactRemovedEvent extends DomainEvent<UUID> {

    private final String companyName;
    private final String username;

    public PartnerContactRemovedEvent(UUID partnerId, String companyName, String username, String userId) {
        super(partnerId, "PartnerContactRemoved", userId);
        this.companyName = companyName;
        this.username = username;
    }
}
