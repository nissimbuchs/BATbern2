package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterRecipient;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.TaskTemplateRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 7.3 — integration tests (real PostgreSQL via Testcontainers, mocked {@link EmailService}).
 *
 * <p>Covers AC7: the task auto-created with due = eventDate+1; the dedicated send goes to active
 * registrants only; global opt-outs are excluded; DE/EN/German-fallback locale selection; the
 * double-send guard; and per-recipient failure isolation. No real outbound mail; rows cleaned per
 * test via the class-level {@code @Transactional} rollback.
 *
 * <p>Lives in {@code ch.batbern.events.service} so it can drive the synchronous
 * {@code SlidesOnlineEmailService.processSend} core directly (deterministic — no @Async race),
 * while still exercising the controller via {@link MockMvc} for the auth + guard cases.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Slides-Online Integration Tests (Story 7.3)")
class SlidesOnlineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;
    @Autowired
    private NewsletterSendRepository sendRepository;
    @Autowired
    private NewsletterRecipientRepository recipientRepository;
    @Autowired
    private TaskTemplateRepository taskTemplateRepository;
    @Autowired
    private EventTaskRepository eventTaskRepository;
    @Autowired
    private EventTaskService eventTaskService;
    @Autowired
    private SlidesOnlineEmailService slidesOnlineEmailService;

    @MockitoBean
    private UserApiClient userApiClient;
    @MockitoBean
    private EmailService emailService;

    private static final String EVENT_CODE = "BATbern760";

    @BeforeEach
    void setUp() {
        recipientRepository.deleteAll();
        sendRepository.deleteAll();
        subscriberRepository.deleteAll();
        // Rendering: keep variable substitution a pass-through so the captured subject still
        // distinguishes locale (DE vs EN), while mergeWithLayout uses the real EmailTemplateService.
        lenient().when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── AC1: task auto-created, due = eventDate + 1 day ──────────────────────

    @Test
    @DisplayName("AC1: default task 'Newsletter: Slides Are Online' is created due eventDate+1 (event_completed)")
    void taskTemplate_autoCreated_dueOneDayAfterEvent() {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));

        List<TaskTemplate> defaults = taskTemplateRepository.findByIsDefaultTrue();
        assertThat(defaults).anySatisfy(t ->
                assertThat(t.getName()).isEqualTo("Newsletter: Slides Are Online"));

        eventTaskService.createTasksForEvent(event.getId(), defaults);

        List<EventTask> tasks = eventTaskRepository.findByEventId(event.getId());
        EventTask slidesTask = tasks.stream()
                .filter(t -> "Newsletter: Slides Are Online".equals(t.getTaskName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("slides-online task was not auto-created"));

        assertThat(slidesTask.getTriggerState()).isEqualTo("event_completed");
        assertThat(slidesTask.getStatus()).isEqualTo("pending");
        assertThat(slidesTask.getDueDate())
                .isEqualTo(event.getDate().plus(1, ChronoUnit.DAYS)); // +1 day AFTER the event
    }

    // ── AC2/AC3/AC4: send → active registrants only, opt-out excluded, per-recipient locale ──

    @Test
    @DisplayName("AC2-4: sends to active registrants only, excludes opt-outs, per-recipient DE/EN/German-fallback")
    void send_activeRegistrantsOnly_optOutExcluded_perRecipientLocale() {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));

        saveRegistration(event.getId(), "att.en", "en@x.ch", "registered");   // EN
        saveRegistration(event.getId(), "att.de", "de@x.ch", "confirmed");    // DE
        saveRegistration(event.getId(), "att.fr", "fr@x.ch", "confirmed");    // fr → German fallback
        saveRegistration(event.getId(), "att.attd", "attended@x.ch", "attended"); // INCLUDED: post-event status
        saveRegistration(event.getId(), "att.cancel", "cancel@x.ch", "cancelled"); // excluded (status)
        saveRegistration(event.getId(), "att.wait", "wait@x.ch", "waitlist");      // excluded (status)
        saveRegistration(event.getId(), "att.opt", "opt@x.ch", "registered");      // excluded (opt-out)

        // opt@x.ch carries a global newsletter suppression → must be excluded (AC3).
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email("opt@x.ch").language("de").source("explicit").unsubscribeToken("tok-opt")
                .suppressedAt(Instant.now()).build());

        lenient().when(userApiClient.getPreferredLanguage("att.en")).thenReturn("en");
        lenient().when(userApiClient.getPreferredLanguage("att.de")).thenReturn("de");
        lenient().when(userApiClient.getPreferredLanguage("att.fr")).thenReturn("fr");
        lenient().when(userApiClient.getPreferredLanguage("att.attd")).thenReturn("en");

        NewsletterSend send = savePendingSend(event.getId());
        slidesOnlineEmailService.processSend(send.getId(), event, "slides-online");

        // Capture every (to, subject) the send dispatched.
        var toCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var subjectCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.times(4))
                .sendHtmlEmailSync(toCaptor.capture(), subjectCaptor.capture(), anyString(),
                        org.mockito.ArgumentMatchers.any());

        Map<String, String> subjectByEmail = new HashMap<>();
        for (int i = 0; i < toCaptor.getAllValues().size(); i++) {
            subjectByEmail.put(toCaptor.getAllValues().get(i), subjectCaptor.getAllValues().get(i));
        }

        // The active, non-opted-out registrants — including the post-event 'attended' one — received it.
        assertThat(subjectByEmail.keySet())
                .containsExactlyInAnyOrder("en@x.ch", "de@x.ch", "fr@x.ch", "attended@x.ch");
        assertThat(subjectByEmail).doesNotContainKeys("cancel@x.ch", "wait@x.ch", "opt@x.ch");

        // Per-recipient locale: en → English subject; de + fr(fallback) → German subject.
        assertThat(subjectByEmail.get("en@x.ch")).contains("The slides are online");
        assertThat(subjectByEmail.get("de@x.ch")).contains("Die Folien sind online");
        assertThat(subjectByEmail.get("fr@x.ch")).contains("Die Folien sind online");
        assertThat(subjectByEmail.get("attended@x.ch")).contains("The slides are online");

        NewsletterSend completed = sendRepository.findById(send.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getSentCount()).isEqualTo(4);
        assertThat(completed.getFailedCount()).isZero();
        assertThat(recipientRepository.findAll())
                .filteredOn(r -> "sent".equals(r.getDeliveryStatus())).hasSize(4);
    }

    // ── AC6: per-recipient failure isolation ─────────────────────────────────

    @Test
    @DisplayName("AC6: a transient SES failure for one recipient is isolated → PARTIAL, others still sent")
    void send_perRecipientFailureIsolation() {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));
        saveRegistration(event.getId(), "att.ok", "ok@x.ch", "confirmed");
        saveRegistration(event.getId(), "att.boom", "boom@x.ch", "confirmed");
        lenient().when(userApiClient.getPreferredLanguage(anyString())).thenReturn("de");

        org.mockito.Mockito.doThrow(new RuntimeException("SES throttled"))
                .when(emailService).sendHtmlEmailSync(
                        org.mockito.ArgumentMatchers.eq("boom@x.ch"), anyString(), anyString(),
                        org.mockito.ArgumentMatchers.any());

        NewsletterSend send = savePendingSend(event.getId());
        slidesOnlineEmailService.processSend(send.getId(), event, "slides-online");

        NewsletterSend result = sendRepository.findById(send.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo("PARTIAL");
        assertThat(result.getSentCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        List<NewsletterRecipient> recipients = recipientRepository.findAll();
        assertThat(recipients).extracting(NewsletterRecipient::getDeliveryStatus)
                .containsExactlyInAnyOrder("sent", "failed");
    }

    // ── AC5: double-send guard via the endpoint ──────────────────────────────

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("AC5: in-progress slides-online send → endpoint returns 409")
    void endpoint_inProgress_returns409() throws Exception {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));
        NewsletterSend inProgress = savePendingSend(event.getId());
        inProgress.setStatus("IN_PROGRESS");
        sendRepository.save(inProgress);

        mockMvc.perform(post("/api/v1/events/{eventCode}/slides-online/send", EVENT_CODE))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("AC5: already-completed slides-online send → endpoint returns 409")
    void endpoint_alreadySent_returns409() throws Exception {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));
        NewsletterSend done = savePendingSend(event.getId());
        done.setStatus("COMPLETED");
        sendRepository.save(done);

        mockMvc.perform(post("/api/v1/events/{eventCode}/slides-online/send", EVENT_CODE))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("AC2: organizer, no prior send → 200 with PENDING + registrant count")
    void endpoint_organizer_returns200Pending() throws Exception {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));

        mockMvc.perform(post("/api/v1/events/{eventCode}/slides-online/send", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.recipientCount").value(0));
    }

    @Test
    @WithMockUser(username = "joe.user", roles = {"USER"})
    @DisplayName("AC2: non-organizer → 403")
    void endpoint_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/slides-online/send", EVENT_CODE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("AC2: organizer, unknown event → 404")
    void endpoint_unknownEvent_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/slides-online/send", "NOPE-EVENT"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Event saveEvent(String code, Instant date) {
        return eventRepository.save(Event.builder()
                .eventCode(code)
                .eventNumber(760)
                .title("Slides Are Online Event")
                .date(date)
                .registrationDeadline(date.minus(10, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .eventType(ch.batbern.events.core.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .organizerUsername("org.user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private void saveRegistration(UUID eventId, String username, String email, String status) {
        registrationRepository.save(Registration.builder()
                .registrationCode("REG-" + UUID.randomUUID())
                .eventId(eventId)
                .attendeeUsername(username)
                .attendeeEmail(email)
                .status(status)
                .registrationDate(Instant.now())
                .build());
    }

    private NewsletterSend savePendingSend(UUID eventId) {
        return sendRepository.save(NewsletterSend.builder()
                .eventId(eventId)
                .templateKey("slides-online")
                .reminder(false)
                .locale("de")
                .sentAt(Instant.now())
                .sentByUsername("org.user")
                .recipientCount(0)
                .status("PENDING")
                .testMode(false)
                .build());
    }
}
