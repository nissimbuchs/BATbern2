package ch.batbern.events.domain;

import ch.batbern.events.converter.TokenActionConverter;
import ch.batbern.shared.types.TokenAction;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Speaker Invitation Token entity for magic link authentication.
 * Story 6.1a: Magic Link Infrastructure (AC1-AC6)
 *
 * Represents a secure token for passwordless speaker portal access.
 * Tokens are:
 * - Single-use for RESPOND action (accept/decline invitation)
 * - Reusable for SUBMIT and VIEW actions
 * - Time-limited (default 30 days)
 *
 * SECURITY: Only the SHA-256 hash is stored, never the plaintext token.
 * The plaintext token is returned to the caller once during generation,
 * then only exists in the magic link URL sent to the speaker.
 *
 * @see ch.batbern.events.service.MagicLinkService
 */
@Entity
@Table(name = "speaker_invitation_tokens")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerInvitationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Reference to the speaker pool entry this token authenticates.
     * The speaker pool contains username, eventCode, and other context.
     */
    @Column(name = "speaker_pool_id", nullable = false, columnDefinition = "UUID")
    private UUID speakerPoolId;

    /**
     * SHA-256 hash of the token (64 hex characters).
     * SECURITY: Plaintext token is NEVER stored in the database.
     * Only the hash is stored for validation.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * Token action type determining behavior and access scope.
     * - RESPOND: Single-use for accept/decline
     * - SUBMIT: Reusable for content submission
     * - VIEW: Reusable for dashboard access
     */
    @Column(name = "action", nullable = false, length = 20)
    @Convert(converter = TokenActionConverter.class)
    private TokenAction action;

    /**
     * Token expiration timestamp.
     * Tokens become invalid after this time.
     * Default: 30 days from creation (set by MagicLinkService).
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Timestamp when token was used (for single-use tokens).
     * NULL if token has not been used yet.
     * Set when RESPOND token is consumed (accept/decline).
     */
    @Column(name = "used_at")
    private Instant usedAt;

    /**
     * Token creation timestamp.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Check if this token has been used (for single-use tokens).
     * @return true if used_at is set
     */
    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * Check if this token has expired.
     * @return true if current time is past expires_at
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this token is valid (not expired and not used for single-use tokens).
     * @return true if token can be used
     */
    public boolean isValid() {
        if (isExpired()) {
            return false;
        }
        // RESPOND tokens are single-use
        if (action == TokenAction.RESPOND && isUsed()) {
            return false;
        }
        return true;
    }

    /**
     * Mark this token as used (for single-use tokens).
     * Should only be called after successful validation.
     */
    public void markAsUsed() {
        this.usedAt = Instant.now();
    }
}
