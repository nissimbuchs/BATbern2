package ch.batbern.events.validation;

import ch.batbern.events.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema Validation Test for Speaker Content Submissions
 * Story 6.3: Speaker Content Self-Submission Portal
 *
 * Verifies that the speaker_content_submissions table and related schema changes
 * match the architecture specification in docs/stories/6.3-content-submission.md.
 *
 * Tests:
 * - Table existence and structure
 * - Column types and constraints
 * - Indexes for performance
 * - Foreign key relationships
 * - content_status field on speaker_pool
 */
class ContentSubmissionSchemaValidationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Test: Verify speaker_content_submissions table exists with all required columns
     */
    @Test
    void should_haveContentSubmissionsTable_when_migrationApplied() {
        String query = """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_name = 'speaker_content_submissions'
            ORDER BY ordinal_position
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(query);
        assertThat(columns).as("speaker_content_submissions table should exist").isNotEmpty();

        List<String> columnNames = columns.stream()
                .map(col -> (String) col.get("column_name"))
                .toList();

        // Verify all required columns exist
        assertThat(columnNames).contains(
                "id",
                "speaker_pool_id",
                "session_id",
                "title",
                "abstract",
                "abstract_char_count",
                "submission_version",
                "reviewer_feedback",
                "submitted_at",
                "reviewed_at",
                "reviewed_by",
                "created_at",
                "updated_at"
        );
    }

    /**
     * Test: Verify id column is UUID primary key
     */
    @Test
    void should_haveUuidPrimaryKey_when_tableCreated() {
        String pkQuery = """
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_name = 'speaker_content_submissions' AND column_name = 'id'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(pkQuery);
        assertThat(columns).hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("id");
        assertThat(column.get("data_type")).isEqualTo("uuid");

        // Verify it's the primary key
        String constraintQuery = """
            SELECT constraint_name, constraint_type
            FROM information_schema.table_constraints
            WHERE table_name = 'speaker_content_submissions'
              AND constraint_type = 'PRIMARY KEY'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
        assertThat(constraints).as("Should have PRIMARY KEY constraint").hasSize(1);
    }

    /**
     * Test: Verify title column has VARCHAR(200) constraint
     */
    @Test
    void should_haveTitleColumnWith200CharLimit_when_tableCreated() {
        String columnQuery = """
            SELECT column_name, data_type, character_maximum_length, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'speaker_content_submissions' AND column_name = 'title'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("title");
        assertThat(column.get("data_type")).isEqualTo("character varying");
        assertThat(column.get("character_maximum_length")).isEqualTo(200);
        assertThat(column.get("is_nullable")).isEqualTo("NO");
    }

    /**
     * Test: Verify abstract_char_count has CHECK constraint <= 1000
     */
    @Test
    void should_haveAbstractCharCountWithCheckConstraint_when_tableCreated() {
        String columnQuery = """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'speaker_content_submissions' AND column_name = 'abstract_char_count'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("abstract_char_count");
        assertThat(column.get("data_type")).isEqualTo("integer");
        assertThat(column.get("is_nullable")).isEqualTo("NO");

        // Verify CHECK constraint exists
        String constraintQuery = """
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE table_name = 'speaker_content_submissions'
              AND constraint_type = 'CHECK'
              AND constraint_name LIKE '%abstract%'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
        assertThat(constraints).as("Should have CHECK constraint for abstract_char_count").isNotEmpty();
    }

    /**
     * Test: Verify foreign key to speaker_pool exists
     */
    @Test
    void should_haveForeignKeyToSpeakerPool_when_tableCreated() {
        String fkQuery = """
            SELECT
                tc.constraint_name,
                ccu.table_name AS foreign_table_name,
                ccu.column_name AS foreign_column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu
                ON ccu.constraint_name = tc.constraint_name
            WHERE tc.table_name = 'speaker_content_submissions'
              AND tc.constraint_type = 'FOREIGN KEY'
              AND ccu.table_name = 'speaker_pool'
            """;

        List<Map<String, Object>> fks = jdbcTemplate.queryForList(fkQuery);
        assertThat(fks).as("Should have FOREIGN KEY to speaker_pool").isNotEmpty();
    }

    /**
     * Test: Verify indexes exist for speaker_pool_id and session_id
     */
    @Test
    void should_haveIndexesForLookupFields_when_tableCreated() {
        String indexQuery = """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE tablename = 'speaker_content_submissions'
              AND (indexname LIKE '%speaker_pool%' OR indexname LIKE '%session%' OR indexname LIKE '%version%')
            """;

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(indexQuery);
        assertThat(indexes).as("Should have indexes for lookup fields").hasSizeGreaterThanOrEqualTo(2);

        List<String> indexNames = indexes.stream()
                .map(idx -> (String) idx.get("indexname"))
                .toList();

        // Verify speaker_pool_id index exists
        assertThat(indexNames.stream().anyMatch(name -> name.contains("speaker_pool")))
                .as("Index on speaker_pool_id should exist")
                .isTrue();
    }

    /**
     * Test: Verify content_status column added to speaker_pool table
     */
    @Test
    void should_haveContentStatusOnSpeakerPool_when_migrationApplied() {
        String columnQuery = """
            SELECT column_name, data_type, character_maximum_length, column_default
            FROM information_schema.columns
            WHERE table_name = 'speaker_pool' AND column_name = 'content_status'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).as("content_status column should exist on speaker_pool").hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("content_status");
        assertThat(column.get("data_type")).isEqualTo("character varying");
        assertThat(column.get("character_maximum_length")).isEqualTo(50);
    }

    /**
     * Test: Verify content_submitted_at column added to speaker_pool table
     */
    @Test
    void should_haveContentSubmittedAtOnSpeakerPool_when_migrationApplied() {
        String columnQuery = """
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_name = 'speaker_pool' AND column_name = 'content_submitted_at'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).as("content_submitted_at column should exist on speaker_pool").hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("content_submitted_at");
        assertThat(column.get("data_type")).isIn("timestamp with time zone", "timestamp without time zone");
    }

    /**
     * Test: Verify submission_version has default value of 1
     */
    @Test
    void should_haveDefaultVersionOf1_when_tableCreated() {
        String columnQuery = """
            SELECT column_name, column_default
            FROM information_schema.columns
            WHERE table_name = 'speaker_content_submissions' AND column_name = 'submission_version'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("submission_version");
        // Default should be 1
        String columnDefault = (String) column.get("column_default");
        assertThat(columnDefault).isEqualTo("1");
    }
}
