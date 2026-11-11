package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.core.dto.generated.UpdateEventSlotConfigurationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Event Types API (Story 5.1), at {@code /event-types} since Story 4.10b (was
 * {@code /events/types}, a literal in the {@code {eventCode}} namespace).
 *
 * Tests verify:
 * - GET /api/v1/event-types - List all event types (AC1), anonymous since 4.10b
 * - GET /api/v1/event-types/{type} - Get specific event type (AC2)
 * - PUT /api/v1/event-types/{type} - Update event type config (AC3, AC8)
 *
 * Uses PostgreSQL via Testcontainers for production parity (never H2). The seeded rows (V10) are
 * snapshotted before and written back after each test, and the cache is cleared, so no class-level
 * {@code @Transactional} (Test fidelity rules).
 */
class EventTypeControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    private List<Map<String, Object>> snapshot;

    @BeforeEach
    void snapshotEventTypes() {
        snapshot = jdbcTemplate.queryForList("SELECT * FROM event_types");
    }

    /** Writes every column of every seeded row back (a PUT commits now) and clears the cache. */
    @AfterEach
    void restoreEventTypes() {
        for (Map<String, Object> row : snapshot) {
            List<String> columns = row.keySet().stream().filter(c -> !c.equals("id")).toList();
            String set = String.join(", ", columns.stream().map(c -> c + " = ?").toList());
            List<Object> values = new ArrayList<>(columns.stream().map(row::get).toList());
            values.add(row.get("id"));
            jdbcTemplate.update("UPDATE event_types SET " + set + " WHERE id = ?", values.toArray());
        }
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    // ==================== AC1 Tests: List all event types ====================

    /**
     * Test 6.1: should_return200WithEventTypes_when_getEventTypesRequested
     * Verifies GET /api/v1/event-types returns all three event types with correct data.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return200WithEventTypes_when_getEventTypesRequested() throws Exception {
        mockMvc.perform(get("/api/v1/event-types")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("FULL_DAY", "AFTERNOON", "EVENING")))
                // Verify FULL_DAY configuration from V10 migration seed data
                .andExpect(jsonPath("$[?(@.type == 'FULL_DAY')].minSlots", contains(6)))
                .andExpect(jsonPath("$[?(@.type == 'FULL_DAY')].maxSlots", contains(8)))
                .andExpect(jsonPath("$[?(@.type == 'FULL_DAY')].slotDuration", contains(45)))
                .andExpect(jsonPath("$[?(@.type == 'FULL_DAY')].defaultCapacity", contains(300)))
                .andExpect(jsonPath("$[?(@.type == 'FULL_DAY')].typicalStartTime", contains("09:00")))
                .andExpect(jsonPath("$[?(@.type == 'FULL_DAY')].typicalEndTime", contains("16:00")));
    }

    // ==================== AC2 Tests: Get specific event type ====================

    /**
     * Test 6.2: should_return200WithSpecificType_when_getEventTypeByNameRequested
     * Verifies GET /api/v1/event-types/{type} returns correct configuration for FULL_DAY.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return200WithSpecificType_when_getEventTypeByNameRequested() throws Exception {
        mockMvc.perform(get("/api/v1/event-types/{type}", EventType.FULL_DAY)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FULL_DAY"))
                .andExpect(jsonPath("$.minSlots").value(6))
                .andExpect(jsonPath("$.maxSlots").value(8))
                .andExpect(jsonPath("$.slotDuration").value(45))
                .andExpect(jsonPath("$.theoreticalSlotsAM").value(true))
                .andExpect(jsonPath("$.breakSlots").value(2))
                .andExpect(jsonPath("$.lunchSlots").value(1))
                .andExpect(jsonPath("$.defaultCapacity").value(300))
                .andExpect(jsonPath("$.typicalStartTime").value("09:00"))
                .andExpect(jsonPath("$.typicalEndTime").value("16:00"));
    }

    /**
     * Test 6.3: should_return404_when_invalidEventTypeRequested
     * Verifies 404 response for invalid event type enum value.
     * Note: Follows existing GlobalExceptionHandler pattern - MethodArgumentTypeMismatchException returns 404.
     * Design rationale: Invalid path parameters represent "resource not found" semantics.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return404_when_invalidEventTypeRequested() throws Exception {
        // Invalid enum value in path parameter returns 404 (existing pattern in codebase)
        mockMvc.perform(get("/api/v1/event-types/INVALID_TYPE")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()); // MethodArgumentTypeMismatchException maps to 404
    }

    // ==================== AC3, AC8 Tests: Update event type (RBAC + Validation) ====================

    /**
     * Test 6.4: should_return403_when_nonOrganizerTriesToUpdateEventType
     * Verifies RBAC: only ORGANIZER role can update event type configurations.
     */
    @Test
    @WithMockUser(username = "jane.smith", roles = {"ATTENDEE"})
    void should_return403_when_nonOrganizerTriesToUpdateEventType() throws Exception {
        UpdateEventSlotConfigurationRequest updateRequest = new UpdateEventSlotConfigurationRequest()
                .minSlots(5)
                .maxSlots(7)
                .slotDuration(45)
                .theoreticalSlotsAM(true)
                .breakSlots(2)
                .lunchSlots(1)
                .defaultCapacity(180)
                .typicalStartTime("09:00")
                .typicalEndTime("17:00");

        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.FULL_DAY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 6.5: should_return200_when_organizerUpdatesEventTypeConfig
     * Verifies ORGANIZER can successfully update event type configuration.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return200_when_organizerUpdatesEventTypeConfig() throws Exception {
        UpdateEventSlotConfigurationRequest updateRequest = new UpdateEventSlotConfigurationRequest()
                .minSlots(5)
                .maxSlots(7)
                .slotDuration(45)
                .theoreticalSlotsAM(true)
                .breakSlots(2)
                .lunchSlots(1)
                .defaultCapacity(180)
                .typicalStartTime("09:00")
                .typicalEndTime("17:00");

        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.FULL_DAY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FULL_DAY"))
                .andExpect(jsonPath("$.minSlots").value(5))
                .andExpect(jsonPath("$.maxSlots").value(7))
                .andExpect(jsonPath("$.defaultCapacity").value(180));
    }

    // ==================== AC8 Tests: Validation rules ====================

    /**
     * Test 8.1: should_return400_when_minSlotsExceedsMaxSlots
     * Verifies validation: minSlots must be <= maxSlots.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return400_when_minSlotsExceedsMaxSlots() throws Exception {
        UpdateEventSlotConfigurationRequest invalidRequest = new UpdateEventSlotConfigurationRequest()
                .minSlots(10)  // Invalid: 10 > 8
                .maxSlots(8)
                .slotDuration(45)
                .theoreticalSlotsAM(true)
                .breakSlots(2)
                .lunchSlots(1)
                .defaultCapacity(200)
                .typicalStartTime("09:00")
                .typicalEndTime("17:00");

        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.FULL_DAY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Regression 2026-10-07: saving a template without a start time wrote NULL, and the evening
     * timetable then started at 09:00. The start time is required.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return400_when_typicalStartTimeMissing() throws Exception {
        UpdateEventSlotConfigurationRequest request = new UpdateEventSlotConfigurationRequest()
                .minSlots(3)
                .maxSlots(4)
                .slotDuration(45)
                .theoreticalSlotsAM(false)
                .breakSlots(1)
                .lunchSlots(0)
                .defaultCapacity(200);

        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.EVENING)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test 8.2: should_return400_when_slotDurationBelowMinimum
     * Verifies validation: slotDuration must be >= 15 minutes.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return400_when_slotDurationBelowMinimum() throws Exception {
        UpdateEventSlotConfigurationRequest invalidRequest = new UpdateEventSlotConfigurationRequest()
                .minSlots(6)
                .maxSlots(8)
                .slotDuration(10)  // Invalid: < 15 minutes
                .theoreticalSlotsAM(true)
                .breakSlots(2)
                .lunchSlots(1)
                .defaultCapacity(200)
                .typicalStartTime("09:00")
                .typicalEndTime("17:00");

        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.FULL_DAY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test 8.3: should_return400_when_defaultCapacityZero
     * Verifies validation: defaultCapacity must be > 0.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return400_when_defaultCapacityZero() throws Exception {
        UpdateEventSlotConfigurationRequest invalidRequest = new UpdateEventSlotConfigurationRequest()
                .minSlots(6)
                .maxSlots(8)
                .slotDuration(45)
                .theoreticalSlotsAM(true)
                .breakSlots(2)
                .lunchSlots(1)
                .defaultCapacity(0)  // Invalid: must be > 0
                .typicalStartTime("09:00")
                .typicalEndTime("17:00");

        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.FULL_DAY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // ==================== Additional coverage tests ====================

    /**
     * Test: Verify AFTERNOON event type configuration from seed data.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnAfternoonConfig_when_afternoonTypeRequested() throws Exception {
        mockMvc.perform(get("/api/v1/event-types/{type}", EventType.AFTERNOON)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("AFTERNOON"))
                .andExpect(jsonPath("$.minSlots").value(6))
                .andExpect(jsonPath("$.maxSlots").value(8))
                .andExpect(jsonPath("$.slotDuration").value(45))
                .andExpect(jsonPath("$.theoreticalSlotsAM").value(false))
                .andExpect(jsonPath("$.defaultCapacity").value(200))
                .andExpect(jsonPath("$.typicalStartTime").value("13:00"))
                .andExpect(jsonPath("$.typicalEndTime").value("19:00"));
    }

    /**
     * Test: Verify EVENING event type configuration from seed data.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnEveningConfig_when_eveningTypeRequested() throws Exception {
        mockMvc.perform(get("/api/v1/event-types/{type}", EventType.EVENING)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EVENING"))
                .andExpect(jsonPath("$.minSlots").value(3))
                .andExpect(jsonPath("$.maxSlots").value(4))
                .andExpect(jsonPath("$.slotDuration").value(45))
                .andExpect(jsonPath("$.theoreticalSlotsAM").value(false))
                .andExpect(jsonPath("$.defaultCapacity").value(200))
                .andExpect(jsonPath("$.typicalStartTime").value("16:00")) // Updated to match seed data
                .andExpect(jsonPath("$.typicalEndTime").value("19:00"));
    }

    // ==================== Story 4.10b: /event-types ====================

    @Test
    void should_listEventTypes_when_anonymous() throws Exception {
        // The format definitions are public (homepage, registration); no data about people.
        mockMvc.perform(get("/api/v1/event-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void should_return401_when_anonymousReadsOrUpdatesOneEventType() throws Exception {
        mockMvc.perform(get("/api/v1/event-types/{type}", EventType.EVENING))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.EVENING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "hongan.nguyen", roles = {"SPEAKER"})
    void should_return403AndKeepConfig_when_speakerUpdatesEventType() throws Exception {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT default_capacity FROM event_types WHERE type = 'evening'", Integer.class);

        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.EVENING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateEventSlotConfigurationRequest()
                                .minSlots(3).maxSlots(4).slotDuration(45).theoreticalSlotsAM(false)
                                .breakSlots(1).lunchSlots(0).defaultCapacity(999)
                                .typicalStartTime("16:00").typicalEndTime("19:00"))))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT default_capacity FROM event_types WHERE type = 'evening'", Integer.class))
                .isEqualTo(before);
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_persistUpdate_when_organizerUpdatesAndReadsAgain() throws Exception {
        // Cache rule: read, mutate, read again.
        mockMvc.perform(get("/api/v1/event-types/{type}", EventType.FULL_DAY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/event-types/{type}", EventType.FULL_DAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateEventSlotConfigurationRequest()
                                .minSlots(5).maxSlots(7).slotDuration(45).theoreticalSlotsAM(true)
                                .breakSlots(2).lunchSlots(1).defaultCapacity(181)
                                .typicalStartTime("09:00").typicalEndTime("17:00"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/event-types/{type}", EventType.FULL_DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCapacity").value(181));
        mockMvc.perform(get("/api/v1/event-types"))
                .andExpect(jsonPath("$[?(@.type == 'FULL_DAY')].defaultCapacity", contains(181)));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_notServeEventTypes_when_oldPathIsCalled() throws Exception {
        // Story 4.10b, no alias: "types" is now read as an event code (GET /events/{eventCode}) and
        // fails its pattern (400); PUT /events/types/{type} has no handler (404).
        mockMvc.perform(get("/api/v1/events/types"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].type").doesNotExist());
        mockMvc.perform(put("/api/v1/events/types/{type}", EventType.FULL_DAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
