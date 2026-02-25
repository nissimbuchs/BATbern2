package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.SessionRepository;
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
}
