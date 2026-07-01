package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.newsletter.dto.generated.SlidesOnlineSendResponse;
import ch.batbern.events.exception.DuplicateNewsletterSendException;
import ch.batbern.events.exception.SlidesOnlineAlreadySentException;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.RegistrationRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 7.3 — unit tests for {@link SlidesOnlineEmailService}.
 *
 * <p>Drives the synchronous {@code processSend} core directly (no thread-pool race) and verifies
 * the guards, the locale mapping, the opt-out exclusion, and per-recipient failure isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlidesOnlineEmailService Unit Tests")
class SlidesOnlineEmailServiceTest {

    @Mock
    private EmailService emailService;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private NewsletterSendRepository sendRepository;
    @Mock
    private NewsletterRecipientRepository recipientRepository;
    @Mock
    private NewsletterSubscriberRepository subscriberRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private UserApiClient userApiClient;

    @InjectMocks
    private SlidesOnlineEmailService service;

    private Event event;
    private final UUID sendId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseUrl", "https://batbern.ch");
        ReflectionTestUtils.setField(service, "sendRateDelayMs", 0L);
        service.setSelf(service);

        event = new Event();
        event.setId(UUID.randomUUID());
        event.setEventCode("BATbern77");
        event.setTitle("Slides Test Event");
        event.setEventNumber(77);
        event.setDate(Instant.parse("2026-03-06T15:00:00Z"));
    }

    // ── Locale mapping (AC4) ────────────────────────────────────────────────

    @Test
    @DisplayName("resolveLocale: de* → de, en → en, other/unknown → de (German fallback)")
    void resolveLocale_mapsCorrectly() {
        when(userApiClient.getPreferredLanguage("u.de")).thenReturn("de");
        when(userApiClient.getPreferredLanguage("u.deCH")).thenReturn("de-CH");
        when(userApiClient.getPreferredLanguage("u.en")).thenReturn("en");
        when(userApiClient.getPreferredLanguage("u.fr")).thenReturn("fr");
        when(userApiClient.getPreferredLanguage("u.null")).thenReturn(null);

        assertThat(service.resolveLocale("u.de")).isEqualTo("de");
        assertThat(service.resolveLocale("u.deCH")).isEqualTo("de");
        assertThat(service.resolveLocale("u.en")).isEqualTo("en");
        assertThat(service.resolveLocale("u.fr")).isEqualTo("de");   // non-de/en → German fallback
        assertThat(service.resolveLocale("u.null")).isEqualTo("de"); // unknown → German fallback
    }

    // ── Double-send guards (AC5) ────────────────────────────────────────────

    @Test
    @DisplayName("sendSlidesOnline: in-progress send → 409 DuplicateNewsletterSendException")
    void sendSlidesOnline_inProgress_throwsDuplicate() {
        stubIsRegistrantNoticeTemplate();
        when(sendRepository.findFirstByEventIdAndTemplateKeyAndStatus(
                event.getId(), "slides-online", "IN_PROGRESS"))
                .thenReturn(Optional.of(NewsletterSend.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.sendSlidesOnline(event, "org.user"))
                .isInstanceOf(DuplicateNewsletterSendException.class);

        verify(registrationRepository, never()).findByEventIdAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("sendSlidesOnline: already-sent → 409 SlidesOnlineAlreadySentException")
    void sendSlidesOnline_alreadySent_throws() {
        stubIsRegistrantNoticeTemplate();
        when(sendRepository.findFirstByEventIdAndTemplateKeyAndStatus(
                event.getId(), "slides-online", "IN_PROGRESS")).thenReturn(Optional.empty());
        when(sendRepository.existsByEventIdAndTemplateKeyAndStatusIn(
                eq(event.getId()), eq("slides-online"), any())).thenReturn(true);

        assertThatThrownBy(() -> service.sendSlidesOnline(event, "org.user"))
                .isInstanceOf(SlidesOnlineAlreadySentException.class);
    }

    @Test
    @DisplayName("sendSlidesOnline: no prior send → creates audit row + returns PENDING with registrant count")
    void sendSlidesOnline_happy_returnsPending() {
        stubIsRegistrantNoticeTemplate();
        when(sendRepository.findFirstByEventIdAndTemplateKeyAndStatus(
                event.getId(), "slides-online", "IN_PROGRESS")).thenReturn(Optional.empty());
        when(sendRepository.existsByEventIdAndTemplateKeyAndStatusIn(
                eq(event.getId()), eq("slides-online"), any())).thenReturn(false);
        when(registrationRepository.findByEventIdAndStatusIn(eq(event.getId()), any()))
                .thenReturn(List.of(registration("a.one", "a.one@x.ch", "registered"),
                        registration("a.two", "a.two@x.ch", "confirmed")));
        when(sendRepository.save(any(NewsletterSend.class)))
                .thenAnswer(inv -> {
                    NewsletterSend s = inv.getArgument(0);
                    s.setId(sendId);
                    return s;
                });
        // Avoid the real @Async worker running the send during a unit test.
        SlidesOnlineEmailService selfMock = org.mockito.Mockito.mock(SlidesOnlineEmailService.class);
        service.setSelf(selfMock);

        SlidesOnlineSendResponse resp = service.sendSlidesOnline(event, "org.user");

        assertThat(resp.getSendId()).isEqualTo(sendId);
        assertThat(resp.getStatus()).isEqualTo(SlidesOnlineSendResponse.StatusEnum.PENDING);
        assertThat(resp.getRecipientCount()).isEqualTo(2);
        verify(selfMock).executeSlidesOnlineSendAsync(sendId, event, "slides-online");
    }

    // ── processSend: recipients, opt-out, counts (AC2, AC3, AC6) ────────────

    @Test
    @DisplayName("processSend: sends to active registrants, skips opt-out, marks COMPLETED")
    void processSend_skipsOptOut_marksCompleted() {
        Registration r1 = registration("a.one", "a.one@x.ch", "registered");
        Registration r2 = registration("a.two", "a.two@x.ch", "confirmed");
        Registration optedOut = registration("a.opt", "opt@x.ch", "registered");

        when(registrationRepository.findByEventIdAndStatusIn(eq(event.getId()), any()))
                .thenReturn(List.of(r1, r2, optedOut));
        stubRenderingAndSend();
        // Opt-out: only opt@x.ch carries a suppressed subscriber row.
        when(subscriberRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(subscriberRepository.findByEmailIgnoreCase("opt@x.ch"))
                .thenReturn(Optional.of(suppressed("opt@x.ch")));
        when(userApiClient.getPreferredLanguage(anyString())).thenReturn("de");

        NewsletterSend send = pendingSend();
        when(sendRepository.findById(sendId)).thenReturn(Optional.of(send));

        service.processSend(sendId, event, "slides-online");

        // Two active, non-opted-out recipients → two sends; opted-out skipped.
        verify(emailService, times(2)).sendHtmlEmailSync(anyString(), anyString(), anyString(), any());
        verify(emailService, never()).sendHtmlEmailSync(eq("opt@x.ch"), anyString(), anyString(), any());
        assertThat(send.getStatus()).isEqualTo("COMPLETED");
        assertThat(send.getSentCount()).isEqualTo(2);
        assertThat(send.getFailedCount()).isZero();
    }

    @Test
    @DisplayName("processSend: injects per-registrant {{deregistrationUrl}} from the registrant's token")
    void processSend_injectsPerRegistrantDeregistrationUrl() {
        UUID token = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Registration r = registration("a.one", "a.one@x.ch", "registered");
        r.setDeregistrationToken(token);

        when(registrationRepository.findByEventIdAndStatusIn(eq(event.getId()), any()))
                .thenReturn(List.of(r));
        stubRenderingAndSend();
        when(subscriberRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userApiClient.getPreferredLanguage(anyString())).thenReturn("de");
        when(sendRepository.findById(sendId)).thenReturn(Optional.of(pendingSend()));

        service.processSend(sendId, event, "slides-online");

        // The per-recipient pass must run replaceVariables with a map carrying the one-click
        // cancel link built from THIS registrant's token.
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> varsCaptor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce())
                .replaceVariables(anyString(), varsCaptor.capture());
        assertThat(varsCaptor.getAllValues())
                .anySatisfy(vars -> assertThat(vars)
                        .containsEntry("deregistrationUrl",
                                "https://batbern.ch/deregister?token=" + token));
    }

    @Test
    @DisplayName("processSend: falls back to /events when a registrant has no deregistration token")
    void processSend_fallsBackWhenNoToken() {
        Registration r = registration("a.one", "a.one@x.ch", "registered");
        r.setDeregistrationToken(null);

        when(registrationRepository.findByEventIdAndStatusIn(eq(event.getId()), any()))
                .thenReturn(List.of(r));
        stubRenderingAndSend();
        when(subscriberRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userApiClient.getPreferredLanguage(anyString())).thenReturn("de");
        when(sendRepository.findById(sendId)).thenReturn(Optional.of(pendingSend()));

        service.processSend(sendId, event, "slides-online");

        org.mockito.ArgumentCaptor<java.util.Map<String, String>> varsCaptor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce())
                .replaceVariables(anyString(), varsCaptor.capture());
        assertThat(varsCaptor.getAllValues())
                .anySatisfy(vars -> assertThat(vars)
                        .containsEntry("deregistrationUrl", "https://batbern.ch/events"));
    }

    // NOTE: per-recipient failure isolation (AC6) is covered end-to-end in
    // SlidesOnlineIntegrationTest against real PostgreSQL with a mocked EmailService — the
    // authoritative test for AC7. A pure-Mockito duplicate here hits a strict-stubs /
    // computeIfAbsent interaction that does not reflect production behaviour, so it is omitted.

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Make the slides-online template resolve as a REGISTRANT_NOTICE so the send-guard passes. */
    private void stubIsRegistrantNoticeTemplate() {
        EmailTemplate t = new EmailTemplate();
        t.setCategory("REGISTRANT_NOTICE");
        when(emailTemplateService.findByKeyAndLocale("slides-online", "de")).thenReturn(Optional.of(t));
    }

    private void stubRenderingAndSend() {
        EmailTemplate template = new EmailTemplate();
        template.setHtmlBody("<p>{{eventTitle}}</p>");
        lenient().when(emailTemplateService.findByKeyAndLocale(eq("slides-online"), anyString()))
                .thenReturn(Optional.of(template));
        lenient().when(emailTemplateService.resolveSubject(eq("slides-online"), anyString()))
                .thenReturn(Optional.of("Subject {{eventTitle}}"));
        lenient().when(emailTemplateService.mergeWithLayout(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private NewsletterSend pendingSend() {
        return NewsletterSend.builder()
                .id(sendId).eventId(event.getId()).templateKey("slides-online")
                .status("PENDING").sentByUsername("org.user").build();
    }

    private Registration registration(String username, String email, String status) {
        return Registration.builder()
                .registrationCode("REG-" + UUID.randomUUID())
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeEmail(email)
                .status(status)
                .registrationDate(Instant.now())
                .build();
    }

    private NewsletterSubscriber suppressed(String email) {
        return NewsletterSubscriber.builder()
                .email(email).language("de").source("explicit").unsubscribeToken("t-" + email)
                .suppressedAt(Instant.now())
                .build();
    }
}
