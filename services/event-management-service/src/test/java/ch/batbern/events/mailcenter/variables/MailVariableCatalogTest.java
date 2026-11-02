package ch.batbern.events.mailcenter.variables;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Mail variable catalog (Story 16.11 AC1, AC3)")
class MailVariableCatalogTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[#/^]?\\s*([A-Za-z0-9_]+)\\s*}}");

    @Test
    @DisplayName("every placeholder of every seeded template is a catalog name or alias")
    void everySeededPlaceholder_isKnown() throws IOException {
        List<String> unknown = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:email-templates/*.html")) {
            Matcher m = PLACEHOLDER.matcher(r.getContentAsString(StandardCharsets.UTF_8));
            while (m.find()) {
                if (MailVariable.byName(m.group(1)).isEmpty()) {
                    unknown.add(r.getFilename() + ": " + m.group(1));
                }
            }
        }
        assertThat(unknown).isEmpty();
    }

    @Test
    @DisplayName("names and aliases are unique across the catalog")
    void namesUnique() {
        List<String> all = new ArrayList<>();
        for (MailVariable v : MailVariable.values()) {
            all.addAll(v.names());
        }
        assertThat(all).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("aliases answer with the canonical value (eventDetailLink, venue, deregistrationLink)")
    void aliasesCarryCanonicalValue() {
        Map<String, String> map = new MailVariables()
                .put(MailVariable.EVENT_URL, "https://batbern.ch/events/BATbern60")
                .put(MailVariable.VENUE_NAME, "Kornhausforum")
                .put(MailVariable.DEREGISTRATION_URL, "https://batbern.ch/deregister?token=x")
                .asMap();

        assertThat(map).containsEntry("eventUrl", "https://batbern.ch/events/BATbern60")
                .containsEntry("eventDetailLink", "https://batbern.ch/events/BATbern60")
                .containsEntry("venue", "Kornhausforum")
                .containsEntry("deregistrationLink", "https://batbern.ch/deregister?token=x");
    }

    @Test
    @DisplayName("a null value renders empty, so {{#block}} sections disappear")
    void nullIsEmpty() {
        assertThat(new MailVariables().put(MailVariable.NOTES, (String) null).asMap()).containsEntry("notes", "");
    }

    @Test
    @DisplayName("credential variables are flagged")
    void credentialsFlagged() {
        assertThat(Set.of(MailVariable.values()).stream().filter(MailVariable::credential).map(MailVariable::placeholder))
                .containsExactlyInAnyOrder("registrationCode", "confirmationUrl", "deregistrationUrl",
                        "temporaryPassword", "unsubscribeLink");
    }
}
