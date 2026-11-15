package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Characterization of every response {@code EventController} gave on {@code fdf9b25b1}, before its
 * logic moved into event-core services and module adapters (backlog story "EventController keeps only
 * HTTP"). Each row's status, {@code X-Cache-Status} and body (UUIDs and ISO dates replaced by
 * {@code uuid#n} / {@code ts#n} / {@code d#n} / {@code -reg-#n} in order of first appearance) is pinned in
 * {@code src/test/resources/eventcontroller-capture} (UUIDs numbered by first appearance, dates and times per
 * occurrence). A write row also pins the event read back,
 * the registrations of the event and the mails SES received. A missing golden file is written; a
 * mismatch fails and writes the actual output to {@code build/eventcontroller-capture-actual}.
 *
 * <p>Commits like production (no test transaction): the create's REQUIRES_NEW auto-enrolment and the
 * waitlist promotion run on their real boundaries. Every event is removed before and after each test.
 * Fixed event numbers {@code BATbern97xx}; dates relative to now.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EventController responses as captured on fdf9b25b1")
class EventControllerCaptureIntegrationTest extends AbstractIntegrationTest {

    private static final Path GOLDEN = Path.of("src/test/resources/eventcontroller-capture");
    private static final Path ACTUAL = Path.of("build/eventcontroller-capture-actual");

    private static final String UPCOMING = "BATbern9701";
    private static final String ARCHIVED = "BATbern9702";
    private static final String ARCHIVED_WITH_TOPIC = "BATbern9703";
    private static final String DRAFT = "BATbern9704";
    private static final String SIM = "@simulator.amazonses.com";

    private static final List<String> INCLUDES = List.of(
            "none", "topics", "venue", "sessions", "speakers", "metrics", "registrations",
            "topics,venue,sessions,speakers,metrics,registrations", "unknown", "sessions,sessions");

    private static final RequestPostProcessor ORGANIZER = user("olga.organizer").roles("ORGANIZER");
    private static final RequestPostProcessor ATTENDEE = user("anna.attendee").roles("ATTENDEE");

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern TS_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:\\d{2})?");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /** Generated registration codes carry a random suffix ({@code BATbern9710-reg-L1UU17}). */
    private static final Pattern REGISTRATION_CODE_PATTERN = Pattern.compile("-reg-[A-Z0-9]{6}");

    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final List<String> mismatches = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private SessionMaterialsRepository sessionMaterialsRepository;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private CumsContractHarness cums;

    @BeforeEach
    void setUp() {
        cleanUp();
        sesRecorder.reset();
        for (String username : List.of("wanda.first", "walter.second", "rita.registered", "carl.confirmed")) {
            cums.user(username, "{\"email\":\"success+" + username.replace('.', '-') + SIM + "\"}");
        }
        // Auto-enrolment at create (REQUIRES_NEW listener) enrols the organizers CUMS lists
        cums.usersWithRole("ORGANIZER", "olga.organizer");
        fixture();
        clearCaches();
    }

    @AfterEach
    void tearDown() {
        sesRecorder.awaitAsyncSends();
        cleanUp();
        cums.reset();
    }

    // ============================================================================================
    // Reads
    // ============================================================================================

    @Test
    @DisplayName("GET /events/{code} for every include and audience, with the cache status")
    void getEvent() throws Exception {
        for (String include : INCLUDES) {
            String name = "get-event_" + slug(include);
            read(name + "_anonymous", withInclude(get("/api/v1/events/" + UPCOMING), include));
            read(name + "_attendee", withInclude(get("/api/v1/events/" + UPCOMING), include).with(ATTENDEE));
            read(name + "_organizer", withInclude(get("/api/v1/events/" + UPCOMING), include).with(ORGANIZER));
            read(name + "_organizer-again", withInclude(get("/api/v1/events/" + UPCOMING), include).with(ORGANIZER));
        }
        read("get-event-archived-with-topic_organizer",
                get("/api/v1/events/" + ARCHIVED_WITH_TOPIC).param("include", "topics,sessions,metrics")
                        .with(ORGANIZER));
        read("get-event-unknown-code_organizer", get("/api/v1/events/BATbern9799").with(ORGANIZER));
        read("get-event-bad-code_anonymous", get("/api/v1/events/not-a-code"));
        assertNoMismatch();
    }

    @Test
    @DisplayName("GET /events for every include and audience")
    void listEvents() throws Exception {
        for (String include : INCLUDES) {
            String name = "list-events_" + slug(include);
            read(name + "_anonymous", withInclude(listAll(), include));
            read(name + "_attendee", withInclude(listAll(), include).with(ATTENDEE));
            read(name + "_organizer", withInclude(listAll(), include).with(ORGANIZER));
        }
        read("list-events_filter-workflow_organizer", get("/api/v1/events")
                .param("filter", "{\"workflowState\":\"AGENDA_PUBLISHED\"}").with(ORGANIZER));
        read("list-events_default_organizer", get("/api/v1/events").with(ORGANIZER));
        read("list-events_page2_organizer", listAll().param("limit", "2").param("page", "2")
                .param("include", "topics").with(ORGANIZER));
        read("list-events_bad-filter_organizer", get("/api/v1/events").param("filter", "{not json")
                .with(ORGANIZER));
        assertNoMismatch();
    }

    @Test
    @DisplayName("GET /events/current for every include and audience")
    void currentEvent() throws Exception {
        for (String include : INCLUDES) {
            String name = "current-event_" + slug(include);
            read(name + "_anonymous", withInclude(get("/api/v1/events/current"), include));
            read(name + "_attendee", withInclude(get("/api/v1/events/current"), include).with(ATTENDEE));
            read(name + "_organizer", withInclude(get("/api/v1/events/current"), include).with(ORGANIZER));
        }
        assertNoMismatch();
    }

    @Test
    @DisplayName("GET /events/current answers 404 when no event qualifies")
    void currentEvent_none() throws Exception {
        jdbc.update("UPDATE events SET current_published_phase = NULL WHERE event_code = ?", UPCOMING);
        read("current-event-no-event_anonymous", get("/api/v1/events/current"));
        read("public-current-no-event_anonymous", get("/api/v1/public/events/current"));
        assertNoMismatch();
    }

    @Test
    @DisplayName("GET /public/events{,/current,/{code}} for every audience")
    void publicEvents() throws Exception {
        Map<String, Supplier<MockHttpServletRequestBuilder>> rows = new LinkedHashMap<>();
        rows.put("public-current", () -> get("/api/v1/public/events/current"));
        rows.put("public-detail", () -> get("/api/v1/public/events/" + UPCOMING));
        rows.put("public-detail-archived", () -> get("/api/v1/public/events/" + ARCHIVED_WITH_TOPIC));
        rows.put("public-detail-draft", () -> get("/api/v1/public/events/" + DRAFT));
        rows.put("public-detail-unknown", () -> get("/api/v1/public/events/BATbern9799"));
        rows.put("public-list-upcoming", () -> get("/api/v1/public/events").param("scope", "upcoming"));
        rows.put("public-list-archive", () -> get("/api/v1/public/events").param("scope", "archive"));
        rows.put("public-list-archive-search", () -> get("/api/v1/public/events").param("scope", "archive")
                .param("search", "Topic"));
        rows.put("public-list-archive-topics", () -> get("/api/v1/public/events").param("scope", "archive")
                .param("topicCodes", "capture-archived-topic, ,"));
        rows.put("public-list-archive-sort", () -> get("/api/v1/public/events").param("scope", "archive")
                .param("sort", "date"));
        rows.put("public-list-archive-bad-sort", () -> get("/api/v1/public/events").param("scope", "archive")
                .param("sort", "title"));
        rows.put("public-list-bad-scope", () -> get("/api/v1/public/events").param("scope", "drafts"));
        rows.put("public-list-no-scope", () -> get("/api/v1/public/events"));
        for (Map.Entry<String, Supplier<MockHttpServletRequestBuilder>> row : rows.entrySet()) {
            read(row.getKey() + "_anonymous", row.getValue().get());
            read(row.getKey() + "_attendee", row.getValue().get().with(ATTENDEE));
            read(row.getKey() + "_organizer", row.getValue().get().with(ORGANIZER));
        }
        assertNoMismatch();
    }

    @Test
    @DisplayName("GET /events/{code}/analytics")
    void analytics() throws Exception {
        read("analytics_default_organizer", get("/api/v1/events/" + UPCOMING + "/analytics").with(ORGANIZER));
        read("analytics_registrations_attendee", get("/api/v1/events/" + UPCOMING + "/analytics")
                .param("metrics", "registrations").with(ATTENDEE));
        read("analytics_timeframe_organizer", get("/api/v1/events/" + UPCOMING + "/analytics")
                .param("metrics", "registrations,attendance,engagement")
                .param("timeframe", "2000-01-01T00:00:00Z,2100-01-01T00:00:00Z").with(ORGANIZER));
        read("analytics_anonymous", get("/api/v1/events/" + UPCOMING + "/analytics"));
        read("analytics_unknown_organizer", get("/api/v1/events/BATbern9799/analytics").with(ORGANIZER));
        read("analytics_bad-code_organizer", get("/api/v1/events/not-a-code/analytics").with(ORGANIZER));
        assertNoMismatch();
    }

    // ============================================================================================
    // Writes (each followed by the event read back, its registrations and the mails sent)
    // ============================================================================================

    @Test
    @DisplayName("POST /events")
    void createEvent() throws Exception {
        confirmedUpload("capture-theme-1", "confirmed");
        confirmedUpload("capture-theme-pending", "pending");
        write("create_minimal_organizer", post("/api/v1/events").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(9710, null)), "BATbern9710");
        write("create_theme_organizer", post("/api/v1/events").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(9711, "capture-theme-1")), "BATbern9711");
        write("create_theme-unknown_organizer", post("/api/v1/events").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(9712, "capture-theme-none")), "BATbern9712");
        write("create_theme-pending_organizer", post("/api/v1/events").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(9713, "capture-theme-pending")),
                "BATbern9713");
        write("create_duplicate-number_organizer", post("/api/v1/events").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(9701, null)), UPCOMING);
        write("create_missing-title_organizer", post("/api/v1/events").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(9714, null).replace("\"title\":\"Capture Created 9714\",", "")), "BATbern9714");
        write("create_anonymous", post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON).content(createBody(9715, null)), "BATbern9715");
        write("create_attendee", post("/api/v1/events").with(ATTENDEE)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(9716, null)), "BATbern9716");
        assertNoMismatch();
    }

    @Test
    @DisplayName("PUT /events/{code}: capacity raised with a waitlist")
    void updateEvent_capacity() throws Exception {
        write("put_capacity-raised_organizer", put("/api/v1/events/" + UPCOMING).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(4, null, null)), UPCOMING);
        write("put_capacity-unchanged_organizer", put("/api/v1/events/" + UPCOMING).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(4, null, null)), UPCOMING);
        assertNoMismatch();
    }

    @Test
    @DisplayName("PUT /events/{code}: theme image new, same, blank; errors")
    void updateEvent_theme() throws Exception {
        confirmedUpload("capture-theme-1", "confirmed");
        confirmedUpload("capture-theme-pending", "pending");
        // Cache filled before the write: the write must evict it
        read("put_before_organizer", get("/api/v1/events/" + UPCOMING).param("include", "venue").with(ORGANIZER));
        write("put_theme-same_organizer", put("/api/v1/events/" + UPCOMING).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(3, "capture-theme-0", null)), UPCOMING);
        write("put_theme-new_organizer", put("/api/v1/events/" + UPCOMING).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(3, "capture-theme-1", null)), UPCOMING);
        write("put_theme-blank_organizer", put("/api/v1/events/" + UPCOMING).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(3, "", null)), UPCOMING);
        write("put_theme-unknown_organizer", put("/api/v1/events/" + UPCOMING).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(9, "capture-theme-none", null)), UPCOMING);
        write("put_theme-pending_organizer", put("/api/v1/events/" + UPCOMING).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(9, "capture-theme-pending", null)),
                UPCOMING);
        write("put_number-taken_organizer", put("/api/v1/events/" + UPCOMING).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(3, null, 9702)), UPCOMING);
        write("put_number-changed_organizer", put("/api/v1/events/" + DRAFT).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(null, null, 9720)), "BATbern9720");
        write("put_unknown_organizer", put("/api/v1/events/BATbern9799").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(3, null, null)), "BATbern9799");
        write("put_anonymous", put("/api/v1/events/" + UPCOMING)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(9, null, null)), UPCOMING);
        write("put_attendee", put("/api/v1/events/" + UPCOMING).with(ATTENDEE)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(9, null, null)), UPCOMING);
        assertNoMismatch();
    }

    @Test
    @DisplayName("PATCH /events/{code}: capacity, theme image, fields; errors")
    void patchEvent() throws Exception {
        confirmedUpload("capture-theme-1", "confirmed");
        confirmedUpload("capture-theme-pending", "pending");
        read("patch_before_organizer", get("/api/v1/events/" + UPCOMING).param("include", "venue").with(ORGANIZER));
        write("patch_capacity-raised_organizer", patchJson(UPCOMING, "{\"registrationCapacity\":5}"), UPCOMING);
        write("patch_capacity-same_organizer", patchJson(UPCOMING, "{\"registrationCapacity\":5}"), UPCOMING);
        write("patch_theme-same_organizer", patchJson(UPCOMING, "{\"themeImageUploadId\":\"capture-theme-0\"}"),
                UPCOMING);
        write("patch_theme-new_organizer", patchJson(UPCOMING, "{\"themeImageUploadId\":\"capture-theme-1\"}"),
                UPCOMING);
        write("patch_theme-blank_organizer", patchJson(UPCOMING, "{\"themeImageUploadId\":\"\"}"), UPCOMING);
        write("patch_theme-unknown_organizer",
                patchJson(UPCOMING, "{\"themeImageUploadId\":\"capture-theme-none\",\"title\":\"Not saved\"}"),
                UPCOMING);
        write("patch_theme-pending_organizer",
                patchJson(UPCOMING, "{\"themeImageUploadId\":\"capture-theme-pending\",\"title\":\"Not saved\"}"),
                UPCOMING);
        write("patch_fields_organizer", patchJson(UPCOMING, """
                {"title":"Patched title","description":"Patched","venueName":"Progr","venueAddress":"Waisenhausplatz 30",
                 "venueCapacity":120,"organizerUsername":"olga.organizer","topicCode":"capture-upcoming-topic",
                 "topicSelectionNote":"from the canvas","qnaEnabled":true,"qnaOpenTrigger":"EVENT_COMPLETED",
                 "qnaWindowDays":5,"eventType":"FULL_DAY","metadata":"{\\"k\\":\\"v\\"}"}"""), UPCOMING);
        write("patch_number-taken_organizer", patchJson(DRAFT, "{\"eventNumber\":9701}"), DRAFT);
        write("patch_number-changed_organizer", patchJson(DRAFT, "{\"eventNumber\":9721}"), "BATbern9721");
        write("patch_unknown_organizer", patchJson("BATbern9799", "{\"title\":\"x\"}"), "BATbern9799");
        write("patch_anonymous", patch("/api/v1/events/" + UPCOMING)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"), UPCOMING);
        write("patch_attendee", patch("/api/v1/events/" + UPCOMING).with(ATTENDEE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"), UPCOMING);
        assertNoMismatch();
    }

    @Test
    @DisplayName("DELETE /events/{code}")
    void deleteEvent() throws Exception {
        write("delete_real-attendees_organizer", delete("/api/v1/events/" + UPCOMING).with(ORGANIZER), UPCOMING);
        write("delete_anonymous", delete("/api/v1/events/" + DRAFT), DRAFT);
        write("delete_attendee", delete("/api/v1/events/" + DRAFT).with(ATTENDEE), DRAFT);
        write("delete_unknown_organizer", delete("/api/v1/events/BATbern9799").with(ORGANIZER), "BATbern9799");
        jdbc.update("INSERT INTO notifications (id, recipient_username, notification_type, channel, priority,"
                + " subject, body, event_code, status, created_at) VALUES (gen_random_uuid(), 'olga.organizer',"
                + " 'EVENT_UPDATE', 'IN_APP', 'NORMAL', 'Capture', 'Capture', ?, 'PENDING', now())", DRAFT);
        write("delete_draft_organizer", delete("/api/v1/events/" + DRAFT).with(ORGANIZER), DRAFT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE event_code = ?", Long.class, DRAFT))
                .isZero();
        write("delete_programmatic-only_organizer", delete("/api/v1/events/" + ARCHIVED).with(ORGANIZER), ARCHIVED);
        assertNoMismatch();
    }

    @Test
    @DisplayName("POST /events/{code}/topics")
    void selectTopic() throws Exception {
        write("topic_anonymous", post("/api/v1/events/" + DRAFT + "/topics")
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicCode\":\"capture-upcoming-topic\"}"), DRAFT);
        write("topic_attendee", post("/api/v1/events/" + DRAFT + "/topics").with(ATTENDEE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicCode\":\"capture-upcoming-topic\"}"), DRAFT);
        write("topic_unknown-topic_organizer", post("/api/v1/events/" + DRAFT + "/topics").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicCode\":\"capture-no-such-topic\"}"), DRAFT);
        write("topic_unknown-event_organizer", post("/api/v1/events/BATbern9799/topics").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicCode\":\"capture-upcoming-topic\"}"),
                "BATbern9799");
        write("topic_missing-code_organizer", post("/api/v1/events/" + DRAFT + "/topics").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), DRAFT);
        write("topic_wrong-state_organizer", post("/api/v1/events/" + UPCOMING + "/topics").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicCode\":\"capture-upcoming-topic\"}"),
                UPCOMING);
        // Cache filled before the write: the write must evict it
        read("topic_before_organizer", get("/api/v1/events/" + DRAFT).param("include", "topics").with(ORGANIZER));
        write("topic_selected_organizer", post("/api/v1/events/" + DRAFT + "/topics").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicCode\":\"capture-upcoming-topic\"}"), DRAFT);
        assertNoMismatch();
    }

    // ============================================================================================
    // Fixture
    // ============================================================================================

    /**
     * BATbern9701: published, upcoming (agenda phase), topic, theme image, two teaser images, sessions
     * with speakers and materials, a structural session, a speaker pool along the lifecycle, a
     * registration per status (capacity 3: two on the waitlist) and a programmatic one.
     * BATbern9702: archived, only programmatic and historical registrations. BATbern9703: archived with
     * a topic and one session. BATbern9704: a draft (CREATED), no registrations.
     */
    private void fixture() {
        // ASCII-only, small fixture (17.0d exception): the goldens pin response shape and module
        // composition, not name handling; names never reach a search or sort here, and changing the
        // data would change the goldens taken on fdf9b25b1.
        Topic upcomingTopic = topic("capture-upcoming-topic", "Capture Upcoming Topic");
        Topic archivedTopic = topic("capture-archived-topic", "Capture Archived Topic");

        Instant upcomingDate = daysFromNow(30);
        Event upcoming = event(UPCOMING, 9701, "Capture Upcoming", upcomingDate,
                EventWorkflowState.AGENDA_PUBLISHED, "agenda");
        upcoming.setTopicCode(upcomingTopic.getTopicCode());
        upcoming.setRegistrationCapacity(3);
        upcoming.setDescription("Upcoming description");
        upcoming.setThemeImageUploadId("capture-theme-0");
        upcoming.setThemeImageUrl("https://cdn.batbern.ch/events/" + UPCOMING + "/theme-capture-theme-0.png");
        upcoming = eventRepository.save(upcoming);
        confirmedUpload("capture-theme-0", "associated");

        Session keynote = session(upcoming, "Keynote Capture", "keynote", upcomingDate.plus(Duration.ofMinutes(30)));
        speaker(keynote, "kim.keynote", SessionUser.SpeakerRole.PRIMARY_SPEAKER, "Kim", "Keynote");
        material(keynote, "capture-material-1", "slides.pdf", Instant.now().minus(Duration.ofDays(2)));
        material(keynote, "capture-material-2", "handout.pdf", Instant.now().minus(Duration.ofDays(1)));
        Session talk = session(upcoming, "Talk Capture", "presentation", upcomingDate.plus(Duration.ofMinutes(90)));
        speaker(talk, "tom.talker", SessionUser.SpeakerRole.PRIMARY_SPEAKER, "Tom", "Talker");
        session(upcoming, "Lunch", "lunch", upcomingDate.plus(Duration.ofMinutes(60)));
        Session unslotted = session(upcoming, "Unslotted Capture", "presentation", null);
        speaker(unslotted, "una.unslotted", SessionUser.SpeakerRole.PRIMARY_SPEAKER, null, null);

        pool(upcoming, "Ida Invited", SpeakerWorkflowState.INVITED, null);
        pool(upcoming, "Kim Keynote", SpeakerWorkflowState.ACCEPTED, keynote);
        pool(upcoming, "Tom Talker", SpeakerWorkflowState.CONTENT_SUBMITTED, talk);
        pool(upcoming, "Una Unslotted", SpeakerWorkflowState.QUALITY_REVIEWED, unslotted);
        pool(upcoming, "Quinn Reviewed", SpeakerWorkflowState.QUALITY_REVIEWED, null);

        teaser(UPCOMING, 0);
        teaser(UPCOMING, 1);

        registration(upcoming, "rita.registered", "registered", null, false, 10);
        registration(upcoming, "carl.confirmed", "confirmed", null, false, 9);
        registration(upcoming, "olga.organizer", "confirmed", null, true, 8);
        registration(upcoming, "wanda.first", "waitlist", 1, false, 7);
        registration(upcoming, "walter.second", "waitlist", 2, false, 6);
        registration(upcoming, "cora.cancelled", "cancelled", null, false, 5);

        Event archived = eventRepository.save(event(ARCHIVED, 9702, "Capture Archived", daysFromNow(-200),
                EventWorkflowState.ARCHIVED, "agenda"));
        registration(archived, "olga.organizer", "confirmed", null, true, 300);
        registration(archived, "nora.noshow", "no_show", null, false, 300);

        Event archivedWithTopic = event(ARCHIVED_WITH_TOPIC, 9703, "Capture Archived Topic Event",
                daysFromNow(-400), EventWorkflowState.ARCHIVED, "agenda");
        archivedWithTopic.setTopicCode(archivedTopic.getTopicCode());
        archivedWithTopic = eventRepository.save(archivedWithTopic);
        Session archivedTalk = session(archivedWithTopic, "Archived Talk", "presentation",
                archivedWithTopic.getDate().plus(Duration.ofMinutes(30)));
        speaker(archivedTalk, "ari.archived", SessionUser.SpeakerRole.PRIMARY_SPEAKER, "Ari", "Archived");
        registration(archivedWithTopic, "ada.attended", "attended", null, false, 500);

        eventRepository.save(event(DRAFT, 9704, "Capture Draft", daysFromNow(120), EventWorkflowState.CREATED, null));
    }

    private Event event(String code, int number, String title, Instant date, EventWorkflowState state,
                        String phase) {
        return Event.builder()
                .eventCode(code)
                .title(title)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("olga.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(state)
                .currentPublishedPhase(phase)
                .build();
    }

    private Topic topic(String code, String title) {
        Topic topic = new Topic();
        topic.setTopicCode(code);
        topic.setTitle(title);
        topic.setDescription(title + " description");
        topic.setCategory("technical");
        return topicRepository.save(topic);
    }

    private Session session(Event event, String title, String type, Instant start) {
        return sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug(title.toLowerCase().replace(' ', '-'))
                .title(title)
                .description(title + " abstract")
                .sessionType(type)
                .startTime(start)
                .endTime(start == null ? null : start.plus(Duration.ofMinutes(30)))
                .room("Saal")
                .language("de")
                .build());
    }

    private void speaker(Session session, String username, SessionUser.SpeakerRole role, String first, String last) {
        sessionUserRepository.save(SessionUser.builder()
                .session(session)
                .username(username)
                .speakerFirstName(first)
                .speakerLastName(last)
                .speakerRole(role)
                .isConfirmed(true)
                .build());
    }

    private void material(Session session, String uploadId, String fileName, Instant createdAt) {
        sessionMaterialsRepository.save(SessionMaterial.builder()
                .session(session)
                .uploadId(uploadId)
                .s3Key("sessions/" + uploadId + "/" + fileName)
                .cloudFrontUrl("https://cdn.batbern.ch/sessions/" + uploadId + "/" + fileName)
                .fileName(fileName)
                .fileExtension("pdf")
                .fileSize(2048L)
                .mimeType("application/pdf")
                .materialType("PRESENTATION")
                .uploadedBy("olga.organizer")
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build());
    }

    private void pool(Event event, String name, SpeakerWorkflowState state, Session session) {
        speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(event.getId())
                .speakerName(name)
                .status(state)
                .sessionId(session == null ? null : session.getId())
                .build());
    }

    private void teaser(String eventCode, int order) {
        jdbc.update("INSERT INTO event_teaser_images (id, event_code, s3_key, image_url, display_order,"
                + " presentation_position, created_at) VALUES (gen_random_uuid(), ?, ?, ?, ?, 'AFTER_TOPIC_REVEAL',"
                + " now())", eventCode, "events/" + eventCode + "/teaser-" + order + ".png",
                "https://cdn.batbern.ch/events/" + eventCode + "/teaser-" + order + ".png", order);
    }

    private void registration(Event event, String username, String status, Integer waitlistPosition,
                              boolean programmatic, int daysAgo) {
        Map<String, Object> metadata = new HashMap<>();
        if (programmatic) {
            metadata.put(Registration.AUTO_REGISTERED_FROM_KEY, Registration.TRIGGER_STAKEHOLDER_ENROLLMENT);
        }
        registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-" + username)
                .eventId(event.getId())
                .deregistrationToken(java.util.UUID.randomUUID())
                .attendeeUsername(username)
                .attendeeFirstName(username.substring(0, 1).toUpperCase() + username.substring(1, username.indexOf('.')))
                .attendeeLastName("Capture")
                .attendeeEmail("success+" + username.replace('.', '-') + SIM)
                .status(status)
                .waitlistPosition(waitlistPosition)
                .registrationDate(Instant.now().minus(Duration.ofDays(daysAgo)))
                .metadata(metadata)
                .build());
    }

    private void confirmedUpload(String uploadId, String status) {
        if ("associated".equals(status)) {
            jdbc.update("INSERT INTO event_management_uploads (upload_id, purpose, s3_key, file_url, file_extension,"
                    + " file_size, content_type, status, target_type, target_id) VALUES (?, 'event-theme-image', ?, ?,"
                    + " 'png', 1024, 'image/png', 'associated', 'EVENT', ?)", uploadId,
                    "events/" + UPCOMING + "/theme-" + uploadId + ".png",
                    "https://cdn.batbern.ch/events/" + UPCOMING + "/theme-" + uploadId + ".png", UPCOMING);
            return;
        }
        jdbc.update("INSERT INTO event_management_uploads (upload_id, purpose, s3_key, file_extension,"
                + " file_size, content_type, status, expires_at) VALUES (?, 'event-theme-image', ?, 'png', 1024,"
                + " 'image/png', ?, now() + interval '1 hour')", uploadId,
                "logos/temp/" + uploadId + "/theme-" + uploadId + ".png", status);
    }

    private void cleanUp() {
        clearCaches();
        eventRepository.deleteAll();
        jdbc.update("DELETE FROM topics WHERE topic_code LIKE 'capture-%'");
        jdbc.update("DELETE FROM event_management_uploads WHERE upload_id LIKE 'capture-%'");
    }

    private void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    private static Instant daysFromNow(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(16));
    }

    private static String iso(Instant instant) {
        return instant.toString();
    }

    private String createBody(int number, String themeUploadId) {
        Instant date = daysFromNow(60);
        return """
                {"title":"Capture Created %d","eventNumber":%d,"date":"%s","registrationDeadline":"%s",
                 "venueName":"Kornhausforum","venueAddress":"Kornhausplatz 18, 3011 Bern","venueCapacity":150,
                 "organizerUsername":"olga.organizer","eventType":"EVENING","description":"Created by capture",
                 "registrationCapacity":100%s}"""
                .formatted(number, number, iso(date), iso(date.minus(7, ChronoUnit.DAYS)),
                        themeUploadId == null ? "" : ",\"themeImageUploadId\":\"" + themeUploadId + "\"");
    }

    private String updateBody(Integer capacity, String themeUploadId, Integer eventNumber) {
        Instant date = daysFromNow(31);
        return """
                {"title":"Capture Updated","date":"%s","registrationDeadline":"%s",
                 "venueName":"Kornhausforum","venueAddress":"Kornhausplatz 18, 3011 Bern","venueCapacity":200,
                 "organizerUsername":"olga.organizer","currentAttendeeCount":0,"eventType":"EVENING",
                 "description":"Updated by capture"%s%s%s}"""
                .formatted(iso(date), iso(date.minus(7, ChronoUnit.DAYS)),
                        capacity == null ? "" : ",\"registrationCapacity\":" + capacity,
                        themeUploadId == null ? "" : ",\"themeImageUploadId\":\"" + themeUploadId + "\"",
                        eventNumber == null ? "" : ",\"eventNumber\":" + eventNumber);
    }

    private MockHttpServletRequestBuilder patchJson(String eventCode, String body) {
        return patch("/api/v1/events/" + eventCode).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder listAll() {
        return get("/api/v1/events").param("includeArchived", "true").param("limit", "50");
    }

    private static MockHttpServletRequestBuilder withInclude(MockHttpServletRequestBuilder request, String include) {
        return "none".equals(include) ? request : request.param("include", include);
    }

    private static String slug(String include) {
        return include.equals("topics,venue,sessions,speakers,metrics,registrations") ? "all"
                : include.replace(',', '+');
    }

    // ============================================================================================
    // Capture and compare
    // ============================================================================================

    private void read(String name, MockHttpServletRequestBuilder request) throws Exception {
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        row.set("response", response(request));
        compare(name, row);
    }

    private void write(String name, MockHttpServletRequestBuilder request, String eventCodeAfter) throws Exception {
        sesRecorder.reset();
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        row.set("response", response(request));
        sesRecorder.awaitAsyncSends();
        row.set("eventAfter", response(get("/api/v1/events/" + eventCodeAfter)
                .param("include", "topics,venue,registrations").with(ORGANIZER)));
        ArrayNode registrations = row.putArray("registrations");
        jdbc.queryForList("SELECT r.registration_code, r.status, r.waitlist_position FROM registrations r"
                        + " JOIN events e ON e.id = r.event_id WHERE e.event_code = ? ORDER BY r.registration_code",
                eventCodeAfter).forEach(r -> registrations.add(r.get("registration_code") + " " + r.get("status")
                + " " + r.get("waitlist_position")));
        ArrayNode uploads = row.putArray("uploads");
        jdbc.queryForList("SELECT upload_id, status, target_id FROM event_management_uploads"
                        + " WHERE upload_id LIKE 'capture-%' ORDER BY upload_id")
                .forEach(u -> uploads.add(u.get("upload_id") + " " + u.get("status") + " " + u.get("target_id")));
        List<String> mails = new ArrayList<>();
        sesRecorder.mails().forEach(m -> mails.add(String.join(",", m.to()) + " | " + m.subject()));
        mails.sort(String::compareTo);
        ArrayNode mailNode = row.putArray("mails");
        mails.forEach(mailNode::add);
        compare(name, row);
    }

    private ObjectNode response(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("status", response.getStatus());
        node.put("xCacheStatus", response.getHeader("X-Cache-Status"));
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        if (body.isEmpty()) {
            node.put("body", "");
        } else {
            try {
                JsonNode parsed = json.readTree(body);
                // An error body's details come from an unordered map (key order varies between runs
                // on the same code): sorted. A 2xx body keeps its key order.
                node.set("body", response.getStatus() >= 400 ? sortedKeys(parsed) : parsed);
            } catch (IOException notJson) {
                node.put("body", body);
            }
        }
        return node;
    }

    private void compare(String name, JsonNode row) throws IOException {
        String actual = json.writeValueAsString(normalise(row, new HashMap<>(), new int[4])) + "\n";
        Path golden = GOLDEN.resolve(name + ".json");
        if (!Files.exists(golden)) {
            Files.createDirectories(GOLDEN);
            Files.writeString(golden, actual, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.readString(golden, StandardCharsets.UTF_8).equals(actual)) {
            Files.createDirectories(ACTUAL);
            Files.writeString(ACTUAL.resolve(name + ".json"), actual, StandardCharsets.UTF_8);
            mismatches.add(name);
        }
    }

    private void assertNoMismatch() {
        assertThat(mismatches).as("responses that differ from their golden file (actual output in %s)",
                ACTUAL.toAbsolutePath()).isEmpty();
    }

    private static JsonNode sortedKeys(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            names.forEach(name -> copy.set(name, sortedKeys(node.get(name))));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(element -> copy.add(sortedKeys(element)));
            return copy;
        }
        return node;
    }

    /** Replaces UUIDs, ISO date-times, dates and registration codes, in document order, by stable tokens. */
    private static JsonNode normalise(JsonNode node, Map<String, String> tokens, int[] counters) {
        if (node.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), normalise(field.getValue(), tokens, counters));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(element -> copy.add(normalise(element, tokens, counters)));
            return copy;
        }
        if (node.isTextual()) {
            String text = replace(node.asText(), UUID_PATTERN, "uuid#", 0, tokens, counters);
            text = replace(text, TS_PATTERN, "ts#", 1, tokens, counters);
            text = replace(text, DATE_PATTERN, "d#", 2, tokens, counters);
            text = replace(text, REGISTRATION_CODE_PATTERN, "-reg-#", 3, tokens, counters);
            return TextNode.valueOf(text);
        }
        return node;
    }

    private static String replace(String text, Pattern pattern, String prefix, int counter,
                                  Map<String, String> tokens, int[] counters) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            // UUIDs and registration codes keep their identity (same value, same token). Dates and
            // times are numbered per occurrence: two rows written in the same microsecond share a
            // value only sometimes, which made value-based numbering flaky.
            String token = prefix.startsWith("ts") || prefix.startsWith("d")
                    ? prefix + (++counters[counter])
                    : tokens.computeIfAbsent(prefix + matcher.group(), k -> prefix + (++counters[counter]));
            matcher.appendReplacement(out, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
