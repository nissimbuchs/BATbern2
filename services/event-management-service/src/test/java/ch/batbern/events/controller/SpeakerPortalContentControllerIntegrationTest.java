package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.ContentSubmission;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerInvitationToken;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerPortalContentController.
 * Story 6.3: Speaker Content Self-Submission Portal
 *
 * Tests REST endpoints:
 * - GET /api/v1/speaker-portal/content?token=xxx - Get content info
 * - POST /api/v1/speaker-portal/content/draft - Save draft
 * - POST /api/v1/speaker-portal/content/submit - Submit content
 *
 * Uses real PostgreSQL (Testcontainers) to test full content submission flow.
 *
 * NOTE: These are PUBLIC endpoints - no authentication required.
 * The magic link token IS the authentication mechanism.
 */
@Transactional
class SpeakerPortalContentControllerIntegrationTest extends AbstractIntegrationTest {

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
    private ContentSubmissionRepository contentSubmissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testSpeakerPoolId;
    private UUID testEventId;
    private UUID testSessionId;
    private Event testEvent;
    private Session testSession;
    private SpeakerPool testSpeakerPool;
    private String validToken;

    @BeforeEach
    void setUp() {
        // Clean up in correct order (FK constraints)
        contentSubmissionRepository.deleteAll();
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event with unique event number
        long uniqueNumber = System.currentTimeMillis() % 100000;
        testEvent = Event.builder()
                .eventCode("bat-bern-2026-content-" + uniqueNumber)
                .eventNumber((int) uniqueNumber)
                .title("BATbern Content Test 2026")
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
                .sessionSlug("cloud-architecture-" + uniqueNumber)
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
                .sessionId(testSessionId) // Session assigned
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

    // ==================== AC1: Content Info Tests ====================

    @Nested
    @DisplayName("AC1: Content Info (GET /speaker-portal/content)")
    class GetContentInfoTests {

        /**
         * AC1: Should return 200 with content info when valid token and session assigned
         */
        @Test
        void should_return200WithContentInfo_when_validTokenAndSessionAssigned() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/content")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.speakerName", is("Jane Speaker")))
                    .andExpect(jsonPath("$.eventCode", is(testEvent.getEventCode())))
                    .andExpect(jsonPath("$.eventTitle", is("BATbern Content Test 2026")))
                    .andExpect(jsonPath("$.hasSessionAssigned", is(true)))
                    .andExpect(jsonPath("$.sessionTitle", is("Cloud Architecture Best Practices")))
                    .andExpect(jsonPath("$.canSubmitContent", is(true)))
                    .andExpect(jsonPath("$.contentStatus", is("PENDING")))
                    .andExpect(jsonPath("$.hasDraft", is(false)));
        }

        /**
         * AC1: Should indicate no session when speaker has no session assigned
         */
        @Test
        void should_indicateNoSession_when_speakerHasNoSession() throws Exception {
            // Given - Speaker without session
            testSpeakerPool.setSessionId(null);
            speakerPoolRepository.save(testSpeakerPool);

            mockMvc.perform(get("/api/v1/speaker-portal/content")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasSessionAssigned", is(false)))
                    .andExpect(jsonPath("$.canSubmitContent", is(false)))
                    .andExpect(jsonPath("$.sessionTitle", nullValue()));
        }

        /**
         * AC4: Should return draft content when draft exists
         */
        @Test
        void should_returnDraftContent_when_draftExists() throws Exception {
            // Given - Create a draft submission
            ContentSubmission draft = ContentSubmission.builder()
                    .speakerPool(testSpeakerPool)
                    .session(testSession)
                    .title("My Draft Title")
                    .contentAbstract("My draft abstract content")
                    .abstractCharCount(25)
                    .submissionVersion(1)
                    .build();
            contentSubmissionRepository.save(draft);

            mockMvc.perform(get("/api/v1/speaker-portal/content")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasDraft", is(true)))
                    .andExpect(jsonPath("$.draftTitle", is("My Draft Title")))
                    .andExpect(jsonPath("$.draftAbstract", is("My draft abstract content")))
                    .andExpect(jsonPath("$.draftVersion", is(1)))
                    .andExpect(jsonPath("$.lastSavedAt", notNullValue()));
        }

        /**
         * AC8: Should include revision feedback when status is REVISION_NEEDED
         */
        @Test
        void should_includeRevisionFeedback_when_revisionNeeded() throws Exception {
            // Given - Speaker needs revision with feedback
            testSpeakerPool.setContentStatus("REVISION_NEEDED");
            speakerPoolRepository.save(testSpeakerPool);

            ContentSubmission submission = ContentSubmission.builder()
                    .speakerPool(testSpeakerPool)
                    .session(testSession)
                    .title("Original Title")
                    .contentAbstract("Original abstract")
                    .abstractCharCount(17)
                    .submissionVersion(1)
                    .reviewerFeedback("Please add more details about implementation")
                    .reviewedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .reviewedBy("organizer.test")
                    .build();
            contentSubmissionRepository.save(submission);

            mockMvc.perform(get("/api/v1/speaker-portal/content")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.needsRevision", is(true)))
                    .andExpect(jsonPath("$.reviewerFeedback", is("Please add more details about implementation")))
                    .andExpect(jsonPath("$.reviewedAt", notNullValue()))
                    .andExpect(jsonPath("$.reviewedBy", is("organizer.test")));
        }

        /**
         * Should return 400 when token parameter missing
         */
        @Test
        void should_return400_when_tokenMissing() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/content"))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when token is blank
         */
        @Test
        void should_return400_when_tokenBlank() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/content")
                            .param("token", "   "))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when token invalid
         */
        @Test
        void should_return400_when_tokenInvalid() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/content")
                            .param("token", "completely-invalid-token"))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when token expired
         */
        @Test
        void should_return400_when_tokenExpired() throws Exception {
            // Given - Create an expired token
            String expiredTokenPlaintext = "expired-content-token-test-12345";
            String tokenHash = sha256(expiredTokenPlaintext);

            SpeakerInvitationToken expiredToken = SpeakerInvitationToken.builder()
                    .speakerPoolId(testSpeakerPoolId)
                    .tokenHash(tokenHash)
                    .action(TokenAction.VIEW)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .createdAt(Instant.now().minus(31, ChronoUnit.DAYS))
                    .build();
            tokenRepository.save(expiredToken);

            mockMvc.perform(get("/api/v1/speaker-portal/content")
                            .param("token", expiredTokenPlaintext))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== AC4: Draft Save Tests ====================

    @Nested
    @DisplayName("AC4: Draft Save (POST /speaker-portal/content/draft)")
    class SaveDraftTests {

        /**
         * AC4: Should save draft and return 200
         */
        @Test
        void should_saveDraftAndReturn200_when_validRequest() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/content/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "My Presentation Title",
                                    "contentAbstract": "This is my presentation abstract describing the topic."
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.draftId", notNullValue()))
                    .andExpect(jsonPath("$.savedAt", notNullValue()));

            // Verify database
            var drafts = contentSubmissionRepository.findAll();
            assertThat(drafts).hasSize(1);
            assertThat(drafts.get(0).getTitle()).isEqualTo("My Presentation Title");
            assertThat(drafts.get(0).getContentAbstract()).isEqualTo("This is my presentation abstract describing the topic.");
        }

        /**
         * AC4: Should update existing draft
         */
        @Test
        void should_updateExistingDraft_when_draftAlreadyExists() throws Exception {
            // Given - Existing draft
            ContentSubmission existingDraft = ContentSubmission.builder()
                    .speakerPool(testSpeakerPool)
                    .session(testSession)
                    .title("Old Title")
                    .contentAbstract("Old abstract")
                    .abstractCharCount(12)
                    .submissionVersion(1)
                    .build();
            contentSubmissionRepository.save(existingDraft);

            // When
            mockMvc.perform(post("/api/v1/speaker-portal/content/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "Updated Title",
                                    "contentAbstract": "Updated abstract content"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());

            // Verify - Should have updated existing, not created new
            var drafts = contentSubmissionRepository.findAll();
            assertThat(drafts).hasSize(1);
            assertThat(drafts.get(0).getTitle()).isEqualTo("Updated Title");
            assertThat(drafts.get(0).getContentAbstract()).isEqualTo("Updated abstract content");
        }

        /**
         * Should return 400 when token missing
         */
        @Test
        void should_return400_when_tokenMissingInDraftRequest() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/content/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "title": "My Title",
                                    "contentAbstract": "My abstract"
                                }
                                """))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should truncate title that exceeds max length
         */
        @Test
        void should_truncateTitle_when_exceedsMaxLength() throws Exception {
            String longTitle = "x".repeat(250);

            mockMvc.perform(post("/api/v1/speaker-portal/content/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "%s",
                                    "contentAbstract": "Short abstract"
                                }
                                """.formatted(validToken, longTitle)))
                    .andExpect(status().isOk());

            // Verify truncation to 200 chars
            var drafts = contentSubmissionRepository.findAll();
            assertThat(drafts.get(0).getTitle()).hasSize(200);
        }
    }

    // ==================== AC5: Content Submit Tests ====================

    @Nested
    @DisplayName("AC5: Content Submit (POST /speaker-portal/content/submit)")
    class SubmitContentTests {

        /**
         * AC5: Should submit content and return 201 Created
         */
        @Test
        void should_submitContentAndReturn201_when_validRequest() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "My Final Presentation Title",
                                    "contentAbstract": "This is my final presentation abstract with all the details."
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.submissionId", notNullValue()))
                    .andExpect(jsonPath("$.version", is(1)))
                    .andExpect(jsonPath("$.status", is("SUBMITTED")))
                    .andExpect(jsonPath("$.sessionTitle", is("Cloud Architecture Best Practices")));

            // Verify database
            var submissions = contentSubmissionRepository.findAll();
            assertThat(submissions).hasSize(1);
            assertThat(submissions.get(0).getSubmittedAt()).isNotNull();

            // Verify speaker pool status updated
            SpeakerPool updated = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
            assertThat(updated.getContentStatus()).isEqualTo("SUBMITTED");
            assertThat(updated.getContentSubmittedAt()).isNotNull();
        }

        /**
         * AC8: Should increment version on resubmission
         */
        @Test
        void should_incrementVersion_when_resubmitting() throws Exception {
            // Given - Previous submission exists
            ContentSubmission previous = ContentSubmission.builder()
                    .speakerPool(testSpeakerPool)
                    .session(testSession)
                    .title("Previous Title")
                    .contentAbstract("Previous abstract")
                    .abstractCharCount(17)
                    .submissionVersion(1)
                    .submittedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();
            contentSubmissionRepository.save(previous);

            // Reset status for resubmission
            testSpeakerPool.setContentStatus("REVISION_NEEDED");
            speakerPoolRepository.save(testSpeakerPool);

            // When
            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "Revised Title",
                                    "contentAbstract": "Revised abstract with more details"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.version", is(2)));

            // Verify - Should have 2 submissions now
            var submissions = contentSubmissionRepository.findAll();
            assertThat(submissions).hasSize(2);
        }

        /**
         * Should return 400 when title missing
         */
        @Test
        void should_return400_when_titleMissing() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "contentAbstract": "My abstract"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when abstract missing
         */
        @Test
        void should_return400_when_abstractMissing() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "My Title"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when title blank
         */
        @Test
        void should_return400_when_titleBlank() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "   ",
                                    "contentAbstract": "My abstract"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when title exceeds max length
         */
        @Test
        void should_return400_when_titleTooLong() throws Exception {
            String longTitle = "x".repeat(201);

            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "%s",
                                    "contentAbstract": "My abstract"
                                }
                                """.formatted(validToken, longTitle)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when abstract exceeds max length
         */
        @Test
        void should_return400_when_abstractTooLong() throws Exception {
            String longAbstract = "x".repeat(1001);

            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "My Title",
                                    "contentAbstract": "%s"
                                }
                                """.formatted(validToken, longAbstract)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * AC1: Should return 400 when no session assigned
         */
        @Test
        void should_return400_when_noSessionAssigned() throws Exception {
            // Given - Remove session assignment
            testSpeakerPool.setSessionId(null);
            speakerPoolRepository.save(testSpeakerPool);

            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "title": "My Title",
                                    "contentAbstract": "My abstract"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Should return 400 when token missing
         */
        @Test
        void should_return400_when_tokenMissingInSubmitRequest() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/content/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "title": "My Title",
                                    "contentAbstract": "My abstract"
                                }
                                """))
                    .andExpect(status().isBadRequest());
        }
    }

    // Helper method to compute SHA-256 hash (same as MagicLinkService)
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
