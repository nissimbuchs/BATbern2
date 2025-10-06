# Company Management API

This document outlines the Company Management Service API, which handles centralized company entity management with logo storage, partner recognition, company creation workflows, and comprehensive file storage management including presigned URLs and CDN integration.

## Overview

The Company Management API provides endpoints for:
- Company CRUD operations and search
- Logo upload and management
- Company name suggestions and duplicate detection
- File storage and content management (presigned URLs, CDN)
- Storage quota management
- User file management

## API Endpoints

### Company Operations

#### Search Companies

```yaml
GET /api/v1/companies
tags: [Companies]
summary: Search companies
parameters:
  - name: query
    in: query
    schema:
      type: string
    description: Search query for company name
  - name: isPartner
    in: query
    schema:
      type: boolean
    description: Filter by partner status
  - name: limit
    in: query
    schema:
      type: integer
      default: 20
      maximum: 100
  - name: offset
    in: query
    schema:
      type: integer
      default: 0
responses:
  '200':
    description: List of companies
    content:
      application/json:
        schema:
          type: object
          properties:
            companies:
              type: array
              items:
                $ref: '#/components/schemas/Company'
            pagination:
              $ref: 'common#/components/schemas/Pagination'
```

#### Create Company

```yaml
POST /api/v1/companies
tags: [Companies]
summary: Create new company
security:
  - BearerAuth: [organizer, speaker]
requestBody:
  required: true
  content:
    multipart/form-data:
      schema:
        type: object
        properties:
          name:
            type: string
          website:
            type: string
          industry:
            type: string
          logo:
            type: string
            format: binary
        required:
          - name
          - industry
responses:
  '201':
    description: Company created
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/Company'
```

### File Storage & Content Management

#### Generate Presigned Upload URL

```yaml
POST /api/v1/files/presigned-upload-url
tags: [Files]
summary: Generate presigned URL for file upload
description: Generate a time-limited presigned URL for direct browser-to-S3 upload
security:
  - BearerAuth: []
requestBody:
  required: true
  content:
    application/json:
      schema:
        type: object
        required:
          - filename
          - contentType
          - fileSizeBytes
          - mimeType
        properties:
          filename:
            type: string
            example: "presentation.pdf"
            maxLength: 255
          contentType:
            type: string
            enum: [presentation, logo, speaker_photo, speaker_cv, event_photo, archive_material]
            example: "presentation"
          fileSizeBytes:
            type: integer
            format: int64
            minimum: 1
            maximum: 104857600  # 100 MB
            example: 15728640
          mimeType:
            type: string
            example: "application/pdf"
responses:
  '200':
    description: Presigned upload URL generated successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            uploadUrl:
              type: string
              format: uri
              description: Presigned S3 upload URL (valid for 15 minutes)
              example: "https://batbern-prod-presentations.s3.eu-central-1.amazonaws.com/..."
            fileId:
              type: string
              format: uuid
              description: Unique file identifier for tracking
              example: "f47ac10b-58cc-4372-a567-0e02b2c3d479"
            expiresIn:
              type: integer
              description: URL expiration time in seconds
              example: 900
            requiredHeaders:
              type: object
              additionalProperties:
                type: string
              description: HTTP headers required for upload
              example:
                Content-Type: "application/pdf"
  '400':
    description: Invalid request (file too large, invalid content type, etc.)
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '403':
    description: Storage quota exceeded
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '401':
    description: Unauthorized
```

#### Confirm File Upload Completion

```yaml
POST /api/v1/files/{fileId}/confirm
tags: [Files]
summary: Confirm file upload completion
description: Verify upload success and activate file in system
security:
  - BearerAuth: []
parameters:
  - name: fileId
    in: path
    required: true
    schema:
      type: string
      format: uuid
requestBody:
  required: true
  content:
    application/json:
      schema:
        type: object
        required:
          - checksum
        properties:
          checksum:
            type: string
            description: SHA-256 checksum or S3 ETag for verification
            example: "5d41402abc4b2a76b9719d911017c592"
responses:
  '200':
    description: Upload confirmed successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            fileId:
              type: string
              format: uuid
              example: "f47ac10b-58cc-4372-a567-0e02b2c3d479"
            status:
              type: string
              enum: [completed]
              example: "completed"
            cdnUrl:
              type: string
              format: uri
              description: CloudFront CDN URL for file access
              example: "https://cdn.batbern.ch/presentations/2024/evt-123/presentation-a7b3.pdf"
            fileSizeBytes:
              type: integer
              format: int64
              example: 15728640
            uploadedAt:
              type: string
              format: date-time
              example: "2024-03-15T10:30:00Z"
  '400':
    description: Checksum mismatch or invalid file ID
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '404':
    description: File not found
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '401':
    description: Unauthorized
```

#### Generate Presigned Download URL

```yaml
GET /api/v1/files/{fileId}/download-url
tags: [Files]
summary: Generate presigned download URL
description: Generate time-limited presigned URL for secure file download
security:
  - BearerAuth: []
parameters:
  - name: fileId
    in: path
    required: true
    schema:
      type: string
      format: uuid
responses:
  '200':
    description: Download URL generated successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            downloadUrl:
              type: string
              format: uri
              description: Presigned download URL (valid for 15 minutes)
              example: "https://batbern-prod-presentations.s3.amazonaws.com/..."
            filename:
              type: string
              description: Original filename
              example: "presentation.pdf"
            fileSizeBytes:
              type: integer
              format: int64
              example: 15728640
            mimeType:
              type: string
              example: "application/pdf"
            expiresIn:
              type: integer
              description: URL expiration time in seconds
              example: 900
  '404':
    description: File not found or not yet completed
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '401':
    description: Unauthorized
```

#### Delete File

```yaml
DELETE /api/v1/files/{fileId}
tags: [Files]
summary: Delete uploaded file
description: Soft delete file and update storage quota
security:
  - BearerAuth: []
parameters:
  - name: fileId
    in: path
    required: true
    schema:
      type: string
      format: uuid
responses:
  '204':
    description: File deleted successfully
  '403':
    description: Not authorized to delete this file
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '404':
    description: File not found
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '401':
    description: Unauthorized
```

#### Get File Metadata

```yaml
GET /api/v1/files/{fileId}/metadata
tags: [Files]
summary: Get file metadata
description: Retrieve file metadata including upload status and CDN URL
security:
  - BearerAuth: []
parameters:
  - name: fileId
    in: path
    required: true
    schema:
      type: string
      format: uuid
responses:
  '200':
    description: File metadata retrieved successfully
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ContentMetadata'
  '404':
    description: File not found
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '401':
    description: Unauthorized
```

### Storage Quota Management

#### Get Storage Quota

```yaml
GET /api/v1/users/{userId}/storage-quota
tags: [Files, Users]
summary: Get storage quota information
description: Retrieve current storage usage and quota limits for user
security:
  - BearerAuth: []
parameters:
  - name: userId
    in: path
    required: true
    schema:
      type: string
      format: uuid
responses:
  '200':
    description: Storage quota information
    content:
      application/json:
        schema:
          type: object
          properties:
            quotaLimitBytes:
              type: integer
              format: int64
              description: Total quota limit (-1 for unlimited)
              example: 209715200  # 200 MB
            currentUsageBytes:
              type: integer
              format: int64
              description: Current storage usage
              example: 157286400  # 150 MB
            fileCount:
              type: integer
              description: Number of files uploaded
              example: 12
            percentageUsed:
              type: number
              format: double
              description: Percentage of quota used
              example: 75.0
            warningThresholdPercentage:
              type: number
              format: double
              description: Warning threshold percentage
              example: 80.0
            availableBytes:
              type: integer
              format: int64
              description: Available storage space
              example: 52428800  # 50 MB
  '404':
    description: User not found
    content:
      application/json:
        schema:
          $ref: 'common#/components/schemas/ErrorResponse'
  '401':
    description: Unauthorized
```

#### List User Files

```yaml
GET /api/v1/users/{userId}/files
tags: [Files, Users]
summary: List user's uploaded files
description: Retrieve paginated list of files uploaded by user
security:
  - BearerAuth: []
parameters:
  - name: userId
    in: path
    required: true
    schema:
      type: string
      format: uuid
  - name: contentType
    in: query
    schema:
      type: string
      enum: [presentation, logo, speaker_photo, speaker_cv, event_photo, archive_material]
  - name: status
    in: query
    schema:
      type: string
      enum: [pending, uploading, completed, failed, deleted]
  - name: limit
    in: query
    schema:
      type: integer
      default: 20
      maximum: 100
  - name: offset
    in: query
    schema:
      type: integer
      default: 0
responses:
  '200':
    description: List of user's files
    content:
      application/json:
        schema:
          type: object
          properties:
            files:
              type: array
              items:
                $ref: 'common#/components/schemas/ContentMetadata'
            pagination:
              $ref: 'common#/components/schemas/Pagination'
            totalSizeBytes:
              type: integer
              format: int64
              description: Total size of all files
              example: 157286400
  '401':
    description: Unauthorized
```

## Schemas

### Company

```yaml
Company:
  type: object
  properties:
    id:
      type: string
      format: uuid
    name:
      type: string
    displayName:
      type: string
      description: Display name (may differ from legal name)
    isPartner:
      type: boolean
      description: Whether company is a BATbern partner
    website:
      type: string
      format: uri
    industry:
      type: string
    logo:
      $ref: 'common#/components/schemas/CompanyLogo'
    createdAt:
      type: string
      format: date-time
    updatedAt:
      type: string
      format: date-time
```

## File Upload Workflow

The file upload process follows a secure presigned URL pattern:

1. **Request Upload URL**:
   - Client requests presigned URL from `/api/v1/files/presigned-upload-url`
   - Server validates quota and generates S3 presigned URL
   - Server returns `uploadUrl` and `fileId`

2. **Direct Browser Upload**:
   - Client uploads file directly to S3 using presigned URL
   - No file content passes through application servers
   - Upload includes required headers (Content-Type, etc.)

3. **Confirm Upload**:
   - Client calls `/api/v1/files/{fileId}/confirm` with checksum
   - Server verifies file exists in S3 and checksum matches
   - Server activates file and returns CDN URL

4. **File Access**:
   - Public files accessed via CloudFront CDN URL
   - Private files require presigned download URL
   - Download URLs valid for 15 minutes

## Storage Architecture

### S3 Bucket Structure

```
batbern-{env}-content/
├── logos/
│   └── {companyId}/
│       └── {fileId}.{ext}
├── presentations/
│   └── {year}/
│       └── {eventId}/
│           └── {fileId}.{ext}
├── speaker-photos/
│   └── {speakerId}/
│       └── {fileId}.{ext}
├── speaker-cvs/
│   └── {speakerId}/
│       └── {fileId}.pdf
└── archive/
    └── {year}/
        └── {eventId}/
            └── {fileId}.{ext}
```

### CDN Configuration

- **CloudFront Distribution**: Global edge caching
- **Cache Headers**: `Cache-Control: public, max-age=31536000` for immutable content
- **Invalidation**: Triggered on file updates
- **Custom Domain**: `cdn.batbern.ch`
- **SSL/TLS**: AWS Certificate Manager certificates

### Storage Quotas

| User Role | Quota Limit | File Size Limit |
|-----------|-------------|-----------------|
| Attendee | 50 MB | 10 MB per file |
| Speaker | 200 MB | 100 MB per file |
| Partner | 500 MB | 100 MB per file |
| Organizer | Unlimited | 500 MB per file |

### Content Types & MIME Types

| Content Type | Allowed MIME Types | Max Size |
|--------------|-------------------|----------|
| presentation | application/pdf, application/vnd.ms-powerpoint, application/vnd.openxmlformats-officedocument.presentationml.presentation | 100 MB |
| logo | image/png, image/jpeg, image/svg+xml | 5 MB |
| speaker_photo | image/png, image/jpeg | 5 MB |
| speaker_cv | application/pdf | 10 MB |
| event_photo | image/png, image/jpeg | 10 MB |
| archive_material | application/pdf, application/zip | 100 MB |

### Security Measures

- **Virus Scanning**: S3 Object Lambda for malware detection
- **Content Validation**: MIME type verification before presigned URL generation
- **Access Control**: IAM policies and S3 bucket policies
- **Encryption**: S3 server-side encryption (SSE-S3)
- **Audit Logging**: CloudTrail logging for all file operations
- **Rate Limiting**: Presigned URL generation rate limits per user

### Lifecycle Policies

- **Temporary Uploads**: Delete incomplete uploads after 24 hours
- **Deleted Files**: Transition to Glacier after 30 days, permanent delete after 90 days
- **Archive Content**: Transition to S3 Glacier after 1 year
- **Access Logs**: Transition to S3 IA after 90 days, delete after 1 year
