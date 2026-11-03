package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.forwarding.ForwardingDirectory;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Story 16.10: the addresses whose mail the Mail tab of a user or a partner shows, resolved at read
 * time, so an additional email added later is picked up without a backfill. A user: primary plus every
 * additional email, verified or not (fan-out and CC reach unverified ones too). A partner: the same for
 * every user with role PARTNER of that company, the set its Contacts tab shows. Lowercased.
 */
@Component
@RequiredArgsConstructor
public class MailParticipantResolver {

    static final String PARTNER_ROLE = "PARTNER";

    private final UserApiClient userApiClient;

    /** @throws ch.batbern.events.exception.UserNotFoundException unknown username (404) */
    public List<String> ofUser(String username) {
        return List.copyOf(ForwardingDirectory.addressesOf(userApiClient.getUserByUsername(username)));
    }

    /**
     * @throws NotFoundException the company has no partner contacts and CUMS does not know it
     */
    public List<String> ofCompany(String companyName) {
        List<UserResponse> contacts = userApiClient.getUsersByCompany(companyName, PARTNER_ROLE);
        if (contacts.isEmpty() && userApiClient.getCompanyDisplayName(companyName) == null) {
            throw new NotFoundException("Company", companyName);
        }
        Set<String> addresses = new LinkedHashSet<>();
        contacts.forEach(contact -> addresses.addAll(ForwardingDirectory.addressesOf(contact)));
        return List.copyOf(addresses);
    }
}
