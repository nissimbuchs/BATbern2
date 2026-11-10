package ch.batbern.events.service;

import ch.batbern.events.config.CumsContractHarness;
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
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 7.3 — integration tests (real PostgreSQL via Testcontainers, the real {@link EmailService}
 * against the {@link SesRecorder}).
 *
 * <p>Covers AC7: the task auto-created with due = eventDate+1; the dedicated send goes to active
 * registrants only; global opt-outs are excluded; DE/EN/German-fallback locale selection; the
 * double-send guard; and per-recipient failure isolation. No real outbound mail. CUMS is the
 * {@link CumsContractHarness} behind the real {@code UserApiClient}.
 *
 * <p>Lives in {@code ch.batbern.events.service} so it can drive the synchronous
 * {@code SlidesOnlineEmailService.processSend} core directly (deterministic — no @Async race),
 * while the auth, guard and first-send cases go through the Mail Center composer via
 * {@link MockMvc} (templateKey {@code slides-online}, Story 16.8c). Not {@code @Transactional}:
 * the composer send runs on an {@code @Async} thread, so this class removes its own rows.
 */
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

    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private JdbcTemplate jdbc;

    private static final String EVENT_CODE = "BATbern760";

    @BeforeEach
    void setUp() {
        cleanUp();
        sesRecorder.reset();
    }

    @AfterEach
    void cleanUp() {
        sesRecorder.awaitAsyncSends();
        cums.reset();
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_recipients WHERE send_id IN (SELECT s.id FROM newsletter_sends s"
                + " JOIN events e ON e.id = s.event_id WHERE e.event_code = ?)", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_sends WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_subscribers WHERE email LIKE 'success+%@simulator.amazonses.com'");
        jdbc.update("DELETE FROM event_tasks WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM registrations WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
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
        // The fixture event is already EVENT_COMPLETED, so the task starts as todo: since the
        // organizer cockpit plan (T1) a task created after its trigger was reached is active at
        // once instead of waiting for a transition that will never come again.
        assertThat(slidesTask.getStatus()).isEqualTo("todo");
        assertThat(slidesTask.getDueDate())
                .isEqualTo(event.getDate().plus(1, ChronoUnit.DAYS)); // +1 day AFTER the event
    }

    // ── AC2/AC3/AC4: send → active registrants only, opt-out excluded, per-recipient locale ──

    @Test
    @DisplayName("AC2-4: sends to active registrants only, excludes opt-outs, per-recipient DE/EN/German-fallback")
    void send_activeRegistrantsOnly_optOutExcluded_perRecipientLocale() {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));

        saveRegistration(event.getId(), "att.en", sim("en"), "registered");   // EN
        saveRegistration(event.getId(), "att.de", sim("de"), "confirmed");    // DE
        saveRegistration(event.getId(), "att.fr", sim("fr"), "confirmed");    // fr → German fallback
        saveRegistration(event.getId(), "att.attd", sim("attended"), "attended"); // INCLUDED: post-event status
        saveRegistration(event.getId(), "att.cancel", sim("cancel"), "cancelled"); // excluded (status)
        saveRegistration(event.getId(), "att.wait", sim("wait"), "waitlist");      // excluded (status)
        saveRegistration(event.getId(), "att.opt", sim("opt"), "registered");      // excluded (opt-out)

        // The opted-out address carries a global newsletter suppression → must be excluded (AC3).
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email(sim("opt")).language("de").source("explicit").unsubscribeToken("tok-opt")
                .suppressedAt(Instant.now()).build());

        cums.userWithLanguage("att.en", "en");
        cums.userWithLanguage("att.de", "de");
        cums.userWithLanguage("att.fr", "fr");
        cums.userWithLanguage("att.attd", "en");

        NewsletterSend send = savePendingSend(event.getId());
        slidesOnlineEmailService.processSend(send.getId(), event, "slides-online");

        // Every (to, subject) SES received: one mail per recipient.
        assertThat(sesRecorder.mails()).hasSize(4).allSatisfy(mail -> assertThat(mail.to()).hasSize(1));
        Map<String, String> subjectByEmail = new HashMap<>();
        sesRecorder.mails().forEach(mail -> subjectByEmail.put(mail.to().get(0), mail.subject()));

        // The active, non-opted-out registrants — including the post-event 'attended' one — received it.
        assertThat(subjectByEmail.keySet())
                .containsExactlyInAnyOrder(sim("en"), sim("de"), sim("fr"), sim("attended"));
        assertThat(subjectByEmail).doesNotContainKeys(sim("cancel"), sim("wait"), sim("opt"));

        // Per-recipient locale: en → English subject; de + fr(fallback) → German subject.
        assertThat(subjectByEmail.get(sim("en"))).contains("The slides are online");
        assertThat(subjectByEmail.get(sim("de"))).contains("Die Folien sind online");
        assertThat(subjectByEmail.get(sim("fr"))).contains("Die Folien sind online");
        assertThat(subjectByEmail.get(sim("attended"))).contains("The slides are online");

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
        saveRegistration(event.getId(), "att.ok", sim("ok"), "confirmed");
        saveRegistration(event.getId(), "att.boom", sim("boom"), "confirmed");

        sesRecorder.failFor(sim("boom"), () -> SesRecorder.sesError("Throttling", "SES throttled"));

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

    // ── AC5: double-send guard, through the Mail Center composer ─────────────
    // Story 17.1 removed POST /events/{c}/slides-online/send, 16.8c the registrant-notices pair;
    // the same mail goes out through POST /events/{c}/mails, audience REGISTRANTS.

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("AC5: in-progress slides-online notice → the composer send answers 409 with a problem body")
    void composer_inProgress_returns409() throws Exception {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));
        saveRegistration(event.getId(), "att.de", sim("de"), "confirmed");
        NewsletterSend inProgress = savePendingSend(event.getId());
        inProgress.setStatus("IN_PROGRESS");
        sendRepository.save(inProgress);

        mockMvc.perform(slidesOnlineViaComposer(EVENT_CODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("AC5: already-completed slides-online notice → the composer send answers 409, nothing is sent")
    void composer_alreadySent_returns409() throws Exception {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));
        saveRegistration(event.getId(), "att.de", sim("de"), "confirmed");
        NewsletterSend done = savePendingSend(event.getId());
        done.setStatus("COMPLETED");
        sendRepository.save(done);

        mockMvc.perform(slidesOnlineViaComposer(EVENT_CODE))
                .andExpect(status().isConflict());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(sendRepository.findAll()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("AC2: organizer, no prior send → 202, the registrant gets the mail, the audit row completes")
    void composer_firstSend_reachesRegistrant() throws Exception {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));
        saveRegistration(event.getId(), "att.de", sim("de"), "confirmed");

        mockMvc.perform(slidesOnlineViaComposer(EVENT_CODE))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recipientCount").value(1));

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(sim("de"))).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).contains("Die Folien sind online"));
        assertThat(sendRepository.findAll()).singleElement().satisfies(send -> {
            assertThat(send.getTemplateKey()).isEqualTo("slides-online");
            assertThat(send.getStatus()).isEqualTo("COMPLETED");
        });
    }

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("16.8c: a registrant notice in progress does not block the newsletter of the same event")
    void composer_registrantNoticeInProgress_doesNotBlockNewsletter() throws Exception {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email(sim("sub")).language("de").source("explicit").unsubscribeToken("tok-760-sub").build());
        NewsletterSend notice = savePendingSend(event.getId()); // slides-online, a registrant notice
        notice.setStatus("IN_PROGRESS");
        sendRepository.save(notice);

        mockMvc.perform(post("/api/v1/events/{eventCode}/mails", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"NEWSLETTER\",\"locale\":\"de\",\"target\":\"RECIPIENTS\"}"))
                .andExpect(status().isAccepted());

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(sim("sub"))).hasSize(1);
    }

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("16.8c: a newsletter in progress still blocks a second newsletter (409, positive control)")
    void composer_newsletterInProgress_blocksNewsletter() throws Exception {
        Event event = saveEvent(EVENT_CODE, Instant.parse("2026-03-06T15:00:00Z"));
        subscriberRepository.save(NewsletterSubscriber.builder()
                .email(sim("sub")).language("de").source("explicit").unsubscribeToken("tok-760-sub").build());
        NewsletterSend running = savePendingSend(event.getId());
        running.setTemplateKey("newsletter-event");
        running.setStatus("IN_PROGRESS");
        sendRepository.save(running);

        mockMvc.perform(post("/api/v1/events/{eventCode}/mails", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"NEWSLETTER\",\"locale\":\"de\",\"target\":\"RECIPIENTS\"}"))
                .andExpect(status().isConflict());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @WithMockUser(username = "joe.user", roles = {"USER"})
    @DisplayName("AC2: non-organizer → 403")
    void composer_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(slidesOnlineViaComposer(EVENT_CODE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "org.user", roles = {"ORGANIZER"})
    @DisplayName("AC2: organizer, unknown event → 404")
    void composer_unknownEvent_returns404() throws Exception {
        mockMvc.perform(slidesOnlineViaComposer("NOPE-EVENT"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder slidesOnlineViaComposer(String eventCode) {
        return post("/api/v1/events/{eventCode}/mails", eventCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":\"REGISTRANTS\",\"templateKey\":\"slides-online\",\"target\":\"RECIPIENTS\"}");
    }

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

    /** A recipient that may receive mail: the SES mailbox simulator (nothing is delivered in tests). */
    private static String sim(String name) {
        return "success+" + name + "@simulator.amazonses.com";
    }
}
