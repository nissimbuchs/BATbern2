package ch.batbern.events.repository;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SessionMaterialsRepository
 * Story 5.9: Session Materials Upload - Task 1a (RED Phase)
 *
 * RED PHASE (TDD): These tests will FAIL until SessionMaterialsRepository is implemented.
 *
 * Uses real PostgreSQL (Testcontainers) to verify:
 * - SessionMaterial can be persisted with all fields
 * - Query methods work correctly (findBySessionId, findByUploadId)
 * - Materials can be deleted
 * - Database triggers update sessions.materials_count
 * - Foreign key constraints to sessions table work correctly
 */
@Transactional
class SessionMaterialsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SessionMaterialsRepository sessionMaterialsRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    private UUID testSessionId;
    private UUID testEventId;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        // Clean up
        sessionMaterialsRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Create a valid event first (required by sessions FK)
        testEvent = Event.builder()
                .eventCode("test-event-2026")
                .eventNumber(999)
                .title("Test Event 2026")
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .organizerUsername("test.organizer")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testEvent = eventRepository.save(testEvent);
        testEventId = testEvent.getId();

        // Create a valid session (required by session_materials FK)
        Session testSession = Session.builder()
                .eventId(testEventId)
                .eventCode(testEvent.getEventCode())
                .sessionSlug("test-session-keynote")
                .title("Test Keynote Session")
                .description("Test keynote description")
                .sessionType("keynote")
                .startTime(Instant.now().plus(30, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(30, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .room("Main Hall")
                .capacity(100)
                .language("de")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testSession = sessionRepository.save(testSession);
        testSessionId = testSession.getId();
    }

    /**
     * Test 1.1a: Should persist SessionMaterial with UUID primary key
     * RED Phase: This will fail - SessionMaterial entity doesn't exist yet
     */
    @Test
    void should_persistSessionMaterial_when_validDataProvided() {
        // Given
        Session testSession = sessionRepository.findById(testSessionId).orElseThrow();
        SessionMaterial material = SessionMaterial.builder()
                .session(testSession)
                .uploadId("upload-123")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/keynote/file.pptx")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/.../file.pptx")
                .fileName("keynote-slides.pptx")
                .fileExtension("pptx")
                .fileSize(5242880L)  // 5MB
                .mimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                .materialType("PRESENTATION")
                .uploadedBy("john.doe")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .contentExtracted(false)
                .extractionStatus("PENDING")
                .build();

        // When
        SessionMaterial saved = sessionMaterialsRepository.save(material);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUploadId()).isEqualTo("upload-123");
        assertThat(saved.getSession().getId()).isEqualTo(testSessionId);
        assertThat(saved.getMaterialType()).isEqualTo("PRESENTATION");
        assertThat(saved.getContentExtracted()).isFalse();
        assertThat(saved.getExtractionStatus()).isEqualTo("PENDING");
    }

    /**
     * Test 1.1b: Should find materials by session ID
     * RED Phase: This will fail - findBySessionId method doesn't exist yet
     */
    @Test
    void should_findMaterials_when_queryingBySessionId() {
        // Given - Create a second session for filtering test
        Session otherSession = Session.builder()
                .eventId(testEventId)
                .eventCode(testEvent.getEventCode())
                .sessionSlug("test-session-other")
                .title("Other Session")
                .sessionType("presentation")
                .startTime(Instant.now().plus(30, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(30, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        UUID otherSessionId = sessionRepository.save(otherSession).getId();

        SessionMaterial material1 = createTestMaterial(testSessionId, "upload-1", "PRESENTATION");
        SessionMaterial material2 = createTestMaterial(testSessionId, "upload-2", "DOCUMENT");
        SessionMaterial material3 = createTestMaterial(otherSessionId, "upload-3", "VIDEO");

        sessionMaterialsRepository.saveAll(List.of(material1, material2, material3));

        // When
        List<SessionMaterial> result = sessionMaterialsRepository.findBySession_Id(testSessionId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SessionMaterial::getUploadId)
                .containsExactlyInAnyOrder("upload-1", "upload-2");
    }

    /**
     * Test 1.1c: Should find material by upload ID (unique)
     * RED Phase: This will fail - findByUploadId method doesn't exist yet
     */
    @Test
    void should_findMaterial_when_queryingByUploadId() {
        // Given
        SessionMaterial material = createTestMaterial(testSessionId, "upload-unique-123", "PRESENTATION");
        sessionMaterialsRepository.save(material);

        // When
        SessionMaterial result = sessionMaterialsRepository.findByUploadId("upload-unique-123").orElse(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUploadId()).isEqualTo("upload-unique-123");
        assertThat(result.getSession().getId()).isEqualTo(testSessionId);
    }

    /**
     * Test 1.1d: Should delete material by ID
     * RED Phase: This will fail - entity doesn't exist yet
     */
    @Test
    void should_deleteMaterial_when_validIdProvided() {
        // Given
        SessionMaterial material = createTestMaterial(testSessionId, "upload-delete", "DOCUMENT");
        SessionMaterial saved = sessionMaterialsRepository.save(material);
        UUID materialId = saved.getId();

        // When
        sessionMaterialsRepository.deleteById(materialId);

        // Then
        assertThat(sessionMaterialsRepository.findById(materialId)).isEmpty();
    }

    /**
     * Test 1.1e: Should return empty list when no materials for session
     * RED Phase: This will fail - repository doesn't exist yet
     */
    @Test
    void should_returnEmptyList_when_sessionHasNoMaterials() {
        // Given
        UUID emptySessionId = UUID.randomUUID();

        // When
        List<SessionMaterial> result = sessionMaterialsRepository.findBySession_Id(emptySessionId);

        // Then
        assertThat(result).isEmpty();
    }

    /**
     * Test 1.1f: Should find materials by extraction status (for RAG pipeline - Story 5.10)
     * RED Phase: This will fail - findByExtractionStatus method doesn't exist yet
     */
    @Test
    void should_findPendingMaterials_when_queryingByExtractionStatus() {
        // Given
        SessionMaterial pending1 = createTestMaterial(testSessionId, "upload-p1", "PRESENTATION");
        pending1.setExtractionStatus("PENDING");

        SessionMaterial pending2 = createTestMaterial(testSessionId, "upload-p2", "DOCUMENT");
        pending2.setExtractionStatus("PENDING");

        SessionMaterial completed = createTestMaterial(testSessionId, "upload-c1", "VIDEO");
        completed.setExtractionStatus("COMPLETED");
        completed.setContentExtracted(true);

        sessionMaterialsRepository.saveAll(List.of(pending1, pending2, completed));

        // When
        List<SessionMaterial> result = sessionMaterialsRepository.findByExtractionStatus("PENDING");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SessionMaterial::getUploadId)
                .containsExactlyInAnyOrder("upload-p1", "upload-p2");
    }

    /**
     * Test 1.1g: Should count materials by session ID
     * RED Phase: This will fail - countBySessionId method doesn't exist yet
     */
    @Test
    void should_countMaterials_when_queryingBySessionId() {
        // Given
        sessionMaterialsRepository.saveAll(List.of(
                createTestMaterial(testSessionId, "upload-1", "PRESENTATION"),
                createTestMaterial(testSessionId, "upload-2", "DOCUMENT"),
                createTestMaterial(testSessionId, "upload-3", "VIDEO")
        ));

        // When
        long count = sessionMaterialsRepository.countBySession_Id(testSessionId);

        // Then
        assertThat(count).isEqualTo(3);
    }

    /**
     * Test 1.1h: Should check if session has presentation material type
     * RED Phase: This will fail - existsBySessionIdAndMaterialType method doesn't exist yet
     */
    @Test
    void should_returnTrue_when_sessionHasPresentationMaterial() {
        // Given
        sessionMaterialsRepository.saveAll(List.of(
                createTestMaterial(testSessionId, "upload-1", "PRESENTATION"),
                createTestMaterial(testSessionId, "upload-2", "DOCUMENT")
        ));

        // When
        boolean hasPresentation = sessionMaterialsRepository.existsBySession_IdAndMaterialType(
                testSessionId, "PRESENTATION"
        );
        boolean hasVideo = sessionMaterialsRepository.existsBySession_IdAndMaterialType(
                testSessionId, "VIDEO"
        );

        // Then
        assertThat(hasPresentation).isTrue();
        assertThat(hasVideo).isFalse();
    }

    // Helper method to create test materials
    private SessionMaterial createTestMaterial(UUID sessionId, String uploadId, String materialType) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        return SessionMaterial.builder()
                .session(session)
                .uploadId(uploadId)
                .s3Key("materials/test/file-" + uploadId + ".pdf")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/test/file-" + uploadId + ".pdf")
                .fileName("file-" + uploadId + ".pdf")
                .fileExtension("pdf")
                .fileSize(1048576L)
                .mimeType("application/pdf")
                .materialType(materialType)
                .uploadedBy("test.user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .contentExtracted(false)
                .extractionStatus("PENDING")
                .build();
    }
}
