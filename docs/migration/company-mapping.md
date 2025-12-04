# Company Mapping Documentation

**Story**: 3.1.2 - Domain Mapping & Schema Transformation Design
**Generated**: 2025-11-19
**Author**: Dev Agent (James)

## Overview

This document specifies the mapping rules for transforming legacy company data from `companies.json` to the Company Management Service schema. The mapping follows Story 1.16.2 (Meaningful IDs) and ADR-004 (Reference Patterns).

## Source Data

**File**: `docs/migration/companies.json`
**Format**: JSON with metadata and company array
**Total Companies**: 70 unique companies (normalized from 143 variations)
**Data Quality**:
- 42 complete (60% - have both logo and URL)
- 53 with URLs (76%)
- 25 with local logos

## Target Schema

**Service**: Company User Management Service
**Table**: `companies`
**Schema Source**: `services/company-user-management-service/src/main/resources/db/migration/V3__Create_companies_schema.sql`

### Company Entity Structure

```sql
CREATE TABLE companies (
  name VARCHAR(255) NOT NULL UNIQUE,        -- Meaningful ID, public identifier
  display_name VARCHAR(255),                -- Full official name
  website VARCHAR(500),                     -- Company website URL
  logo_url VARCHAR(1000),                   -- CloudFront CDN URL
  logo_s3_key VARCHAR(500),                 -- S3 storage key
  logo_file_id VARCHAR(100),                -- File identifier (UUID)
  is_verified BOOLEAN NOT NULL DEFAULT FALSE
);
```

## Mapping Rules

### 1. Company Identifier (Meaningful ID)

**Source**: `companies.json` → `id` field
**Target**: `Company.name` (VARCHAR(255))
**Constraint**: Must also satisfy user_profiles.company_id FK constraint (VARCHAR(12), alphanumeric)

**Rule**:
- Normalize to max 12 characters
- Remove special characters (keep only alphanumeric)
- Convert to lowercase
- Pattern: `^[a-zA-Z0-9]{1,12}$`

**Examples**:
- `"mobiliar"` → `"mobiliar"` ✅ (8 chars)
- `"sbb"` → `"sbb"` ✅ (3 chars)
- `"swisscom"` → `"swisscom"` ✅ (8 chars)
- `"verylongcompanyname"` → `"verylongcomp"` (truncated to 12 chars)

### 2. Display Name

**Source**: `companies.json` → `displayName` field
**Target**: `Company.displayName` (VARCHAR(255))

**Rule**: Direct mapping, no transformation

**Examples**:
- `"Die Mobiliar"` → `"Die Mobiliar"`
- `"SBB CFF FFS"` → `"SBB CFF FFS"`
- `"Swisscom AG"` → `"Swisscom AG"`

### 3. Website URL

**Source**: `companies.json` → `url` field
**Target**: `Company.website` (VARCHAR(500))

**Rule**: Direct mapping if present, undefined if missing

**Examples**:
- `"https://www.mobiliar.ch"` → `"https://www.mobiliar.ch"`
- Missing → `undefined`

### 4. Logo Mapping Strategies

#### Strategy A: Local Logo Files

**Condition**: Company has `logo` and `logoFilePath` fields
**Count**: 25 companies

**Rules**:
1. Generate unique file ID (UUID)
2. Extract file extension from filename
3. Generate S3 key following GenericLogoService pattern
4. Generate CloudFront URL from S3 key

**S3 Key Pattern**: `logos/{year}/companies/{companyName}/logo-{fileId}.{ext}`

**Example**:
```json
{
  "id": "mobiliar",
  "logo": "mobiliar.jpg",
  "logoFilePath": "/path/to/mobiliar.jpg"
}
```

**Mapping**:
- `logoS3Key`: `logos/2025/companies/mobiliar/logo-{uuid}.jpg`
- `logoUrl`: `https://cdn.batbern.ch/logos/2025/companies/mobiliar/logo-{uuid}.jpg`
- `logoFileId`: `{uuid}`

#### Strategy B: Online Logo URLs

**Condition**: Company has `logoUrl` field (no local file)
**Count**: 8 companies

**Rules**:
1. Download logo from URL during migration
2. Generate unique file ID (UUID)
3. Extract file extension from URL (default to .svg if missing)
4. Generate S3 key and upload to S3
5. Generate CloudFront URL

**Example**:
```json
{
  "id": "puzzle",
  "logoUrl": "https://www.puzzle.ch/assets/img/puzzle-logo.svg"
}
```

**Mapping**:
- Download from logoUrl
- `logoS3Key`: `logs/2025/companies/puzzle/logo-{uuid}.svg`
- `logoUrl`: `https://cdn.batbern.ch/logos/2025/companies/puzzle/logo-{uuid}.svg`
- `logoFileId`: `{uuid}`

#### Strategy C: Missing Logos

**Condition**: Company has `status: "needs_logo"`
**Count**: ~17 companies

**Rules**:
- Skip logo upload
- Set `logoUrl = undefined`, `logoS3Key = undefined`, `logoFileId = undefined`
- Can be backfilled post-migration

### 5. Duplicate Handling

**Condition**: Company has `status: "duplicate"`
**Count**: 5 companies (logos-pf, aws-logo, ibm-8bar-logo, etc.)

**Rule**: Return null from mapper (do NOT create entity)

**Migration Strategy**: Map speaker references to the original company ID specified in the `note` field

**Examples**:
- `"logo-pf"` (status: "duplicate") → Skip, map to `"postfinance"`
- `"aws-logo"` (status: "duplicate") → Skip, map to `"aws"`

### 6. Verification Status

**Target**: `Company.isVerified` (BOOLEAN NOT NULL DEFAULT FALSE)

**Rule**: All migrated companies set to `false`

**Rationale**: Historical companies need manual verification for accuracy

## Batch Processing Priority

Process companies in the following order for efficient migration:

1. **Batch 1 - Top 5 Companies** (10+ speakers each):
   - SBB, Mobiliar, Swisscom, PostFinance, ELCA
   - Status: 100% complete (logos + URLs)

2. **Batch 2 - Priority Companies** (5-9 speakers):
   - RTC, BKW, ipt, mtrail
   - Status: 100% complete

3. **Batch 3 - Standard Priority** (2-4 speakers):
   - 31 companies
   - Status: 55% complete

4. **Batch 4 - Low Priority** (1 speaker):
   - 29 companies
   - Status: 55% complete

## Schema Compliance

### Constraints Validated

✅ **Company.name VARCHAR(255)**: All company names ≤ 255 chars
✅ **user_profiles.company_id VARCHAR(12) FK**: All company names ≤ 12 chars, alphanumeric
✅ **isVerified DEFAULT FALSE**: All migrated companies unverified
✅ **GenericLogoService pattern**: S3 keys follow `logos/{year}/companies/{name}/logo-{fileId}.{ext}`

### Foreign Key Relationships

- **User → Company**: `user_profiles.company_id` → `companies.name` (meaningful ID, NOT UUID)

**Critical**: Company entities MUST be created BEFORE User entities to satisfy FK constraints.

## Migration Order

1. **Create Company entities** (this document) ✅
2. Create User entities (requires companies to exist)
3. Create Speaker entities (requires users to exist)
4. Create Event entities (independent)
5. Create Session entities (requires events to exist)
6. Create SessionUser junctions (requires sessions and users to exist)

## Implementation

**Module**: `apps/migration-analysis/src/mappers/company-mapper.ts`
**Tests**: `apps/migration-analysis/src/__tests__/company-mapper.test.ts`
**Test Results**: 17 tests passing ✅

### Key Functions

- `loadCompaniesFromJson(jsonPath)`: Load companies from JSON file
- `mapCompany(legacyCompany)`: Map single company to target schema
- `generateCompanyLogoS3Key(name, filename, fileId)`: Generate S3 key for logo
- `mapAllCompanies(jsonPath)`: Map all companies excluding duplicates

## Output

**Expected Result**: 65 Company entities created (70 total - 5 duplicates)

**Entity Structure**:
```typescript
{
  name: "mobiliar",
  displayName: "Die Mobiliar",
  website: "https://www.mobiliar.ch",
  logoUrl: "https://cdn.batbern.ch/logos/2025/companies/mobiliar/logo-{uuid}.jpg",
  logoS3Key: "logos/2025/companies/mobiliar/logo-{uuid}.jpg",
  logoFileId: "{uuid}",
  isVerified: false
}
```

## References

- **Story 1.16.2**: Meaningful IDs pattern (eventCode, Company.name, username)
- **ADR-004**: Reference pattern (no field duplication)
- **GenericLogoService**: S3 key pattern reference (CompanyService.java:369)
- **Flyway Migration**: V3__Create_companies_schema.sql (source of truth)

## Validation

All mapping rules validated against:
- ✅ Flyway migration schema constraints
- ✅ user_profiles.company_id FK constraint (VARCHAR(12))
- ✅ GenericLogoService S3 key pattern
- ✅ CloudFront CDN URL structure
- ✅ Story 1.16.2 meaningful ID requirements
