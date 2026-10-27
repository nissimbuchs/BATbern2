package ch.batbern.events.forwarding;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Email addresses of all organizers / partners, primary plus every additional email
 * (Story 10.32), lowercased and deduplicated.
 *
 * <p>Reads CUMS in-process over the existing {@link UserApiClient} (VPC-internal, no public
 * endpoint). Per-user lookups are cached in {@code userApiCache}. A CUMS failure on the role
 * list propagates: the caller must not mistake "CUMS down" for "nobody is an organizer".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForwardingDirectory {

    private final UserApiClient userApiClient;

    public Set<String> organizerEmails() {
        return emailsOf(userApiClient.getOrganizerUsernames());
    }

    public Set<String> partnerEmails() {
        return emailsOf(userApiClient.getPartnerUsernames());
    }

    private Set<String> emailsOf(List<String> usernames) {
        Set<String> emails = new LinkedHashSet<>();
        for (String username : usernames) {
            try {
                UserResponse user = userApiClient.getUserByUsername(username);
                add(user.getEmail(), emails);
                List<AdditionalEmail> additional = user.getAdditionalEmails();
                if (additional != null) {
                    for (AdditionalEmail extra : additional) {
                        add(extra.getEmail(), emails);
                    }
                }
            } catch (UserNotFoundException e) {
                log.warn("Forwarding directory: user '{}' not found in CUMS, skipping", username);
            }
        }
        return emails;
    }

    private static void add(String email, Set<String> sink) {
        if (email != null && !email.isBlank()) {
            sink.add(email.trim().toLowerCase(Locale.ROOT));
        }
    }
}
