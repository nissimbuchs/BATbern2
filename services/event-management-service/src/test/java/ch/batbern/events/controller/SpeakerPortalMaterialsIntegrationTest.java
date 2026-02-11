package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for speaker portal material upload endpoints.
 * Story 6.3: Speaker Content Self-Submission Portal - AC7
 *
 * Tests REST endpoints:
 * - POST /api/v1/speaker-portal/materials/presigned-url
 * - POST /api/v1/speaker-portal/materials/confirm
 *
 * Uses real PostgreSQL (Testcontainers) to test full material upload flow.
 *
 * NOTE: These are PUBLIC endpoints - no authentication required.
 * The magic link token IS the authentication mechanism.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@Transactional
class SpeakerPortalMaterialsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MagicLinkService magicLinkService;

    @Autowired
    private SpeakerInvitationTokenRepository tokenRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionMaterialsRepository sessionMaterialsRepository;

    /**
     * Mock S3Presigner to avoid real AWS calls in tests.
     * This ensures presigned URL generation works without AWS credentials.
     */
    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private S3Client s3Client;

    private UUID testSpeakerPoolId;
    private UUID testEventId;
    private UUID testSessionId;
    private Event testEvent;
    private Session testSession;
    private SpeakerPool testSpeakerPool;
    private String validToken;

    @BeforeEach
    void setUp() throws Exception {
        // Configure S3Presigner mock to return a presigned URL
        PresignedPutObjectRequest mockPresignedRequest = Mockito.mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(URI.create("https://test-bucket.s3.amazonaws.com/test-upload-url").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

        // Configure S3Client mock to return success for copyObject (used by confirmUpload)
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CopyObjectResponse.builder().build());

        // Clean up in correct order (FK constraints)
        sessionMaterialsRepository.deleteAll();
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event with unique event number
        long uniqueNumber = System.currentTimeMillis() % 100000;
        testEvent = Event.builder()
                .eventCode("bat-bern-2026-materials-" + uniqueNumber)
                .eventNumber((int) uniqueNumber)
                .title("BATbern Materials Test 2026")
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername("organizer.test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testEvent = eventRepository.save(testEvent);
        testEventId = testEvent.getId();

        // Create test session
        testSession = Session.builder()
                .eventId(testEventId)
                .eventCode(testEvent.getEventCode())
                .sessionSlug("cloud-architecture-materials-" + uniqueNumber)
                .title("Cloud Architecture Best Practices")
                .description("Learn about cloud architecture patterns")
                .sessionType("presentation")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testSession = sessionRepository.save(testSession);
        testSessionId = testSession.getId();

        // Create test speaker pool entry with session assigned
        testSpeakerPool = SpeakerPool.builder()
                .eventId(testEventId)
                .speakerName("Jane Speaker")
                .company("Tech Corp AG")
                .expertise("Cloud Architecture")
                .email("jane@techcorp.ch")
                .status(SpeakerWorkflowState.ACCEPTED)
                .username("jane.speaker")
                .sessionId(testSessionId)
                .invitedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .acceptedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .responseDeadline(LocalDate.now().plusDays(10))
                .contentDeadline(LocalDate.now().plusDays(30))
                .contentStatus("PENDING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testSpeakerPool = speakerPoolRepository.save(testSpeakerPool);
        testSpeakerPoolId = testSpeakerPool.getId();

        // Generate a valid token for testing
        validToken = magicLinkService.generateToken(testSpeakerPoolId, TokenAction.VIEW);
    }

    // ==================== AC7: Presigned URL Tests ====================

    @Nested
    @DisplayName("AC7: Generate Presigned URL (POST /speaker-portal/materials/presigned-url)")
    class GeneratePresignedUrlTests {

        /**
         * AC7: Should return 200 with presigned URL for valid PPTX file
         */
        @Test
        void should_return200WithPresignedUrl_when_validPptxFile() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/materials/presigned-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "fileName": "presentation.pptx",
                                    "fileSize": 5242880,
                                    "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadUrl", notNullValue()))
                    .andExpect(jsonPath("$.uploadId", notNullValue()))
                    .andExpect(jsonPath("$.s3Key", notNullValue()))
                    .andExpect(jsonPath("$.fileExtension", is("pptx")))
                    .andExpect(jsonPath("$.expiresInMinutes", is(15)));
        }

        /**
         * AC7: Should return 200 with presigned URL for valid PDF file
         */
        @Test
        void should_return200WithPresignedUrl_when_validPdfFile() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/materials/presigned-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "fileName": "presentation.pdf",
                                    "fileSize": 1048576,
                                    "mimeType": "application/pdf"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadUrl", notNullValue()))
                    .andExpect(jsonPath("$.fileExtension", is("pdf")));
        }

        /**
         * AC7: Should return 400 when file exceeds 50MB limit
         */
        @Test
        void should_return400_when_fileSizeExceeds50MB() throws Exception {
            long fileSizeOver50MB = 51L * 1024 * 1024; // 51MB

            mockMvc.perform(post("/api/v1/speaker-portal/materials/presigned-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "fileName": "large-presentation.pptx",
                                    "fileSize": %d,
                                    "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                }
                                """.formatted(validToken, fileSizeOver50MB)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * AC7: Should return 400 when file type not allowed
         */
        @Test
        void should_return400_when_fileTypeNotAllowed() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/materials/presigned-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "fileName": "video.mp4",
                                    "fileSize": 1048576,
                                    "mimeType": "video/mp4"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when token missing
         */
        @Test
        void should_return400_when_tokenMissing() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/materials/presigned-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "fileName": "presentation.pptx",
                                    "fileSize": 5242880,
                                    "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                }
                                """))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when token invalid
         */
        @Test
        void should_return400_when_tokenInvalid() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/materials/presigned-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "invalid-token-12345",
                                    "fileName": "presentation.pptx",
                                    "fileSize": 5242880,
                                    "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                }
                                """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== AC7: Confirm Upload Tests ====================

    @Nested
    @DisplayName("AC7: Confirm Upload (POST /speaker-portal/materials/confirm)")
    class ConfirmUploadTests {

        /**
         * AC7: Should return 201 with material info when upload confirmed
         */
        @Test
        void should_return201WithMaterialInfo_when_uploadConfirmed() throws Exception {
            String uploadId = UUID.randomUUID().toString();

            mockMvc.perform(post("/api/v1/speaker-portal/materials/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "uploadId": "%s",
                                    "fileName": "presentation.pptx",
                                    "fileExtension": "pptx",
                                    "fileSize": 5242880,
                                    "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "materialType": "PRESENTATION"
                                }
                                """.formatted(validToken, uploadId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.materialId", notNullValue()))
                    .andExpect(jsonPath("$.uploadId", is(uploadId)))
                    .andExpect(jsonPath("$.fileName", is("presentation.pptx")))
                    .andExpect(jsonPath("$.cloudFrontUrl", notNullValue()))
                    .andExpect(jsonPath("$.materialType", is("PRESENTATION")))
                    .andExpect(jsonPath("$.uploadedAt", notNullValue()));
        }

        /**
         * AC7: Should return 400 when no session assigned
         */
        @Test
        void should_return400_when_noSessionAssigned() throws Exception {
            // Remove session assignment
            testSpeakerPool.setSessionId(null);
            speakerPoolRepository.save(testSpeakerPool);

            String uploadId = UUID.randomUUID().toString();

            mockMvc.perform(post("/api/v1/speaker-portal/materials/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "uploadId": "%s",
                                    "fileName": "presentation.pptx",
                                    "fileExtension": "pptx",
                                    "fileSize": 5242880,
                                    "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "materialType": "PRESENTATION"
                                }
                                """.formatted(validToken, uploadId)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when token missing
         */
        @Test
        void should_return400_when_tokenMissingInConfirm() throws Exception {
            String uploadId = UUID.randomUUID().toString();

            mockMvc.perform(post("/api/v1/speaker-portal/materials/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "uploadId": "%s",
                                    "fileName": "presentation.pptx",
                                    "fileExtension": "pptx",
                                    "fileSize": 5242880,
                                    "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "materialType": "PRESENTATION"
                                }
                                """.formatted(uploadId)))
                    .andExpect(status().isBadRequest());
        }
    }
}
