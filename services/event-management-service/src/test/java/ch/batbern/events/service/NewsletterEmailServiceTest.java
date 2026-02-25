package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.UserPortraitProjection;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NewsletterEmailService (Story 10.7 — AC8, AC10, AC12).
 *
 * Focuses on:
 * - reminderPrefix substitution in buildVariables
 * - unsubscribeLink presence in every rendered output
 * - buildSpeakersSection returns empty string when workflow state is not published
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewsletterEmailService Unit Tests")
class NewsletterEmailServiceTest {

    @Mock
    private EmailService emailService;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private NewsletterSubscriberService subscriberService;
    @Mock
    private NewsletterSendRepository sendRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionUserRepository sessionUserRepository;
    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private NewsletterEmailService newsletterEmailService;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(newsletterEmailService, "baseUrl", "https://batbern.ch");

        testEvent = new Event();
        testEvent.setEventCode("BATbern58");
        testEvent.setTitle("AI in der Software Entwicklung");
        testEvent.setDate(Instant.parse("2026-03-06T15:00:00Z"));
    }

    // ── buildVariables: reminderPrefix ────────────────────────────────────────

    @Test
    @DisplayName("buildVariables: isReminder=false → empty reminderPrefix (DE)")
    void buildVariables_notReminder_de_emptyPrefix() {
        Map<String, String> vars = newsletterEmailService.buildVariables(testEvent, "de", false, "https://batbern.ch/unsubscribe?token=TEST");

        assertThat(vars.get("reminderPrefix")).isEmpty();
    }

    @Test
    @DisplayName("buildVariables: isReminder=true → 'Erinnerung: ' prefix (DE)")
    void buildVariables_reminder_de_germanPrefix() {
        Map<String, String> vars = newsletterEmailService.buildVariables(testEvent, "de", true, "https://batbern.ch/unsubscribe?token=TEST");

        assertThat(vars.get("reminderPrefix")).isEqualTo("Erinnerung: ");
    }

    @Test
    @DisplayName("buildVariables: isReminder=true → 'Reminder: ' prefix (EN)")
    void buildVariables_reminder_en_englishPrefix() {
        Map<String, String> vars = newsletterEmailService.buildVariables(testEvent, "en", true, "https://batbern.ch/unsubscribe?token=TEST");

        assertThat(vars.get("reminderPrefix")).isEqualTo("Reminder: ");
    }

    // ── buildVariables: unsubscribeLink presence ──────────────────────────────

    @Test
    @DisplayName("buildVariables: unsubscribeLink is always populated")
    void buildVariables_unsubscribeLinkAlwaysPresent() {
        String token = "abc-123";
        String expectedLink = "https://batbern.ch/unsubscribe?token=" + token;

        Map<String, String> vars = newsletterEmailService.buildVariables(testEvent, "de", false, expectedLink);

        assertThat(vars.get("unsubscribeLink")).isEqualTo(expectedLink);
    }

    @Test
    @DisplayName("buildVariables: unsubscribeLink is distinct per token (GDPR requirement)")
    void buildVariables_unsubscribeLinkContainsToken() {
        String token1 = "token-alice";
        String token2 = "token-bob";

        Map<String, String> vars1 = newsletterEmailService.buildVariables(testEvent, "de", false,
                "https://batbern.ch/unsubscribe?token=" + token1);
        Map<String, String> vars2 = newsletterEmailService.buildVariables(testEvent, "de", false,
                "https://batbern.ch/unsubscribe?token=" + token2);

        assertThat(vars1.get("unsubscribeLink")).contains(token1);
        assertThat(vars2.get("unsubscribeLink")).contains(token2);
        assertThat(vars1.get("unsubscribeLink")).isNotEqualTo(vars2.get("unsubscribeLink"));
    }

    // ── buildSpeakersSection ──────────────────────────────────────────────────

    @Test
    @DisplayName("buildSpeakersSection: null workflowState → empty string")
    void buildSpeakersSection_nullState_returnsEmpty() {
        testEvent.setWorkflowState(null);

        String result = newsletterEmailService.buildSpeakersSection(testEvent, true);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("buildSpeakersSection: CREATED state → empty string (not yet published)")
    void buildSpeakersSection_createdState_returnsEmpty() {
        testEvent.setWorkflowState(ch.batbern.shared.types.EventWorkflowState.CREATED);

        String result = newsletterEmailService.buildSpeakersSection(testEvent, true);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("buildSpeakersSection: AGENDA_PUBLISHED state → queries sessions")
    void buildSpeakersSection_publishedState_queriesSessions() {
        testEvent.setWorkflowState(ch.batbern.shared.types.EventWorkflowState.AGENDA_PUBLISHED);
        when(sessionRepository.findByEventIdWithSpeakers(testEvent.getId())).thenReturn(List.of());

        String result = newsletterEmailService.buildSpeakersSection(testEvent, true);

        // No sessions → still returns empty string
        assertThat(result).isEmpty();
    }

    // ── eventDetailLink + registrationLink ───────────────────────────────────

    @Test
    @DisplayName("buildVariables: eventDetailLink uses correct base URL + event code")
    void buildVariables_eventDetailLink_correctUrl() {
        Map<String, String> vars = newsletterEmailService.buildVariables(testEvent, "de", false, "");

        assertThat(vars.get("eventDetailLink")).isEqualTo("https://batbern.ch/events/BATbern58");
        assertThat(vars.get("registrationLink")).isEqualTo("https://batbern.ch/register/BATbern58");
    }

    // ── buildVariables: currentYear ───────────────────────────────────────────

    @Test
    @DisplayName("buildVariables: currentYear is populated")
    void buildVariables_currentYear_populated() {
        Map<String, String> vars = newsletterEmailService.buildVariables(testEvent, "de", false, "");

        assertThat(vars.get("currentYear")).isEqualTo(String.valueOf(java.time.Year.now().getValue()));
    }

    // ── buildSpeakersSection: structural session filtering ────────────────────

    @Test
    @DisplayName("buildSpeakersSection: moderation sessions are filtered out")
    void buildSpeakersSection_moderationFiltered() {
        testEvent.setWorkflowState(ch.batbern.shared.types.EventWorkflowState.AGENDA_PUBLISHED);

        Session modSession = new Session();
        modSession.setSessionType("moderation");
        modSession.setTitle("Moderation Start");
        SessionUser su = new SessionUser();
        su.setUsername("nissim.buchs");
        su.setSpeakerFirstName("Nissim");
        su.setSpeakerLastName("Buchs");
        modSession.setSessionUsers(List.of(su));

        when(sessionRepository.findByEventIdWithSpeakers(testEvent.getId()))
                .thenReturn(List.of(modSession));

        String result = newsletterEmailService.buildSpeakersSection(testEvent, true);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("buildSpeakersSection: two speakers on same session → one row, names joined by '; '")
    void buildSpeakersSection_twoSpeakers_oneRow() {
        testEvent.setWorkflowState(ch.batbern.shared.types.EventWorkflowState.AGENDA_PUBLISHED);

        Session session = new Session();
        session.setSessionType("presentation");
        session.setTitle("Zero Trust at PostFinance");
        SessionUser su1 = new SessionUser();
        su1.setUsername("mustapha.bouaaoud");
        su1.setSpeakerFirstName("Mustapha");
        su1.setSpeakerLastName("Bouaaoud");
        su1.setPresentationTitle("Zero Trust at PostFinance");
        SessionUser su2 = new SessionUser();
        su2.setUsername("philippe.halbeisen");
        su2.setSpeakerFirstName("Philippe");
        su2.setSpeakerLastName("Halbeisen");
        session.setSessionUsers(List.of(su1, su2));

        UserPortraitProjection p1 = mockPortrait("mustapha.bouaaoud", "postfinance");
        UserPortraitProjection p2 = mockPortrait("philippe.halbeisen", "postfinance");

        when(sessionRepository.findByEventIdWithSpeakers(testEvent.getId()))
                .thenReturn(List.of(session));
        when(sessionUserRepository.findUserPortraitsByUsernames(
                java.util.Set.of("mustapha.bouaaoud", "philippe.halbeisen")))
                .thenReturn(List.of(p1, p2));

        String result = newsletterEmailService.buildSpeakersSection(testEvent, true);

        assertThat(result).contains("<table");
        assertThat(result).contains("Zero Trust at PostFinance");
        assertThat(result).contains("Mustapha Bouaaoud; Philippe Halbeisen");
        assertThat(result).contains("postfinance");
        // Only one data row in tbody (one session → one row)
        assertThat(result).containsOnlyOnce("</tr></tbody>");
    }

    @Test
    @DisplayName("buildSpeakersSection: company comes from user_profiles via companyId")
    void buildSpeakersSection_companyFromUserProfiles() {
        testEvent.setWorkflowState(ch.batbern.shared.types.EventWorkflowState.AGENDA_PUBLISHED);

        Session session = new Session();
        session.setSessionType("presentation");
        session.setTitle("Zero Trust");
        SessionUser su = new SessionUser();
        su.setUsername("igor.masen");
        su.setSpeakerFirstName("Igor");
        su.setSpeakerLastName("Masen");
        su.setPresentationTitle("Zero Trust at SBB");
        session.setSessionUsers(List.of(su));

        when(sessionRepository.findByEventIdWithSpeakers(testEvent.getId()))
                .thenReturn(List.of(session));
        when(sessionUserRepository.findUserPortraitsByUsernames(java.util.Set.of("igor.masen")))
                .thenReturn(List.of(mockPortrait("igor.masen", "sbb")));

        String result = newsletterEmailService.buildSpeakersSection(testEvent, true);

        assertThat(result).contains("<table");
        assertThat(result).contains("Zero Trust at SBB");
        assertThat(result).contains("Igor Masen");
        assertThat(result).contains("sbb");
    }

    private UserPortraitProjection mockPortrait(String username, String companyId) {
        return new UserPortraitProjection() {
            public String getUsername() { return username; }
            public String getProfilePictureUrl() { return null; }
            public String getCompanyId() { return companyId; }
            public String getCompanyLogoUrl() { return null; }
        };
    }
}
