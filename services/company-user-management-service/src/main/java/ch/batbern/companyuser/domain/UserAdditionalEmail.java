package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Story 10.32: additional email address registered on a user profile.
 *
 * <p>Used by:
 * <ul>
 *   <li>SES email forwarder Lambda (Story 10.26) — additional emails count as
 *       authorised senders for {@code ok@}, {@code partner@}, {@code batbern{N}@}
 *       and receive forwarded copies on the role-based fan-out.</li>
 *   <li>Event registration confirmation emails — CC'd to additional emails
 *       when the registration belongs to a known user.</li>
 * </ul>
 *
 * <p>Ownership verification is intentionally not implemented in v1 — the
 * {@code verifiedAt} column is reserved for a future verification flow
 * (Story 10.32 Resolved Decision #1).
 */
@Entity
@Table(
    name = "user_additional_emails",
    indexes = {
        @Index(name = "idx_user_additional_emails_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
public class UserAdditionalEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Owning user. LAZY to avoid round-tripping the full aggregate every time
     * the Lambda fans out role-based recipient lists.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @Setter(AccessLevel.PACKAGE)
    private User user;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * Optional free-text user hint (e.g. "Hostpoint shared mailbox").
     */
    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Reserved for the v2 verification flow. v1 always writes {@code null}.
     */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
