package ch.batbern.events.service;

import ch.batbern.events.config.JwtConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerInvitationToken;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import ch.batbern.shared.utils.LoggingUtils;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for magic link token generation and validation.
 * Story 6.1a: Magic Link Infrastructure
 * Story 9.1: JWT Token Generation for speaker portal access
 *
 * Provides passwordless authentication for the speaker portal:
 * - Generates secure tokens (32-byte random, base64url encoded)
 * - Validates tokens (hash lookup, expiry check, single-use enforcement)
 * - Returns speaker context for valid tokens
 * - Generates long-lived JWT tokens for speaker portal access (Story 9.1)
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
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    // Workflow states indicating speaker has responded
    private static final Set<SpeakerWorkflowState> RESPONDED_STATES = Set.of(
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.DECLINED,
            SpeakerWorkflowState.CONTENT_SUBMITTED,
            SpeakerWorkflowState.QUALITY_REVIEWED,
            SpeakerWorkflowState.SLOT_ASSIGNED,
            SpeakerWorkflowState.CONFIRMED,
            SpeakerWorkflowState.WITHDREW
    );

    private final SpeakerInvitationTokenRepository tokenRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final JwtConfig jwtConfig;
    private final SecureRandom secureRandom;

    public MagicLinkService(
            SpeakerInvitationTokenRepository tokenRepository,
            SpeakerPoolRepository speakerPoolRepository,
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            JwtConfig jwtConfig) {
        this.tokenRepository = tokenRepository;
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.jwtConfig = jwtConfig;
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
     * Generate a JWT token for speaker portal access (Story 9.1).
     *
     * The JWT is signed with RS256 and contains the speaker's identity claims.
     * Unlike magic link tokens, JWTs are stateless and reusable for 30 days.
     *
     * @param speakerPoolId the speaker pool entry
     * @return signed RS256 JWT string (30-day expiry, reusable)
     */
    @Transactional(readOnly = true)
    public String generateJwtToken(UUID speakerPoolId) {
        SpeakerPool speakerPool = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new IllegalArgumentException("SpeakerPool not found: " + speakerPoolId));

        KeyPair keyPair = jwtConfig.getKeyPair();
        Instant now = Instant.now();

        String jwt = Jwts.builder()
                .subject(speakerPoolId.toString())
                .issuer(jwtConfig.getIssuer())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(jwtConfig.getExpiryDays(), ChronoUnit.DAYS)))
                .claim("email", speakerPool.getEmail())
                .claim("roles", List.of("SPEAKER"))
                .claim("speakerPoolId", speakerPoolId.toString())
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        String maskedEmail = LoggingUtils.maskEmail(
                speakerPool.getEmail() != null ? speakerPool.getEmail() : "unknown");
        LOG.info("Generated JWT token for speaker pool: {} (email: {})", speakerPoolId, maskedEmail);
        return jwt;
    }

    /**
     * Validate a magic link token without consuming it.
     * AC2: Token validation
     * Story 6.2a: Enhanced to return full speaker context for frontend display
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

        // Fetch Event details for display
        String eventCode = null;
        String eventTitle = null;
        String eventDate = null;
        Optional<Event> eventOpt = eventRepository.findById(speakerPool.getEventId());
        if (eventOpt.isPresent()) {
            Event event = eventOpt.get();
            eventCode = event.getEventCode();
            eventTitle = event.getTitle();
            if (event.getDate() != null) {
                eventDate = event.getDate().atZone(SWISS_ZONE).format(DATE_FORMATTER);
            }
        }

        // Fetch Session title if speaker is assigned to a session
        String sessionTitle = null;
        if (speakerPool.getSessionId() != null) {
            Optional<Session> sessionOpt = sessionRepository.findById(speakerPool.getSessionId());
            if (sessionOpt.isPresent()) {
                sessionTitle = sessionOpt.get().getTitle();
            }
        }

        // Format response deadline
        String responseDeadline = null;
        if (speakerPool.getResponseDeadline() != null) {
            responseDeadline = speakerPool.getResponseDeadline().format(DATE_FORMATTER);
        }

        // Determine if already responded and get previous response details
        boolean alreadyResponded = RESPONDED_STATES.contains(speakerPool.getStatus());
        String previousResponse = null;
        Instant previousResponseDate = null;

        if (alreadyResponded) {
            if (speakerPool.getAcceptedAt() != null) {
                previousResponse = "ACCEPTED";
                previousResponseDate = speakerPool.getAcceptedAt();
            } else if (speakerPool.getDeclinedAt() != null) {
                previousResponse = "DECLINED";
                previousResponseDate = speakerPool.getDeclinedAt();
            } else if (Boolean.TRUE.equals(speakerPool.getIsTentative())) {
                previousResponse = "TENTATIVE";
                // No specific timestamp for tentative, use updated timestamp
                previousResponseDate = speakerPool.getUpdatedAt();
            }
        }

        // AC2: Return valid result with full context
        LOG.info("Token validated successfully for speaker pool: {}", token.getSpeakerPoolId());
        return TokenValidationResult.valid(
                token.getSpeakerPoolId(),
                speakerPool.getUsername(),
                speakerPool.getSpeakerName(),
                eventCode,
                eventTitle,
                eventDate,
                sessionTitle,
                responseDeadline,
                null, // invitationMessage - could be added to SpeakerPool if needed
                alreadyResponded,
                previousResponse,
                previousResponseDate,
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
