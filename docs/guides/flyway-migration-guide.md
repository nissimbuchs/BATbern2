# Flyway Migration Guide for BATbern Microservices

**Pattern**: Service-Specific Migrations with Shared Baseline
**Last Updated**: 2025-01-08

---

## Table of Contents

- [Overview](#overview)
- [Migration Strategy](#migration-strategy)
- [Baseline Migration (V1)](#baseline-migration-v1)
- [Service-Specific Migrations (V2+)](#service-specific-migrations-v2)
- [Migration File Naming](#migration-file-naming)
- [PostgreSQL Patterns](#postgresql-patterns)
- [ADR-003 Compliance](#adr-003-compliance)
- [Testing Migrations](#testing-migrations)
- [Deployment](#deployment)
- [Related Documentation](#related-documentation)

---

## Overview

BATbern uses Flyway for database schema versioning. Each microservice runs its own migrations on startup.

**Key Principles**:
- ✅ Each service manages its own database schema
- ✅ Migrations run automatically on service startup
- ✅ V1 baseline copied from shared-kernel (UUID extension, triggers)
- ✅ V2+ service-specific schema
- ✅ NO centralized migration management
- ❌ NO cross-service foreign keys
- ❌ NO database access from GitHub Actions (RDS in private subnet)

**Reference**: `.github/workflows/deploy-production.yml` lines 122-136

---

## Migration Strategy

### Service-Specific Migrations

**Each microservice** has its own migrations in:

```
services/{service-name}/src/main/resources/db/migration/
├── V1__Initial_baseline.sql       # Copied from shared-kernel
└── V2__create_{domain}_schema.sql # Service-specific schema
```

**Examples**:

```
services/company-user-management-service/src/main/resources/db/migration/
├── V1__Initial_baseline.sql
├── V3__Create_companies_schema.sql
├── V4__Create_user_profiles_table.sql
└── V5__Create_role_assignments_table.sql

services/event-management-service/src/main/resources/db/migration/
├── V1__Initial_baseline.sql
├── V2__Create_events_schema.sql
├── V3__Add_event_code_and_organizer_username.sql
└── V4__Add_session_slug_and_registration_code.sql

services/partner-coordination-service/src/main/resources/db/migration/
├── V1__Initial_baseline.sql
└── V2__create_partner_coordination_schema.sql
```

### Why Service-Specific?

1. **Microservices Independence**: Each service owns its schema
2. **Security**: Services run migrations from within VPC, GitHub Actions doesn't need RDS access
3. **Deployment**: Migrations run automatically on ECS task startup
4. **Isolation**: One service's migrations can't break another service

---

## Baseline Migration (V1)

### V1__Initial_baseline.sql

**Location**: Copy from `shared-kernel/src/main/resources/db/migration/V1__Initial_baseline.sql`

**Content**:

```sql
-- V1__Initial_baseline.sql
-- Initial database baseline for BATbern platform
-- This establishes the baseline for Flyway migrations

-- Create UUID extension for PostgreSQL (H2 has built-in UUID support)
-- This will fail gracefully in H2 and succeed in PostgreSQL
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;

-- Create trigger function for automatic updated_at column updates
-- This function is used across all tables with updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Initial baseline established
-- Future migrations will build upon this foundation
```

**Why Copy to Each Service?**

- Flyway requires a continuous version sequence (V1, V2, V3...)
- Each service has its own Flyway schema history table
- V1 provides common infrastructure (UUID support, triggers)
- Ensures all services start from the same baseline

---

## Service-Specific Migrations (V2+)

### ADR-003 Compliance: Meaningful IDs

**CRITICAL**: Cross-service references use **meaningful IDs** (companyName, username), NOT UUIDs.

#### Example: Partner Coordination Service V2

```sql
-- V2__create_partner_coordination_schema.sql

-- Partners table (stores companyName, NOT companyId UUID)
CREATE TABLE partners (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_name VARCHAR(12) NOT NULL UNIQUE,  -- ✅ ADR-003: meaningful ID
    -- NO: company_id UUID REFERENCES companies(id)  ❌ WRONG: cross-service FK
    partnership_level VARCHAR(50) NOT NULL CHECK (partnership_level IN (
        'bronze', 'silver', 'gold', 'platinum', 'strategic'
    )),
    partnership_start_date DATE NOT NULL,
    partnership_end_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Partner contacts (stores username, NOT userId UUID)
CREATE TABLE partner_contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,  -- ✅ Within-service FK OK
    username VARCHAR(100) NOT NULL,  -- ✅ ADR-003: meaningful ID
    -- NO: user_id UUID REFERENCES users(id)  ❌ WRONG: cross-service FK
    contact_role VARCHAR(50) NOT NULL CHECK (contact_role IN (
        'primary', 'billing', 'technical', 'marketing'
    )),
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(partner_id, username)  -- Composite unique constraint
);

-- Topic voting
CREATE TABLE topic_votes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL,  -- References topic in Event Management Service (no FK)
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,  -- ✅ Within-service FK
    vote_weight INTEGER DEFAULT 1,  -- Based on partnership_level
    vote_value INTEGER NOT NULL CHECK (vote_value BETWEEN 1 AND 5),
    voted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(topic_id, partner_id)
);

-- Indexes (Note: company_name and username, NOT UUID FKs)
CREATE INDEX idx_partners_company_name ON partners(LOWER(company_name));
CREATE INDEX idx_partners_active ON partners(is_active);
CREATE INDEX idx_partner_contacts_partner_id ON partner_contacts(partner_id);
CREATE INDEX idx_partner_contacts_username ON partner_contacts(LOWER(username));
CREATE INDEX idx_topic_votes_partner_id ON topic_votes(partner_id);
CREATE INDEX idx_topic_votes_topic_id ON topic_votes(topic_id);

-- Triggers for updated_at (using V1 baseline function)
CREATE TRIGGER update_partners_updated_at BEFORE UPDATE ON partners
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

### Decision Tree: FK or Meaningful ID?

```
Does this field reference an entity?
├─ YES → Is the entity in THIS service's database?
│   ├─ YES → Use UUID FK ✅
│   │   Example: partner_id UUID REFERENCES partners(id)
│   └─ NO → Use Meaningful ID ✅
│       Example: company_name VARCHAR(12), username VARCHAR(100)
└─ NO → Use appropriate data type
    Example: vote_value INTEGER, description TEXT
```

---

## Migration File Naming

### Version Number Format

**V{version}__{description}.sql**

- **V**: Version prefix (required)
- **{version}**: Sequential number (1, 2, 3, 4...)
- **__**: Double underscore separator (required)
- **{description}**: Descriptive name (snake_case)

**Examples**:
- ✅ `V1__Initial_baseline.sql`
- ✅ `V2__create_partner_coordination_schema.sql`
- ✅ `V3__add_event_code_and_organizer_username.sql`
- ✅ `V10__create_logos_table.sql`
- ❌ `V2_create_schema.sql` (single underscore)
- ❌ `create_schema.sql` (no version)

### Repeatable Migrations

**R__{description}.sql** - Runs every time checksum changes

```sql
-- R__Backfill_session_users_username.sql
-- Data migration that can be re-run safely
UPDATE session_users
SET username = (
    SELECT u.username FROM users u WHERE u.id = session_users.user_id
)
WHERE username IS NULL;
```

---

## PostgreSQL Patterns

### UUID Primary Keys

```sql
-- Use uuid_generate_v4() from V1 baseline
CREATE TABLE partners (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- ...
);
```

### Enums via CHECK Constraints

```sql
-- ✅ RECOMMENDED: CHECK constraint (portable, easy to change)
partnership_level VARCHAR(50) NOT NULL CHECK (partnership_level IN (
    'bronze', 'silver', 'gold', 'platinum', 'strategic'
))

-- ❌ AVOID: PostgreSQL ENUM type (harder to change)
CREATE TYPE partnership_tier AS ENUM ('bronze', 'silver', 'gold', 'platinum', 'strategic');
```

### Timestamps

```sql
created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
```

### Updated At Triggers

```sql
-- Trigger that uses V1 baseline function
CREATE TRIGGER update_partners_updated_at BEFORE UPDATE ON partners
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

### Indexes

```sql
-- Case-insensitive index for meaningful IDs
CREATE INDEX idx_partners_company_name ON partners(LOWER(company_name));
CREATE INDEX idx_partner_contacts_username ON partner_contacts(LOWER(username));

-- Boolean index for active flags
CREATE INDEX idx_partners_active ON partners(is_active);

-- Foreign key indexes (within service)
CREATE INDEX idx_partner_contacts_partner_id ON partner_contacts(partner_id);
```

### Constraints

```sql
-- Composite unique constraint
UNIQUE(partner_id, username)

-- Check constraint with multiple values
CHECK (status IN ('submitted', 'under_review', 'accepted', 'rejected'))

-- Check constraint with range
CHECK (vote_value BETWEEN 1 AND 5)

-- Foreign key with cascade delete (within service only)
partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE
```

---

## ADR-003 Compliance

### Within-Service Relationships: UUID FKs ✅

```sql
-- ✅ CORRECT: Within-service FK
CREATE TABLE partner_contacts (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners(id),  -- ✅ Same service
    ...
);
```

### Cross-Service Relationships: Meaningful IDs ✅

```sql
-- ✅ CORRECT: Cross-service reference with meaningful ID
CREATE TABLE partners (
    id UUID PRIMARY KEY,
    company_name VARCHAR(12) NOT NULL UNIQUE,  -- ✅ Meaningful ID
    -- NO FOREIGN KEY to companies table
);

-- ❌ WRONG: Cross-service reference with UUID FK
CREATE TABLE partners (
    id UUID PRIMARY KEY,
    company_id UUID REFERENCES companies(id),  -- ❌ WRONG: cross-service FK
);
```

### NO Foreign Keys Across Services ❌

```sql
-- ❌ WRONG: This violates microservices boundary
ALTER TABLE partner_contacts
ADD CONSTRAINT fk_user
FOREIGN KEY (user_id) REFERENCES users(id);  -- ❌ users table is in User Service!

-- ✅ CORRECT: Store meaningful ID instead
ALTER TABLE partner_contacts
ADD COLUMN username VARCHAR(100) NOT NULL;  -- ✅ No FK constraint
```

---

## Testing Migrations

### Schema Validation Test

**Purpose**: Ensure JPA entities match Flyway migration schema

```java
package ch.batbern.partners.validation;

import ch.batbern.partners.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_havePartnersTable_when_migrationsRun() {
        // Verify table exists
        String sql = "SELECT table_name FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_name = 'partners'";
        List<String> tables = jdbcTemplate.queryForList(sql, String.class);
        assertThat(tables).contains("partners");
    }

    @Test
    void should_haveCompanyNameColumn_when_migrationsRun() {
        // Verify meaningful ID column exists (NOT company_id UUID)
        String sql = "SELECT column_name, data_type FROM information_schema.columns " +
                     "WHERE table_name = 'partners' AND column_name = 'company_name'";
        List<String> columns = jdbcTemplate.queryForList(sql, String.class);
        assertThat(columns).contains("company_name");
    }

    @Test
    void should_notHaveCompanyIdColumn_when_migrationsRun() {
        // Verify UUID FK column does NOT exist
        String sql = "SELECT column_name FROM information_schema.columns " +
                     "WHERE table_name = 'partners' AND column_name = 'company_id'";
        List<String> columns = jdbcTemplate.queryForList(sql, String.class);
        assertThat(columns).isEmpty();  // Should NOT have company_id
    }

    @Test
    void should_haveCorrectIndexes_when_migrationsRun() {
        // Verify indexes on meaningful IDs
        String sql = "SELECT indexname FROM pg_indexes " +
                     "WHERE tablename = 'partners' AND indexname = 'idx_partners_company_name'";
        List<String> indexes = jdbcTemplate.queryForList(sql, String.class);
        assertThat(indexes).contains("idx_partners_company_name");
    }
}
```

### Testcontainers with PostgreSQL

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest extends SharedAbstractIntegrationTest {
    // Extends shared-kernel AbstractIntegrationTest
    // Provides singleton PostgreSQL Testcontainer
    // Flyway runs migrations automatically against Testcontainer
}
```

**application-test.properties**:

```properties
# Testcontainers provides PostgreSQL
spring.datasource.url=jdbc:tc:postgresql:15:///testdb
spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver

# Flyway runs migrations in tests
spring.flyway.enabled=true
spring.flyway.clean-disabled=false

# JPA validates schema matches entities
spring.jpa.hibernate.ddl-auto=validate
```

---

## Deployment

### How Migrations Run

**Automatic on Service Startup** (per `.github/workflows/deploy-production.yml`):

1. ECS task starts in VPC (has RDS access)
2. Spring Boot application starts
3. Flyway runs pending migrations (spring.flyway.enabled=true)
4. Service becomes healthy and ready

**GitHub Actions does NOT run migrations** (RDS is in private subnet).

### Flyway Configuration

**application.yml**:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true  # Allow Flyway to baseline existing database
```

### Migration History

Flyway tracks applied migrations in `flyway_schema_history` table:

```sql
SELECT installed_rank, version, description, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Rollback Strategy

**Flyway does NOT support automatic rollback**. For rollback:

1. Write a new forward migration (V3, V4, etc.) that undoes V2
2. Test migration on staging first
3. Deploy to production

**Example**: If V2 added a column, V3 drops it:

```sql
-- V3__rollback_add_column.sql
ALTER TABLE partners DROP COLUMN IF EXISTS new_column;
```

---

## Related Documentation

### Architecture Decisions

- **[ADR-003: Meaningful Identifiers in Public APIs](../architecture/ADR-003-meaningful-identifiers-public-apis.md)** - Microservices isolation rules
- **[Data Architecture](../architecture/03-data-architecture.md)** - Database schemas for all services

### Related Guides

- **[Service Foundation Pattern](./service-foundation-pattern.md)** - JPA entities matching migrations
- **[Microservices HTTP Clients](./microservices-http-clients.md)** - Cross-service data access (not FKs)

### Deployment

- **[.github/workflows/deploy-production.yml](../../.github/workflows/deploy-production.yml)** lines 122-136 - Migration strategy

### Tools

- **[Flyway Documentation](https://flywaydb.org/documentation/)** - Official docs
- **[Testcontainers](https://www.testcontainers.org/)** - PostgreSQL for tests

---

**Last Updated**: 2025-01-08
**Maintained By**: Development Team
