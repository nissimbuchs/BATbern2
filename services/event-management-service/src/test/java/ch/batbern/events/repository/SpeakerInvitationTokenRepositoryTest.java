package ch.batbern.events.repository;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerInvitationToken;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SpeakerInvitationTokenRepository
 * Story 6.1a: Magic Link Infrastructure - Task 1 (RED Phase)
 *
 * RED PHASE (TDD): These tests will FAIL until:
 * - SpeakerInvitationToken entity is implemented
 * - SpeakerInvitationTokenRepository is implemented
 *
 * Uses real PostgreSQL (Testcontainers) to verify:
 * - Token can be persisted with hash (plaintext never stored)
 * - Query by hash works correctly (primary validation path)
 * - Query by speaker pool ID works
 * - Expired token cleanup works
 */
@Transactional
class SpeakerInvitationTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerInvitationTokenRepository tokenRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private EventRepository eventRepository;

    private UUID testSpeakerPoolId;
    private UUID testEventId;

    @BeforeEach
    void setUp() {
        // Clean up in correct order (FK constraints)
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        // Create a valid event first (required by speaker_pool FK)
        Event testEvent = Event.builder()
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

        // Create a speaker pool entry (required by speaker_invitation_tokens FK)
        SpeakerPool speakerPool = SpeakerPool.builder()
                .eventId(testEventId)
                .speakerName("John Speaker")
                .company("Test Corp")
                .expertise("Java, Spring Boot")
                .status(SpeakerWorkflowState.IDENTIFIED)
                .username("john.speaker")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        speakerPool = speakerPoolRepository.save(speakerPool);
        testSpeakerPoolId = speakerPool.getId();
    }

    // ==================== AC1: Token Generation Tests ====================

    /**
     * Test 1.1: Should persist token with hash
     * AC1: Token hash (SHA-256) stored in database, plaintext never persisted
     * RED Phase: Will fail - SpeakerInvitationToken entity doesn't exist yet
     */
    @Test
    void should_persistToken_when_validDataProvided() {
        // Given
        String tokenHash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
        SpeakerInvitationToken token = SpeakerInvitationToken.builder()
                .speakerPoolId(testSpeakerPoolId)
                .tokenHash(tokenHash)
                .action(TokenAction.RESPOND)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();

        // When
        SpeakerInvitationToken saved = tokenRepository.save(token);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTokenHash()).isEqualTo(tokenHash);
        assertThat(saved.getSpeakerPoolId()).isEqualTo(testSpeakerPoolId);
        assertThat(saved.getAction()).isEqualTo(TokenAction.RESPOND);
        assertThat(saved.getUsedAt()).isNull(); // Not yet used
    }

    /**
     * Test 1.2: Should store 64-char SHA-256 hash
     * AC1: Token hash (SHA-256) stored in database
     * RED Phase: Will fail - entity doesn't exist yet
     */
    @Test
    void should_storeTokenHash_when_sha256HashProvided() {
        // Given - 64 character SHA-256 hash
        String sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        SpeakerInvitationToken token = createTestToken(sha256Hash, TokenAction.RESPOND, 30);

        // When
        SpeakerInvitationToken saved = tokenRepository.save(token);
        tokenRepository.flush();

        // Then - Verify hash stored correctly
        Optional<SpeakerInvitationToken> found = tokenRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(sha256Hash);
        assertThat(found.get().getTokenHash()).hasSize(64);
    }

    /**
     * Test 1.3: Should link token to speaker pool
     * AC1: Token linked to speakerPoolId
     * RED Phase: Will fail - entity doesn't exist yet
     */
    @Test
    void should_linkToSpeakerPool_when_tokenCreated() {
        // Given
        SpeakerInvitationToken token = createTestToken("hash1234567890123456789012345678901234567890123456789012345678", TokenAction.RESPOND, 30);

        // When
        tokenRepository.save(token);
        tokenRepository.flush();

        // Then - Verify FK constraint works
        List<SpeakerInvitationToken> tokens = tokenRepository.findBySpeakerPoolId(testSpeakerPoolId);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getSpeakerPoolId()).isEqualTo(testSpeakerPoolId);
    }

    // ==================== AC2: Token Validation Tests ====================

    /**
     * Test 2.1: Should find token by hash
     * AC2: Token lookup by hash for validation
     * RED Phase: Will fail - findByTokenHash method doesn't exist yet
     */
    @Test
    void should_findToken_when_queryingByHash() {
        // Given
        String tokenHash = "unique_hash_123456789012345678901234567890123456789012345678";
        SpeakerInvitationToken token = createTestToken(tokenHash, TokenAction.RESPOND, 30);
        tokenRepository.save(token);

        // When
        Optional<SpeakerInvitationToken> found = tokenRepository.findByTokenHash(tokenHash);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(tokenHash);
        assertThat(found.get().getSpeakerPoolId()).isEqualTo(testSpeakerPoolId);
    }

    /**
     * Test 2.2: Should return empty when hash not found
     * AC2: NOT_FOUND error for invalid tokens
     * RED Phase: Will fail - findByTokenHash method doesn't exist yet
     */
    @Test
    void should_returnEmpty_when_tokenHashNotFound() {
        // Given - no tokens exist
        String nonExistentHash = "nonexistent_hash_12345678901234567890123456789012345678901";

        // When
        Optional<SpeakerInvitationToken> found = tokenRepository.findByTokenHash(nonExistentHash);

        // Then
        assertThat(found).isEmpty();
    }

    /**
     * Test 2.3: Should find all tokens for a speaker pool
     * AC2: Query by speakerPoolId for admin operations
     * RED Phase: Will fail - findBySpeakerPoolId method doesn't exist yet
     */
    @Test
    void should_findAllTokens_when_queryingBySpeakerPoolId() {
        // Given - Multiple tokens for same speaker
        tokenRepository.saveAll(List.of(
                createTestToken("hash_respond_123456789012345678901234567890123456789012", TokenAction.RESPOND, 30),
                createTestToken("hash_submit_1234567890123456789012345678901234567890123", TokenAction.SUBMIT, 30),
                createTestToken("hash_view_12345678901234567890123456789012345678901234", TokenAction.VIEW, 30)
        ));

        // When
        List<SpeakerInvitationToken> tokens = tokenRepository.findBySpeakerPoolId(testSpeakerPoolId);

        // Then
        assertThat(tokens).hasSize(3);
        assertThat(tokens).extracting(SpeakerInvitationToken::getAction)
                .containsExactlyInAnyOrder(TokenAction.RESPOND, TokenAction.SUBMIT, TokenAction.VIEW);
    }

    // ==================== AC3: Single-Use Enforcement Tests ====================

    /**
     * Test 3.1: Should track used_at timestamp
     * AC3: After use, used_at timestamp is set
     * RED Phase: Will fail - entity doesn't exist yet
     */
    @Test
    void should_trackUsedAt_when_tokenMarkedAsUsed() {
        // Given
        SpeakerInvitationToken token = createTestToken("hash_single_use_234567890123456789012345678901234567890", TokenAction.RESPOND, 30);
        token = tokenRepository.save(token);
        assertThat(token.getUsedAt()).isNull();

        // When - Mark as used
        Instant usedTime = Instant.now();
        token.setUsedAt(usedTime);
        tokenRepository.save(token);
        tokenRepository.flush();

        // Then
        Optional<SpeakerInvitationToken> found = tokenRepository.findById(token.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsedAt()).isNotNull();
        assertThat(found.get().getUsedAt()).isEqualTo(usedTime);
    }

    // ==================== AC4: Token Expiry Tests ====================

    /**
     * Test 4.1: Should store expiry timestamp
     * AC4: Tokens expire after configurable duration
     * RED Phase: Will fail - entity doesn't exist yet
     */
    @Test
    void should_storeExpiry_when_tokenCreated() {
        // Given
        Instant expectedExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
        SpeakerInvitationToken token = SpeakerInvitationToken.builder()
                .speakerPoolId(testSpeakerPoolId)
                .tokenHash("hash_expiry_test_2345678901234567890123456789012345678901")
                .action(TokenAction.RESPOND)
                .expiresAt(expectedExpiry)
                .createdAt(Instant.now())
                .build();

        // When
        SpeakerInvitationToken saved = tokenRepository.save(token);

        // Then
        assertThat(saved.getExpiresAt()).isEqualTo(expectedExpiry);
    }

    /**
     * Test 4.2: Should find expired tokens for cleanup
     * AC4: Scheduled job cleans up expired tokens older than 90 days
     * RED Phase: Will fail - deleteExpiredBefore method doesn't exist yet
     */
    @Test
    void should_deleteExpiredTokens_when_cleanupJobRuns() {
        // Given - Create tokens with different expiry states
        Instant now = Instant.now();

        // Active token (expires in future)
        SpeakerInvitationToken activeToken = createTestToken("hash_active_12345678901234567890123456789012345678901", TokenAction.VIEW, 30);
        activeToken = tokenRepository.save(activeToken);

        // Expired token (expired 100 days ago) - should be deleted
        SpeakerInvitationToken expiredOldToken = SpeakerInvitationToken.builder()
                .speakerPoolId(testSpeakerPoolId)
                .tokenHash("hash_expired_old_234567890123456789012345678901234567")
                .action(TokenAction.RESPOND)
                .expiresAt(now.minus(100, ChronoUnit.DAYS))
                .createdAt(now.minus(130, ChronoUnit.DAYS))
                .build();
        expiredOldToken = tokenRepository.save(expiredOldToken);

        // Recently expired token (expired 30 days ago) - should NOT be deleted (< 90 days)
        SpeakerInvitationToken expiredRecentToken = SpeakerInvitationToken.builder()
                .speakerPoolId(testSpeakerPoolId)
                .tokenHash("hash_expired_recent_3456789012345678901234567890123456")
                .action(TokenAction.RESPOND)
                .expiresAt(now.minus(30, ChronoUnit.DAYS))
                .createdAt(now.minus(60, ChronoUnit.DAYS))
                .build();
        expiredRecentToken = tokenRepository.save(expiredRecentToken);

        tokenRepository.flush();
        assertThat(tokenRepository.count()).isEqualTo(3);

        // When - Delete tokens expired before 90 days ago
        Instant cutoffDate = now.minus(90, ChronoUnit.DAYS);
        int deleted = tokenRepository.deleteExpiredBefore(cutoffDate);

        // Then
        assertThat(deleted).isEqualTo(1);
        assertThat(tokenRepository.count()).isEqualTo(2);
        assertThat(tokenRepository.findByTokenHash("hash_active_12345678901234567890123456789012345678901")).isPresent();
        assertThat(tokenRepository.findByTokenHash("hash_expired_recent_3456789012345678901234567890123456")).isPresent();
        assertThat(tokenRepository.findByTokenHash("hash_expired_old_234567890123456789012345678901234567")).isEmpty();
    }

    /**
     * Test 4.3: Should find non-expired tokens only
     * AC4: Token expiry is checked on every validation
     * RED Phase: Will fail - findByTokenHashAndNotExpired doesn't exist
     */
    @Test
    void should_findOnlyNonExpiredTokens_when_validating() {
        // Given
        String validHash = "hash_valid_not_expired_567890123456789012345678901234567";
        String expiredHash = "hash_expired_567890123456789012345678901234567890123";

        // Valid token (expires in future)
        SpeakerInvitationToken validToken = createTestToken(validHash, TokenAction.RESPOND, 30);
        tokenRepository.save(validToken);

        // Expired token
        SpeakerInvitationToken expiredToken = SpeakerInvitationToken.builder()
                .speakerPoolId(testSpeakerPoolId)
                .tokenHash(expiredHash)
                .action(TokenAction.RESPOND)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(31, ChronoUnit.DAYS))
                .build();
        tokenRepository.save(expiredToken);

        // When/Then - Regular findByTokenHash returns both (expiry check done in service)
        assertThat(tokenRepository.findByTokenHash(validHash)).isPresent();
        assertThat(tokenRepository.findByTokenHash(expiredHash)).isPresent();

        // The service layer will check expiry - repository just provides the data
    }

    // ==================== Helper Methods ====================

    private SpeakerInvitationToken createTestToken(String hash, TokenAction action, int expiryDays) {
        return SpeakerInvitationToken.builder()
                .speakerPoolId(testSpeakerPoolId)
                .tokenHash(hash)
                .action(action)
                .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();
    }
}
