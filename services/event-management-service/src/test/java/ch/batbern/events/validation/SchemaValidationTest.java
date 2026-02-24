package ch.batbern.events.validation;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema Validation Test for Event Management Service
 * Story 2.2: Architecture Compliance Refactoring - AC8
 *
 * Verifies that the Event entity structure matches the database schema exactly.
 * This test ensures architecture compliance by checking:
 * - Column existence and types
 * - Unique constraints on eventCode and eventNumber
 * - Index existence for performance-critical fields
 */
class SchemaValidationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Test AC8: Verify Event entity matches architecture schema
     * Checks that @Column annotations match actual database columns
     */
    @Test
    void should_matchArchitecture_when_entityMapped() {
        // Verify table exists
        Table tableAnnotation = Event.class.getAnnotation(Table.class);
        assertThat(tableAnnotation).isNotNull();
        assertThat(tableAnnotation.name()).isEqualTo("events");

        // Get actual database columns
        String query = """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_name = 'events'
            ORDER BY ordinal_position
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(query);
        assertThat(columns).isNotEmpty();

        // Extract column names for validation
        List<String> columnNames = columns.stream()
                .map(col -> (String) col.get("column_name"))
                .toList();

        // Verify critical columns exist
        assertThat(columnNames).contains(
                "id",
                "event_code",
                "event_number",
                "title",
                "event_date",
                "registration_deadline",
                "venue_name",
                "venue_address",
                "venue_capacity",
                "workflow_state",
                "organizer_username",
                "created_at",
                "updated_at"
        );

        // Verify each field in Event entity has matching database column
        for (Field field : Event.class.getDeclaredFields()) {
            Column columnAnnotation = field.getAnnotation(Column.class);
            Id idAnnotation = field.getAnnotation(Id.class);

            if (columnAnnotation != null || idAnnotation != null) {
                String columnName = columnAnnotation != null ? columnAnnotation.name() : "id";
                assertThat(columnNames).as("Column " + columnName + " for field " + field.getName())
                        .contains(columnName);
            }
        }
    }

    /**
     * Test AC9: Verify eventCode column exists with unique constraint
     * Architecture requires eventCode as public identifier
     */
    @Test
    void should_haveEventCodeColumn_when_migrationApplied() {
        // Check column exists
        String columnQuery = """
            SELECT column_name, data_type, character_maximum_length, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'events' AND column_name = 'event_code'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("event_code");
        assertThat(column.get("data_type")).isEqualTo("character varying");
        assertThat(column.get("character_maximum_length")).isEqualTo(50);
        assertThat(column.get("is_nullable")).isEqualTo("NO");

        // Check unique constraint exists
        String constraintQuery = """
            SELECT constraint_name, constraint_type
            FROM information_schema.table_constraints
            WHERE table_name = 'events'
              AND constraint_type = 'UNIQUE'
              AND constraint_name LIKE '%event_code%'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
        assertThat(constraints).as("eventCode should have UNIQUE constraint").isNotEmpty();
    }

    /**
     * Test AC9: Verify eventNumber column exists with unique constraint
     * Architecture requires eventNumber as sequential identifier
     */
    @Test
    void should_haveEventNumberColumn_when_migrationApplied() {
        // Check column exists
        String columnQuery = """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'events' AND column_name = 'event_number'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
        assertThat(columns).hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("event_number");
        assertThat(column.get("data_type")).isEqualTo("integer");
        assertThat(column.get("is_nullable")).isEqualTo("NO");

        // Check unique constraint exists
        String constraintQuery = """
            SELECT constraint_name, constraint_type
            FROM information_schema.table_constraints
            WHERE table_name = 'events'
              AND constraint_type = 'UNIQUE'
              AND constraint_name LIKE '%event_number%'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
        assertThat(constraints).as("eventNumber should have UNIQUE constraint").isNotEmpty();
    }

    /**
     * Test AC8: Verify indexes exist for performance-critical fields
     * Architecture requires indexes on eventCode and eventNumber for lookups
     */
    @Test
    void should_haveIndexes_when_performanceCriticalFields() {
        String indexQuery = """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE tablename = 'events'
              AND (indexname LIKE '%event_code%' OR indexname LIKE '%event_number%')
            """;

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(indexQuery);

        // Should have indexes on event_code and event_number
        assertThat(indexes).as("Should have indexes for eventCode and eventNumber")
                .hasSizeGreaterThanOrEqualTo(2);

        List<String> indexNames = indexes.stream()
                .map(idx -> (String) idx.get("indexname"))
                .toList();

        // Verify event_code index exists (either explicit or from UNIQUE constraint)
        assertThat(indexNames.stream().anyMatch(name -> name.contains("event_code")))
                .as("Index on event_code should exist")
                .isTrue();

        // Verify event_number index exists (either explicit or from UNIQUE constraint)
        assertThat(indexNames.stream().anyMatch(name -> name.contains("event_number")))
                .as("Index on event_number should exist")
                .isTrue();
    }

    /**
     * Test AC8: Verify primary key is UUID type
     * Architecture requires UUID as internal database key
     */
    @Test
    void should_haveUuidPrimaryKey_when_entityCreated() {
        String pkQuery = """
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_name = 'events' AND column_name = 'id'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(pkQuery);
        assertThat(columns).hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("id");
        assertThat(column.get("data_type")).isEqualTo("uuid");
    }

    /**
     * Test AC8: Verify JSONB metadata column exists
     * Architecture uses PostgreSQL JSONB for flexible metadata storage
     */
    @Test
    void should_haveJsonbMetadataColumn_when_postgresqlUsed() {
        String metadataQuery = """
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_name = 'events' AND column_name = 'metadata'
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(metadataQuery);
        assertThat(columns).hasSize(1);

        Map<String, Object> column = columns.get(0);
        assertThat(column.get("column_name")).isEqualTo("metadata");
        assertThat(column.get("data_type")).isEqualTo("jsonb");
    }
}
