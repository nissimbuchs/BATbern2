package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1260 AC4: no response to a caller who is neither an organizer nor a speaker of the session names a
 * non-public material, and the public read model never does. Every read that carries materials or
 * their counts: the session (A), the public event detail, current and list (B), {@code /events/current}
 * (C), the materials list (H) and the sessions list. Absence is checked by a string search of the raw
 * body, with the public PDF of the same session as positive control. Real controllers, services and
 * security chain on PostgreSQL.
 *
 * <p>Not {@code @Transactional}: every request commits; {@code @AfterEach} removes the rows.
 */
@Import(TestAwsConfig.class)
@DisplayName("Session materials: only public ones for everyone else (#1260)")
class SessionMaterialAudienceIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern9955";
    private static final String ARCHIVED = "BATbern9956";
    private static final String OTHER = "BATbern9957";
    private static final String TALK = "it-1261-talk";
    private static final String INTERNAL_ONLY = "it-1261-internal-only";
    private static final String ARCHIVED_TALK = "it-1261-archived-talk";
    private static final String ARCHIVED_INTERNAL = "it-1261-archived-internal";
    private static final String ORGANIZER = "it.orga.1261";
    private static final String SPEAKER = "it.speaker.1261";
    private static final String OTHER_SPEAKER = "other.speaker.1261";
    private static final String ARCHIVE_TITLE = "Audience1261 Archive";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private SessionMaterialsRepository sessionMaterialsRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CacheManager cacheManager;

    /** The talk's public PDF and non-public PPTX, and the PPTX of the session that has no public file. */
    private SessionMaterial pdf;
    private SessionMaterial pptx;
    private SessionMaterial internalOnly;
    private SessionMaterial archivedPptx;

    @BeforeEach
    void setUp() {
        removeRows();
        // Completed late yesterday: the afterglow event of /events/current and /public/events/current.
        Event event = saveEvent(EVENT, 9955, "Audience1261 Event", EventWorkflowState.EVENT_COMPLETED,
                lateYesterdayInBern());
        Session talk = saveSession(event, TALK);
        Session internal = saveSession(event, INTERNAL_ONLY);
        speaker(talk, SPEAKER);
        speaker(internal, OTHER_SPEAKER);
        pdf = material(talk, "it-1261-pdf", "folien.pdf", "application/pdf", true, 2);
        pptx = material(talk, "it-1261-pptx", "folien.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", false, 1);
        internalOnly = material(internal, "it-1261-internal", "intern.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", false, 1);

        Event archived = saveEvent(ARCHIVED, 9956, ARCHIVE_TITLE, EventWorkflowState.ARCHIVED, daysAgo(40));
        Session archivedTalk = saveSession(archived, ARCHIVED_TALK);
        Session archivedInternal = saveSession(archived, ARCHIVED_INTERNAL);
        material(archivedTalk, "it-1261-archived-pdf", "archiv.pdf", "application/pdf", true, 2);
        archivedPptx = material(archivedInternal, "it-1261-archived-pptx", "archiv.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", false, 1);

        Event other = saveEvent(OTHER, 9957, "Audience1261 Other", EventWorkflowState.ARCHIVED, daysAgo(80));
        saveSession(other, "it-1261-other-session");
        clearCaches();
    }

    @AfterEach
    void tearDown() {
        removeRows();
        clearCaches();
    }

    private void removeRows() {
        // Sessions, their speakers and materials cascade from the event (V2, V7, V41).
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?, ?)", EVENT, ARCHIVED, OTHER);
    }

    private void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private Event saveEvent(String code, int number, String title, EventWorkflowState state, Instant date) {
        return eventRepository.saveAndFlush(Event.builder().eventCode(code).title(title)
                .eventNumber(number).date(date).registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum").venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(100)
                .organizerUsername(ORGANIZER).currentAttendeeCount(0).eventType(EventType.EVENING)
                .workflowState(state).currentPublishedPhase("agenda").build());
    }

    /**
     * One minute before today's midnight in Bern: the latest date {@code findCurrentEvent} accepts for a
     * completed event, so an event another test left behind does not displace this one.
     */
    private static Instant lateYesterdayInBern() {
        ZoneId bern = ZoneId.of("Europe/Zurich");
        return LocalDate.now(bern).atStartOfDay(bern).toInstant().minus(1, ChronoUnit.MINUTES);
    }

    private Session saveSession(Event event, String slug) {
        Instant start = event.getDate().plus(1, ChronoUnit.HOURS);
        return sessionRepository.saveAndFlush(Session.builder().sessionSlug(slug).eventId(event.getId())
                .eventCode(event.getEventCode()).title("Session " + slug).sessionType("presentation")
                .startTime(start).endTime(start.plus(45, ChronoUnit.MINUTES))
                .materialsCount(0).hasPresentation(false).materialsStatus("NONE").build());
    }

    private void speaker(Session session, String username) {
        sessionUserRepository.saveAndFlush(SessionUser.builder().session(session).username(username)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER).build());
    }

    private SessionMaterial material(Session session, String uploadId, String fileName, String mime,
            boolean publiclyVisible, int daysAgo) {
        Instant created = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        String key = "materials/events/" + session.getEventCode() + "/sessions/" + session.getSessionSlug()
                + "/file-" + uploadId + "." + fileName.substring(fileName.lastIndexOf('.') + 1);
        return sessionMaterialsRepository.saveAndFlush(SessionMaterial.builder()
                .session(session)
                .uploadId(uploadId)
                .s3Key(key)
                .cloudFrontUrl("https://cdn.batbern.ch/" + key)
                .fileName(fileName)
                .fileExtension(fileName.substring(fileName.lastIndexOf('.') + 1))
                .fileSize(4096L)
                .mimeType(mime)
                .materialType("PRESENTATION")
                .uploadedBy(SPEAKER)
                .publiclyVisible(publiclyVisible)
                .contentExtracted(false)
                .extractionStatus("PENDING")
                .createdAt(created)
                .updatedAt(created)
                .build());
    }

    private static RequestPostProcessor organizer() {
        return user(ORGANIZER).roles("ORGANIZER");
    }

    private static RequestPostProcessor sessionSpeaker() {
        return user(SPEAKER).roles("SPEAKER");
    }

    /** Callers who are neither organizer nor a speaker of the talk; anonymous only where the route allows it. */
    private static List<RequestPostProcessor> outsiders(boolean withAnonymous) {
        List<RequestPostProcessor> who = new ArrayList<>(List.of(
                user("it.attendee.1261").roles("ATTENDEE"),
                user(OTHER_SPEAKER).roles("SPEAKER")));
        if (withAnonymous) {
            who.add(0, anonymous());
        }
        return who;
    }

    private String body(MockHttpServletRequestBuilder request, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(request.with(who)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The public PDF is there, the non-public files are nowhere, by id or by URL. */
    private static void assertPublicOnly(String body, SessionMaterial shown, SessionMaterial... hidden) {
        assertThat(body).contains(shown.getId().toString()).contains(shown.getCloudFrontUrl());
        for (SessionMaterial h : hidden) {
            assertThat(body).doesNotContain(h.getId().toString()).doesNotContain(h.getCloudFrontUrl())
                    .doesNotContain(h.getFileName());
        }
    }

    private JsonNode sessionIn(String body, String slug) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode sessions = root.has("sessions") ? root.path("sessions") : root.path("data");
        for (JsonNode s : sessions) {
            if (slug.equals(s.path("sessionSlug").asText())) {
                return s;
            }
        }
        throw new AssertionError("session " + slug + " not in " + body);
    }

    private static void assertBlanked(JsonNode material) {
        assertThat(material.path("uploadedBy").asText()).isEmpty();
        assertThat(material.path("s3Key").asText()).isEmpty();
        assertThat(material.path("uploadId").asText()).isEmpty();
        assertThat(material.path("cloudFrontUrl").asText()).isNotEmpty();
    }

    // ---- A: GET /events/{eventCode}/sessions/{sessionSlug} -------------------------------------

    @Test
    @DisplayName("A: anyone else gets the public PDF only, blanked, counted once")
    void should_showPublicOnly_when_outsiderReadsTheSession() throws Exception {
        for (RequestPostProcessor who : outsiders(true)) {
            String body = body(get("/api/v1/events/{ec}/sessions/{slug}", EVENT, TALK), who);
            assertPublicOnly(body, pdf, pptx);
            JsonNode session = objectMapper.readTree(body);
            assertThat(session.path("materialsCount").asInt()).isEqualTo(1);
            assertThat(session.path("materialsStatus").asText()).isEqualTo("COMPLETE");
            assertBlanked(session.path("materials").get(0));
        }
        for (RequestPostProcessor who : List.of(anonymous(), user("it.attendee.1261").roles("ATTENDEE"))) {
            JsonNode internal = objectMapper.readTree(
                    body(get("/api/v1/events/{ec}/sessions/{slug}", EVENT, INTERNAL_ONLY), who));
            assertThat(internal.path("materials")).isEmpty();
            assertThat(internal.path("materialsCount").asInt()).isZero();
            assertThat(internal.path("materialsStatus").asText()).isEqualTo("NONE");
        }
        // Positive control, decided per session: the other speaker is on the internal-only session.
        String own = body(get("/api/v1/events/{ec}/sessions/{slug}", EVENT, INTERNAL_ONLY),
                user(OTHER_SPEAKER).roles("SPEAKER"));
        assertThat(own).contains(internalOnly.getId().toString());
    }

    @Test
    @DisplayName("A: organizer and the session's speaker get both files with every field")
    void should_showAll_when_organizerOrSessionSpeakerReadsTheSession() throws Exception {
        for (RequestPostProcessor who : List.of(organizer(), sessionSpeaker())) {
            String body = body(get("/api/v1/events/{ec}/sessions/{slug}", EVENT, TALK), who);
            assertThat(body).contains(pdf.getId().toString()).contains(pptx.getId().toString());
            JsonNode session = objectMapper.readTree(body);
            assertThat(session.path("materialsCount").asInt()).isEqualTo(2);
            assertThat(session.path("materials").get(0).path("uploadedBy").asText()).isEqualTo(SPEAKER);
            assertThat(session.path("materials").get(0).path("s3Key").asText()).isNotEmpty();
        }
    }

    // ---- B: /public/events -------------------------------------------------------------------

    @Test
    @DisplayName("B: the public detail and current event carry the public PDF only, for every caller")
    void should_showPublicOnly_when_publicReadModelIsRead() throws Exception {
        List<RequestPostProcessor> everyone = new ArrayList<>(outsiders(true));
        everyone.add(organizer());
        everyone.add(sessionSpeaker());
        for (RequestPostProcessor who : everyone) {
            for (MockHttpServletRequestBuilder request : List.of(get("/api/v1/public/events/{ec}", EVENT),
                    get("/api/v1/public/events/current"))) {
                String body = body(request, who);
                assertThat(objectMapper.readTree(body).path("eventCode").asText()).isEqualTo(EVENT);
                assertPublicOnly(body, pdf, pptx, internalOnly);
                assertThat(sessionIn(body, TALK).path("materialsCount").asInt()).isEqualTo(1);
                assertBlanked(sessionIn(body, TALK).path("materials").get(0));
                assertThat(sessionIn(body, INTERNAL_ONLY).path("materialsCount").asInt()).isZero();
                assertThat(sessionIn(body, INTERNAL_ONLY).path("materialsStatus").asText()).isEqualTo("NONE");
            }
        }
    }

    @Test
    @DisplayName("B: the public archive list counts the public files only")
    void should_countPublicOnly_when_publicArchiveListIsRead() throws Exception {
        for (RequestPostProcessor who : List.of(anonymous(), organizer())) {
            String body = body(get("/api/v1/public/events").param("scope", "archive")
                    .param("search", "Audience1261"), who);
            JsonNode archived = null;
            for (JsonNode e : objectMapper.readTree(body).path("data")) {
                if (ARCHIVED.equals(e.path("eventCode").asText())) {
                    archived = e;
                }
            }
            assertThat(archived).as("archived event in %s", body).isNotNull();
            assertThat(sessionIn(archived.toString(), ARCHIVED_TALK).path("materialsStatus").asText())
                    .isEqualTo("COMPLETE");
            assertThat(sessionIn(archived.toString(), ARCHIVED_INTERNAL).path("materialsStatus").asText())
                    .isEqualTo("NONE");
            assertThat(body).doesNotContain(archivedPptx.getId().toString())
                    .doesNotContain(archivedPptx.getCloudFrontUrl());
        }
    }

    // ---- C: GET /events/current?include=sessions ---------------------------------------------

    @Test
    @DisplayName("C: anyone but an organizer gets the public view; the organizer every file")
    void should_showPublicOnly_when_currentEventIsReadByNonOrganizer() throws Exception {
        List<RequestPostProcessor> nonOrganizers = new ArrayList<>(outsiders(true));
        nonOrganizers.add(sessionSpeaker());
        for (RequestPostProcessor who : nonOrganizers) {
            String body = body(get("/api/v1/events/current").param("include", "sessions"), who);
            assertThat(objectMapper.readTree(body).path("eventCode").asText()).isEqualTo(EVENT);
            assertPublicOnly(body, pdf, pptx, internalOnly);
            assertThat(sessionIn(body, TALK).path("materialsCount").asInt()).isEqualTo(1);
        }

        String body = body(get("/api/v1/events/current").param("include", "sessions"), organizer());
        assertThat(body).contains(pptx.getId().toString()).contains(internalOnly.getId().toString());
        assertThat(sessionIn(body, TALK).path("materialsCount").asInt()).isEqualTo(2);
    }

    // ---- Metrics: include=metrics counts sessions with materials -------------------------------

    @Test
    @DisplayName("Metrics: only an organizer gets them, and the organizer counts every file")
    void should_countAllSessions_when_organizerReadsMetrics_andOmitThemForEveryoneElse() throws Exception {
        // The session's speaker reads first: no non-organizer response carries a count at all.
        for (RequestPostProcessor who : List.of(user(OTHER_SPEAKER).roles("SPEAKER"), anonymous(),
                user("it.attendee.1261").roles("ATTENDEE"))) {
            for (MockHttpServletRequestBuilder request : List.of(
                    get("/api/v1/events/{ec}", EVENT).param("include", "metrics"),
                    get("/api/v1/events/current").param("include", "metrics"))) {
                JsonNode event = objectMapper.readTree(body(request, who));
                assertThat(event.path("eventCode").asText()).isEqualTo(EVENT);
                assertThat(event.has("sessionsWithMaterialsCount")).isFalse();
            }
        }
        JsonNode byOrganizer = objectMapper.readTree(
                body(get("/api/v1/events/{ec}", EVENT).param("include", "metrics"), organizer()));
        assertThat(byOrganizer.path("sessionsWithMaterialsCount").asInt()).isEqualTo(2);
    }

    // ---- H: GET /events/{eventCode}/sessions/{sessionSlug}/materials ---------------------------

    @Test
    @DisplayName("H: an attendee or another session's speaker gets the public PDF only, blanked")
    void should_listPublicOnly_when_outsiderListsMaterials() throws Exception {
        for (RequestPostProcessor who : outsiders(false)) {
            String body = body(get("/api/v1/events/{ec}/sessions/{slug}/materials", EVENT, TALK), who);
            assertPublicOnly(body, pdf, pptx);
            JsonNode materials = objectMapper.readTree(body).path("materials");
            assertThat(materials).hasSize(1);
            assertBlanked(materials.get(0));
        }
        mockMvc.perform(get("/api/v1/events/{ec}/sessions/{slug}/materials", EVENT, TALK).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("H: organizer and the session's speaker list both files")
    void should_listAll_when_organizerOrSessionSpeakerListsMaterials() throws Exception {
        for (RequestPostProcessor who : List.of(organizer(), sessionSpeaker())) {
            JsonNode materials = objectMapper.readTree(
                    body(get("/api/v1/events/{ec}/sessions/{slug}/materials", EVENT, TALK), who)).path("materials");
            assertThat(materials.findValuesAsText("id"))
                    .containsExactly(pdf.getId().toString(), pptx.getId().toString());
            assertThat(materials.get(1).path("uploadedBy").asText()).isEqualTo(SPEAKER);
        }
    }

    @Test
    @DisplayName("H: a session of another event is 404")
    void should_return404_when_sessionIsNotInTheEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/{ec}/sessions/{slug}/materials", OTHER, TALK).with(organizer()))
                .andExpect(status().isNotFound());
    }

    // ---- Sessions list: GET /events/{eventCode}/sessions --------------------------------------

    @Test
    @DisplayName("Sessions list: counts what the caller may see")
    void should_countVisibleMaterials_when_sessionsAreListed() throws Exception {
        for (RequestPostProcessor who : List.of(anonymous(), user("it.attendee.1261").roles("ATTENDEE"))) {
            String body = body(get("/api/v1/events/{ec}/sessions", EVENT), who);
            assertThat(sessionIn(body, TALK).path("materialsCount").asInt()).isEqualTo(1);
            assertThat(sessionIn(body, TALK).path("materialsStatus").asText()).isEqualTo("COMPLETE");
            assertThat(sessionIn(body, INTERNAL_ONLY).path("materialsCount").asInt()).isZero();
            assertThat(sessionIn(body, INTERNAL_ONLY).path("materialsStatus").asText()).isEqualTo("NONE");
        }

        // The other speaker: public count on the talk, every file of the session they are on
        String byOther = body(get("/api/v1/events/{ec}/sessions", EVENT), user(OTHER_SPEAKER).roles("SPEAKER"));
        assertThat(sessionIn(byOther, TALK).path("materialsCount").asInt()).isEqualTo(1);
        assertThat(sessionIn(byOther, INTERNAL_ONLY).path("materialsCount").asInt()).isEqualTo(1);
        assertThat(sessionIn(byOther, INTERNAL_ONLY).path("materialsStatus").asText()).isEqualTo("COMPLETE");

        String byOrganizer = body(get("/api/v1/events/{ec}/sessions", EVENT), organizer());
        assertThat(sessionIn(byOrganizer, TALK).path("materialsCount").asInt()).isEqualTo(2);
        assertThat(sessionIn(byOrganizer, INTERNAL_ONLY).path("materialsCount").asInt()).isEqualTo(1);

        String bySpeaker = body(get("/api/v1/events/{ec}/sessions", EVENT), sessionSpeaker());
        assertThat(sessionIn(bySpeaker, TALK).path("materialsCount").asInt()).isEqualTo(2);
        assertThat(sessionIn(bySpeaker, INTERNAL_ONLY).path("materialsCount").asInt()).isZero();
    }
}
