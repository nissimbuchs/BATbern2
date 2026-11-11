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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Homepage photo row, {@code GET /public/event-photos} (Story 4.10b; was {@code
 * /events/recent-photos}), anonymous, on the production chain. Recent photos come from events that
 * have already taken place. Bug 2026-10-24: BATbern61–66 were created ahead of time; as the five
 * events with the latest date they filled the "last N events" window, none had photos, and the
 * homepage row vanished.
 *
 * <p>Data is committed and removed in {@code @AfterEach} (photos cascade from the event), no
 * class-level {@code @Transactional} (Test fidelity rules).
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Recent event photos (GET /public/event-photos)")
class RecentEventPhotosIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> PAST = List.of("BATbern99901", "BATbern99902");
    private static final List<String> FUTURE = List.of("BATbern99910", "BATbern99911", "BATbern99912",
            "BATbern99913", "BATbern99914", "BATbern99915");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPhotoRepository photoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void deleteTestEvents() {
        List<String> codes = new ArrayList<>(PAST);
        codes.addAll(FUTURE);
        codes.forEach(code -> jdbcTemplate.update("DELETE FROM events WHERE event_code = ?", code));
    }

    @Test
    @DisplayName("should_returnPhotosOfPastEvents_when_moreFutureEventsThanWindowExist")
    void should_returnPhotosOfPastEvents_when_moreFutureEventsThanWindowExist() throws Exception {
        // The two most recent past events of the database (minutes ago), so the window holds them.
        Instant now = Instant.now();
        saveEvent(PAST.get(0), 99901, now.minus(1, ChronoUnit.MINUTES));
        savePhoto(PAST.get(0));
        savePhoto(PAST.get(0));
        saveEvent(PAST.get(1), 99902, now.minus(2, ChronoUnit.MINUTES));
        savePhoto(PAST.get(1));
        for (int i = 0; i < FUTURE.size(); i++) {
            saveEvent(FUTURE.get(i), 99910 + i, now.plus(100 + i * 100L, ChronoUnit.DAYS));
            savePhoto(FUTURE.get(i));
        }

        mockMvc.perform(get("/api/v1/public/event-photos").param("limit", "20").param("lastNEvents", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventCode").value(hasItems(PAST.get(0), PAST.get(1))))
                .andExpect(jsonPath("$[*].eventCode").value(everyItem(not(in(FUTURE)))));
    }

    @Test
    @DisplayName("should_answerAnonymousWithoutLogin_when_noPhotoExists")
    void should_answerAnonymousWithoutLogin_when_noPhotoExists() throws Exception {
        mockMvc.perform(get("/api/v1/public/event-photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("should_notServeHomepagePhotos_when_oldPathIsCalled")
    void should_notServeHomepagePhotos_when_oldPathIsCalled() throws Exception {
        // Story 4.10b, no alias: "recent-photos" is now read as an event code and fails the
        // event-code pattern of GET /events/{eventCode}: 400, no photo list.
        mockMvc.perform(get("/api/v1/events/recent-photos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    private void saveEvent(String eventCode, int eventNumber, Instant date) {
        eventRepository.save(Event.builder()
                .eventCode(eventCode)
                .eventNumber(eventNumber)
                .title("Photo window test " + eventCode)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum Bern")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(EventType.EVENING)
                .workflowState(date.isBefore(Instant.now())
                        ? EventWorkflowState.ARCHIVED
                        : EventWorkflowState.CREATED)
                .organizerUsername("test.organizer")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private void savePhoto(String eventCode) {
        UUID id = UUID.randomUUID();
        photoRepository.save(EventPhoto.builder()
                .id(id)
                .newEntity(true)
                .eventCode(eventCode)
                .s3Key("events/" + eventCode + "/photos/" + id + ".jpg")
                .displayUrl("https://cdn.batbern.ch/events/" + eventCode + "/photos/" + id + ".jpg")
                .filename(id + ".jpg")
                .uploadedAt(Instant.now())
                .uploadedBy("test.organizer")
                .build());
    }
}
