package ch.batbern.events.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for Flyway migration V93__migrate_legacy_speaker_states.sql.
 *
 * <p>Story 11.B.3 AC13: rehearse the migration against a Testcontainers-loaded copy of
 * the speaker_pool schema. Seed legacy state values (slot_assigned, confirmed, withdrew,
 * overflow) under the V44/V45 constraint shape, run V93, then assert that the mapping,
 * audit-row creation, constraint-tighten, and column-drop all behave as specified.
 *
 * <p>Uses its OWN PostgreSQL container (not the shared singleton from
 * {@code AbstractIntegrationTest}) because the test needs to control the migration target
 * version: stop at V92 to seed legacy data under the V44 constraint, then apply V93
 * separately. The shared singleton would have already applied V93 by the time this test
 * class loads.
 *
 * <p>Test layout:
 * <ol>
 *   <li>{@link #setUp()} starts a fresh container, migrates to V92, seeds 5 speaker_pool
 *       rows (one per legacy status + one already at 'accepted' to verify idempotency).</li>
 *   <li>Each test runs V93 and asserts a specific behaviour.</li>
 *   <li>{@link #tearDown()} stops the container.</li>
 * </ol>
 *
 * <p>Note: {@code @TestInstance(PER_CLASS)} keeps the container alive for the whole class
 * (saves ~5 seconds of container startup per test).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("V93 Legacy Speaker States Migration Tests")
class V93LegacySpeakerStatesMigrationIntegrationTest {

    private PostgreSQLContainer<?> postgres;
    private JdbcTemplate jdbcTemplate;
    private DriverManagerDataSource dataSource;

    // Test event + speakers (5 rows: 4 legacy + 1 already-mapped 'accepted' for idempotency).
    private UUID eventId;
    private UUID slotAssignedSpeakerId;
    private UUID confirmedSpeakerId;
    private UUID withdrewSpeakerId;
    private UUID overflowSpeakerId;
    private UUID alreadyAcceptedSpeakerId;

    @BeforeAll
    void startContainer() {
        @SuppressWarnings("resource")  // Container lifecycle managed in @AfterAll.
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v93testdb")
                .withUsername("v93test")
                .withPassword("v93test");
        container.start();
        this.postgres = container;
        this.dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterAll
    void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // Reset the schema between tests so each one runs against the same baseline.
        // Drop every object Flyway might have created in the public schema.
        jdbcTemplate.execute("DROP SCHEMA public CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA public");

        // Migrate to V92 only — V93 runs per-test so we can assert on its side-effects.
        Flyway flywayToV92 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_schema_history_event_management")
                .target("92")
                .load();
        flywayToV92.migrate();

        // Seed one event + 5 speakers via raw JDBC (bypassing JPA + the entity's @Convert
        // annotation, which would reject the legacy enum values at the Java layer).
        eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO events (id, event_code, event_number, title, description, "
                        + "event_date, registration_deadline, venue_name, venue_address, venue_capacity, "
                        + "organizer_username, event_type, workflow_state, created_at, updated_at) "
                        + "VALUES (?, 'BATbernV93', 9993, 'V93 Test Event', 'Test', NOW() + interval '30 days', "
                        + "NOW() + interval '20 days', 'Test Venue', 'Test Address', 100, "
                        + "'organizer.v93', 'evening', 'created', NOW(), NOW())",
                eventId);

        slotAssignedSpeakerId = insertLegacySpeaker("Slot Assigned Speaker", "slot_assigned", null);
        confirmedSpeakerId = insertLegacySpeaker("Confirmed Speaker", "confirmed", null);
        withdrewSpeakerId = insertLegacySpeaker("Withdrew Speaker", "withdrew", null);
        overflowSpeakerId = insertLegacySpeaker("Overflow Speaker", "overflow", null);
        // Already-mapped row — V93 must NOT touch it.
        alreadyAcceptedSpeakerId = insertLegacySpeaker("Already Accepted", "accepted", null);
    }

    private UUID insertLegacySpeaker(String name, String legacyStatus, String declineReason) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO speaker_pool (id, event_id, speaker_name, status, decline_reason, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                id, eventId, name, legacyStatus, declineReason);
        return id;
    }

    private void runV93() {
        Flyway flywayV93 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_schema_history_event_management")
                .target("93")
                .load();
        flywayV93.migrate();
    }

    // ========================================================================
    // AC1: legacy → new mapping correctness
    // ========================================================================

    @Test
    @DisplayName("should_mapSlotAssignedToAccepted_and_writeAuditRow")
    void should_mapSlotAssignedToAccepted_and_writeAuditRow() {
        runV93();

        String newStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM speaker_pool WHERE id = ?", String.class, slotAssignedSpeakerId);
        assertThat(newStatus).isEqualTo("accepted");

        // Audit row must exist with legacy previous_status (grandfathered under the tightened constraint).
        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                "SELECT previous_status, new_status, changed_by_username, change_reason "
                        + "FROM speaker_status_history WHERE speaker_pool_id = ?",
                slotAssignedSpeakerId);
        assertThat(auditRow).containsEntry("previous_status", "slot_assigned");
        assertThat(auditRow).containsEntry("new_status", "accepted");
        assertThat(auditRow).containsEntry("changed_by_username", "system-migration-v93");
        assertThat((String) auditRow.get("change_reason"))
                .contains("SLOT_ASSIGNED → ACCEPTED")
                .contains("ADR-009 §0.7");
    }

    @Test
    @DisplayName("should_mapConfirmedToQualityReviewed_and_writeAuditRow")
    void should_mapConfirmedToQualityReviewed_and_writeAuditRow() {
        runV93();

        String newStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM speaker_pool WHERE id = ?", String.class, confirmedSpeakerId);
        assertThat(newStatus).isEqualTo("quality_reviewed");

        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                "SELECT previous_status, new_status, change_reason "
                        + "FROM speaker_status_history WHERE speaker_pool_id = ?",
                confirmedSpeakerId);
        assertThat(auditRow).containsEntry("previous_status", "confirmed");
        assertThat(auditRow).containsEntry("new_status", "quality_reviewed");
        assertThat((String) auditRow.get("change_reason"))
                .contains("CONFIRMED → QUALITY_REVIEWED");
    }

    @Test
    @DisplayName("should_mapWithdrewToDeclined_and_backfillDeclineReason")
    void should_mapWithdrewToDeclined_and_backfillDeclineReason() {
        runV93();

        Map<String, Object> speakerRow = jdbcTemplate.queryForMap(
                "SELECT status, decline_reason FROM speaker_pool WHERE id = ?", withdrewSpeakerId);
        assertThat(speakerRow).containsEntry("status", "declined");
        // decline_reason was null at seed time — V93 must backfill the standard literal.
        assertThat(speakerRow).containsEntry("decline_reason", "Withdrew after acceptance (legacy)");

        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                "SELECT previous_status, new_status, change_reason "
                        + "FROM speaker_status_history WHERE speaker_pool_id = ?",
                withdrewSpeakerId);
        assertThat(auditRow).containsEntry("previous_status", "withdrew");
        assertThat(auditRow).containsEntry("new_status", "declined");
        assertThat((String) auditRow.get("change_reason"))
                .isEqualTo("Withdrew after acceptance (legacy)");
    }

    @Test
    @DisplayName("should_preserveExistingDeclineReason_when_mappingWithdrewToDeclined")
    void should_preserveExistingDeclineReason_when_mappingWithdrewToDeclined() {
        // Re-seed the withdrew speaker with a pre-existing decline_reason.
        jdbcTemplate.update(
                "UPDATE speaker_pool SET decline_reason = 'Original reason from organizer' WHERE id = ?",
                withdrewSpeakerId);

        runV93();

        String preserved = jdbcTemplate.queryForObject(
                "SELECT decline_reason FROM speaker_pool WHERE id = ?", String.class, withdrewSpeakerId);
        // COALESCE keeps the original — the backfill literal is the fallback only.
        assertThat(preserved).isEqualTo("Original reason from organizer");
    }

    @Test
    @DisplayName("should_mapOverflowToReady_and_writeAuditRow")
    void should_mapOverflowToReady_and_writeAuditRow() {
        runV93();

        String newStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM speaker_pool WHERE id = ?", String.class, overflowSpeakerId);
        assertThat(newStatus).isEqualTo("ready");

        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                "SELECT previous_status, new_status, change_reason "
                        + "FROM speaker_status_history WHERE speaker_pool_id = ?",
                overflowSpeakerId);
        assertThat(auditRow).containsEntry("previous_status", "overflow");
        assertThat(auditRow).containsEntry("new_status", "ready");
        assertThat((String) auditRow.get("change_reason"))
                .contains("OVERFLOW → READY")
                .contains("slot-capacity gate");
    }

    // ========================================================================
    // AC1: idempotency — re-running V93 against an already-migrated DB is a no-op
    // ========================================================================

    @Test
    @DisplayName("should_notTouchAlreadyAcceptedRow_when_migrationRuns")
    void should_notTouchAlreadyAcceptedRow_when_migrationRuns() {
        Instant originalUpdatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM speaker_pool WHERE id = ?", Instant.class, alreadyAcceptedSpeakerId);

        runV93();

        String statusAfter = jdbcTemplate.queryForObject(
                "SELECT status FROM speaker_pool WHERE id = ?", String.class, alreadyAcceptedSpeakerId);
        Instant updatedAtAfter = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM speaker_pool WHERE id = ?", Instant.class, alreadyAcceptedSpeakerId);

        assertThat(statusAfter).isEqualTo("accepted");
        // updated_at should be unchanged (V93's UPDATE statements are scoped to legacy values only).
        assertThat(updatedAtAfter).isEqualTo(originalUpdatedAt);

        // And no audit row should have been written for it.
        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM speaker_status_history WHERE speaker_pool_id = ?",
                Long.class, alreadyAcceptedSpeakerId);
        assertThat(auditCount).isZero();
    }

    @Test
    @DisplayName("should_beNoOp_when_migrationRunsTwice")
    void should_beNoOp_when_migrationRunsTwice() {
        runV93();
        Long auditCountAfterFirstRun = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM speaker_status_history", Long.class);

        // Flyway will refuse to re-apply V93 (already in the history table). The "idempotency"
        // we're proving here is at the SQL level: the audit-insert and UPDATE statements are
        // scoped via WHERE status IN (legacy values), so if they did re-run they would be no-ops.
        // To prove this, execute V93's INSERT and UPDATE bodies directly against the migrated
        // schema and confirm zero rows are touched.

        int slotInserts = jdbcTemplate.update(
                "INSERT INTO speaker_status_history (id, speaker_pool_id, event_id, session_id, "
                        + "previous_status, new_status, changed_by_username, change_reason, changed_at) "
                        + "SELECT uuid_generate_v4(), sp.id, sp.event_id, sp.session_id, "
                        + "'slot_assigned', 'accepted', 'system-migration-v93', 'replay test', NOW() "
                        + "FROM speaker_pool sp WHERE sp.status = 'slot_assigned'");
        int updateCount = jdbcTemplate.update(
                "UPDATE speaker_pool SET status = 'accepted' WHERE status = 'slot_assigned'");

        assertThat(slotInserts).isZero();
        assertThat(updateCount).isZero();

        Long auditCountAfterReplay = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM speaker_status_history", Long.class);
        assertThat(auditCountAfterReplay).isEqualTo(auditCountAfterFirstRun);
    }

    // ========================================================================
    // AC2: tightened CHECK constraints reject legacy values
    // ========================================================================

    @Test
    @DisplayName("should_rejectLegacyStatusInsert_when_constraintsTightened")
    void should_rejectLegacyStatusInsert_when_constraintsTightened() {
        runV93();

        // Direct INSERT of a legacy value must be rejected by the new CHECK constraint.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO speaker_pool (id, event_id, speaker_name, status, created_at, updated_at) "
                                + "VALUES (?, ?, 'Late-arriving legacy', 'slot_assigned', NOW(), NOW())",
                        UUID.randomUUID(), eventId)
        ).hasMessageContaining("speaker_pool_status_check");
    }

    @Test
    @DisplayName("should_rejectLegacyStatusUpdate_when_constraintsTightened")
    void should_rejectLegacyStatusUpdate_when_constraintsTightened() {
        runV93();

        // Attempt to UPDATE an existing row to a legacy value must also be rejected.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE speaker_pool SET status = 'overflow' WHERE id = ?",
                        slotAssignedSpeakerId)
        ).hasMessageContaining("speaker_pool_status_check");
    }

    @Test
    @DisplayName("should_grandfatherLegacyAuditRows_when_previousStatusConstraintTightened")
    void should_grandfatherLegacyAuditRows_when_previousStatusConstraintTightened() {
        runV93();

        // Audit rows inserted in Step 1 of V93 have legacy previous_status values ('withdrew',
        // 'slot_assigned', etc.) and must survive the constraint tighten in Step 4.
        List<String> grandfatheredPreviousStatuses = jdbcTemplate.queryForList(
                "SELECT previous_status FROM speaker_status_history "
                        + "WHERE changed_by_username = 'system-migration-v93'",
                String.class);
        assertThat(grandfatheredPreviousStatuses)
                .containsExactlyInAnyOrder("slot_assigned", "confirmed", "withdrew", "overflow");
    }

    @Test
    @DisplayName("should_rejectFutureLegacyAuditInsert_when_constraintsTightened")
    void should_rejectFutureLegacyAuditInsert_when_constraintsTightened() {
        runV93();

        // A new INSERT into speaker_status_history that uses a legacy previous_status must fail.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO speaker_status_history (id, speaker_pool_id, event_id, "
                                + "previous_status, new_status, changed_by_username, change_reason, changed_at) "
                                + "VALUES (?, ?, ?, 'slot_assigned', 'accepted', 'post-v93-user', 'should fail', NOW())",
                        UUID.randomUUID(), slotAssignedSpeakerId, eventId)
        ).hasMessageContaining("speaker_status_history_previous_status_check");
    }

    @Test
    @DisplayName("should_rejectFutureLegacyNewStatusInsert_when_newStatusConstraintTightened")
    void should_rejectFutureLegacyNewStatusInsert_when_newStatusConstraintTightened() {
        runV93();

        // A new INSERT into speaker_status_history that uses a legacy new_status must also fail.
        // This verifies the speaker_status_history_new_status_check constraint is also enforced.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO speaker_status_history (id, speaker_pool_id, event_id, "
                                + "previous_status, new_status, changed_by_username, change_reason, changed_at) "
                                + "VALUES (?, ?, ?, 'identified', 'slot_assigned', 'post-v93-user', 'should fail', NOW())",
                        UUID.randomUUID(), slotAssignedSpeakerId, eventId)
        ).hasMessageContaining("speaker_status_history_new_status_check");
    }

    // ========================================================================
    // AC3: column + index drops
    // ========================================================================

    @Test
    @DisplayName("should_dropIsTentativeColumn_when_migrationRuns")
    void should_dropIsTentativeColumn_when_migrationRuns() {
        runV93();

        Long col = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'speaker_pool' AND column_name = 'is_tentative'",
                Long.class);
        assertThat(col).isZero();
    }

    @Test
    @DisplayName("should_dropTentativeReasonColumn_when_migrationRuns")
    void should_dropTentativeReasonColumn_when_migrationRuns() {
        runV93();

        Long col = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'speaker_pool' AND column_name = 'tentative_reason'",
                Long.class);
        assertThat(col).isZero();
    }

    @Test
    @DisplayName("should_dropTentativeIndex_when_migrationRuns")
    void should_dropTentativeIndex_when_migrationRuns() {
        runV93();

        Long idx = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE tablename = 'speaker_pool' AND indexname = 'idx_speaker_pool_tentative'",
                Long.class);
        assertThat(idx).isZero();
    }

    // ========================================================================
    // AC1: every legacy speaker_pool row was mapped (no leftovers)
    // ========================================================================

    @Test
    @DisplayName("should_haveZeroLegacyStatusValues_when_migrationCompletes")
    void should_haveZeroLegacyStatusValues_when_migrationCompletes() {
        runV93();

        Long legacyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM speaker_pool "
                        + "WHERE status IN ('slot_assigned', 'confirmed', 'withdrew', 'overflow')",
                Long.class);
        assertThat(legacyCount).isZero();
    }

    @Test
    @DisplayName("should_haveOneAuditRowPerMappedSpeaker_when_migrationCompletes")
    void should_haveOneAuditRowPerMappedSpeaker_when_migrationCompletes() {
        runV93();

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM speaker_status_history "
                        + "WHERE changed_by_username = 'system-migration-v93'",
                Long.class);
        // 4 legacy speakers → 4 audit rows. The 'already accepted' row gets no audit row.
        assertThat(auditCount).isEqualTo(4L);
    }
}
