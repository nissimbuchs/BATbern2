package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AdminExportImportController.
 * Story 10.20: AC1–AC4, AC7 — verifies export/import endpoints end-to-end with PostgreSQL.
 *
 * Uses Testcontainers PostgreSQL (via AbstractIntegrationTest) — never H2.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class, TestUserApiClientConfig.class})
@DisplayName("AdminExportImportController Integration Tests")
class AdminExportImportControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    // ── AC1: Export legacy JSON ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/admin/export/legacy as ORGANIZER → 200 with Content-Disposition attachment")
    void exportLegacy_asOrganizer_returns200WithAttachment() throws Exception {
        mockMvc.perform(get("/api/v1/admin/export/legacy"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("batbern-export-")));
    }

    @Test
    @WithMockUser(username = "partner", roles = {"PARTNER"})
    @DisplayName("GET /api/v1/admin/export/legacy as PARTNER → 403 Forbidden")
    void exportLegacy_asPartner_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/export/legacy"))
                .andExpect(status().isForbidden());
    }

    // ── AC3: Import legacy JSON ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/admin/import/legacy with valid JSON → 200 with import result")
    void importLegacy_withValidJson_returns200WithImportResult() throws Exception {
        String validJson = """
                {
                  "version": "2.0",
                  "exportedAt": "2026-03-01T12:00:00Z",
                  "events": [],
                  "companies": [],
                  "speakers": [],
                  "attendees": []
                }
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "export.json", MediaType.APPLICATION_JSON_VALUE, validJson.getBytes());

        mockMvc.perform(multipart("/api/v1/admin/import/legacy").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported.events").value(0))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/admin/import/legacy with invalid JSON → 400 with error message in body")
    void importLegacy_withInvalidJson_returns400WithStructuredError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.json", MediaType.APPLICATION_JSON_VALUE, "not-valid-json".getBytes());

        mockMvc.perform(multipart("/api/v1/admin/import/legacy").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).isNotBlank();
                });
    }

    // ── AC2: Export asset manifest ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/admin/export/assets as ORGANIZER → 200 with manifest body")
    void exportAssets_asOrganizer_returns200WithManifest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/export/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.assetCount").isNumber())
                .andExpect(jsonPath("$.assets").isArray());
    }

    @Test
    @WithMockUser(username = "partner", roles = {"PARTNER"})
    @DisplayName("GET /api/v1/admin/export/assets as PARTNER → 403 Forbidden")
    void exportAssets_asPartner_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/export/assets"))
                .andExpect(status().isForbidden());
    }

    // ── AC4: Import assets ZIP ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/admin/import/assets with valid ZIP → 200 with import result")
    void importAssets_withValidZip_returns200WithImportResult() throws Exception {
        // Create a minimal valid ZIP in memory
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("test-image.png");
            zos.putNextEntry(entry);
            zos.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}); // PNG magic bytes
            zos.closeEntry();
        }

        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "assets.zip", "application/zip", bos.toByteArray());

        mockMvc.perform(multipart("/api/v1/admin/import/assets").file(zipFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").isNumber())
                .andExpect(jsonPath("$.s3Prefix").value(org.hamcrest.Matchers.startsWith("imports/")))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @WithMockUser(username = "partner", roles = {"PARTNER"})
    @DisplayName("POST /api/v1/admin/import/assets as PARTNER → 403 Forbidden")
    void importAssets_asPartner_returns403() throws Exception {
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "assets.zip", "application/zip", new byte[0]);

        mockMvc.perform(multipart("/api/v1/admin/import/assets").file(zipFile))
                .andExpect(status().isForbidden());
    }

    // ── AC7: Round-trip test ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    @DisplayName("Round-trip: export → import → result shows imported event count = 1")
    void roundTrip_exportThenImport_eventCountIsOne() throws Exception {
        // Seed one event (all required fields must be set to pass Jakarta validation)
        Event event = Event.builder()
                .eventCode("BATbern99")
                .title("BATbern 99 Round-trip Test")
                .eventNumber(99)
                .date(Instant.parse("2026-11-14T17:00:00Z"))
                .registrationDeadline(Instant.parse("2026-11-07T17:00:00Z"))
                .venueName("Test Venue")
                .venueAddress("Test Street 1, 3001 Bern")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .build();
        eventRepository.save(event);

        // Export → get bytes
        MvcResult exportResult = mockMvc.perform(get("/api/v1/admin/export/legacy"))
                .andExpect(status().isOk())
                .andReturn();
        byte[] exportedJson = exportResult.getResponse().getContentAsByteArray();
        assertThat(exportedJson).isNotEmpty();

        // Verify exported JSON contains the seeded event
        String exportedStr = new String(exportedJson);
        assertThat(exportedStr).contains("BATbern99");

        // Import the exported file
        MockMultipartFile importFile = new MockMultipartFile(
                "file", "round-trip.json", MediaType.APPLICATION_JSON_VALUE, exportedJson);

        mockMvc.perform(multipart("/api/v1/admin/import/legacy").file(importFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported.events").value(1));
    }
}
