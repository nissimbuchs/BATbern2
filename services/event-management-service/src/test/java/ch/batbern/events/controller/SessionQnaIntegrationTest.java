package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.QnaWindowStatus;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionQnaPost;
import ch.batbern.events.domain.SessionQnaWindow;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionQnaPostRepository;
import ch.batbern.events.repository.SessionQnaWindowRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.SessionQnaScheduledService;
import ch.batbern.events.service.SessionQnaService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for per-session Q&A — Story 7.5 "The Apéro Continues" (PostgreSQL/Testcontainers).
 *
 * <p>Covers AC1-AC7: window opens on completion (idempotent), logged-in post within window,
 * anonymous post rejected, organizer extend/close-early/takedown, post-after-close → 409, frozen
 * thread readable publicly, and the scheduled freeze job.
 *
 * <p>ShedLock: the freeze job is {@code @SchedulerLock}-annotated; a <b>class-scoped
 * {@code @MockBean LockProvider}</b> (per the PR #773 fix) grants a no-op lock so the committed
 * lock row never leaks across the rolled-back test transactions. (NOT a {@code @TestConfiguration
 * @Primary} bean, which would leak globally.)
 *
 * <p>Anonymous POST: AC3 specifies 401, produced at the api-gateway (the public entry point). In
 * this service in isolation {@code TestSecurityConfig} has no JWT entry point, so method security
 * rejects the anonymous principal with 403 — either way the post is refused and no row is created.
 */
@Transactional
class SessionQnaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionQnaWindowRepository windowRepository;

    @Autowired
    private SessionQnaPostRepository postRepository;

    @Autowired
    private SessionQnaService qnaService;

    @Autowired
    private SessionQnaScheduledService scheduledService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private LockProvider lockProvider;

    private static final String EVENT_CODE = "BATbern960";
    private static final String SLUG = "cloud-native-foundations";
    private static final String ATTENDEE = "jane.attendee";
    private static final String ORGANIZER = "org.user";

    @BeforeEach
    void setUp() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(mock(SimpleLock.class)));
        postRepository.deleteAll();
        windowRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();
    }

    // ==================== AC1: windows open on completion (idempotent) ====================

    @Test
    @DisplayName("AC1: EVENT_COMPLETED trigger opens one window per session, idempotently")
    void should_openOneWindowPerSession_idempotently() {
        Event event = saveEvent(EventWorkflowState.EVENT_COMPLETED); // default trigger = EVENT_COMPLETED
        saveSession(event, SLUG);
        saveSession(event, "second-session");

        qnaService.openWindowsIfTrigger(EVENT_CODE, ch.batbern.events.domain.QnaOpenTrigger.EVENT_COMPLETED);
        assertThat(windowRepository.count()).isEqualTo(2);

        // Re-firing the completion trigger must not create duplicates.
        qnaService.openWindowsIfTrigger(EVENT_CODE, ch.batbern.events.domain.QnaOpenTrigger.EVENT_COMPLETED);
        assertThat(windowRepository.count()).isEqualTo(2);
        assertThat(windowRepository.findAll())
                .allSatisfy(w -> assertThat(w.getStatus()).isEqualTo(QnaWindowStatus.OPEN));
    }

    @Test
    @DisplayName("Story 7.5 rework: a trigger that doesn't match the event's setting opens nothing")
    void should_notOpen_when_triggerMismatch() {
        Event event = saveEvent(EventWorkflowState.EVENT_COMPLETED); // trigger defaults to EVENT_COMPLETED
        saveSession(event, SLUG);

        // SPEAKERS_PUBLISHED trigger on an EVENT_COMPLETED-configured event → no windows.
        qnaService.openWindowsIfTrigger(EVENT_CODE, ch.batbern.events.domain.QnaOpenTrigger.SPEAKERS_PUBLISHED);
        assertThat(windowRepository.count()).isZero();
    }

    // ==================== AC2: logged-in post within window ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC2: logged-in attendee posts a question within an open window → 201, attributed")
    void should_post_when_loggedInAndOpen() throws Exception {
        openWindow(QnaWindowStatus.OPEN, Instant.now().plus(10, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/events/{e}/sessions/{s}/qna/posts", EVENT_CODE, SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"Great talk — how did the migration go?\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postedByUsername", is(ATTENDEE)))
                .andExpect(jsonPath("$.removed", is(false)));

        mockMvc.perform(get("/api/v1/events/{e}/sessions/{s}/qna", EVENT_CODE, SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.posts.length()", is(1)));
    }

    @Test
    @DisplayName("Q&A poster is enriched with display name + company logo (not the raw username)")
    void should_enrichPoster_withNameAndCompanyLogo() throws Exception {
        SessionQnaWindow window = openWindow(QnaWindowStatus.FROZEN, Instant.now().minus(1, ChronoUnit.DAYS));
        seedAuthor(ATTENDEE, "Jane", "Attendee", "bkw", true,
                "BKW Energie AG", "https://cdn.batbern.ch/logos/bkw.png");
        postRepository.save(SessionQnaPost.builder().windowId(window.getId())
                .postedByUsername(ATTENDEE).body("Great talk").build());

        mockMvc.perform(get("/api/v1/events/{e}/sessions/{s}/qna", EVENT_CODE, SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].postedByUsername", is(ATTENDEE)))
                .andExpect(jsonPath("$.posts[0].postedByFirstName", is("Jane")))
                .andExpect(jsonPath("$.posts[0].postedByLastName", is("Attendee")))
                .andExpect(jsonPath("$.posts[0].postedByCompanyName", is("BKW Energie AG")))
                .andExpect(jsonPath("$.posts[0].postedByCompanyLogoUrl",
                        is("https://cdn.batbern.ch/logos/bkw.png")));
    }

    @Test
    @DisplayName("Poster who opted out of showing company → name kept, company fields suppressed")
    void should_suppressCompany_when_showCompanyFalse() throws Exception {
        SessionQnaWindow window = openWindow(QnaWindowStatus.FROZEN, Instant.now().minus(1, ChronoUnit.DAYS));
        seedAuthor(ATTENDEE, "Jane", "Attendee", "bkw", false,
                "BKW Energie AG", "https://cdn.batbern.ch/logos/bkw.png");
        postRepository.save(SessionQnaPost.builder().windowId(window.getId())
                .postedByUsername(ATTENDEE).body("Great talk").build());

        mockMvc.perform(get("/api/v1/events/{e}/sessions/{s}/qna", EVENT_CODE, SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].postedByFirstName", is("Jane")))
                .andExpect(jsonPath("$.posts[0].postedByCompanyName", nullValue()))
                .andExpect(jsonPath("$.posts[0].postedByCompanyLogoUrl", nullValue()));
    }

    // ==================== AC3: anonymous cannot post; can read ====================

    @Test
    @DisplayName("AC3: anonymous post is rejected (401 at gateway / 403 in isolation), no row created")
    void should_reject_anonymousPost() throws Exception {
        openWindow(QnaWindowStatus.OPEN, Instant.now().plus(10, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/events/{e}/sessions/{s}/qna/posts", EVENT_CODE, SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"anon question\" }"))
                .andExpect(status().isForbidden());

        assertThat(postRepository.count()).isZero();
    }

    @Test
    @DisplayName("AC3: anonymous can READ the (frozen) thread publicly")
    void should_allowAnonymousRead_ofFrozenThread() throws Exception {
        SessionQnaWindow window = openWindow(QnaWindowStatus.FROZEN, Instant.now().minus(1, ChronoUnit.DAYS));
        postRepository.save(SessionQnaPost.builder().windowId(window.getId())
                .postedByUsername(ATTENDEE).body("archived question").build());

        mockMvc.perform(get("/api/v1/events/{e}/sessions/{s}/qna", EVENT_CODE, SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FROZEN")))
                .andExpect(jsonPath("$.posts.length()", is(1)))
                .andExpect(jsonPath("$.posts[0].body", is("archived question")));
    }

    // ==================== AC5: post after close rejected ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC5: posting to a frozen window → 409 QNA_WINDOW_FROZEN, no row created")
    void should_reject_post_when_frozen() throws Exception {
        openWindow(QnaWindowStatus.FROZEN, Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/events/{e}/sessions/{s}/qna/posts", EVENT_CODE, SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"too late\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("QNA_WINDOW_FROZEN")));

        assertThat(postRepository.count()).isZero();
    }

    // ==================== AC4: organizer extend / close-early / takedown ====================

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC4 (rework): organizer closes the event's Q&A early → all windows FROZEN")
    void should_closeEarly_when_organizer() throws Exception {
        openWindow(QnaWindowStatus.OPEN, Instant.now().plus(10, ChronoUnit.DAYS));

        // Event-level endpoint (per-session PATCH removed).
        mockMvc.perform(patch("/api/v1/events/{e}/qna", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"close\": true }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FROZEN")))
                .andExpect(jsonPath("$.windowsAdjusted", is(1)));

        assertThat(windowRepository.findByEventCode(EVENT_CODE))
                .allSatisfy(w -> assertThat(w.getStatus()).isEqualTo(QnaWindowStatus.FROZEN));
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC4 (rework): organizer extends the event's Q&A → all windows reopen OPEN")
    void should_extend_when_organizer() throws Exception {
        openWindow(QnaWindowStatus.FROZEN, Instant.now().minus(1, ChronoUnit.DAYS));
        String future = Instant.now().plus(30, ChronoUnit.DAYS).toString();

        mockMvc.perform(patch("/api/v1/events/{e}/qna", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"closesAt\": \"" + future + "\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OPEN")));

        assertThat(windowRepository.findByEventCode(EVENT_CODE))
                .allSatisfy(w -> assertThat(w.getStatus()).isEqualTo(QnaWindowStatus.OPEN));
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("Rework: open creates windows for every session when none exist (manual open)")
    void should_openCreatesWindows_when_noneExist() throws Exception {
        // An event whose trigger moment already passed / never fired → no windows yet.
        Event event = saveEvent(EventWorkflowState.SPEAKER_IDENTIFICATION);
        saveSession(event, SLUG);
        saveSession(event, "second-session");
        assertThat(windowRepository.findByEventCode(EVENT_CODE)).isEmpty();

        // Manual open via the event-level endpoint must CREATE the windows (no 404).
        mockMvc.perform(patch("/api/v1/events/{e}/qna", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"open\": true }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.windowsAdjusted", is(2)));

        assertThat(windowRepository.findByEventCode(EVENT_CODE)).hasSize(2)
                .allSatisfy(w -> assertThat(w.getStatus()).isEqualTo(QnaWindowStatus.OPEN));
    }

    @Test
    @DisplayName("AC4: organizer takedown soft-deletes (tombstone); attendee role cannot take down (403)")
    void should_takedownPost_when_organizer() throws Exception {
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN, Instant.now().plus(10, ChronoUnit.DAYS));
        SessionQnaPost p = postRepository.save(SessionQnaPost.builder().windowId(window.getId())
                .postedByUsername(ATTENDEE).body("to be removed").build());

        // Attendee cannot take down.
        mockMvc.perform(delete("/api/v1/events/{e}/sessions/{s}/qna/posts/{id}", EVENT_CODE, SLUG, p.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(ATTENDEE).roles("ATTENDEE")))
                .andExpect(status().isForbidden());

        // Organizer takes it down.
        mockMvc.perform(delete("/api/v1/events/{e}/sessions/{s}/qna/posts/{id}", EVENT_CODE, SLUG, p.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(ORGANIZER).roles("ORGANIZER")))
                .andExpect(status().isNoContent());

        // Tombstone: soft-deleted (removed_at set), body/author hidden in the response.
        assertThat(postRepository.findById(p.getId())).get()
                .satisfies(post -> assertThat(post.getRemovedAt()).isNotNull());
        mockMvc.perform(get("/api/v1/events/{e}/sessions/{s}/qna", EVENT_CODE, SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].removed", is(true)))
                .andExpect(jsonPath("$.posts[0].body", nullValue()))
                .andExpect(jsonPath("$.posts[0].postedByUsername", nullValue()));
    }

    // ==================== AC5/AC6: scheduled freeze job ====================

    @Test
    @DisplayName("AC5/6: freeze job flips OPEN windows past closesAt → FROZEN (ShedLock mocked)")
    void should_freezeExpiredWindows() {
        SessionQnaWindow expired = openWindow(QnaWindowStatus.OPEN, Instant.now().minus(1, ChronoUnit.HOURS));
        // A still-open window must be left alone.
        Event event2 = saveEvent(EventWorkflowState.EVENT_COMPLETED, "BATbern961");
        Session s2 = saveSession(event2, "future-window-session");
        windowRepository.save(SessionQnaWindow.builder().sessionId(s2.getId()).eventCode("BATbern961")
                .status(QnaWindowStatus.OPEN).opensAt(Instant.now())
                .closesAt(Instant.now().plus(10, ChronoUnit.DAYS)).build());

        scheduledService.freezeExpiredWindows();

        assertThat(windowRepository.findById(expired.getId())).get()
                .satisfies(w -> assertThat(w.getStatus()).isEqualTo(QnaWindowStatus.FROZEN));
        assertThat(windowRepository.findBySessionId(s2.getId())).get()
                .satisfies(w -> assertThat(w.getStatus()).as("future window stays open")
                        .isEqualTo(QnaWindowStatus.OPEN));
    }

    // ==================== Threading guards (code-review fixes) ====================

    @Test
    @DisplayName("Reply to an answer (2nd level) → 400; reply to a removed post → 400")
    void should_reject_nestedReply_and_replyToRemoved() throws Exception {
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN, Instant.now().plus(10, ChronoUnit.DAYS));
        SessionQnaPost question = postRepository.save(SessionQnaPost.builder()
                .windowId(window.getId()).postedByUsername(ATTENDEE).body("question").build());
        SessionQnaPost answer = postRepository.save(SessionQnaPost.builder()
                .windowId(window.getId()).parentPostId(question.getId())
                .postedByUsername(ATTENDEE).body("answer").build());

        // Reply to an answer → one-level-threading violation → 400.
        mockMvc.perform(post("/api/v1/events/{e}/sessions/{s}/qna/posts", EVENT_CODE, SLUG)
                        .with(SecurityMockMvcRequestPostProcessors.user(ATTENDEE).roles("ATTENDEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"reply to answer\", \"parentPostId\": \"" + answer.getId() + "\" }"))
                .andExpect(status().isBadRequest());

        // Remove the question, then reply to the tombstone → 400.
        question.setRemovedAt(Instant.now());
        postRepository.save(question);
        mockMvc.perform(post("/api/v1/events/{e}/sessions/{s}/qna/posts", EVENT_CODE, SLUG)
                        .with(SecurityMockMvcRequestPostProcessors.user(ATTENDEE).roles("ATTENDEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"reply to removed\", \"parentPostId\": \"" + question.getId() + "\" }"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Misc ====================

    @Test
    @DisplayName("GET on a session with no window → 404")
    void should_return404_when_noWindow() throws Exception {
        Event event = saveEvent(EventWorkflowState.EVENT_COMPLETED);
        saveSession(event, SLUG);

        mockMvc.perform(get("/api/v1/events/{e}/sessions/{s}/qna", EVENT_CODE, SLUG))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Empty body → 400")
    void should_reject_emptyBody() throws Exception {
        openWindow(QnaWindowStatus.OPEN, Instant.now().plus(10, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/events/{e}/sessions/{s}/qna/posts", EVENT_CODE, SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"\" }"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Helpers ====================

    /**
     * Seed the cross-service rows the Q&A poster-enrichment native query reads: a company, the
     * poster's user_profiles row (with the show-company privacy flag), and an ASSOCIATED company
     * logo. These live in CUMS-owned tables stubbed for the EMS test container.
     */
    private void seedAuthor(String username, String firstName, String lastName, String companyKey,
                            boolean showCompany, String companyDisplayName, String logoUrl) {
        java.util.UUID companyId = java.util.UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, name, display_name) VALUES (?, ?, ?)",
                companyId, companyKey, companyDisplayName);
        jdbcTemplate.update(
                "INSERT INTO user_profiles (username, company_id, first_name, last_name, settings_show_company) "
                        + "VALUES (?, ?, ?, ?, ?)",
                username, companyKey, firstName, lastName, showCompany);
        jdbcTemplate.update(
                "INSERT INTO logos (upload_id, s3_key, cloudfront_url, file_extension, file_size, mime_type, "
                        + "status, associated_entity_type, associated_entity_id) "
                        + "VALUES (?, ?, ?, 'png', 1024, 'image/png', 'ASSOCIATED', 'COMPANY', ?)",
                "upl-" + companyKey, "logos/" + companyKey + ".png", logoUrl, companyId.toString());
    }

    private SessionQnaWindow openWindow(QnaWindowStatus status, Instant closesAt) {
        Event event = saveEvent(EventWorkflowState.EVENT_COMPLETED);
        Session session = saveSession(event, SLUG);
        return windowRepository.save(SessionQnaWindow.builder()
                .sessionId(session.getId())
                .eventCode(EVENT_CODE)
                .status(status)
                .opensAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .closesAt(closesAt)
                .build());
    }

    private Event saveEvent(EventWorkflowState state) {
        return saveEvent(state, EVENT_CODE);
    }

    private Event saveEvent(EventWorkflowState state, String eventCode) {
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventNumber(Integer.parseInt(eventCode.replaceAll("\\D", "")));
        event.setTitle("Q&A Test Event");
        event.setDate(Instant.now().minus(2, ChronoUnit.DAYS));
        event.setRegistrationDeadline(Instant.now().minus(9, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(150);
        event.setOrganizerUsername(ORGANIZER);
        event.setEventType(ch.batbern.events.dto.generated.EventType.EVENING);
        event.setWorkflowState(state);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy(ORGANIZER);
        event.setUpdatedBy(ORGANIZER);
        return eventRepository.save(event);
    }

    private Session saveSession(Event event, String slug) {
        return sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug(slug)
                .title("Session " + slug)
                .sessionType("presentation")
                .startTime(Instant.now().minus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().minus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }
}
