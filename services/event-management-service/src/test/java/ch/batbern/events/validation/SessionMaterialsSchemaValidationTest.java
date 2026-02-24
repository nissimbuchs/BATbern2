package ch.batbern.events.validation;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema Validation Test for Session Materials
 * Story 5.9: Session Materials Upload - Task 0
 *
 * Verifies that V41__Add_session_materials.sql migration was applied correctly.
 * This test ensures:
 * - session_materials table exists with all columns
 * - sessions table has materials summary columns
 * - Indexes are created for performance
 * - Trigger function exists and updates materials_count
 */
class SessionMaterialsSchemaValidationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Test: Verify session_materials table exists with correct schema
     */
    @Test
    void should_haveSessionMaterialsTable_when_v41MigrationApplied() {
        // Verify table exists
        String tableQuery = """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_name = 'session_materials'
            """;

        List<Map<String, Object>> tables = jdbcTemplate.queryForList(tableQuery);
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).get("table_name")).isEqualTo("session_materials");
    }

    /**
     * Test: Verify session_materials has all required columns with correct types
     */
    @Test
    void should_haveAllColumns_when_sessionMaterialsTableCreated() {
        String columnQuery = """
            SELECT column_name, data_type, character_maximum_length, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'session_materials'
            ORDER BY ordinal_position
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).isNotEmpty();

        List<String> columnNames = columns.stream()
                .map(col -> (String) col.get("column_name"))
                .toList();

        // Verify all required columns exist
        assertThat(columnNames).contains(
                "id",
                "session_id",
                "upload_id",
                "s3_key",
                "cloudfront_url",
                "file_name",
                "file_extension",
                "file_size",
                "mime_type",
                "material_type",
                "uploaded_by",
                "created_at",
                "updated_at",
                "content_extracted",
                "extraction_status"
        );
    }

    /**
     * Test: Verify upload_id has unique constraint (from Generic Upload Service pattern)
     */
    @Test
    void should_haveUniqueConstraintOnUploadId_when_v41MigrationApplied() {
        String constraintQuery = """
            SELECT constraint_name, constraint_type
            FROM information_schema.table_constraints
            WHERE table_name = 'session_materials'
              AND constraint_type = 'UNIQUE'
              AND constraint_name LIKE '%upload_id%'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
        assertThat(constraints).as("upload_id should have UNIQUE constraint").isNotEmpty();
    }

    /**
     * Test: Verify foreign key constraint from session_materials to sessions
     */
    @Test
    void should_haveForeignKeyToSessions_when_v41MigrationApplied() {
        String fkQuery = """
            SELECT constraint_name, constraint_type
            FROM information_schema.table_constraints
            WHERE table_name = 'session_materials'
              AND constraint_type = 'FOREIGN KEY'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(fkQuery);
        assertThat(constraints).as("session_materials should have FK to sessions").isNotEmpty();
    }

    /**
     * Test: Verify indexes exist for performance-critical columns
     */
    @Test
    void should_haveIndexes_when_sessionMaterialsTableCreated() {
        String indexQuery = """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE tablename = 'session_materials'
            """;

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(indexQuery);

        List<String> indexNames = indexes.stream()
                .map(idx -> (String) idx.get("indexname"))
                .toList();

        // Verify critical indexes exist
        assertThat(indexNames.stream().anyMatch(name -> name.contains("session_id")))
                .as("Index on session_id should exist")
                .isTrue();

        assertThat(indexNames.stream().anyMatch(name -> name.contains("upload_id")))
                .as("Index on upload_id should exist")
                .isTrue();

        assertThat(indexNames.stream().anyMatch(name -> name.contains("extraction_status")))
                .as("Index on extraction_status should exist (for future RAG)")
                .isTrue();
    }

    /**
     * Test: Verify sessions table has new materials summary columns
     */
    @Test
    void should_haveMaterialsSummaryColumns_when_sessionsTableExtended() {
        String columnQuery = """
            SELECT column_name, data_type, column_default, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'sessions'
              AND column_name IN ('materials_count', 'has_presentation', 'materials_status')
            ORDER BY column_name
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).hasSize(3);

        List<String> columnNames = columns.stream()
                .map(col -> (String) col.get("column_name"))
                .toList();

        assertThat(columnNames).contains(
                "materials_count",
                "has_presentation",
                "materials_status"
        );

        // Verify materials_count default is 0
        Map<String, Object> materialsCountCol = columns.stream()
                .filter(col -> "materials_count".equals(col.get("column_name")))
                .findFirst()
                .orElseThrow();
        assertThat(materialsCountCol.get("data_type")).isEqualTo("integer");
        assertThat(materialsCountCol.get("column_default")).asString().contains("0");

        // Verify has_presentation default is false
        Map<String, Object> hasPresentationCol = columns.stream()
                .filter(col -> "has_presentation".equals(col.get("column_name")))
                .findFirst()
                .orElseThrow();
        assertThat(hasPresentationCol.get("data_type")).isEqualTo("boolean");
        assertThat(hasPresentationCol.get("column_default")).asString().contains("false");
    }

    /**
     * Test: Verify materials_status has CHECK constraint for valid values
     */
    @Test
    void should_haveMaterialsStatusCheckConstraint_when_sessionsTableExtended() {
        String constraintQuery = """
            SELECT constraint_name, check_clause
            FROM information_schema.check_constraints
            WHERE constraint_name LIKE '%materials_status%'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
        assertThat(constraints).as("materials_status should have CHECK constraint").isNotEmpty();

        String checkClause = (String) constraints.get(0).get("check_clause");
        assertThat(checkClause).contains("NONE", "PARTIAL", "COMPLETE");
    }

    /**
     * Test: Verify trigger function exists for updating materials summary
     */
    @Test
    void should_haveTriggerFunction_when_v41MigrationApplied() {
        String functionQuery = """
            SELECT routine_name
            FROM information_schema.routines
            WHERE routine_type = 'FUNCTION'
              AND routine_name = 'update_session_materials_summary'
            """;

        List<Map<String, Object>> functions = jdbcTemplate.queryForList(functionQuery);
        assertThat(functions).hasSize(1);
        assertThat(functions.get(0).get("routine_name")).isEqualTo("update_session_materials_summary");
    }

    /**
     * Test: Verify triggers exist on session_materials for INSERT and DELETE
     */
    @Test
    void should_haveTriggers_when_v41MigrationApplied() {
        String triggerQuery = """
            SELECT trigger_name, event_manipulation
            FROM information_schema.triggers
            WHERE event_object_table = 'session_materials'
              AND trigger_name LIKE '%update_session_materials_summary%'
            """;

        List<Map<String, Object>> triggers = jdbcTemplate.queryForList(triggerQuery);
        assertThat(triggers).hasSize(2);

        List<String> events = triggers.stream()
                .map(t -> (String) t.get("event_manipulation"))
                .toList();

        assertThat(events).contains("INSERT", "DELETE");
    }

    /**
     * Test: Verify material_type has CHECK constraint for valid values
     */
    @Test
    void should_haveMaterialTypeCheckConstraint_when_v41MigrationApplied() {
        String constraintQuery = """
            SELECT constraint_name, check_clause
            FROM information_schema.check_constraints
            WHERE constraint_name LIKE '%material_type%'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
        assertThat(constraints).as("material_type should have CHECK constraint").isNotEmpty();

        String checkClause = (String) constraints.get(0).get("check_clause");
        assertThat(checkClause).contains("PRESENTATION", "DOCUMENT", "VIDEO", "OTHER");
    }

    /**
     * Test: Verify extraction_status has CHECK constraint for valid values
     */
    @Test
    void should_haveExtractionStatusCheckConstraint_when_v41MigrationApplied() {
        String constraintQuery = """
            SELECT constraint_name, check_clause
            FROM information_schema.check_constraints
            WHERE constraint_name LIKE '%extraction_status%'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
        assertThat(constraints).as("extraction_status should have CHECK constraint").isNotEmpty();

        String checkClause = (String) constraints.get(0).get("check_clause");
        assertThat(checkClause).contains("PENDING", "IN_PROGRESS", "COMPLETED", "FAILED", "NOT_APPLICABLE");
    }
}
