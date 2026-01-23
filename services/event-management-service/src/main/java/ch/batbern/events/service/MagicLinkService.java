package ch.batbern.events.service;

import ch.batbern.events.domain.SpeakerInvitationToken;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.TokenAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for magic link token generation and validation.
 * Story 6.1a: Magic Link Infrastructure
 *
 * Provides passwordless authentication for the speaker portal:
 * - Generates secure tokens (32-byte random, base64url encoded)
 * - Validates tokens (hash lookup, expiry check, single-use enforcement)
 * - Returns speaker context for valid tokens
 *
 * SECURITY:
 * - Only token hash (SHA-256) is stored in database
 * - Plaintext token is returned once during generation
 * - Token never appears in logs (security requirement AC6)
 */
@Service
public class MagicLinkService {

    private static final Logger LOG = LoggerFactory.getLogger(MagicLinkService.class);
    private static final int TOKEN_LENGTH_BYTES = 32;
    private static final long DEFAULT_EXPIRY_DAYS = 30;

    private final SpeakerInvitationTokenRepository tokenRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final SecureRandom secureRandom;

    public MagicLinkService(
            SpeakerInvitationTokenRepository tokenRepository,
            SpeakerPoolRepository speakerPoolRepository) {
        this.tokenRepository = tokenRepository;
        this.speakerPoolRepository = speakerPoolRepository;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generate a new magic link token for a speaker.
     * AC1: Token generation
     *
     * @param speakerPoolId the speaker pool entry to link to
     * @param action the token action type (RESPOND, SUBMIT, VIEW)
     * @return the plaintext token (base64url encoded, URL-safe)
     */
    @Transactional
    public String generateToken(UUID speakerPoolId, TokenAction action) {
        return generateToken(speakerPoolId, action, DEFAULT_EXPIRY_DAYS);
    }

    /**
     * Generate a new magic link token with custom expiry.
     *
     * @param speakerPoolId the speaker pool entry to link to
     * @param action the token action type
     * @param expiryDays days until token expires
     * @return the plaintext token (base64url encoded, URL-safe)
     */
    @Transactional
    public String generateToken(UUID speakerPoolId, TokenAction action, long expiryDays) {
        // AC6: Generate cryptographically secure random bytes
        byte[] randomBytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(randomBytes);

        // AC1: Encode as base64url (URL-safe, no padding)
        String plaintextToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // AC1, AC6: Hash for storage (SHA-256, never store plaintext)
        String tokenHash = sha256(plaintextToken);

        // AC1: Create and persist token entity
        SpeakerInvitationToken token = SpeakerInvitationToken.builder()
                .speakerPoolId(speakerPoolId)
                .tokenHash(tokenHash)
                .action(action)
                .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();

        tokenRepository.save(token);

        // Log token generation (but NEVER the plaintext - AC6)
        LOG.info("Generated {} token for speaker pool: {}", action, speakerPoolId);

        return plaintextToken;
    }

    /**
     * Validate a magic link token without consuming it.
     * AC2: Token validation
     *
     * @param plaintextToken the base64url-encoded token from the magic link
     * @return validation result with speaker context if valid
     */
    @Transactional(readOnly = true)
    public TokenValidationResult validateToken(String plaintextToken) {
        // AC2: Hash the provided token and look up
        String tokenHash = sha256(plaintextToken);
        Optional<SpeakerInvitationToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            // AC2: NOT_FOUND error
            LOG.warn("Token validation failed: token not found (hash lookup failed)");
            return TokenValidationResult.notFound();
        }

        SpeakerInvitationToken token = tokenOpt.get();

        // AC4: Check expiry
        if (token.isExpired()) {
            LOG.warn("Token validation failed: token expired for speaker pool {}",
                    token.getSpeakerPoolId());
            return TokenValidationResult.expired();
        }

        // AC3: Check single-use for RESPOND tokens
        if (token.getAction() == TokenAction.RESPOND && token.isUsed()) {
            LOG.warn("Token validation failed: RESPOND token already used for speaker pool {}",
                    token.getSpeakerPoolId());
            return TokenValidationResult.alreadyUsed();
        }

        // AC2: Get speaker context
        Optional<SpeakerPool> speakerPoolOpt = speakerPoolRepository.findById(token.getSpeakerPoolId());
        if (speakerPoolOpt.isEmpty()) {
            LOG.error("Token validation failed: speaker pool {} not found",
                    token.getSpeakerPoolId());
            return TokenValidationResult.notFound();
        }

        SpeakerPool speakerPool = speakerPoolOpt.get();

        // AC2: Return valid result with context
        LOG.info("Token validated successfully for speaker pool: {}", token.getSpeakerPoolId());
        return TokenValidationResult.valid(
                token.getSpeakerPoolId(),
                speakerPool.getUsername(),
                null, // eventCode would require Event lookup, handled by controller
                token.getAction()
        );
    }

    /**
     * Validate and consume a single-use token (RESPOND action).
     * AC3: Single-use enforcement
     *
     * @param plaintextToken the base64url-encoded token
     * @return validation result with speaker context if valid
     */
    @Transactional
    public TokenValidationResult validateAndConsumeToken(String plaintextToken) {
        // First validate
        TokenValidationResult result = validateToken(plaintextToken);
        if (!result.valid()) {
            return result;
        }

        // AC3: Mark RESPOND tokens as used
        String tokenHash = sha256(plaintextToken);
        Optional<SpeakerInvitationToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isPresent()) {
            SpeakerInvitationToken token = tokenOpt.get();
            if (token.getAction() == TokenAction.RESPOND) {
                token.markAsUsed();
                tokenRepository.save(token);
                LOG.info("RESPOND token consumed for speaker pool: {}", token.getSpeakerPoolId());
            }
        }

        return result;
    }

    /**
     * Mark a token as used without re-validating.
     * Story 6.2a: Used after processing ACCEPT/DECLINE responses.
     *
     * @param plaintextToken the base64url-encoded token
     */
    @Transactional
    public void markTokenAsUsed(String plaintextToken) {
        String tokenHash = sha256(plaintextToken);
        Optional<SpeakerInvitationToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isPresent()) {
            SpeakerInvitationToken token = tokenOpt.get();
            if (!token.isUsed()) {
                token.markAsUsed();
                tokenRepository.save(token);
                LOG.info("Token marked as used for speaker pool: {}", token.getSpeakerPoolId());
            }
        }
    }

    /**
     * Compute SHA-256 hash of the token.
     *
     * @param input the plaintext token
     * @return lowercase hex string (64 characters)
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in Java
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
