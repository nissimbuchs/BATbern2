package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * #1183: the addresses that prove who a person is, so a registration made under another of their
 * profiles (a guest registration under the work address, an imported history profile) is found too.
 *
 * <p>Only the primary address and <b>verified</b> additional addresses count. An unverified additional
 * address is just a claim: anyone can type someone else's address into their own profile, and must not
 * see that person's registration or registration code (the check-in ticket). The Mail tab (Story 16.10)
 * deliberately reads unverified ones too; this does not.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PersonAddresses {

    private static final String CANCELLED = "cancelled";

    /** Prefer an active registration, then the person's own profile, then the newest. */
    public static Comparator<Registration> best(String username) {
        return Comparator.<Registration>comparingInt(r -> CANCELLED.equalsIgnoreCase(r.getStatus()) ? 1 : 0)
                .thenComparingInt(r -> username.equals(r.getAttendeeUsername()) ? 0 : 1)
                .thenComparing(Registration::getRegistrationDate,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private final UserApiClient userApiClient;

    /**
     * Lowercased primary and verified additional addresses; empty when the profile cannot be read
     * (the caller then falls back to the username alone, as before).
     */
    public Set<String> verified(String username) {
        Set<String> addresses = new LinkedHashSet<>();
        try {
            UserResponse user = userApiClient.getUserByUsername(username);
            if (user == null) {
                return addresses;
            }
            add(user.getEmail(), addresses);
            List<AdditionalEmail> additional = user.getAdditionalEmails();
            if (additional != null) {
                additional.stream().filter(extra -> extra.getVerifiedAt() != null)
                        .forEach(extra -> add(extra.getEmail(), addresses));
            }
        } catch (RuntimeException e) {
            log.debug("Addresses of {} not readable, username only: {}", username, e.getMessage());
        }
        return addresses;
    }

    private static void add(String address, Set<String> addresses) {
        if (address != null && !address.isBlank()) {
            addresses.add(address.trim().toLowerCase(Locale.ROOT));
        }
    }
}
