package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.SessionMaterialAssociationRequest;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for SessionMaterialsController (Story 5.9 - Task 3a: RED Phase)
 *
 * Test Scenarios (AC5 & AC7):
 * - POST /api/v1/sessions/{sessionSlug}/materials - Associate materials with session (201 Created)
 * - GET /api/v1/sessions/{sessionSlug}/materials - List all materials for session (200 OK)
 * - DELETE /api/v1/sessions/{sessionSlug}/materials/{materialId} - Remove material (204 No Content)
 * - RBAC: Speaker can only upload to own sessions (403 Forbidden if not owner)
 * - RBAC: Organizer can upload to any session (200 OK)
 *
 * TDD Workflow: RED Phase - These tests MUST fail until SessionMaterialsController is implemented
 * Uses Testcontainers PostgreSQL for production parity
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class SessionMaterialsControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionMaterialsRepository sessionMaterialsRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private S3Client s3Client;

    private Event testEvent;
    private Session testSession;
    private Session anotherSession;
    private String sessionSlug;
    private String anotherSessionSlug;
    private String eventCode;

    @BeforeEach
    void setUp() {
        // Clean database
        sessionMaterialsRepository.deleteAll();
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        eventCode = "bat-bern-2026-spring";
        testEvent = Event.builder()
                .eventCode(eventCode)
                .eventNumber(130)
                .title("BATbern Spring 2026")
                .description("Test Event for Materials Upload")
                .date(Instant.now().plusSeconds(86400 * 30)) // 30 days from now
                .registrationDeadline(Instant.now().plusSeconds(86400 * 15))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("organizer.user")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .build();
        testEvent = eventRepository.save(testEvent);

        // Create test session (speaker: john.doe)
        sessionSlug = "keynote-digital-transformation";
        testSession = Session.builder()
                .sessionSlug(sessionSlug)
                .eventId(testEvent.getId())
                .eventCode(eventCode)
                .title("Keynote: Digital Transformation")
                .sessionType("presentation")
                .startTime(Instant.now().plusSeconds(86400 * 30))
                .endTime(Instant.now().plusSeconds(86400 * 30 + 3600))
                .materialsCount(0)
                .hasPresentation(false)
                .materialsStatus("NONE")
                .build();
        testSession = sessionRepository.save(testSession);

        // Assign speaker to testSession (john.doe)
        SessionUser speakerAssignment = SessionUser.builder()
                .session(testSession)
                .username("john.doe")
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .build();
        sessionUserRepository.save(speakerAssignment);

        // Create another session (speaker: jane.smith) for RBAC tests
        anotherSessionSlug = "workshop-cloud-architecture";
        anotherSession = Session.builder()
                .sessionSlug(anotherSessionSlug)
                .eventId(testEvent.getId())
                .eventCode(eventCode)
                .title("Workshop: Cloud Architecture")
                .sessionType("workshop")
                .startTime(Instant.now().plusSeconds(86400 * 30 + 7200))
                .endTime(Instant.now().plusSeconds(86400 * 30 + 10800))
                .materialsCount(0)
                .hasPresentation(false)
                .materialsStatus("NONE")
                .build();
        anotherSession = sessionRepository.save(anotherSession);

        // Assign different speaker to anotherSession (jane.smith)
        SessionUser anotherSpeakerAssignment = SessionUser.builder()
                .session(anotherSession)
                .username("jane.smith")
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .build();
        sessionUserRepository.save(anotherSpeakerAssignment);

        // Mock S3Client responses
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CopyObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
    }

    /**
     * Test 3.1: POST /api/v1/sessions/{sessionSlug}/materials - Success (AC5)
     * Organizer associates uploaded materials with session, returns 201 Created
     */
    @Test
    @DisplayName("Test 3.1: should_associateMaterials_when_organizerAndValidUploadIds")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_associateMaterials_when_organizerAndValidUploadIds() throws Exception {
        // Given: Valid association request with 2 materials
        SessionMaterialAssociationRequest request = new SessionMaterialAssociationRequest(
                Arrays.asList("upload-id-1", "upload-id-2"),
                Arrays.asList("PRESENTATION", "DOCUMENT")
        );

        // When: POST /api/v1/sessions/{sessionSlug}/materials
        // Then: 201 Created with materials list
        mockMvc.perform(post("/api/v1/sessions/{sessionSlug}/materials", sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.materials", hasSize(2)))
                .andExpect(jsonPath("$.materials[0].uploadId", is("upload-id-1")))
                .andExpect(jsonPath("$.materials[0].materialType", is("PRESENTATION")))
                .andExpect(jsonPath("$.materials[0].fileName", notNullValue()))
                .andExpect(jsonPath("$.materials[0].cloudFrontUrl", notNullValue()))
                .andExpect(jsonPath("$.materials[0].uploadedBy", is("organizer.user")))
                .andExpect(jsonPath("$.materials[0].contentExtracted", is(false)))
                .andExpect(jsonPath("$.materials[0].extractionStatus", is("PENDING")))
                .andExpect(jsonPath("$.materials[1].uploadId", is("upload-id-2")))
                .andExpect(jsonPath("$.materials[1].materialType", is("DOCUMENT")));
    }

    /**
     * Test 3.2: GET /api/v1/sessions/{sessionSlug}/materials - Success (AC5)
     * Returns materials list ordered by created_at
     */
    @Test
    @DisplayName("Test 3.2: should_returnMaterialsList_when_sessionHasMaterials")
    @WithMockUser(roles = "ORGANIZER")
    void should_returnMaterialsList_when_sessionHasMaterials() throws Exception {
        // Given: Session with 2 existing materials
        SessionMaterial material1 = SessionMaterial.builder()
                .session(testSession)
                .uploadId("existing-upload-1")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/" + sessionSlug + "/file-1.pptx")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/.../file-1.pptx")
                .fileName("keynote-slides.pptx")
                .fileExtension("pptx")
                .fileSize(5242880L) // 5 MB
                .mimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                .materialType("PRESENTATION")
                .uploadedBy("organizer.user")
                .build();
        sessionMaterialsRepository.save(material1);

        SessionMaterial material2 = SessionMaterial.builder()
                .session(testSession)
                .uploadId("existing-upload-2")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/" + sessionSlug + "/file-2.pdf")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/.../file-2.pdf")
                .fileName("handout.pdf")
                .fileExtension("pdf")
                .fileSize(1048576L) // 1 MB
                .mimeType("application/pdf")
                .materialType("DOCUMENT")
                .uploadedBy("john.doe")
                .build();
        sessionMaterialsRepository.save(material2);

        // When: GET /api/v1/sessions/{sessionSlug}/materials
        // Then: 200 OK with materials list (2 materials)
        mockMvc.perform(get("/api/v1/sessions/{sessionSlug}/materials", sessionSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials", hasSize(2)))
                .andExpect(jsonPath("$.materials[0].fileName", is("keynote-slides.pptx")))
                .andExpect(jsonPath("$.materials[0].materialType", is("PRESENTATION")))
                .andExpect(jsonPath("$.materials[0].fileSize", is(5242880)))
                .andExpect(jsonPath("$.materials[1].fileName", is("handout.pdf")))
                .andExpect(jsonPath("$.materials[1].materialType", is("DOCUMENT")));
    }

    /**
     * Test 3.3: GET /api/v1/sessions/{sessionSlug}/materials - Empty List
     * Returns empty list when session has no materials
     */
    @Test
    @DisplayName("Test 3.3: should_returnEmptyList_when_sessionHasNoMaterials")
    @WithMockUser(roles = "ORGANIZER")
    void should_returnEmptyList_when_sessionHasNoMaterials() throws Exception {
        // Given: Session with no materials (setup already creates empty session)

        // When: GET /api/v1/sessions/{sessionSlug}/materials
        // Then: 200 OK with empty materials list
        mockMvc.perform(get("/api/v1/sessions/{sessionSlug}/materials", sessionSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials", hasSize(0)));
    }

    /**
     * Test 3.4: DELETE /api/v1/sessions/{sessionSlug}/materials/{materialId} - Success (AC5)
     * Organizer deletes material, returns 204 No Content
     */
    @Test
    @DisplayName("Test 3.4: should_deleteMaterial_when_organizerAndValidMaterialId")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_deleteMaterial_when_organizerAndValidMaterialId() throws Exception {
        // Given: Session with 1 material
        SessionMaterial material = SessionMaterial.builder()
                .session(testSession)
                .uploadId("upload-to-delete")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/" + sessionSlug + "/file-delete.pdf")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/.../file-delete.pdf")
                .fileName("to-delete.pdf")
                .fileExtension("pdf")
                .fileSize(1048576L)
                .mimeType("application/pdf")
                .materialType("DOCUMENT")
                .uploadedBy("organizer.user")
                .build();
        SessionMaterial savedMaterial = sessionMaterialsRepository.save(material);

        // When: DELETE /api/v1/sessions/{sessionSlug}/materials/{materialId}
        // Then: 204 No Content
        mockMvc.perform(delete("/api/v1/sessions/{sessionSlug}/materials/{materialId}",
                        sessionSlug, savedMaterial.getId()))
                .andExpect(status().isNoContent());
    }

    /**
     * Test 3.5: POST /api/v1/sessions/{sessionSlug}/materials - Session Not Found (AC5)
     * Returns 404 Not Found when session doesn't exist
     */
    @Test
    @DisplayName("Test 3.5: should_return404_when_sessionNotFound")
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_sessionNotFound() throws Exception {
        // Given: Non-existent session slug
        String nonExistentSlug = "non-existent-session";
        SessionMaterialAssociationRequest request = new SessionMaterialAssociationRequest(
                List.of("upload-id-1"),
                List.of("PRESENTATION")
        );

        // When: POST /api/v1/sessions/{sessionSlug}/materials with invalid slug
        // Then: 404 Not Found
        mockMvc.perform(post("/api/v1/sessions/{sessionSlug}/materials", nonExistentSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session not found: " + nonExistentSlug));
    }

    /**
     * Test 3.6: DELETE /api/v1/sessions/{sessionSlug}/materials/{materialId} - Material Not Found (AC5)
     * Returns 404 Not Found when material doesn't exist
     */
    @Test
    @DisplayName("Test 3.6: should_return404_when_materialNotFound")
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_materialNotFound() throws Exception {
        // Given: Non-existent material ID
        UUID nonExistentMaterialId = UUID.randomUUID();

        // When: DELETE /api/v1/sessions/{sessionSlug}/materials/{materialId} with invalid ID
        // Then: 404 Not Found
        mockMvc.perform(delete("/api/v1/sessions/{sessionSlug}/materials/{materialId}",
                        sessionSlug, nonExistentMaterialId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session material not found with ID: " + nonExistentMaterialId));
    }

    /**
     * Test 3.7: RBAC - Speaker can upload to own session (AC7)
     * Speaker john.doe can upload materials to their own session
     */
    @Test
    @DisplayName("Test 3.7: should_allowUpload_when_speakerOwnsSession")
    @WithMockUser(username = "john.doe", roles = "SPEAKER")
    void should_allowUpload_when_speakerOwnsSession() throws Exception {
        // Given: Speaker john.doe owns testSession, valid request
        SessionMaterialAssociationRequest request = new SessionMaterialAssociationRequest(
                List.of("upload-id-speaker"),
                List.of("PRESENTATION")
        );

        // When: POST /api/v1/sessions/{sessionSlug}/materials as session owner
        // Then: 201 Created (speaker can upload to own session)
        mockMvc.perform(post("/api/v1/sessions/{sessionSlug}/materials", sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.materials[0].uploadedBy", is("john.doe")));
    }

    /**
     * Test 3.8: RBAC - Speaker cannot upload to another speaker's session (AC7)
     * Speaker john.doe CANNOT upload materials to jane.smith's session (403 Forbidden)
     */
    @Test
    @DisplayName("Test 3.8: should_forbidUpload_when_speakerDoesNotOwnSession")
    @WithMockUser(username = "john.doe", roles = "SPEAKER")
    void should_forbidUpload_when_speakerDoesNotOwnSession() throws Exception {
        // Given: Speaker john.doe does NOT own anotherSession (owned by jane.smith)
        SessionMaterialAssociationRequest request = new SessionMaterialAssociationRequest(
                List.of("upload-id-unauthorized"),
                List.of("PRESENTATION")
        );

        // When: POST /api/v1/sessions/{anotherSessionSlug}/materials as non-owner
        // Then: 403 Forbidden (speaker cannot upload to another speaker's session)
        mockMvc.perform(post("/api/v1/sessions/{sessionSlug}/materials", anotherSessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 3.9: RBAC - Organizer can upload to any session (AC7)
     * Organizer can upload materials to sessions they don't own
     */
    @Test
    @DisplayName("Test 3.9: should_allowUpload_when_userIsOrganizer")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_allowUpload_when_userIsOrganizer() throws Exception {
        // Given: Organizer (not session owner), valid request for anotherSession
        SessionMaterialAssociationRequest request = new SessionMaterialAssociationRequest(
                List.of("upload-id-organizer"),
                List.of("DOCUMENT")
        );

        // When: POST /api/v1/sessions/{anotherSessionSlug}/materials as organizer
        // Then: 201 Created (organizer can upload to any session)
        mockMvc.perform(post("/api/v1/sessions/{sessionSlug}/materials", anotherSessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.materials[0].uploadedBy", is("organizer.user")));
    }

    /**
     * Test 3.10: RBAC - Speaker can delete own materials from own session (AC7)
     * Speaker john.doe can delete materials they uploaded to their session
     */
    @Test
    @DisplayName("Test 3.10: should_allowDelete_when_speakerOwnsSessionAndMaterial")
    @WithMockUser(username = "john.doe", roles = "SPEAKER")
    void should_allowDelete_when_speakerOwnsSessionAndMaterial() throws Exception {
        // Given: Material uploaded by john.doe to their own session
        SessionMaterial material = SessionMaterial.builder()
                .session(testSession)
                .uploadId("upload-by-speaker")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/" + sessionSlug + "/speaker-file.pdf")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/.../speaker-file.pdf")
                .fileName("speaker-material.pdf")
                .fileExtension("pdf")
                .fileSize(1048576L)
                .mimeType("application/pdf")
                .materialType("DOCUMENT")
                .uploadedBy("john.doe")
                .build();
        SessionMaterial savedMaterial = sessionMaterialsRepository.save(material);

        // When: DELETE /api/v1/sessions/{sessionSlug}/materials/{materialId} as material owner
        // Then: 204 No Content (speaker can delete own materials)
        mockMvc.perform(delete("/api/v1/sessions/{sessionSlug}/materials/{materialId}",
                        sessionSlug, savedMaterial.getId()))
                .andExpect(status().isNoContent());
    }

    /**
     * Test 3.11: RBAC - Speaker cannot delete materials from another speaker's session (AC7)
     * Speaker john.doe CANNOT delete materials from jane.smith's session (403 Forbidden)
     */
    @Test
    @DisplayName("Test 3.11: should_forbidDelete_when_speakerDoesNotOwnSession")
    @WithMockUser(username = "john.doe", roles = "SPEAKER")
    void should_forbidDelete_when_speakerDoesNotOwnSession() throws Exception {
        // Given: Material in anotherSession (owned by jane.smith)
        SessionMaterial material = SessionMaterial.builder()
                .session(anotherSession)
                .uploadId("upload-by-jane")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/" + anotherSessionSlug + "/jane-file.pdf")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/.../jane-file.pdf")
                .fileName("jane-material.pdf")
                .fileExtension("pdf")
                .fileSize(1048576L)
                .mimeType("application/pdf")
                .materialType("DOCUMENT")
                .uploadedBy("jane.smith")
                .build();
        SessionMaterial savedMaterial = sessionMaterialsRepository.save(material);

        // When: DELETE /api/v1/sessions/{anotherSessionSlug}/materials/{materialId} as non-owner
        // Then: 403 Forbidden (speaker cannot delete from another speaker's session)
        mockMvc.perform(delete("/api/v1/sessions/{sessionSlug}/materials/{materialId}",
                        anotherSessionSlug, savedMaterial.getId()))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 3.12: RBAC - Organizer can delete materials from any session (AC7)
     * Organizer can delete materials regardless of who uploaded them
     */
    @Test
    @DisplayName("Test 3.12: should_allowDelete_when_userIsOrganizer")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_allowDelete_when_userIsOrganizer() throws Exception {
        // Given: Material uploaded by speaker to anotherSession
        SessionMaterial material = SessionMaterial.builder()
                .session(anotherSession)
                .uploadId("upload-by-jane-2")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/" + anotherSessionSlug + "/file.pdf")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/.../file.pdf")
                .fileName("session-material.pdf")
                .fileExtension("pdf")
                .fileSize(1048576L)
                .mimeType("application/pdf")
                .materialType("DOCUMENT")
                .uploadedBy("jane.smith")
                .build();
        SessionMaterial savedMaterial = sessionMaterialsRepository.save(material);

        // When: DELETE /api/v1/sessions/{anotherSessionSlug}/materials/{materialId} as organizer
        // Then: 204 No Content (organizer can delete any material)
        mockMvc.perform(delete("/api/v1/sessions/{sessionSlug}/materials/{materialId}",
                        anotherSessionSlug, savedMaterial.getId()))
                .andExpect(status().isNoContent());
    }
}
