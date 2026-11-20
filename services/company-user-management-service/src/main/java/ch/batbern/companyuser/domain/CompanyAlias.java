package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An old key and display name of a company, kept after a merge or a rename so that search, the
 * typeahead and get-or-create still find the company (#1251). Points at the company by its CUMS id
 * (same service, ADR-003); the database removes it with the company.
 */
@Entity
@Table(name = "company_aliases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyAlias {

    /** Why the alias exists. */
    public enum Reason {
        MERGE,
        RENAME
    }

    @Id
    @Column(name = "alias_key", length = 12, nullable = false, updatable = false)
    private String aliasKey;

    @Column(name = "alias_display_name", length = 255)
    private String aliasDisplayName;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 10, nullable = false)
    private Reason reason;

    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
