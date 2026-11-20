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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1260 AC2: a material downloads only through its own event and session, and a non-public one only
 * for organizers and the speakers of its session; everyone else gets the 404 of an unknown id. Real
 * controller, service and security chain on PostgreSQL; the presigner is {@link TestAwsConfig}'s.
 *
 * <p>Not {@code @Transactional}: every request commits; {@code @AfterEach} removes the rows.
 */
@Import(TestAwsConfig.class)
@DisplayName("Session materials: download by event, session and visibility (#1260)")
class SessionMaterialDownloadIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern9958";
    private static final String OTHER_EVENT = "BATbern9959";
    private static final String TALK = "it-1262-talk";
    private static final String OTHER_TALK = "it-1262-other-talk";
    private static final String OTHER_EVENT_TALK = "it-1262-other-event-talk";
    private static final String ORGANIZER = "it.orga.1262";
    private static final String SPEAKER = "it.speaker.1262";
    private static final String OTHER_SPEAKER = "other.speaker.1262";
    private static final String DOWNLOAD = "/api/v1/events/{ec}/sessions/{slug}/materials/{id}/download";

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

    private SessionMaterial pdf;
    private SessionMaterial pptx;

    @BeforeEach
    void setUp() {
        removeRows();
        Event event = saveEvent(EVENT, 9958);
        Session talk = saveSession(event, TALK);
        Session otherTalk = saveSession(event, OTHER_TALK);
        saveSession(saveEvent(OTHER_EVENT, 9959), OTHER_EVENT_TALK);
        speaker(talk, SPEAKER);
        speaker(otherTalk, OTHER_SPEAKER);
        pdf = material(talk, "it-1262-pdf", "folien.pdf", "application/pdf", true);
        pptx = material(talk, "it-1262-pptx", "folien.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", false);
    }

    @AfterEach
    void tearDown() {
        removeRows();
    }

    private void removeRows() {
        // Sessions, their speakers and materials cascade from the event (V2, V7, V41).
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT, OTHER_EVENT);
    }

    private Event saveEvent(String code, int number) {
        Instant date = Instant.now().minus(30, ChronoUnit.DAYS);
        return eventRepository.saveAndFlush(Event.builder().eventCode(code).title("Download Event " + number)
                .eventNumber(number).date(date).registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum").venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(100)
                .organizerUsername(ORGANIZER).currentAttendeeCount(0).eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED).currentPublishedPhase("agenda").build());
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
            boolean publiclyVisible) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
        String key = "materials/events/" + session.getEventCode() + "/sessions/" + session.getSessionSlug()
                + "/file-" + uploadId + "." + ext;
        return sessionMaterialsRepository.saveAndFlush(SessionMaterial.builder()
                .session(session).uploadId(uploadId).s3Key(key).cloudFrontUrl("https://cdn.batbern.ch/" + key)
                .fileName(fileName).fileExtension(ext).fileSize(4096L).mimeType(mime)
                .materialType("PRESENTATION").uploadedBy(SPEAKER).publiclyVisible(publiclyVisible)
                .contentExtracted(false).extractionStatus("PENDING").build());
    }

    private static RequestPostProcessor organizer() {
        return user(ORGANIZER).roles("ORGANIZER");
    }

    private static RequestPostProcessor sessionSpeaker() {
        return user(SPEAKER).roles("SPEAKER");
    }

    private String download(String eventCode, String slug, Object id, RequestPostProcessor who, int expected)
            throws Exception {
        return mockMvc.perform(get(DOWNLOAD, eventCode, slug, id).with(who))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** An error body with the material id replaced and the per-request fields (time, path, ids) removed. */
    private JsonNode comparable(String body, UUID materialId) throws Exception {
        JsonNode tree = objectMapper.readTree(body.replace(materialId.toString(), "<id>"));
        if (tree instanceof com.fasterxml.jackson.databind.node.ObjectNode node) {
            node.remove(List.of("timestamp", "path", "correlationId", "traceId", "requestId"));
        }
        return tree;
    }

    private String urlOf(String body) throws Exception {
        return objectMapper.readTree(body).path("downloadUrl").asText();
    }

    @Test
    @DisplayName("public PDF: anonymous gets the presigned URL")
    void should_returnUrl_when_anonymousDownloadsPublicMaterial() throws Exception {
        assertThat(urlOf(download(EVENT, TALK, pdf.getId(), anonymous(), 200))).contains(pdf.getS3Key());
    }

    @Test
    @DisplayName("non-public PPTX: organizer and the session's speaker get the presigned URL")
    void should_returnUrl_when_organizerOrSessionSpeakerDownloadsNonPublic() throws Exception {
        for (RequestPostProcessor who : List.of(organizer(), sessionSpeaker())) {
            assertThat(urlOf(download(EVENT, TALK, pptx.getId(), who, 200))).contains(pptx.getS3Key());
        }
    }

    @Test
    @DisplayName("non-public PPTX: anonymous, attendee and another session's speaker get the 404 of an unknown id")
    void should_return404_when_outsiderDownloadsNonPublic() throws Exception {
        UUID unknownId = UUID.randomUUID();
        JsonNode unknown = comparable(download(EVENT, TALK, unknownId, anonymous(), 404), unknownId);
        for (RequestPostProcessor who : List.of(anonymous(), user("it.attendee.1262").roles("ATTENDEE"),
                user(OTHER_SPEAKER).roles("SPEAKER"))) {
            String body = download(EVENT, TALK, pptx.getId(), who, 404);
            assertThat(body).doesNotContain(pptx.getS3Key()).doesNotContain("downloadUrl");
            // The whole answer of an id that does not exist, apart from the id and the volatile fields
            assertThat(comparable(body, pptx.getId())).isEqualTo(unknown);
        }
    }

    @Test
    @DisplayName("a material asked for under another session or another event is 404, even for the organizer")
    void should_return404_when_materialIsNotInThatEventAndSession() throws Exception {
        download(EVENT, OTHER_TALK, pdf.getId(), anonymous(), 404);
        download(EVENT, OTHER_TALK, pptx.getId(), organizer(), 404);
        download(OTHER_EVENT, TALK, pdf.getId(), anonymous(), 404);
        download(OTHER_EVENT, OTHER_EVENT_TALK, pdf.getId(), organizer(), 404);
        download(EVENT, TALK, UUID.randomUUID(), organizer(), 404);
    }
}
