# ADR-002: Generic File Upload Service Architecture

**Status**: Accepted
**Date**: 2025-10-27
**Decision Makers**: Development Team, Architect Agent (Winston)
**Related Stories**: Story 1.16.3 (Generic File Upload Service), Story 2.5.1 (Company Management Frontend)

## Context

### The Circular Dependency Problem

During implementation of company creation with logo upload functionality, we encountered a fundamental architectural issue: **circular dependency between entity creation and file uploads**.

When creating a new company in the web-frontend, users expect to provide all company data including the logo in a single atomic operation. However, the current architecture creates a catch-22 situation:

1. **Logo upload requires company to exist**: The endpoint `POST /companies/{name}/logo/presigned-url` requires the company name as a path parameter
2. **Company lookup fails**: Backend looks up the company by name (`companyRepository.findByName(name)`) to generate S3 keys
3. **Company doesn't exist yet**: During creation flow, the company record hasn't been persisted to the database
4. **Poor workaround**: Frontend uses placeholder `'temp-company-name'`, which fails backend validation

**Current Flow (Broken):**
```
┌──────────┐  1. Create Company     ┌─────────────────┐
│ Frontend │───────────────────────>│ POST /companies │
│          │  {name, website, ...}  │                 │
└──────────┘                         └────────┬────────┘
                                              │
                                              v
                                     ❌ Cannot upload logo
                                        (company must exist first)

┌──────────┐  2. Upload Logo        ┌──────────────────────┐
│ Frontend │───────────────────────>│ POST /companies/{name}│
│          │                         │ /logo/presigned-url  │
└──────────┘                         └────────┬─────────────┘
                                              │
                                              v
                                     ❌ Company not found!
                                        (lookup by name fails)
```

**Current Workaround (Poor UX):**
- User must create company WITHOUT logo first
- User then edits the company to add the logo
- Requires two separate operations
- Confusing user experience

### Problems with Entity-Specific Upload Endpoints

The current `CompanyLogoService` architecture has several issues:

**CompanyLogoService.java (Current Implementation):**
```java
public PresignedUploadUrl generateLogoUploadUrl(String userId, String filename, long fileSizeBytes) {
    // ❌ Problem: Requires company to exist
    Company company = companyRepository.findByName(userId)
        .orElseThrow(() -> new CompanyNotFoundException(userId));

    // S3 key depends on company name
    String s3Key = generateS3Key(company.getName(), fileId, extension);
}
```

**LogoUpload.tsx (Current Implementation):**
```typescript
const companyNameForUpload =
    mode === 'edit' && initialData?.name
        ? initialData.name
        : 'temp-company-name'; // ❌ Placeholder hack

// ❌ Fails because 'temp-company-name' doesn't exist
const presignedResponse = await apiClient.post(
    `/companies/${companyNameForUpload}/logo/presigned-url`,
    { fileName, fileSize, mimeType }
);
```

### Key Architectural Issues

1. **Tight Coupling**: File upload logic tightly coupled to Company entity
2. **Not Reusable**: Cannot be used for User profiles, Event banners, Partner logos
3. **Circular Dependency**: Entity must exist before file upload, but file must exist for entity creation
4. **Poor UX**: Forces multi-step operation where single step is expected
5. **Scalability Issue**: Need separate upload service for every entity type

### Scope of Impact

This problem affects (or will affect) multiple domains:
- ✅ **Company Management**: Logo uploads (immediate problem)
- ⏳ **User Management**: Profile picture uploads (future)
- ⏳ **Event Management**: Event banner/thumbnail uploads (future)
- ⏳ **Partner Management**: Partner logo uploads (future)
- ⏳ **Speaker Management**: Speaker photo uploads (future)

## Decision

We have decided to implement a **Generic File Upload Service** that completely decouples file uploads from entity lifecycle, solving the circular dependency through a three-phase upload pattern.

### Core Principles

1. **Entity-Agnostic Uploads**: Files can be uploaded without any entity existing
2. **State Machine Pattern**: Explicit upload lifecycle (PENDING → CONFIRMED → ASSOCIATED)
3. **Deferred Association**: Files are associated with entities after upload completion
4. **Automatic Cleanup**: Orphaned uploads are automatically removed
5. **Single Reusable Service**: Works for all entity types (companies, users, events, etc.)

### Three-Phase Upload Pattern

```
PHASE 1: Upload Initiation (No Entity Required)
┌──────────┐  POST /logos/presigned-url  ┌─────────────────┐
│ Frontend │───────────────────────────>│ GenericLogo     │
│          │  {fileName, fileSize, type} │ Service         │
└──────────┘                              └────────┬────────┘
                                                   │
                                                   v
                                          ┌────────────────┐
                                          │ Create Logo    │
                                          │ status=PENDING │
                                          │                │
                                          │ S3: logos/temp/│
                                          │     {uploadId}/│
                                          └────────┬───────┘
                                                   │
                                                   v
                                          ┌────────────────┐
                                          │ Return presigned│
                                          │ URL + uploadId │
                                          └────────────────┘

PHASE 2: S3 Upload & Confirmation
┌──────────┐  PUT (presigned URL)        ┌─────────────────┐
│ Frontend │───────────────────────────>│   AWS S3        │
│          │  [binary file data]         │                 │
└──────────┘                              └─────────────────┘
     │
     └────> POST /logos/{uploadId}/confirm ┌─────────────────┐
            {fileExtension, checksum}     │ Update Logo     │
            ─────────────────────────────>│ status=CONFIRMED│
                                           └─────────────────┘

PHASE 3: Entity Association (During Entity Creation)
┌──────────┐  POST /companies             ┌─────────────────┐
│ Frontend │───────────────────────────>│ CompanyService  │
│          │  {name, website, industry,  │                 │
│          │   logoUploadId}              │ 1. Create       │
└──────────┘                              │    company      │
                                           │                 │
                                           │ 2. Associate    │
                                           │    logo         │
                                           │                 │
                                           │ 3. Copy S3 file │
                                           │    temp → final │
                                           │                 │
                                           │ 4. Update Logo  │
                                           │    ASSOCIATED   │
                                           └─────────────────┘
```

### Database Schema

**New Table: `logos`**

```sql
CREATE TABLE logos (
    id UUID PRIMARY KEY,
    upload_id VARCHAR(100) UNIQUE NOT NULL,  -- Public identifier
    s3_key VARCHAR(500) NOT NULL,             -- Current S3 key
    cloudfront_url VARCHAR(1000),             -- CDN URL for access
    file_extension VARCHAR(10) NOT NULL,      -- png, jpg, jpeg, svg
    file_size BIGINT NOT NULL,                -- Size in bytes
    mime_type VARCHAR(100) NOT NULL,          -- image/png, etc.
    checksum VARCHAR(100),                    -- SHA-256 for integrity

    -- State machine
    status VARCHAR(20) NOT NULL,              -- PENDING, CONFIRMED, ASSOCIATED

    -- Entity association (populated when ASSOCIATED)
    associated_entity_type VARCHAR(50),       -- COMPANY, USER, EVENT, etc.
    associated_entity_id VARCHAR(255),        -- Entity's identifier

    -- Lifecycle timestamps
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,                     -- For cleanup job

    -- Performance indexes
    INDEX idx_upload_id (upload_id),
    INDEX idx_status_expires (status, expires_at)
);
```

### Service Architecture

**1. GenericLogoService (Core Upload Logic)**

```java
@Service
@Transactional
public class GenericLogoService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final LogoRepository logoRepository;

    /**
     * Phase 1: Generate presigned URL for upload
     * No entity required - works standalone
     */
    public PresignedUploadUrl generatePresignedUrl(
            String fileName,
            long fileSize,
            String mimeType) {

        validateFile(fileName, fileSize, mimeType);

        String uploadId = UUID.randomUUID().toString();
        String fileId = UUID.randomUUID().toString();
        String extension = getExtension(fileName);

        // Temp S3 key (no entity reference)
        String tempS3Key = generateTempS3Key(uploadId, fileId, extension);

        // Create Logo entity with PENDING status
        Logo logo = Logo.builder()
            .uploadId(uploadId)
            .s3Key(tempS3Key)
            .fileExtension(extension)
            .fileSize(fileSize)
            .mimeType(mimeType)
            .status(LogoStatus.PENDING)
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build();

        logoRepository.save(logo);

        // Generate presigned URL
        String presignedUrl = s3Presigner.presignPutObject(...);

        return new PresignedUploadUrl(presignedUrl, uploadId, fileId, ...);
    }

    /**
     * Phase 2: Confirm upload completion
     * Updates status to CONFIRMED
     */
    public void confirmUpload(String uploadId, String checksum) {
        Logo logo = logoRepository.findByUploadId(uploadId)
            .orElseThrow(() -> new LogoNotFoundException(uploadId));

        logo.setStatus(LogoStatus.CONFIRMED);
        logo.setChecksum(checksum);
        logo.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

        logoRepository.save(logo);
    }

    /**
     * Phase 3: Associate logo with entity
     * Called during entity creation
     */
    public String associateLogoWithEntity(
            String uploadId,
            String entityType,
            String entityId,
            String finalS3Key) {

        Logo logo = logoRepository.findByUploadId(uploadId)
            .orElseThrow(() -> new LogoNotFoundException(uploadId));

        if (logo.getStatus() != LogoStatus.CONFIRMED) {
            throw new IllegalStateException("Logo must be CONFIRMED before association");
        }

        // Copy S3 object from temp to final location
        copyS3Object(logo.getS3Key(), finalS3Key);

        // Delete temp file
        deleteS3Object(logo.getS3Key());

        // Update Logo entity
        logo.setS3Key(finalS3Key);
        logo.setCloudFrontUrl(buildCloudFrontUrl(finalS3Key));
        logo.setStatus(LogoStatus.ASSOCIATED);
        logo.setAssociatedEntityType(entityType);
        logo.setAssociatedEntityId(entityId);
        logo.setExpiresAt(null); // No expiration for associated logos

        logoRepository.save(logo);

        return logo.getCloudFrontUrl();
    }

    private String generateTempS3Key(String uploadId, String fileId, String ext) {
        return String.format("logos/temp/%s/logo-%s.%s", uploadId, fileId, ext);
    }
}
```

**2. LogoCleanupService (Scheduled Cleanup Job)**

```java
@Service
public class LogoCleanupService {

    private final LogoRepository logoRepository;
    private final S3Client s3Client;

    /**
     * Runs daily to clean up orphaned uploads
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void cleanupOrphanedLogos() {
        Instant now = Instant.now();

        // Find expired logos (PENDING > 24h, CONFIRMED > 7 days)
        List<Logo> expiredLogos = logoRepository
            .findByStatusAndExpiresAtBefore(LogoStatus.PENDING, now);

        expiredLogos.addAll(logoRepository
            .findByStatusAndExpiresAtBefore(LogoStatus.CONFIRMED, now));

        for (Logo logo : expiredLogos) {
            try {
                // Delete S3 file
                deleteS3Object(logo.getS3Key());

                // Delete database record
                logoRepository.delete(logo);

                log.info("Cleaned up orphaned logo: {}", logo.getUploadId());
            } catch (Exception e) {
                log.error("Failed to cleanup logo: {}", logo.getUploadId(), e);
            }
        }
    }
}
```

**3. LogoController (REST API)**

```java
@RestController
@RequestMapping("/api/v1/logos")
@Tag(name = "File Upload", description = "Generic file upload operations")
public class LogoController {

    private final GenericLogoService logoService;

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUploadUrl> requestUploadUrl(
            @Valid @RequestBody LogoUploadRequest request) {

        PresignedUploadUrl response = logoService.generatePresignedUrl(
            request.getFileName(),
            request.getFileSize(),
            request.getMimeType()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{uploadId}/confirm")
    public ResponseEntity<Void> confirmUpload(
            @PathVariable String uploadId,
            @Valid @RequestBody LogoUploadConfirmRequest request) {

        logoService.confirmUpload(uploadId, request.getChecksum());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{uploadId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteUnusedLogo(@PathVariable String uploadId) {
        logoService.deleteUnusedLogo(uploadId);
        return ResponseEntity.noContent().build();
    }
}
```

### Integration with Company Creation

**Updated CreateCompanyRequest:**
```java
public class CreateCompanyRequest {
    @NotBlank
    private String name;

    private String displayName;
    private String website;
    private String industry;
    private String description;

    // NEW FIELD: Reference to uploaded logo
    @Schema(description = "Upload ID from /logos/presigned-url")
    private String logoUploadId;  // Optional
}
```

**Updated CompanyService:**
```java
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final GenericLogoService logoService;

    public CompanyResponse createCompany(CreateCompanyRequest request) {
        // Create company entity
        Company company = Company.builder()
            .name(request.getName())
            .displayName(request.getDisplayName())
            .website(request.getWebsite())
            .industry(request.getIndustry())
            .description(request.getDescription())
            .build();

        Company savedCompany = companyRepository.save(company);

        // Associate logo if provided
        if (request.getLogoUploadId() != null) {
            String finalS3Key = generateFinalS3Key(
                company.getName(),
                request.getLogoUploadId()
            );

            String logoUrl = logoService.associateLogoWithEntity(
                request.getLogoUploadId(),
                "COMPANY",
                company.getName(),
                finalS3Key
            );

            savedCompany.setLogoUrl(logoUrl);
            companyRepository.save(savedCompany);
        }

        return mapToResponse(savedCompany);
    }

    private String generateFinalS3Key(String companyName, String uploadId) {
        int year = LocalDate.now().getYear();
        return String.format("logos/%d/companies/%s/logo-%s.png",
            year, companyName, uploadId);
    }
}
```

### Frontend Implementation

**New Reusable FileUpload Component:**
```typescript
// Location: web-frontend/src/components/shared/FileUpload/FileUpload.tsx
interface FileUploadProps {
  currentFileUrl?: string;
  onUploadSuccess?: (data: { uploadId: string; fileUrl: string }) => void;
  onUploadError?: (error: { type: string; message: string }) => void;
  maxFileSize?: number;
  allowedTypes?: string[];
}

export const FileUpload: React.FC<FileUploadProps> = ({
  currentFileUrl,
  onUploadSuccess,
  onUploadError,
  maxFileSize = 5 * 1024 * 1024, // 5MB
  allowedTypes = ['image/png', 'image/jpeg', 'image/svg+xml'],
}) => {
  const uploadFile = async (file: File) => {
    // Phase 1: Request presigned URL (generic endpoint)
    const presignedResponse = await apiClient.post('/logos/presigned-url', {
      fileName: file.name,
      fileSize: file.size,
      mimeType: file.type,
    });

    const { uploadUrl, uploadId, fileExtension } = presignedResponse.data;

    // Phase 2: Upload to S3
    await axios.put(uploadUrl, file, {
      headers: { 'Content-Type': file.type },
      onUploadProgress: (e) => setProgress(e.loaded / e.total * 100),
    });

    // Phase 3: Confirm upload
    await apiClient.post(`/logos/${uploadId}/confirm`, {
      fileExtension,
      checksum: await calculateChecksum(file),
    });

    // Notify parent component
    onUploadSuccess?.({ uploadId, fileUrl: uploadUrl });
  };

  // ... drag-and-drop UI
};
```

**Updated CompanyForm:**
```typescript
export const CompanyForm: React.FC<CompanyFormProps> = ({ ... }) => {
  const [logoUploadId, setLogoUploadId] = useState<string | undefined>();

  const handleLogoUploadSuccess = (data: { uploadId: string }) => {
    setLogoUploadId(data.uploadId); // Store for company creation
  };

  const handleFormSubmit = async (data: CompanyFormData) => {
    const createRequest = {
      ...data,
      logoUploadId, // Include upload ID
    };

    await onSubmit(createRequest);
  };

  return (
    <Dialog open={open}>
      {/* ... other form fields */}

      <FileUpload
        currentFileUrl={logoUrl}
        onUploadSuccess={handleLogoUploadSuccess}
        onUploadError={handleLogoUploadError}
      />
    </Dialog>
  );
};
```

### S3 Key Strategy

**Temporary Storage (During Upload):**
```
logos/temp/{uploadId}/logo-{fileId}.{ext}

Example:
logos/temp/abc123-def456/logo-f3e8d1a4.png
```

**Final Storage (After Association):**
```
logos/{year}/{entity-type}/{entity-name}/logo-{fileId}.{ext}

Examples:
- logos/2025/companies/Swisscom-AG/logo-f3e8d1a4.png
- logos/2025/users/john-doe/profile-a1b2c3d4.jpg (future)
- logos/2025/events/bat-bern-2025/banner-e5f6g7h8.jpg (future)
```

### API Changes

**New Endpoints:**
```
✅ POST   /api/v1/logos/presigned-url      → Generate upload URL (no auth)
✅ POST   /api/v1/logos/{uploadId}/confirm → Confirm upload
✅ DELETE /api/v1/logos/{uploadId}         → Delete unused logo
```

**Updated Endpoints:**
```
✅ POST /api/v1/companies
   Request: { name, website, industry, logoUploadId }
   → Now accepts logoUploadId for logo association
```

**Removed Endpoints (Clean Break):**
```
❌ POST /api/v1/companies/{name}/logo/presigned-url
❌ POST /api/v1/companies/{name}/logo/confirm
```

## Scope Clarification (2026-03-03)

### When to Use GenericLogoService vs Entity-Specific Presigned URL

**ADR-002 was designed to solve one specific problem: circular dependency between entity creation and file upload.** The three-phase PENDING → CONFIRMED → ASSOCIATED state machine is the right tool when a file must be uploaded *before* the entity it belongs to has been created.

#### Use `GenericLogoService` (ADR-002 pattern) when:
- Uploading a file **at entity-creation time** — the entity does not yet exist in the DB
- Example: uploading a company logo while filling out the "Create Company" form
- Example: uploading a profile picture during new user registration

#### Use **entity-specific presigned URL pattern** (direct `S3Presigner` + `S3Client`) when:
- Uploading or managing files **on an existing entity** — the entity is already persisted
- The circular dependency problem does not apply
- Example: `SpeakerProfilePhotoService` — speaker already exists, organizer adds/replaces photo
- Example: `EventPhotoService` (Story 10.21) — event already exists, organizer uploads post-event photos
- Example: `EventTeaserImageService` (Story 10.22) — event already exists, organizer uploads teaser images

The entity-specific pattern for existing-entity uploads is **compliant with the spirit of ADR-002** — it is not a workaround or drift. The `GenericLogoService` should not be called cross-service from `event-management-service` or `speaker-coordination-service` purely for uploads to already-persisted entities.

#### Pattern summary

| Scenario | Pattern | Service |
|----------|---------|---------|
| Create entity + upload file atomically | GenericLogoService (PENDING → CONFIRMED → ASSOCIATED) | company-user-management-service |
| Add/replace file on existing entity | Direct S3Presigner + S3Client in domain service | Domain service (e.g. event-management-service) |

This clarification is retroactively valid for `SpeakerProfilePhotoService` and is the governing rule for all future file upload stories involving existing entities.

## Consequences

### Positive

1. **✅ Eliminates Circular Dependency**: Files can be uploaded before entities exist
   - Logo upload is completely independent of company creation
   - User can upload file first, then create company
   - Single atomic operation from user's perspective

2. **✅ Highly Reusable Architecture**: One service works for all entity types
   - Company logos (immediate)
   - User profile pictures (future)
   - Event banners (future)
   - Partner logos (future)
   - Speaker photos (future)

3. **✅ Better User Experience**: Single seamless operation
   - User provides all data (including logo) in one form
   - No confusing multi-step workflow
   - Immediate visual feedback (logo preview)

4. **✅ Scalable Design**: Direct client-to-S3 uploads
   - Backend never proxies file data (5MB files)
   - Reduced backend bandwidth costs
   - Better performance for large files
   - Easy progress tracking on client

5. **✅ Clean Separation of Concerns**: Upload logic independent of business entities
   - GenericLogoService has no knowledge of Company/User/Event
   - Business services (CompanyService) decide when to associate
   - Clear state machine (PENDING → CONFIRMED → ASSOCIATED)

6. **✅ Type-Safe State Management**: Explicit lifecycle states
   - PENDING: Upload initiated, file may not exist in S3 yet
   - CONFIRMED: File successfully uploaded to S3 and verified
   - ASSOCIATED: Linked to entity, file in final location

7. **✅ Automatic Resource Cleanup**: Scheduled job prevents S3 bloat
   - PENDING uploads > 24 hours old: Removed (user abandoned upload)
   - CONFIRMED uploads > 7 days old: Removed (entity never created)
   - ASSOCIATED uploads: Kept indefinitely (in use)
   - S3 storage costs minimized

8. **✅ Future-Proof Design**: Ready for additional entity types
   - Add User profile pictures: No changes to GenericLogoService
   - Add Event banners: No changes to GenericLogoService
   - Just update entity services to use logoUploadId pattern

### Negative

1. **⚠️ Increased System Complexity**: More moving parts
   - New service (GenericLogoService)
   - New database table (logos)
   - New scheduled job (LogoCleanupService)
   - New state machine to understand
   - More code to maintain

2. **⚠️ Temporary Storage Costs**: Orphaned uploads accumulate
   - Users may upload files but never create company
   - Files sit in S3 temp/ for up to 24 hours before cleanup
   - Minimal cost impact (estimate: <$1/month for 1000 orphans)

3. **⚠️ Breaking Change**: Old endpoints removed
   - Cannot deploy backend without frontend update
   - Requires coordinated deployment
   - No backward compatibility with old clients

4. **⚠️ Additional S3 Operations**: Copy operation on association
   - Extra S3 API call (copy temp → final)
   - Adds ~500ms latency to company creation
   - Acceptable tradeoff for architectural benefits

5. **⚠️ Database Growth**: Logos table accumulates records
   - One record per upload attempt (including abandoned)
   - Cleanup job runs daily (not real-time)
   - Estimate: 10-100 GB over 5 years (negligible)

6. **⚠️ More Complex Testing**: Multiple phases to test
   - Unit tests for each phase (generate, confirm, associate)
   - Integration tests for full flow
   - Test cleanup job behavior
   - More test code required

### Neutral

1. **Migration Required**: Frontend and backend must be updated together
   - Coordinated deployment necessary
   - Rollback requires reverting both frontend and backend
   - Not an issue with proper CI/CD

2. **Monitoring Needed**: Track system health
   - Orphaned upload rate (should be low)
   - Cleanup job success rate (should be 100%)
   - S3 temp/ directory size (should be small)
   - CloudWatch alarms recommended

3. **Database Records Persist**: Logos table grows over time
   - Associated logos kept indefinitely (business requirement)
   - Soft delete if entity deleted (for audit trail)
   - Periodic archive to cold storage (future consideration)

## Implementation Status

### Completed
- ✅ Architectural design documented (ADR-002)
- ✅ Story created (1.16.3)
- ✅ Database schema designed

### In Progress (Story 1.16.3)
- ⏳ V6 Flyway migration creation
- ⏳ Logo entity and repository
- ⏳ GenericLogoService implementation
- ⏳ LogoCleanupService implementation
- ⏳ LogoController REST endpoints
- ⏳ Frontend FileUpload component
- ⏳ Frontend useFileUpload hook
- ⏳ CompanyService integration
- ⏳ Test suite

### Planned (Post-Implementation)
- ⏳ User profile picture support (Story TBD)
- ⏳ Event banner support (Story TBD)
- ⏳ CloudWatch dashboards for monitoring
- ⏳ S3 lifecycle policies for automated archival

## Alternatives Considered

### Alternative 1: Keep Entity-Specific Endpoints (Status Quo)

**Decision**: REJECTED

**Approach**: Continue using `POST /companies/{name}/logo/presigned-url` and workarounds

**Why Rejected**:
- Does not solve circular dependency
- Poor user experience (multi-step process)
- Not reusable for other entities (User, Event, etc.)
- Technical debt accumulates
- Same problem will recur for every entity type

### Alternative 2: Maintain Backward Compatibility

**Decision**: REJECTED per user feedback

**Approach**: Keep old company-specific endpoints alongside new generic endpoints

**Why Rejected**:
- Adds complexity maintaining two upload flows
- Confuses developers about which flow to use
- Delays deprecation indefinitely
- More test coverage required
- User explicitly requested clean break

### Alternative 3: Base64 Inline Upload in Request

**Decision**: REJECTED

**Approach**: Send logo as base64 string in `CreateCompanyRequest` JSON

```json
{
  "name": "Swisscom AG",
  "website": "https://swisscom.ch",
  "logoBase64": "data:image/png;base64,iVBORw0KGgo..."
}
```

**Why Rejected**:
- **33% larger payload**: 5MB file becomes ~6.6MB base64
- **Backend becomes proxy**: Must decode and upload to S3 (bandwidth cost)
- **Slower uploads**: No parallel upload, no progress tracking
- **Request size limits**: Many API gateways limit request to 6MB
- **Poor performance**: Single synchronous operation

### Alternative 4: Multipart Form Upload

**Decision**: REJECTED

**Approach**: Accept logo file directly in POST /companies as multipart/form-data

```http
POST /api/v1/companies
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="name"

Swisscom AG
--boundary
Content-Disposition: form-data; name="logo"; filename="logo.png"
Content-Type: image/png

[binary data]
--boundary--
```

**Why Rejected**:
- **Backend proxies files**: 5MB files flow through backend (bandwidth cost)
- **Loses presigned URL benefits**: Direct-to-S3 upload is more scalable
- **Harder progress tracking**: Cannot use XHR progress events easily
- **Synchronous operation**: Blocks request until upload completes
- **Less flexible**: Cannot reuse upload across multiple operations

### Alternative 5: Pre-allocation Pattern (Token-Based Upload)

**Decision**: CONSIDERED but chose Generic Service instead

**Approach**: Generate temporary upload token, associate after entity creation

```
1. POST /upload-tokens → {token, presignedUrl}
2. Upload to S3 using presignedUrl
3. POST /companies {name, uploadToken}
```

**Why Not Chosen**:
- Similar complexity to generic service
- Less flexible (tokens tied to specific use cases)
- Generic service is more reusable
- Tokens add another concept to understand
- Generic service has clearer semantics (uploadId vs token)

**Note**: Pre-allocation pattern is a valid alternative and was close runner-up. Generic service was chosen for its broader reusability.

## References

- **AWS Documentation**:
  - [S3 Presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html)
  - [S3 Copy Object](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CopyObject.html)
  - [CloudFront Distribution](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html)

- **Related Stories**:
  - Story 1.16.3: Generic File Upload Service (this implementation)
  - Story 2.5.1: Company Management Frontend
  - Story 1.16.2: Dual-identifier pattern (UUID internal, name public)

- **Related ADRs**:
  - ADR-001: Invitation-Based User Registration Architecture

- **Architecture Docs**:
  - 03-data-architecture.md: Database schemas and patterns
  - 04-api-design.md: RESTful API patterns
  - 06-backend-architecture.md: Spring Boot service patterns
  - 05-frontend-architecture.md: React component patterns

## Related Files

### Backend (Story 1.16.3)

**New Files:**
- `services/company-user-management-service/src/main/resources/db/migration/V6__Create_logos_table.sql` - Database schema
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/Logo.java` - Entity
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/LogoStatus.java` - Status enum
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/repository/LogoRepository.java` - Repository
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/GenericLogoService.java` - Core service
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/LogoCleanupService.java` - Cleanup job
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/LogoController.java` - REST API

**Modified Files:**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/CreateCompanyRequest.java` - Add logoUploadId
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CompanyService.java` - Logo association
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/CompanyController.java` - Remove old endpoints

**Deleted Files:**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CompanyLogoService.java` - Replaced by GenericLogoService

### Frontend (Story 1.16.3)

**New Files:**
- `web-frontend/src/components/shared/FileUpload/FileUpload.tsx` - Reusable upload component
- `web-frontend/src/components/shared/FileUpload/FileUpload.test.tsx` - Component tests
- `web-frontend/src/hooks/useFileUpload/useFileUpload.ts` - Upload hook
- `web-frontend/src/hooks/useFileUpload/useFileUpload.test.ts` - Hook tests

**Modified Files:**
- `web-frontend/src/components/shared/Company/CompanyForm.tsx` - Use new FileUpload

**Deleted Files:**
- `web-frontend/src/components/shared/Company/LogoUpload.tsx` - Replaced by generic FileUpload
- `web-frontend/src/components/shared/Company/__tests__/LogoUpload.test.tsx` - Obsolete tests

### Documentation

**New Files:**
- `docs/architecture/ADR-002-generic-file-upload-service.md` - This document
- `docs/stories/1.16.3-generic-file-upload-service.md` - Implementation story

## Notes

This decision represents a significant architectural improvement from **entity-coupled** to **entity-agnostic** file upload architecture.

### Key Architectural Benefits

1. **Decoupling**: File uploads completely independent of entity lifecycle
2. **Reusability**: Single service handles all entity types (COMPANY, USER, EVENT, etc.)
3. **Scalability**: Direct client-to-S3 uploads (no backend proxy)
4. **Maintainability**: Clear state machine, explicit lifecycle, automatic cleanup
5. **User Experience**: Single atomic operation from user's perspective

### State Machine Summary

```
┌─────────┐  generatePresignedUrl()   ┌───────────┐
│ (Start) │──────────────────────────>│  PENDING  │
└─────────┘                            └─────┬─────┘
                                             │
                                             │ confirmUpload()
                                             │
                                             v
                                       ┌───────────┐
                                       │ CONFIRMED │
                                       └─────┬─────┘
                                             │
                                             │ associateLogoWithEntity()
                                             │
                                             v
                                       ┌─────────────┐
                                       │ ASSOCIATED  │
                                       │ (terminal)  │
                                       └─────────────┘

Cleanup transitions:
- PENDING > 24h → Deleted
- CONFIRMED > 7 days → Deleted
- ASSOCIATED → Kept indefinitely
```

### S3 Storage Strategy

**Temporary Location** (PENDING/CONFIRMED):
- Path: `logos/temp/{uploadId}/`
- Lifetime: 24 hours (PENDING) or 7 days (CONFIRMED)
- Cleaned up automatically

**Final Location** (ASSOCIATED):
- Path: `logos/{year}/{entity-type}/{entity-name}/`
- Lifetime: Indefinite (business data)
- Served via CloudFront CDN

### Success Metrics

Post-implementation monitoring should track:

**Performance Metrics:**
- Presigned URL generation: < 100ms (P95)
- S3 copy operation: < 2s (P95)
- Cleanup job duration: < 30s for 1000+ logos

**Operational Metrics:**
- Orphaned upload rate: < 10% of total uploads
- Cleanup job success rate: > 99%
- S3 temp/ directory size: < 1GB at any time

**User Experience Metrics:**
- Company creation with logo: Single operation success
- Upload progress feedback: Real-time updates
- Error rate: < 1% of upload attempts

The Generic File Upload Service provides a solid, scalable foundation for all file upload needs across the BATbern platform, eliminating circular dependencies while maintaining excellent user experience.
