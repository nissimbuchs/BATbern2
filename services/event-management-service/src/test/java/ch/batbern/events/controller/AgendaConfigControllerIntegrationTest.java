package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.repository.EventAgendaConfigRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.StructuralSessionService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 15.2 — integration tests for {@link AgendaConfigController} + resolver + apéro persistence.
 *
 * <p>Covers AC6 (GET resolved template/override, PUT copy-on-edit upsert, 400/404/403),
 * AC4 (config isolation: editing one event never affects another or the template), and
 * AC5 (apéro persists as an 'aperitif' Session — exercises the V122 CHECK widen + V121 seed).</p>
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class, TestUserApiClientConfig.class})
class AgendaConfigControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID_BODY = """
            {"minSlots":6,"maxSlots":8,"slotDuration":45,"theoreticalSlotsAM":false,
             "breakSlots":2,"lunchSlots":0,"defaultCapacity":200,
             "moderationStartDuration":5,"moderationEndDuration":5,
             "breakDuration":20,"lunchDuration":60,
             "aperitifSlots":1,"aperitifDuration":120,"aperitifPosition":"end",
             "typicalStartTime":"13:00","typicalEndTime":"19:00"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventAgendaConfigRepository agendaConfigRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private StructuralSessionService structuralSessionService;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        agendaConfigRepository.deleteAll();
        eventRepository.deleteAll();
    }

    private Event saveEvent(String eventCode, int eventNumber, EventType type) {
        Event event = Event.builder()
                .eventCode(eventCode)
                .eventNumber(eventNumber)
                .title("Event " + eventCode)
                .date(Instant.parse("2025-06-15T11:00:00Z"))
                .registrationDeadline(Instant.now().minus(1, ChronoUnit.DAYS))
                .venueName("Kultur Casino Bern")
                .venueAddress("Casinoplatz 1, 3011 Bern")
                .venueCapacity(200)
                .eventType(type)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .organizerUsername("test.organizer")
                .build();
        return eventRepository.save(event);
    }

    // MARK: - AC6: GET resolved config

    @Test
    @DisplayName("should_returnTemplateSource_when_noOverrideExists")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_returnTemplateSource_when_noOverrideExists() throws Exception {
        saveEvent("BATbern90", 9090, EventType.AFTERNOON);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/events/BATbern90/agenda-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("TEMPLATE"))
                // afternoon template defaults to apéro ON @ 90 min (V121 seed, AC8)
                .andExpect(jsonPath("$.aperitifSlots").value(1))
                .andExpect(jsonPath("$.aperitifDuration").value(90))
                .andExpect(jsonPath("$.aperitifPosition").value("end"));
    }

    @Test
    @DisplayName("should_return404_when_unknownEvent")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_return404_when_unknownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern-nope/agenda-config"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return403_when_notOrganizer")
    @WithMockUser(username = "attendee.user", roles = {"ATTENDEE"})
    void should_return403_when_notOrganizer() throws Exception {
        saveEvent("BATbern91", 9091, EventType.AFTERNOON);
        mockMvc.perform(get("/api/v1/events/BATbern91/agenda-config"))
                .andExpect(status().isForbidden());
    }

    // MARK: - AC2/AC6: PUT copy-on-edit upsert

    @Test
    @DisplayName("should_createOverrideAndReturnOverrideSource_when_put")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_createOverrideAndReturnOverrideSource_when_put() throws Exception {
        Event event = saveEvent("BATbern92", 9092, EventType.AFTERNOON);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/v1/events/BATbern92/agenda-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("EVENT_OVERRIDE"))
                .andExpect(jsonPath("$.breakSlots").value(2))
                .andExpect(jsonPath("$.aperitifDuration").value(120));

        // Exactly one override row for this event
        assertThat(agendaConfigRepository.findByEventId(event.getId())).isPresent();
        assertThat(agendaConfigRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_updateInPlace_when_putTwice (no duplicate rows)")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_updateInPlace_when_putTwice() throws Exception {
        saveEvent("BATbern93", 9093, EventType.AFTERNOON);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/v1/events/BATbern93/agenda-config")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/events/BATbern93/agenda-config")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());

        assertThat(agendaConfigRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_return400_when_invalidAperitifPosition")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_return400_when_invalidAperitifPosition() throws Exception {
        saveEvent("BATbern94", 9094, EventType.AFTERNOON);
        mockMvc.perform(put("/api/v1/events/BATbern94/agenda-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"end\"", "\"middle\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_malformedTime")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_return400_when_malformedTime() throws Exception {
        saveEvent("BATbern98", 9098, EventType.AFTERNOON);
        mockMvc.perform(put("/api/v1/events/BATbern98/agenda-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"13:00\"", "\"25:99\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_maxSlotsLessThanMinSlots")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_return400_when_maxSlotsLessThanMinSlots() throws Exception {
        saveEvent("BATbern99", 9099, EventType.AFTERNOON);
        // minSlots 6 > maxSlots 4 → cross-field rule must surface as 400, not 500
        mockMvc.perform(put("/api/v1/events/BATbern99/agenda-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"maxSlots\":8", "\"maxSlots\":4")))
                .andExpect(status().isBadRequest());
    }

    // MARK: - AC4: config isolation

    @Test
    @DisplayName("should_notAffectOtherEventOrTemplate_when_oneEventEdited")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_notAffectOtherEventOrTemplate_when_oneEventEdited() throws Exception {
        saveEvent("BATbern95", 9095, EventType.AFTERNOON);
        saveEvent("BATbern96", 9096, EventType.AFTERNOON);
        entityManager.flush();
        entityManager.clear();

        // Edit event A only
        mockMvc.perform(put("/api/v1/events/BATbern95/agenda-config")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // Event A reflects the override
        mockMvc.perform(get("/api/v1/events/BATbern95/agenda-config"))
                .andExpect(jsonPath("$.source").value("EVENT_OVERRIDE"))
                .andExpect(jsonPath("$.aperitifDuration").value(120));

        // Event B still resolves to the unchanged template (apéro 90 min)
        mockMvc.perform(get("/api/v1/events/BATbern96/agenda-config"))
                .andExpect(jsonPath("$.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.aperitifDuration").value(90));
    }

    // MARK: - AC5: apéro persists as a structural session (V122 CHECK + V121 template seed)

    @Test
    @DisplayName("should_persistAperitifSession_when_structuralGeneratedForAfternoonEvent")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_persistAperitifSession_when_structuralGeneratedForAfternoonEvent() {
        Event event = saveEvent("BATbern97", 9097, EventType.AFTERNOON);
        entityManager.flush();
        entityManager.clear();

        // Afternoon template defaults apéro ON → an 'aperitif' Session must persist.
        structuralSessionService.generateStructuralSessions("BATbern97", false);
        entityManager.flush();

        long aperitifCount = sessionRepository.findByEventId(event.getId()).stream()
                .filter(s -> "aperitif".equals(s.getSessionType()))
                .count();
        assertThat(aperitifCount).isEqualTo(1);
    }
}
