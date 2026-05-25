package ch.batbern.partners.repository;

import ch.batbern.partners.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Native-query repository for the PCS test-fixture cleanup endpoint.
 *
 * <p>Bypasses the regular {@code PartnerRepository} so that:
 * <ul>
 *   <li>The cleanup-specific {@code @PreAuthorize}-gated controller is the only entry point;
 *       these deletion methods aren't reachable from normal application code paths.</li>
 *   <li>The DELETE statement uses {@code LIKE :pattern} for predictable, set-based deletion
 *       with one SQL round-trip — JPA cascade isn't relied on for performance.</li>
 *   <li>Cascade-delete via FK constraints (declared in V2 + V7) handles all partner-bound
 *       dependent rows: partner_meeting_attendance, partner_meeting_rsvps, partner_notes,
 *       topic_suggestions (and transitively topic_votes via topic_id → topic_suggestions
 *       ON DELETE CASCADE in V4).</li>
 * </ul>
 *
 * <p>Note: {@code partner_meetings} is a standalone table not linked to a specific partner
 * via FK — meetings are top-level events. Bruno tests creating ad-hoc partner meetings
 * leave them in place after this cleanup; that cleanup belongs to a separate entityType
 * or to the partner-meetings-api collection audit, deferred to a follow-up.
 *
 * <p>Bound to {@code Partner} as the parameterized type purely so Spring Data picks it up as a
 * repository bean; none of the methods here use the JpaRepository<Partner, UUID> interface.
 */
@Repository
public interface TestFixtureCleanupRepository extends JpaRepository<Partner, UUID> {

    /**
     * Delete partners whose {@code company_name} starts with the prefix.
     * Cascades through partner_meeting_attendance, partner_meeting_rsvps, partner_notes,
     * topic_suggestions (and transitively topic_votes) via FK ON DELETE CASCADE.
     *
     * @param companyNamePattern {@code LIKE} pattern for the company_name column
     * @return number of partner rows deleted (cascade dependents not counted)
     */
    @Modifying
    @Query(
            value = "DELETE FROM partners WHERE company_name LIKE :companyNamePattern",
            nativeQuery = true
    )
    int deletePartnersByCompanyNameLike(@Param("companyNamePattern") String companyNamePattern);
}
