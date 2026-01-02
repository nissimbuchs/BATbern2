# Flyway Migration Pattern

**Category**: Backend
**Used in Stories**: 5.5
**Last Updated**: 2025-12-24

## Overview

Standard patterns for creating Flyway database migrations in the BATbern event management system. This template covers PostgreSQL-specific features, ADR-003 compliance, table creation, indexes, constraints, and data seeding.

## Prerequisites

- PostgreSQL 15+
- Flyway 9.x+ configured in Spring Boot
- Knowledge of ADR-003 naming conventions
- Understanding of PostgreSQL features (UUID, JSONB, constraints)

## Migration File Naming Convention

```
V{VERSION}__{DESCRIPTION}.sql

Examples:
V1__Initial_schema.sql
V20__Add_session_id_to_speaker_pool.sql
V22__Add_task_system.sql
```

**Rules:**
- VERSION: Sequential integer (check existing migrations, use next number)
- DESCRIPTION: Snake_case, descriptive, reflects purpose
- Two underscores (__) separate version from description

## Implementation Steps

### Step 1: Create Migration File

Place in `services/{service-name}/src/main/resources/db/migration/`

```sql
-- V{N}__{description}.sql
-- Story {story-id}: {brief description}

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table creation with standard patterns
CREATE TABLE IF NOT EXISTS {table_name} (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

  -- Business columns
  name VARCHAR(255) NOT NULL,
  description TEXT,

  -- Foreign keys with explicit constraints
  parent_id UUID REFERENCES {parent_table}(id) ON DELETE CASCADE,

  -- Enums via CHECK constraints
  status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'inactive', 'archived')),

  -- Optimistic locking (if needed for concurrent updates)
  version BIGINT DEFAULT 0 NOT NULL,

  -- Audit timestamps (always include)
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Step 2: Add Indexes for Performance

```sql
-- Indexes for foreign keys (always index FKs)
CREATE INDEX IF NOT EXISTS idx_{table}_parent_id ON {table_name}(parent_id);

-- Indexes for frequently queried columns
CREATE INDEX IF NOT EXISTS idx_{table}_status ON {table_name}(status);
CREATE INDEX IF NOT EXISTS idx_{table}_created_at ON {table_name}(created_at);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_{table}_status_created
  ON {table_name}(status, created_at DESC);

-- Partial indexes for specific conditions
CREATE INDEX IF NOT EXISTS idx_{table}_active
  ON {table_name}(id) WHERE status = 'active';

-- Unique indexes for business constraints
CREATE UNIQUE INDEX IF NOT EXISTS idx_{table}_unique_name
  ON {table_name}(name) WHERE status != 'archived';
```

### Step 3: Add Unique Constraints for Idempotency

```sql
-- Prevent duplicate records with unique constraints
CREATE UNIQUE INDEX idx_{table}_unique_constraint
  ON {table_name}({column1}, {column2})
  WHERE {condition};

-- Example: Prevent duplicate tasks from same template for same event
CREATE UNIQUE INDEX idx_event_tasks_unique_template
  ON event_tasks(event_id, template_id)
  WHERE template_id IS NOT NULL;

COMMENT ON INDEX idx_event_tasks_unique_template IS
  'Prevent duplicate tasks from same template for same event (AC36)';
```

### Step 4: Add Triggers for Auto-Update Timestamps

```sql
-- Reuse existing trigger function (created in V1 migration)
DROP TRIGGER IF EXISTS update_{table}_updated_at ON {table_name};
CREATE TRIGGER update_{table}_updated_at
  BEFORE UPDATE ON {table_name}
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Note:** The `update_updated_at_column()` function should be created once in initial migration:

```sql
-- Only in V1 or early migration
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';
```

### Step 5: Add Foreign Key Constraints with Proper Cascading

```sql
-- Foreign key deletion strategies:
-- ON DELETE CASCADE: Child deleted when parent deleted
ALTER TABLE child_table
  ADD CONSTRAINT fk_child_parent
  FOREIGN KEY (parent_id) REFERENCES parent_table(id)
  ON DELETE CASCADE;

-- ON DELETE SET NULL: FK set to NULL when parent deleted (preserves child)
ALTER TABLE speaker_pool
  ADD CONSTRAINT fk_speaker_session
  FOREIGN KEY (session_id) REFERENCES sessions(id)
  ON DELETE SET NULL;

-- ON DELETE RESTRICT: Prevent parent deletion if children exist (default)
ALTER TABLE events
  ADD CONSTRAINT fk_event_organizer
  FOREIGN KEY (organizer_id) REFERENCES users(id)
  ON DELETE RESTRICT;
```

### Step 6: Seed Default Data

```sql
-- Insert default/reference data
INSERT INTO {table_name} (name, status, is_default)
VALUES
  ('Default Template 1', 'active', true),
  ('Default Template 2', 'active', true)
ON CONFLICT DO NOTHING; -- Idempotent for re-running migrations
```

**Example: Task Templates Seeding**

```sql
INSERT INTO task_templates (name, trigger_state, due_date_type, due_date_offset_days, is_default)
VALUES
  ('Venue Booking', 'topic_selection', 'relative_to_event', -90, true),
  ('Partner Meeting Coordination', 'topic_selection', 'relative_to_event', 0, true),
  ('Moderator Assignment', 'topic_selection', 'relative_to_event', -14, true),
  ('Newsletter: Topic Announcement', 'topic_selection', 'immediate', 0, true),
  ('Newsletter: Speaker Lineup', 'agenda_published', 'relative_to_event', -30, true),
  ('Newsletter: Final Agenda', 'agenda_finalized', 'relative_to_event', -14, true),
  ('Catering Coordination', 'agenda_finalized', 'relative_to_event', -30, true)
ON CONFLICT DO NOTHING;
```

### Step 7: Add Column Comments for Documentation

```sql
-- Table-level comment
COMMENT ON TABLE {table_name} IS 'Description of table purpose - Story {story-id}';

-- Column-level comments for complex fields
COMMENT ON COLUMN {table_name}.version IS 'Optimistic locking version for concurrent updates';
COMMENT ON COLUMN {table_name}.due_date_offset_days IS 'Days relative to event date (negative = before, positive = after)';
COMMENT ON COLUMN {table_name}.status IS 'Workflow state: todo, in_progress, completed';
```

## Common Migration Patterns

### Adding a New Column to Existing Table

```sql
-- Add column with default value (safe for existing rows)
ALTER TABLE {table_name}
  ADD COLUMN IF NOT EXISTS new_column VARCHAR(100) DEFAULT 'default_value';

-- Make column NOT NULL after backfilling (if needed)
UPDATE {table_name} SET new_column = 'value' WHERE new_column IS NULL;
ALTER TABLE {table_name} ALTER COLUMN new_column SET NOT NULL;
```

### Renaming Columns (ADR-003 Compliance)

```sql
-- Rename column to match ADR-003 naming (snake_case)
ALTER TABLE {table_name}
  RENAME COLUMN oldColumnName TO new_column_name;
```

### Creating Junction Tables (Many-to-Many)

```sql
CREATE TABLE IF NOT EXISTS {table1}_{table2} (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  {table1}_id UUID NOT NULL REFERENCES {table1}(id) ON DELETE CASCADE,
  {table2}_id UUID NOT NULL REFERENCES {table2}(id) ON DELETE CASCADE,

  -- Additional junction table fields
  role VARCHAR(50),
  is_primary BOOLEAN DEFAULT false,

  -- Audit timestamps
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

  -- Prevent duplicate associations
  UNIQUE({table1}_id, {table2}_id)
);

-- Indexes for both FK directions
CREATE INDEX IF NOT EXISTS idx_{table1}_{table2}_{table1}_id
  ON {table1}_{table2}({table1}_id);
CREATE INDEX IF NOT EXISTS idx_{table1}_{table2}_{table2}_id
  ON {table1}_{table2}({table2}_id);
```

### PostgreSQL-Specific Features

**JSONB Columns:**
```sql
ALTER TABLE {table_name}
  ADD COLUMN metadata JSONB DEFAULT '{}';

-- GIN index for JSONB queries
CREATE INDEX IF NOT EXISTS idx_{table}_metadata_gin
  ON {table_name} USING GIN (metadata);
```

**Array Columns:**
```sql
ALTER TABLE {table_name}
  ADD COLUMN tags TEXT[] DEFAULT '{}';

-- Index for array containment queries
CREATE INDEX IF NOT EXISTS idx_{table}_tags_gin
  ON {table_name} USING GIN (tags);
```

**Enum Types (use CHECK constraints instead):**
```sql
-- ❌ Avoid PostgreSQL ENUM types (hard to modify)
-- CREATE TYPE status_enum AS ENUM ('active', 'inactive');

-- ✅ Use VARCHAR with CHECK constraint (easier to extend)
ALTER TABLE {table_name}
  ADD COLUMN status VARCHAR(20) DEFAULT 'active'
  CHECK (status IN ('active', 'inactive', 'archived'));
```

## Testing Migrations

### Local Testing

```bash
# Run migrations on local PostgreSQL
./gradlew :services:{service-name}:flywayMigrate

# Rollback (Flyway doesn't support automatic rollback)
./gradlew :services:{service-name}:flywayClean  # ⚠️ DROPS ALL TABLES
./gradlew :services:{service-name}:flywayMigrate

# Check migration status
./gradlew :services:{service-name}:flywayInfo
```

### Repair Failed Migrations

```bash
# If migration fails mid-execution (checksum mismatch)
./gradlew :services:{service-name}:flywayRepair
./gradlew :services:{service-name}:flywayMigrate
```

### Integration Test Validation

```java
@SpringBootTest
@Testcontainers
class MigrationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_applyAllMigrations_when_startingCleanDatabase() {
        // Flyway runs automatically on startup
        // Just verify tables exist
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'task_templates'",
            Integer.class
        )).isEqualTo(1);
    }

    @Test
    void should_containDefaultData_when_migrationComplete() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM task_templates WHERE is_default = true",
            Integer.class
        );
        assertThat(count).isEqualTo(7); // 7 default templates
    }
}
```

## Common Pitfalls

### Pitfall 1: Missing IF NOT EXISTS
**Problem**: Migration fails on re-run or rollback/replay scenarios
```sql
❌ CREATE TABLE task_templates (...);
✅ CREATE TABLE IF NOT EXISTS task_templates (...);
```

### Pitfall 2: Not Making Migrations Idempotent
**Problem**: Can't safely re-run migrations
```sql
❌ INSERT INTO task_templates VALUES (...);
✅ INSERT INTO task_templates VALUES (...) ON CONFLICT DO NOTHING;
```

### Pitfall 3: Forgetting Indexes on Foreign Keys
**Problem**: Slow queries on joins and cascading deletes
```sql
-- Always index foreign keys
CREATE INDEX IF NOT EXISTS idx_event_tasks_event_id ON event_tasks(event_id);
```

### Pitfall 4: Using ON DELETE CASCADE Incorrectly
**Problem**: Unintended data loss
```sql
❌ FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
   -- Deletes speaker_pool entry when session deleted (BAD!)

✅ FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL
   -- Preserves speaker_pool, allows recovery (GOOD!)
```

### Pitfall 5: Not Using Optimistic Locking for Concurrent Updates
**Problem**: Race conditions in concurrent workflow updates
```sql
-- Add version column for @Version annotation
ALTER TABLE {table_name} ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
```

### Pitfall 6: Hardcoding Migration Version Numbers
**Problem**: Merge conflicts when multiple developers create migrations
**Solution**: Always check latest migration number before creating new one
```bash
ls services/event-management-service/src/main/resources/db/migration/ | sort | tail -1
```

## Story-Specific Adaptations

### Story 5.5: Task System with Idempotency

**Unique Index for Idempotency:**
```sql
CREATE UNIQUE INDEX idx_event_tasks_unique_template
  ON event_tasks(event_id, template_id)
  WHERE template_id IS NOT NULL;
```

**Optimistic Locking for Concurrent Updates:**
```sql
ALTER TABLE event_tasks ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE speaker_pool ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
```

**Seeding Task Templates:**
```sql
INSERT INTO task_templates (name, trigger_state, due_date_type, due_date_offset_days, is_default)
VALUES
  ('Venue Booking', 'topic_selection', 'relative_to_event', -90, true),
  -- ... 6 more templates
ON CONFLICT DO NOTHING;
```

## Related Patterns

- See also: `docs/guides/flyway-migration-guide.md` - Detailed Flyway configuration
- See also: `docs/architecture/coding-standards.md` - ADR-003 database naming conventions
- See also: `backend/integration-test-pattern.md` - Testing migrations with Testcontainers

## ADR-003 Compliance Checklist

- [ ] Table names: `lowercase_snake_case`
- [ ] Column names: `lowercase_snake_case`
- [ ] Enum values stored as `lowercase_snake_case` in DB
- [ ] Timestamps: `TIMESTAMP WITH TIME ZONE`
- [ ] Primary keys: `UUID DEFAULT uuid_generate_v4()`
- [ ] Foreign keys: Explicit `REFERENCES` with `ON DELETE` strategy
- [ ] Indexes: Named `idx_{table}_{column}` or `idx_{table}_{purpose}`
- [ ] Triggers: Named `update_{table}_{action}`
- [ ] Comments: Added for complex tables/columns
