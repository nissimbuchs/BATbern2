# Story: Backend DTO Migration: Users API Cleanup (ADR-006)

**Linear Issue**: [BAT-21](https://linear.app/batbern/issue/BAT-21/backend-dto-migration-users-api-cleanup-adr-006) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-21)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-21) (see checkboxes)
- **Tasks/Subtasks**: [Linear subtasks](https://linear.app/batbern/issue/BAT-21)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-21)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-21)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
- Backend: `docs/templates/backend/spring-boot-service-foundation.md`
- Backend: `docs/templates/backend/integration-test-pattern.md`

**Existing Code References**:
- Reference: User API already uses generated DTOs extensively
  - UserResponseMapper: `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserResponseMapper.java`
  - UserService: `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java`

### Migration Plan Reference

**Source**: `docs/plans/backend-dto-openapi-migration-plan.md#story-5-users-api-cleanup`

**Note**: This is a cleanup task - Users API is mostly compliant already. Only 3 manual DTOs remain for profile picture upload.

### OpenAPI Spec Verification

**File**: `docs/api/users-api.openapi.yml`

**Schemas to Verify**:
1. `PresignedUploadUrl` - Should have fields: `uploadUrl`, `fileId`, `expiresAt`
2. `ProfilePictureUploadRequest` - Request to initiate upload
3. `ProfilePictureUploadConfirmRequest` - Confirm successful upload

**If schemas are missing**, add them based on manual DTO structure.

### Manual DTOs to Delete

**After migration is complete** (3 files only):
```bash
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/ProfilePictureUploadRequest.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/ProfilePictureUploadConfirmRequest.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/PresignedUploadUrl.java
```

### Test Implementation Details

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2.

#### Test File Locations

**Backend Tests**:
- Integration: `services/company-user-management-service/src/test/integration/controller/ProfilePictureControllerIntegrationTest.java`
- Unit: `services/company-user-management-service/src/test/unit/service/ProfilePictureServiceTest.java`

**Frontend Tests** (minimal impact):
- `web-frontend/src/components/profile/ProfilePictureUpload.test.tsx`
- `web-frontend/src/services/userService.test.ts` (profile picture methods)

### Story-Specific Implementation

**Presigned URL Pattern** (critical for file uploads):
```java
// Generate presigned URL for direct S3 upload
PresignedUploadUrl presignedUrl = s3Service.generatePresignedUploadUrl(
    bucket,
    key,
    contentType,
    Duration.ofMinutes(15)
);

// Return to frontend (no file proxy through backend)
return ResponseEntity.ok(presignedUrl);
```

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#bat-21` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- {files}

**Modified**:
- {files}

**Deleted**:
- {files}

### Change Log
- {date}: {change}

### Deployment Notes
**No breaking changes** - API structure unchanged (only implementation)

### Status
Draft
