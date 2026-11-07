package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * #1246: a participant's request for a new logo of their company, decided by an organizer in the
 * Mail Center. See {@code V25__company_logo_requests.sql}.
 */
@Entity
@Table(name = "company_logo_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyLogoRequest {

    public enum Status { OPEN, APPROVED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requester_username", nullable = false, length = 100)
    private String requesterUsername;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "old_logo_url", length = 1000)
    private String oldLogoUrl;

    @Column(name = "new_logo_upload_id", nullable = false, length = 100)
    private String newLogoUploadId;

    @Column(name = "new_logo_url", nullable = false, length = 1000)
    private String newLogoUrl;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "reason", length = 1000)
    private String reason;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
