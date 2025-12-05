package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.UpdateEventSlotConfigurationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Event Types API (Story 5.1).
 *
 * Tests verify:
 * - GET /api/v1/events/types - List all event types (AC1)
 * - GET /api/v1/events/types/{type} - Get specific event type (AC2)
 * - PUT /api/v1/events/types/{type} - Update event type config (AC3, AC8)
 *
 * TDD RED PHASE: These tests should FAIL until EventTypeController is implemented.
 *
 * Uses PostgreSQL via Testcontainers for production parity (never H2).
 */
@Transactional
class EventTypeControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== AC1 Tests: List all event types ====================

    /**
     * Test 6.1: should_return200WithEventTypes_when_getEventTypesRequested
     * Verifies GET /api/v1/events/types returns all three event types with correct data.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return200WithEventTypes_when_getEventTypesRequested() throws Exception {
        mockMvc.perform(get("/api/v1/events/types")
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
     * Verifies GET /api/v1/events/types/{type} returns correct configuration for FULL_DAY.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return200WithSpecificType_when_getEventTypeByNameRequested() throws Exception {
        mockMvc.perform(get("/api/v1/events/types/{type}", EventType.FULL_DAY)
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
        mockMvc.perform(get("/api/v1/events/types/INVALID_TYPE")
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

        mockMvc.perform(put("/api/v1/events/types/{type}", EventType.FULL_DAY)
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

        mockMvc.perform(put("/api/v1/events/types/{type}", EventType.FULL_DAY)
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

        mockMvc.perform(put("/api/v1/events/types/{type}", EventType.FULL_DAY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
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

        mockMvc.perform(put("/api/v1/events/types/{type}", EventType.FULL_DAY)
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

        mockMvc.perform(put("/api/v1/events/types/{type}", EventType.FULL_DAY)
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
        mockMvc.perform(get("/api/v1/events/types/{type}", EventType.AFTERNOON)
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
        mockMvc.perform(get("/api/v1/events/types/{type}", EventType.EVENING)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EVENING"))
                .andExpect(jsonPath("$.minSlots").value(3))
                .andExpect(jsonPath("$.maxSlots").value(4))
                .andExpect(jsonPath("$.slotDuration").value(45))
                .andExpect(jsonPath("$.theoreticalSlotsAM").value(false))
                .andExpect(jsonPath("$.defaultCapacity").value(200))
                .andExpect(jsonPath("$.typicalStartTime").value("18:00"))
                .andExpect(jsonPath("$.typicalEndTime").value("19:00"));
    }
}
