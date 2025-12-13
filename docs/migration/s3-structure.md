# S3 Bucket Structure Documentation

**Story**: 3.1.2 - Domain Mapping & Schema Transformation Design
**Generated**: 2025-11-19
**Author**: Dev Agent (James)

## Overview

This document defines the S3 bucket organization for BATbern file storage, including company logos, profile pictures, presentations, and event photos.

## Bucket Organization

**Primary Bucket**: `batbern-development-company-logos` (development environment)

```
batbern-development-company-logos/
├── logos/
│   ├── temp/                                    # Temporary uploads (15-min expiration)
│   │   └── {uploadId}/
│   │       └── logo-{fileId}.{ext}
│   └── {year}/                                  # Year-based organization
│       ├── companies/                           # Company logos
│       │   └── {companyName}/
│       │       └── logo-{fileId}.{ext}
│       └── events/                              # Event theme logos
│           └── {eventCode}/
│               └── theme-{fileId}.{ext}
├── profile-pictures/
│   └── {year}/                                  # Year-based organization
│       └── {username}/
│           └── profile-{fileId}.{ext}
├── presentations/                               # Historical presentations (no year subdirectory)
│   └── {eventNumber}/
│       └── {originalFilename}
└── photos/
    └── events/                                  # Historical event photos (no year subdirectory)
        └── {eventNumber}/
            └── {originalFilename}
```

## File Type Patterns

### 1. Company Logos

**Pattern**: `logos/{year}/companies/{companyName}/logo-{fileId}.{ext}`
**Reference**: GenericLogoService (CompanyService.java:369)

**Examples**:
- `logos/2025/companies/mobiliar/logo-a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg`
- `logos/2025/companies/sbb/logo-b2c3d4e5-f6a7-8901-bcde-f12345678901.png`

**Characteristics**:
- Year-based organization for easier archival
- Company name as subdirectory (normalized, max 12 chars)
- Unique file ID (UUID) to prevent collisions
- Original extension preserved

### 2. Profile Pictures

**Pattern**: `profile-pictures/{year}/{username}/profile-{fileId}.{ext}`
**Reference**: ProfilePictureService.java:161

**Examples**:
- `profile-pictures/2025/thomas.goetz/profile-c3d4e5f6-a7b8-9012-cdef-123456789012.jpg`
- `profile-pictures/2025/anna.mueller/profile-d4e5f6a7-b8c9-0123-def1-234567890123.png`

**Characteristics**:
- Year-based organization
- Username as subdirectory (lowercase, alphanumeric + dot)
- Unique file ID (UUID)
- Original extension preserved

### 3. Presentation PDFs

**Pattern**: `presentations/{eventNumber}/{originalFilename}`

**Examples**:
- `presentations/56/Cloud_Security.pdf`
- `presentations/1/BAT_Nr.01.pdf`
- `presentations/60/Latest_Presentation.pdf`

**Characteristics**:
- Event number as subdirectory (1-60)
- Original filename preserved (no UUID, simpler for historical data)
- No year subdirectory (events are self-organizing by number)

### 4. Event Photos

**Pattern**: `photos/events/{eventNumber}/{originalFilename}`

**Examples**:
- `photos/events/56/01.jpg`
- `photos/events/56/event-photo-networking.jpg`
- `photos/events/1/group-photo.jpg`

**Characteristics**:
- Event number as subdirectory
- Original filename preserved
- No year subdirectory

## CDN URLs

**CloudFront Distribution** (Production/Staging): `https://cdn.batbern.ch/`

**URL Format**: `https://cdn.batbern.ch/{s3Key}`

**Examples**:
- Company logo: `https://cdn.batbern.ch/logos/2025/companies/mobiliar/logo-{uuid}.jpg`
- Profile picture: `https://cdn.batbern.ch/profile-pictures/2025/thomas.goetz/profile-{uuid}.jpg`
- Presentation: `https://cdn.batbern.ch/presentations/56/Cloud_Security.pdf`
- Event photo: `https://cdn.batbern.ch/photos/events/56/01.jpg`

## Local Development (MinIO)

**MinIO URL Format**: `http://localhost:8450/{bucketName}/{s3Key}`

**Example**: `http://localhost:8450/batbern-development-company-logos/logos/2025/companies/mobiliar/logo-{uuid}.jpg`

## File ID Generation

**Format**: UUID v4 (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)
**Purpose**: Unique identifier for each file to prevent collisions

**Generation**: `randomUUID()` in Node.js/TypeScript

**Storage**:
- Company logos: `Company.logoFileId` (VARCHAR(100))
- Profile pictures: `User.profilePictureFileId` (VARCHAR(100))
- Presentations/Photos: Tracked in migration logs

## Migration Strategy

### Two-Phase Upload (for Company Logos)

**Phase 1 - Temporary Location**:
- Pattern: `logos/temp/{uploadId}/logo-{fileId}.{ext}`
- Duration: 15 minutes expiration
- Purpose: Pre-upload validation

**Phase 2 - Final Location**:
- Pattern: `logos/{year}/companies/{companyName}/logo-{fileId}.{ext}`
- Action: Move from temp to final location after confirmation

**Note**: For historical data migration, upload directly to final location (skip temp phase).

### Direct Upload (for Historical Data)

For presentations and event photos from legacy system:
1. Read file from local legacy application directory
2. Upload directly to final S3 location
3. Record S3 key in database
4. Generate and store CloudFront URL

## Implementation

**Module**: `apps/migration-analysis/src/mappers/file-mapper.ts`
**Tests**: `apps/migration-analysis/src/__tests__/file-mapper.test.ts`
**Test Results**: 24 tests passing ✅

### Key Functions

- `generateCompanyLogoMapping(params)`: Generate S3 mapping for company logo
- `generateProfilePictureMapping(params)`: Generate S3 mapping for profile picture
- `generatePresentationMapping(params)`: Generate S3 mapping for presentation PDF
- `generateEventPhotoMapping(params)`: Generate S3 mapping for event photo
- `generateCloudFrontUrl(s3Key)`: Generate CDN URL
- `generateMinIOUrl(s3Key, bucketName)`: Generate local development URL

## References

- **GenericLogoService**: CompanyService.java:369 (company logo pattern)
- **ProfilePictureService**: ProfilePictureService.java:161 (profile picture pattern)
- **ADR-002**: Generic File Upload Service specification
- **CloudFront**: AWS CDN for public media access
