package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventPhoto;
import ch.batbern.events.repository.EventPhotoRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for EventPhotoController: list and delete on real data (Story 10.21). The upload
 * is {@link EventPhotoUploadIntegrationTest} (Story 17.11b); the homepage sample is
 * {@link RecentEventPhotosIntegrationTest}.
 *
 * <p>Data is committed and removed in {@code @AfterEach} (photos cascade from the event).
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class EventPhotoControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern9942";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    EventPhotoRepository photoRepository;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        removeEvent();
        Instant date = Instant.now().minus(3, ChronoUnit.DAYS);
        eventRepository.saveAndFlush(Event.builder().eventCode(EVENT_CODE).title("Photo Gallery Event")
                .eventNumber(9942).date(date).registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum").venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(100)
                .organizerUsername("orga.nizer").currentAttendeeCount(0).eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED).build());
    }

    @AfterEach
    void removeEvent() {
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
    }

    private UUID savePhoto() {
        UUID id = UUID.randomUUID();
        String key = "events/" + EVENT_CODE + "/photos/" + id + ".jpg";
        photoRepository.saveAndFlush(EventPhoto.builder().id(id).newEntity(true).eventCode(EVENT_CODE).s3Key(key)
                .displayUrl("https://cdn.batbern.ch/" + key).filename("photo.jpg").uploadedAt(Instant.now())
                .uploadedBy("orga.nizer").sortOrder(0).build());
        return id;
    }

    // ── Public endpoints ─────────────────────────────────────────────────────────

    @Test
    void listPhotos_asAnonymous_returns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/photos", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listPhotos_asAnonymous_returnsTheEventsPhotos() throws Exception {
        UUID id = savePhoto();

        mockMvc.perform(get("/api/v1/events/{eventCode}/photos", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].eventCode").value(EVENT_CODE))
                .andExpect(jsonPath("$[0].uploadedBy").value("orga.nizer"));
    }

    // ── ORGANIZER-only delete ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deletePhoto_forNonexistentPhoto_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/events/{eventCode}/photos/{photoId}", EVENT_CODE, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deletePhoto_asOrganizer_returns204() throws Exception {
        UUID id = savePhoto();

        mockMvc.perform(delete("/api/v1/events/{eventCode}/photos/{photoId}", EVENT_CODE, id))
                .andExpect(status().isNoContent());

        assertThat(photoRepository.findById(id)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "PARTNER")
    void deletePhoto_asPartner_returns403() throws Exception {
        UUID id = savePhoto();

        mockMvc.perform(delete("/api/v1/events/{eventCode}/photos/{photoId}", EVENT_CODE, id))
                .andExpect(status().isForbidden());

        assertThat(photoRepository.findById(id)).isPresent();
    }

    @Test
    void deletePhoto_asAnonymous_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/events/{eventCode}/photos/{photoId}", EVENT_CODE, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
