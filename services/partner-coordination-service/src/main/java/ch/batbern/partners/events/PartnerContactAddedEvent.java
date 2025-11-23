package ch.batbern.partners.events;

import ch.batbern.shared.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Domain event published when a contact is added to a partner.
 */
@Getter
public class PartnerContactAddedEvent extends DomainEvent<UUID> {

    private final String companyName;
    private final String username;
    private final String contactRole;

    public PartnerContactAddedEvent(UUID partnerId, String companyName, String username,
                                    String contactRole, String userId) {
        super(partnerId, "PartnerContactAdded", userId);
        this.companyName = companyName;
        this.username = username;
        this.contactRole = contactRole;
    }
}
