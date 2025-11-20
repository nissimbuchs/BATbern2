# BATbern Migration Tool

Spring Boot batch migration tool for migrating 20+ years of historical BATbern data from legacy Angular app to new microservices platform.

**Story**: 3.2.1 - Migration Tool Implementation

## Overview

This tool migrates historical data from the legacy BATbern Angular SPA to the new microservices architecture:

- **60 historical events** (1996-2024)
- **302 event sessions** with speaker assignments
- **269 unique speakers** with profiles and photos
- **65 companies** (after deduplication)
- **Presentations, photos, and logos** to AWS S3

## Architecture

### Migration Flow

```
Company → Event → User+Speaker → Session → Files
   ↓         ↓          ↓            ↓        ↓
 API      API        API          API      S3
```

**Sequential Execution Order:**
1. **Company Migration** - Creates 65 companies (no dependencies)
2. **Event Migration** - Creates 60 events (no dependencies)
3. **User+Speaker Migration** - Creates 269 users + speakers (depends on Company)
4. **Session Migration** - Creates 302 sessions (depends on Event + User)
5. **File Migration** - Uploads presentations/photos to S3 (depends on all above)

### Technology Stack

- **Spring Boot 3.2** - Application framework
- **Spring Batch** - Batch processing framework
- **PostgreSQL** - Job repository and ID mappings
- **AWS SDK S3** - File uploads to S3
- **Flyway** - Database migrations
- **Testcontainers** - Integration testing

## Prerequisites

- Java 21
- PostgreSQL 15+
- AWS credentials with S3 access
- Docker (for tests)
- Source data: `apps/BATspa-old/src/api/` (JSON files)

## Configuration

### application.yml

```yaml
migration:
  source-data-path: apps/BATspa-old/src/api

  target-api:
    company-management:
      base-url: http://localhost:8081
    event-management:
      base-url: http://localhost:8082
    speaker-coordination:
      base-url: http://localhost:8083

  s3:
    bucket-name: batbern-development-company-logos
    region: eu-central-1

  batch:
    chunk-size: 100
    skip-limit: 10
    retry-limit: 3

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/migration
    username: migration
    password: migration

  batch:
    jdbc:
      initialize-schema: always
```

### Environment Variables

```bash
# Source data location
SOURCE_DATA_PATH=/path/to/BATspa-old/src/api

# Target API URLs
COMPANY_API_URL=http://localhost:8081
EVENT_API_URL=http://localhost:8082
SPEAKER_API_URL=http://localhost:8083

# AWS S3 configuration
S3_BUCKET=batbern-development-company-logos
AWS_REGION=eu-central-1

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=migration
DB_USER=migration
DB_PASSWORD=migration
```

## Running Migration

### Build

```bash
cd apps/migration-tool
./gradlew clean build
```

### Run Migration (Development)

```bash
java -jar build/libs/migration-tool-1.0.0.jar \
  --spring.profiles.active=dev \
  --SOURCE_DATA_PATH=/path/to/BATspa-old/src/api \
  --COMPANY_API_URL=http://localhost:8081 \
  --EVENT_API_URL=http://localhost:8082 \
  --SPEAKER_API_URL=http://localhost:8083 \
  --S3_BUCKET=batbern-development-company-logos
```

### Run Migration via API

Start the migration tool application:

```bash
java -jar build/libs/migration-tool-1.0.0.jar
```

Trigger migration via REST API:

```bash
# Start migration
curl -X POST http://localhost:8090/migration/start

# Check status
curl http://localhost:8090/migration/status

# View errors
curl http://localhost:8090/migration/errors

# Export errors to CSV
curl http://localhost:8090/migration/errors/export?outputPath=/tmp/errors.csv

# Health check
curl http://localhost:8090/migration/health
```

## Monitoring

### REST Endpoints

The migration tool exposes monitoring endpoints on port 8090:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/migration/start` | POST | Trigger full migration workflow |
| `/migration/status` | GET | Get entity counts, error summary, last job status |
| `/migration/errors` | GET | List unresolved migration errors |
| `/migration/errors/export` | GET | Export errors to CSV file |
| `/migration/health` | GET | Health check endpoint |

### Example Status Response

```json
{
  "entityCounts": {
    "companies": 65,
    "events": 60,
    "users": 269,
    "speakers": 269,
    "sessions": 302
  },
  "errors": {
    "total": 5,
    "unresolved": 2,
    "resolved": 3
  },
  "lastJobExecution": {
    "status": "COMPLETED",
    "startTime": "2025-11-20T19:00:00",
    "endTime": "2025-11-20T19:05:30"
  }
}
```

## Error Handling

### Skip Policy

Validation errors are skipped and logged to `migration_errors` table:
- **Skip Limit**: 10 items per job
- **Skip Exception**: `IllegalArgumentException` (validation failures)
- **Logged Info**: Job ID, entity type, legacy ID, phase (READ/PROCESS/WRITE), error message, stack trace

### Retry Policy

Transient API failures are retried automatically:
- **Retry Limit**: 3 attempts per item
- **Retry Exception**: `RestClientException` (API timeouts, network errors)
- **Backoff**: Exponential backoff between retries

### Error Reporting

Generate CSV report of all migration errors:

```bash
curl http://localhost:8090/migration/errors/export?outputPath=/tmp/migration-errors.csv
```

CSV format:
```
ID,Job Execution ID,Entity Type,Legacy ID,Phase,Error Message,Retry Count,Created At,Resolved,Resolved At
1,12345,Company,mobiliar,WRITE,"Connection timeout",3,2025-11-20 19:05:12,false,
```

## Database Schema

The migration tool uses its own PostgreSQL database for tracking:

### migration_job_execution

Tracks Spring Batch job executions:

```sql
CREATE TABLE migration_job_execution (
    job_execution_id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    records_processed INTEGER DEFAULT 0,
    records_failed INTEGER DEFAULT 0
);
```

### entity_id_mapping

Maps legacy IDs to new UUIDs for foreign key resolution:

```sql
CREATE TABLE entity_id_mapping (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    legacy_id VARCHAR(255) NOT NULL,
    new_id UUID NOT NULL,
    UNIQUE (entity_type, legacy_id)
);
```

### migration_errors

Logs migration errors for manual review:

```sql
CREATE TABLE migration_errors (
    id BIGSERIAL PRIMARY KEY,
    job_execution_id BIGINT,
    entity_type VARCHAR(50) NOT NULL,
    legacy_id VARCHAR(255),
    phase VARCHAR(20),
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    retry_count INTEGER DEFAULT 0,
    resolved BOOLEAN DEFAULT FALSE
);
```

## Testing

### Run All Tests

```bash
./gradlew :apps:migration-tool:test
```

### Run Specific Test Suite

```bash
# Company migration tests
./gradlew :apps:migration-tool:test --tests CompanyMigrationJobTest

# Event migration tests
./gradlew :apps:migration-tool:test --tests EventMigrationJobTest

# User/Speaker migration tests
./gradlew :apps:migration-tool:test --tests UserSpeakerMigrationJobTest

# Session migration tests
./gradlew :apps:migration-tool:test --tests SessionMigrationJobTest

# Orchestrator tests
./gradlew :apps:migration-tool:test --tests MigrationJobOrchestratorTest

# Monitoring tests
./gradlew :apps:migration-tool:test --tests MigrationMonitoringControllerTest
```

### Integration Tests

Integration tests use:
- **Testcontainers PostgreSQL** for database (production parity)
- **WireMock** for mocking target service APIs
- **Test data** from `src/test/resources/test-data/`

## Troubleshooting

### Migration Fails with "Company not found"

**Issue**: UserSpeaker job can't find company mappings.

**Solution**: Ensure Company job completed successfully before UserSpeaker job runs. Check `entity_id_mapping` table for Company entries.

```sql
SELECT * FROM entity_id_mapping WHERE entity_type = 'Company';
```

### API Connection Refused

**Issue**: Target services not running.

**Solution**: Start all target services before migration:

```bash
# Start services locally
make docker-up

# Or check service health
curl http://localhost:8081/actuator/health  # Company Management
curl http://localhost:8082/actuator/health  # Event Management
curl http://localhost:8083/actuator/health  # Speaker Coordination
```

### S3 Upload Fails

**Issue**: AWS credentials not configured.

**Solution**: Configure AWS credentials:

```bash
aws configure
# Or set environment variables
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
export AWS_REGION=eu-central-1
```

### Test Data Mismatch

**Issue**: Integration tests fail with "No mapping found".

**Solution**: Test data has consistency issues. Use small test dataset or skip integration tests and test with real production data.

## Migration Validation

After migration completes, validate data integrity:

```bash
# Check entity counts
curl http://localhost:8090/migration/status | jq '.entityCounts'

# Verify no unresolved errors
curl http://localhost:8090/migration/status | jq '.errors.unresolved'

# Query target databases
psql -h localhost -U postgres -d company_management \
  -c "SELECT COUNT(*) FROM companies;"

psql -h localhost -U postgres -d event_management \
  -c "SELECT COUNT(*) FROM events;"
```

Expected counts:
- Companies: 65
- Events: 60
- Users: 269
- Speakers: 269
- Sessions: 302

## Performance

- **Chunk Size**: 100 records per chunk (configurable)
- **Thread Pool**: Sequential execution (no parallelization for small dataset)
- **Expected Duration**: ~5 minutes for full migration (test environment)
- **Throughput**: ~50-100 entities/second

## Data Mappings

### German Date Parsing

The migration tool handles 3 German date formats from legacy data:

```java
"15. November 1996"    → 1996-11-15
"3. März 2008"         → 2008-03-03
"November 2010"        → 2010-11-01 (defaults to 1st)
```

### Company Name Normalization

Company names are normalized to max 12 characters:

```java
"Die Mobiliar"         → "mobiliar"
"SBB CFF FFS"          → "sbb"
"VeryLongCompanyName"  → "verylongcomp"
```

### Event Code Generation

Event codes follow pattern: `"BATbern" + bat_number`

```java
BAT 40 → "BATbern40"
BAT 123 → "BATbern123"
```

## Deployment

For deployment instructions, see:
- Main project README: `/README.md`
- CLAUDE.md: `/CLAUDE.md`
- Infrastructure docs: `/docs/architecture/02-infrastructure-deployment.md`

## Support

- **Issues**: Report issues in GitHub
- **Questions**: Contact platform team
- **Documentation**: See `/docs/stories/3.2.1.migration-tool-implementation.md`
