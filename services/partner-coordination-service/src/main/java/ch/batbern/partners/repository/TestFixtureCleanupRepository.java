package ch.batbern.partners.repository;

import ch.batbern.partners.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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
 * via FK — meetings are top-level events, so {@link #deletePartnersByCompanyNameLike} does NOT
 * reach them. They are cleaned via {@link #deleteMeetingsByIdIn} using an explicit id allowlist
 * (PR 13 / plan §B2 option 1); {@code partner_meeting_attendance} and {@code partner_meeting_rsvps}
 * cascade-delete from {@code partner_meetings(id)} via ON DELETE CASCADE (V2 + V9).
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

    /**
     * Delete partner_meetings by an explicit id allowlist.
     * partner_meeting_attendance + partner_meeting_rsvps cascade-delete via FK ON DELETE CASCADE
     * (V2:152 + V9:9). Used by the {@code meetings} cleanup entityType — meetings carry no
     * Bruno-identifying prefix, so Bruno posts back the exact IDs it created.
     *
     * @param ids meeting UUIDs to delete (validated non-empty + bounded at the service layer)
     * @return number of partner_meetings rows deleted (cascade dependents not counted)
     */
    @Modifying
    @Query(
            value = "DELETE FROM partner_meetings WHERE id IN (:ids)",
            nativeQuery = true
    )
    int deleteMeetingsByIdIn(@Param("ids") List<UUID> ids);

    /**
     * Delete topic_suggestions whose {@code title} starts with the prefix.
     * topic_votes cascade-delete via {@code topic_id} FK ON DELETE CASCADE (V4) — no
     * explicit vote delete is needed.
     *
     * @param titlePattern {@code LIKE} pattern for the title column
     * @return number of topic_suggestions rows deleted (cascade votes not counted)
     */
    @Modifying
    @Query(
            value = "DELETE FROM topic_suggestions WHERE title LIKE :titlePattern",
            nativeQuery = true
    )
    int deleteTopicsByTitleLike(@Param("titlePattern") String titlePattern);
}
