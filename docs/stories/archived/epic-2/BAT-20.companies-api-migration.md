# Story: Backend DTO Migration: Companies API (ADR-006 Only)

**Linear Issue**: [BAT-20](https://linear.app/batbern/issue/BAT-20/backend-dto-migration-companies-api-adr-006-only) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-20)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-20) (see checkboxes)
- **Tasks/Subtasks**: [Linear subtasks](https://linear.app/batbern/issue/BAT-20)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-20)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-20)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
- Backend: `docs/templates/backend/spring-boot-service-foundation.md`
- Backend: `docs/templates/backend/integration-test-pattern.md`

**Existing Code References**:
- Reference Implementation: User API (already uses generated DTOs)
  - UserResponseMapper: `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserResponseMapper.java`
  - UserService: `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java`

### Migration Plan Reference

**Source**: `docs/plans/backend-dto-openapi-migration-plan.md#story-4-companies-api-migration`

**Note**: This is the SIMPLEST migration because:
- Already ADR-003 compliant (uses `name` as identifier)
- No UUID changes needed
- Good practice run before tackling larger migrations

### OpenAPI Spec Reconciliation

**File**: `docs/api/companies-api.openapi.yml`

**Field Discrepancies to Fix** (from Appendix D.2 in plan):

| Field | OpenAPI Spec | Manual DTO | Decision Required |
|-------|--------------|------------|-------------------|
| `name` pattern | `^[A-Za-z0-9]+$` | Allows spaces | ⚠️ OpenAPI is stricter |
| `description` maxLength | 5000 | 2000 | ⚠️ Mismatch: recommend 5000 |
| `logoUploadId` | ❌ MISSING | ✅ String | ⚠️ Add to OpenAPI spec |

**Reconciliation Actions**:
1. Add `logoUploadId` field to CreateCompanyRequest schema
2. Add `logoUploadId` field to UpdateCompanyRequest schema
3. Set `description` maxLength to 5000 (more generous)
4. Verify `name` pattern matches current behavior

### Build Configuration

**Already Configured**: `services/company-user-management-service/build.gradle`

```gradle
task openApiGenerateCompanies(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
    inputSpec = "$rootDir/docs/api/companies-api.openapi.yml"
    outputDir = "$buildDir/generated-companies"
    modelPackage = 'ch.batbern.companyuser.dto.generated.companies'
    generateBuilders = 'true'
    // ... existing configuration
}
```

✅ Configuration already exists, just need to use it.

### Manual DTOs to Delete

**After migration is complete** (11 files):
```bash
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/CompanyResponse.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/CreateCompanyRequest.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/UpdateCompanyRequest.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/CompanySearchResponse.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/PaginatedCompanyResponse.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/UIDValidationResponse.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/CompanyLogo.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/CompanyStatistics.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/LogoUploadRequest.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/LogoUploadConfirmRequest.java
rm services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/LogoUploadConfirmResponse.java
```

### Test Implementation Details

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2.

#### Test File Locations

**Backend Tests**:
- Integration: `services/company-user-management-service/src/test/integration/controller/CompanyControllerIntegrationTest.java`
- Unit: `services/company-user-management-service/src/test/unit/mapper/CompanyMapperTest.java`
- Unit: `services/company-user-management-service/src/test/unit/service/CompanyServiceTest.java`

**Frontend Tests** (minimal impact expected):
- `web-frontend/src/components/companies/CompanyManagement.test.tsx`
- `web-frontend/src/services/companyService.test.ts`

### Story-Specific Implementation

**Deviations from Templates**:
```java
// To be filled during implementation
// ONLY code that differs from standard patterns
```

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#bat-20` for detailed implementation debugging

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
