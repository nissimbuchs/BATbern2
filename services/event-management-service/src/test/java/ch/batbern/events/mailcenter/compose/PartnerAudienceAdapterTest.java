package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.mailcenter.render.MailPipelines;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Story 16.9 Part B AC7/AC8: the partner audience. One mail with every PARTNER-role user on the To:
 * line, sorted by company then name; users without an address are skipped; PARTNER templates only;
 * no per-person variables. Synthetic addresses only.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Partner audience adapter (Story 16.9)")
class PartnerAudienceAdapterTest {

    @Mock
    private UserApiClient userApiClient;
    @Mock
    private EmailTemplateService templates;
    @Mock
    private EmailService emailService;

    private PartnerAudienceAdapter adapter;
    private final Event event = Event.builder().id(UUID.randomUUID()).eventCode("BATbern60").title("BATbern60")
            .eventNumber(60).date(Instant.parse("2027-03-05T17:00:00Z")).organizerUsername("organizer1").build();

    @BeforeEach
    void setUp() {
        adapter = new PartnerAudienceAdapter(userApiClient, templates, emailService,
                new EmailForwardingProperties(false, null, "batbern.ch", null, null, 0),
                MailPipelines.over(emailService, templates));
    }

    @Test
    @DisplayName("AC7: one mail for everyone, so the preview does not step; audience PARTNERS")
    void notPersonalized() {
        assertThat(adapter.audience()).isEqualTo(MailContext.Audience.PARTNERS);
        assertThat(adapter.personalized()).isFalse();
        assertThat(adapter.allowedCategories()).containsExactly("PARTNER");
    }

    @Test
    @DisplayName("AC6: only templates of category PARTNER are allowed")
    void onlyPartnerTemplates() {
        when(templates.findByKeyAndLocale("partner-info", "de")).thenReturn(Optional.of(template("PARTNER")));
        when(templates.findByKeyAndLocale("venue-timetable", "de"))
                .thenReturn(Optional.of(template("VENUE_COORDINATION")));

        assertThat(adapter.allowsTemplate("partner-info")).isTrue();
        assertThat(adapter.allowsTemplate("venue-timetable")).isFalse();
        assertThat(adapter.allowsTemplate(null)).isFalse();
    }

    @Test
    @DisplayName("AC7: a template is required")
    void templateRequired() {
        assertThatThrownBy(() -> adapter.validate(request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateKey");
    }

    @Test
    @DisplayName("AC8: system and event variables only (catalog, Story 16.11), nothing per person, no credential")
    void noPerPersonVariables() {
        assertThat(adapter.allowedVariables())
                .contains("eventTitle", "eventDate", "eventNumber", "currentYear", "logoUrl", "supportUrl")
                .doesNotContain("recipientName", "salutation", "deregistrationUrl", "unsubscribeLink");
        assertThat(adapter.credentialVariables()).isEmpty();
        assertThat(adapter.variables(context("partner-info"), null, false))
                .containsEntry("eventTitle", "BATbern60")
                .containsEntry("eventDate", "Freitag, 5. März 2027")
                .doesNotContainKey("recipientName");
    }

    @Test
    @DisplayName("AC8 + #1176: deliver hands every partner address to the group send on its own")
    void deliver_groupSendWithEachAddress() {
        when(emailService.replaceVariables(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap())).thenCallRealMethod();
        EmailTemplate stored = template("PARTNER");
        stored.setSubject("Info {{eventTitle}}");
        stored.setHtmlBody("<p>{{eventTitle}}</p>");
        stored.setLocale("de");
        when(templates.findByKeyAndLocale("partner-info", "de")).thenReturn(Optional.of(stored));
        when(templates.mergeWithLayout(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        Recipients recipients = new Recipients(List.of(new ComposeRecipient("Ann Able, Bob Brown",
                "success+ann@simulator.amazonses.com,success+bob@simulator.amazonses.com", List.of(), null)),
                List.of());

        adapter.deliver(context("partner-info"), recipients, MailTrigger.forSend(UUID.randomUUID(), "organizer1"));

        org.mockito.Mockito.verify(emailService).sendGroupHtmlEmailSync(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(List.of("success+ann@simulator.amazonses.com",
                        "success+bob@simulator.amazonses.com")),
                org.mockito.ArgumentMatchers.eq(List.of()), org.mockito.ArgumentMatchers.eq("Info BATbern60"),
                org.mockito.ArgumentMatchers.eq("<p>BATbern60</p>"),
                org.mockito.ArgumentMatchers.eq("partner@batbern.ch"));
        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.never()).sendHtmlEmailSync(
                org.mockito.ArgumentMatchers.any(MailContext.class), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AC8: ONE recipient entry, every partner on To: sorted by company then name; no Cc, no organizers")
    void oneMail_allPartnersOnTo_sorted() {
        partners(
                user("zoe", "success+zoe@simulator.amazonses.com", "Zoe", "Zed", "AlphaAG"),
                user("bob", "success+bob@simulator.amazonses.com", "Bob", "Brown", "BetaGmbH"),
                user("ann", "success+ann@simulator.amazonses.com", "Ann", "Able", "AlphaAG"));

        Recipients recipients = adapter.resolveRecipients(context("partner-info"));

        assertThat(recipients.recipients()).singleElement().satisfies(r -> {
            assertThat(r.address()).isEqualTo("success+ann@simulator.amazonses.com,"
                    + "success+zoe@simulator.amazonses.com,success+bob@simulator.amazonses.com");
            assertThat(r.cc()).isEmpty();
            assertThat(r.displayName()).isEqualTo("Ann Able, Zoe Zed, Bob Brown");
        });
        assertThat(recipients.skipped()).isEmpty();
    }

    @Test
    @DisplayName("AC8: users without an email are skipped (NO_EMAIL); a repeated address once (DUPLICATE)")
    void skipsNoEmailAndDuplicates() {
        partners(
                user("ann", "success+ann@simulator.amazonses.com", "Ann", "Able", "AlphaAG"),
                user("nil", null, "Nina", "Null", "AlphaAG"),
                user("dup", "SUCCESS+ANN@simulator.amazonses.com", "Ann", "Again", "BetaGmbH"));

        Recipients recipients = adapter.resolveRecipients(context("partner-info"));

        assertThat(recipients.recipients()).singleElement()
                .satisfies(r -> assertThat(r.address()).isEqualTo("success+ann@simulator.amazonses.com"));
        assertThat(recipients.skipped()).extracting(SkippedRecipient::displayName, SkippedRecipient::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Nina Null", SkippedRecipient.NO_EMAIL),
                        org.assertj.core.groups.Tuple.tuple("Ann Again", SkippedRecipient.DUPLICATE));
    }

    @Test
    @DisplayName("AC8: nobody with an address leaves no recipient (the composer answers 400)")
    void nobody_noRecipient() {
        partners(user("nil", " ", "Nina", "Null", "AlphaAG"));

        assertThat(adapter.resolveRecipients(context("partner-info")).recipients()).isEmpty();
    }

    @Test
    @DisplayName("AC8: more addresses than SES accepts in one mail (50) is a 400, not a failed send")
    void tooManyForOneMail_rejected() {
        List<UserResponse> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(user("p" + i, "success+p" + i + "@simulator.amazonses.com", "P", "N" + i, "Co"));
        }
        partners(many.toArray(UserResponse[]::new));

        assertThatThrownBy(() -> adapter.resolveRecipients(context("partner-info")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    private void partners(UserResponse... users) {
        List<String> usernames = new ArrayList<>();
        for (UserResponse user : users) {
            usernames.add(user.getId());
            when(userApiClient.getUserByUsername(user.getId())).thenReturn(user);
        }
        when(userApiClient.getPartnerUsernames()).thenReturn(usernames);
    }

    private static UserResponse user(String username, String email, String first, String last, String company) {
        return new UserResponse().id(username).email(email).firstName(first).lastName(last).companyId(company);
    }

    private static EmailTemplate template(String category) {
        EmailTemplate template = new EmailTemplate();
        template.setCategory(category);
        return template;
    }

    private static ComposeRequest request(String templateKey) {
        return new ComposeRequest(MailContext.Audience.PARTNERS, MailContext.ContentSource.TEMPLATE, templateKey,
                "de", 0, false, null, List.of(), null);
    }

    private ComposeContext context(String templateKey) {
        return new ComposeContext(event, request(templateKey), "organizer1");
    }
}
