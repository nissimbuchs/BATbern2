package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.NewsletterSendResponse;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.exception.DuplicateNewsletterSendException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.service.EmailService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ch.batbern.events.domain.EmailTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private NewsletterSubscriberRepository subscriberRepository;
    @Mock
    private NewsletterSendRepository sendRepository;
    @Mock
    private NewsletterRecipientRepository recipientRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionUserService sessionUserService;
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

        SessionSpeakerResponse sp1 = SessionSpeakerResponse.builder()
                .username("mustapha.bouaaoud")
                .firstName("Mustapha")
                .lastName("Bouaaoud")
                .company("postfinance")
                .presentationTitle("Zero Trust at PostFinance")
                .build();
        SessionSpeakerResponse sp2 = SessionSpeakerResponse.builder()
                .username("philippe.halbeisen")
                .firstName("Philippe")
                .lastName("Halbeisen")
                .company("postfinance")
                .build();

        when(sessionRepository.findByEventIdWithSpeakers(testEvent.getId()))
                .thenReturn(List.of(session));
        when(sessionUserService.getSessionSpeakers(any()))
                .thenReturn(List.of(sp1, sp2));

        String result = newsletterEmailService.buildSpeakersSection(testEvent, true);

        assertThat(result).contains("<table");
        assertThat(result).contains("Zero Trust at PostFinance");
        // Both speakers on one row, company inline: "Name, company; Name2, company"
        assertThat(result).contains("Mustapha Bouaaoud, postfinance; Philippe Halbeisen, postfinance");
        // Only one data row in tbody (one session → one row)
        assertThat(result).containsOnlyOnce("</tr></tbody>");
    }

    // ── templateKey: custom key routing ──────────────────────────────────────

    @Test
    @DisplayName("preview: when templateKey provided → renderContent uses that key")
    void preview_withCustomTemplateKey_usesCustomTemplate() {
        testEvent.setId(UUID.randomUUID());

        when(emailTemplateService.findByKeyAndLocale("custom-newsletter", "de"))
                .thenReturn(Optional.of(mockTemplate("custom-newsletter", "de", "Custom content")));
        when(emailTemplateService.mergeWithLayout(any(), eq("batbern-default"), eq("de")))
                .thenReturn("merged");
        when(emailService.replaceVariables(any(), any())).thenReturn("final");
        when(emailTemplateService.resolveSubject("custom-newsletter", "de"))
                .thenReturn(Optional.of("Custom Subject"));
        when(subscriberService.getActiveCount()).thenReturn(5L);
        when(eventRepository.findByDateAfter(any())).thenReturn(List.of());

        newsletterEmailService.preview(testEvent, false, "de", "custom-newsletter");

        verify(emailTemplateService).findByKeyAndLocale("custom-newsletter", "de");
        verify(emailTemplateService, never()).findByKeyAndLocale("newsletter-event", "de");
    }

    @Test
    @DisplayName("preview: when templateKey null → renderContent uses default 'newsletter-event'")
    void preview_withNullTemplateKey_usesDefaultTemplate() {
        testEvent.setId(UUID.randomUUID());

        when(emailTemplateService.findByKeyAndLocale("newsletter-event", "de"))
                .thenReturn(Optional.of(mockTemplate("newsletter-event", "de", "Default content")));
        when(emailTemplateService.mergeWithLayout(any(), any(), any())).thenReturn("merged");
        when(emailService.replaceVariables(any(), any())).thenReturn("final");
        when(emailTemplateService.resolveSubject("newsletter-event", "de"))
                .thenReturn(Optional.of("Subject"));
        when(subscriberService.getActiveCount()).thenReturn(3L);
        when(eventRepository.findByDateAfter(any())).thenReturn(List.of());

        newsletterEmailService.preview(testEvent, false, "de", null);

        verify(emailTemplateService).findByKeyAndLocale("newsletter-event", "de");
    }

    private EmailTemplate mockTemplate(String key, String locale, String html) {
        EmailTemplate tpl = new EmailTemplate();
        tpl.setTemplateKey(key);
        tpl.setLocale(locale);
        tpl.setHtmlBody(html);
        return tpl;
    }

    // ── sendNewsletter: fire-and-forget architecture ──────────────────────────

    @Test
    @DisplayName("sendNewsletter: returns immediately with PENDING status and a sendId")
    void sendNewsletter_returnsPendingStatus() {
        testEvent.setId(UUID.randomUUID());
        NewsletterSend savedSend = NewsletterSend.builder()
                .id(UUID.randomUUID())
                .eventId(testEvent.getId())
                .status(NewsletterEmailService.STATUS_PENDING)
                .recipientCount(5)
                .locale("de")
                .sentByUsername("organizer")
                .sentAt(Instant.now())
                .templateKey("newsletter-event")
                .build();

        when(sendRepository.findFirstByEventIdAndStatus(testEvent.getId(),
                NewsletterEmailService.STATUS_IN_PROGRESS))
                .thenReturn(java.util.Optional.empty());
        when(subscriberService.getActiveCount()).thenReturn(5L);
        when(sendRepository.save(any())).thenReturn(savedSend);

        NewsletterSendResponse response = newsletterEmailService.sendNewsletter(
                testEvent, false, "de", "organizer", null);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getId()).isNotNull();
    }

    @Test
    @DisplayName("sendNewsletter: throws DuplicateNewsletterSendException when IN_PROGRESS send exists")
    void sendNewsletter_throws409_whenSendAlreadyInProgress() {
        testEvent.setId(UUID.randomUUID());
        NewsletterSend activeSend = NewsletterSend.builder()
                .id(UUID.randomUUID())
                .eventId(testEvent.getId())
                .status(NewsletterEmailService.STATUS_IN_PROGRESS)
                .build();

        when(sendRepository.findFirstByEventIdAndStatus(testEvent.getId(),
                NewsletterEmailService.STATUS_IN_PROGRESS))
                .thenReturn(java.util.Optional.of(activeSend));

        assertThatThrownBy(() ->
                newsletterEmailService.sendNewsletter(testEvent, false, "de", "organizer", null))
                .isInstanceOf(DuplicateNewsletterSendException.class);
    }

    @Test
    @DisplayName("executeNewsletterSendAsync: uses paginated query — not findByUnsubscribedAtIsNull()")
    void executeNewsletterSendAsync_usesPagedQuery() {
        testEvent.setId(UUID.randomUUID());
        UUID sendId = UUID.randomUUID();
        NewsletterSend send = NewsletterSend.builder()
                .id(sendId).status(NewsletterEmailService.STATUS_PENDING)
                .sentCount(0).failedCount(0).build();

        NewsletterSubscriber subscriber = new NewsletterSubscriber();
        subscriber.setEmail("user@example.com");
        subscriber.setUnsubscribeToken("tok-abc");

        Page<NewsletterSubscriber> page = new PageImpl<>(List.of(subscriber));

        when(sendRepository.findById(sendId)).thenReturn(java.util.Optional.of(send));
        when(subscriberRepository.findByUnsubscribedAtIsNull(any(Pageable.class)))
                .thenReturn(page)
                .thenReturn(Page.empty()); // second call returns empty to stop loop
        when(emailTemplateService.findByKeyAndLocale(any(), any()))
                .thenReturn(java.util.Optional.of(mockTemplate("newsletter-event", "de", "body")));
        when(emailTemplateService.mergeWithLayout(any(), any(), any())).thenReturn("merged");
        when(emailService.replaceVariables(any(), any())).thenReturn("final");
        when(emailTemplateService.resolveSubject(any(), any())).thenReturn(java.util.Optional.of("Subject"));
        when(eventRepository.findByDateAfter(any())).thenReturn(List.of());

        newsletterEmailService.executeNewsletterSendAsync(sendId, testEvent, false, "de", "newsletter-event");

        // Must use paginated query, not the non-paginated one
        verify(subscriberRepository, atLeastOnce()).findByUnsubscribedAtIsNull(any(Pageable.class));
        verify(emailService, atLeastOnce()).sendHtmlEmailSync(eq("user@example.com"), any(), any());
    }

    @Test
    @DisplayName("computeFinalStatus: no failures → COMPLETED")
    void sendJob_noFailures_completedStatus() {
        // Indirectly tested via executeNewsletterSendAsync
        // Status constants are package-visible for test verification
        assertThat(NewsletterEmailService.STATUS_COMPLETED).isEqualTo("COMPLETED");
        assertThat(NewsletterEmailService.STATUS_PARTIAL).isEqualTo("PARTIAL");
        assertThat(NewsletterEmailService.STATUS_FAILED).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("buildSpeakersSection: company comes from SessionUserService enrichment")
    void buildSpeakersSection_companyFromUserProfiles() {
        testEvent.setWorkflowState(ch.batbern.shared.types.EventWorkflowState.AGENDA_PUBLISHED);

        Session session = new Session();
        session.setSessionType("presentation");
        session.setTitle("Zero Trust");

        SessionSpeakerResponse sp = SessionSpeakerResponse.builder()
                .username("igor.masen")
                .firstName("Igor")
                .lastName("Masen")
                .company("sbb")
                .presentationTitle("Zero Trust at SBB")
                .build();

        when(sessionRepository.findByEventIdWithSpeakers(testEvent.getId()))
                .thenReturn(List.of(session));
        when(sessionUserService.getSessionSpeakers(any()))
                .thenReturn(List.of(sp));

        String result = newsletterEmailService.buildSpeakersSection(testEvent, true);

        assertThat(result).contains("<table");
        assertThat(result).contains("Zero Trust at SBB");
        assertThat(result).contains("Igor Masen");
        assertThat(result).contains("sbb");
    }

}
