package ch.batbern.events.mailcenter;

import ch.batbern.events.service.AdminSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Story 16.6 AC7: the alias groups whose mail is answered in the Mail Center, so the forwarded copy
 * is replaced by a short notification. Runtime setting {@value #SETTING_KEY} (admin settings store,
 * read per mail): a list of group names, comma or space separated. Empty or missing = today's
 * forwarding. Removing a group switches it back at once, no deploy. Switched on only with the
 * owner's go, per group.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailCenterSwitch {

    public static final String SETTING_KEY = "mailcenter.notify-instead-of-forward";

    /** The alias groups that can be switched. */
    public enum Group {
        /** {@code batbern{N}-moderator@}. */
        MODERATOR,
        /** Unrecognized mail and token replies at {@code replies@} ({@code replies+{token}@}). */
        REPLIES,
        /** {@code info@}, {@code events@}, {@code support@}. */
        CONTACT
    }

    private final AdminSettingsService settings;

    public Set<Group> groups() {
        String value = settings.getSetting(SETTING_KEY).orElse("");
        Set<Group> groups = EnumSet.noneOf(Group.class);
        Arrays.stream(value.split("[,\\s]+"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .forEach(part -> {
                    try {
                        groups.add(Group.valueOf(part.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        log.warn("Setting {}: unknown group '{}' ignored", SETTING_KEY, part);
                    }
                });
        return groups;
    }

    public boolean isOn(Group group) {
        return groups().contains(group);
    }
}
