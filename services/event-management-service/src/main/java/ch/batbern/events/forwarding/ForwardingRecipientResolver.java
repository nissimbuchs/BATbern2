package ch.batbern.events.forwarding;

import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.events.service.DistributionListService;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Who receives mail sent to a forwarding alias (port of the Lambda's {@code address-resolver.ts}).
 *
 * <ul>
 *   <li>{@code ok@}, {@code info@}, {@code events@} → all organizers</li>
 *   <li>{@code partner@} → all partner users</li>
 *   <li>{@code support@} → admin setting {@value #SUPPORT_CONTACTS_KEY}, else all organizers</li>
 *   <li>{@code batbern{N}-speaker|moderator|participants@} → {@link DistributionListService}</li>
 *   <li>{@code batbern{N}@} → deprecated spelling of {@code -participants@}</li>
 * </ul>
 * An unknown event resolves to nobody (logged), never to an exception.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForwardingRecipientResolver {

    static final String SUPPORT_CONTACTS_KEY = "email-forwarding.support-contacts";

    private final ForwardingDirectory directory;
    private final AdminSettingsService adminSettingsService;
    private final DistributionListService distributionListService;

    public Set<String> resolve(ForwardingAlias alias) {
        try {
            return switch (alias.kind()) {
                case ORGANIZERS -> directory.organizerEmails();
                case PARTNERS -> directory.partnerEmails();
                case SUPPORT -> supportContacts();
                case EVENT_SPEAKERS -> distributionListService.resolveSpeakers(alias.eventCode());
                case EVENT_MODERATOR -> distributionListService.resolveModerator(alias.eventCode());
                case EVENT_PARTICIPANTS -> distributionListService.resolveParticipants(alias.eventCode());
                case EVENT_PARTICIPANTS_DEPRECATED -> {
                    log.warn("Deprecated alias {}@ used, forward to {}-participants@ instead",
                            alias.localPart(), alias.localPart());
                    yield distributionListService.resolveParticipants(alias.eventCode());
                }
                case NO_REPLY, UNKNOWN -> Set.of();
            };
        } catch (NotFoundException e) {
            log.warn("Forwarding alias {}@ names an unknown event {}", alias.localPart(), alias.eventCode());
            return Set.of();
        }
    }

    private Set<String> supportContacts() {
        String configured = adminSettingsService.getSetting(SUPPORT_CONTACTS_KEY).orElse("");
        Set<String> contacts = new LinkedHashSet<>();
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .forEach(contacts::add);
        return contacts.isEmpty() ? directory.organizerEmails() : contacts;
    }
}
