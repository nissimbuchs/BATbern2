# Story 10.20: Legacy BAT Format Data Export & Import (Admin Tool)

Status: ready-for-dev

<!-- Prerequisite: Story 10.1 (Admin page must exist — ✅ done) -->

## Story

As an **organizer**,
I want to export all BATbern data in the legacy BAT JSON format and import data in that same format,
so that I can migrate data between system versions and maintain interoperability with the old BATspa platform.

## Acceptance Criteria

1. **AC1 — JSON Export**: `GET /api/v1/admin/export/legacy` returns a valid legacy JSON file for download (organizer-only; 403 for other roles); envelope: `{ version, exportedAt, events[], companies[], speakers[], attendees[] }`.
2. **AC2 — Asset Manifest Export**: `GET /api/v1/admin/export/assets` returns a JSON presigned-URL manifest `{ exportedAt, assetCount, assets: [{ type, entityId, filename, presignedUrl }] }`; each URL valid 1 hour; organizer-only.
3. **AC3 — JSON Import**: `POST /api/v1/admin/import/legacy` (multipart `file`) upserts all entity types; returns `{ imported: { events, sessions, speakers, companies, attendees }, skipped: [], errors: [] }`; idempotent (importing same file twice has no side effects); 400 with structured errors on invalid JSON.
4. **AC4 — Asset Import**: `POST /api/v1/admin/import/assets` accepts a ZIP file; unpacks to S3 under `imports/{timestamp}/` prefix; links assets to entities by filename convention.
5. **AC5 — Frontend Tab**: Admin page Tab 5 "Export / Import" shows export buttons and import file pickers with result summary after import.
6. **AC6 — Confirmation dialog**: Any import action shows confirmation dialog before execution.
7. **AC7 — TDD**: `LegacyExportServiceTest` and `LegacyImportServiceTest` written first (RED); integration test covers export → import → verify DB round-trip; all tests pass; Checkstyle passes.
8. **AC8 — OpenAPI first**: `docs/api/events-api.openapi.yml` updated before implementation per ADR-006.
9. **AC9 — i18n**: `admin.exportImport.*` keys added to all 10 locale files (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE); type-check passes.

---

## Tasks / Subtasks

### Phase 0: Pre-checks (read before touching anything)

- [ ] **T0 — Understand company data access** (AC: #1)
  - [ ] T0.1 — Read `UserApiClient.java` fully to check if `getAllCompanies()` or similar exists:
    ```
    services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java
    ```
  - [ ] T0.2 — If no company bulk-fetch method exists, check `user-service.base-url` config and plan `GET /api/v1/companies` call to company-user-management-service via RestTemplate (add `getAllCompanies()` to `UserApiClient` or create a dedicated `CompanyApiClient`).
  - [ ] T0.3 — Check `UserApiClient` for `getUsersByRole(String role)` or similar to gather SPEAKER users. If absent, use `getOrganizerUsernames()` as a reference and add `getSpeakerUsernames()`.

- [ ] **T1 — Check Flyway version** (AC: #7)
  - [ ] T1.1 — List `services/event-management-service/src/main/resources/db/migration/` — find the highest V number. V77 is Story 10.16. No migration needed for 10.20 (export/import operates on existing tables), but confirm.

- [ ] **T2 — Read existing export pattern** (AC: #1)
  - [ ] T2.1 — Read `PartnerAttendanceExportService.java` to understand the `ResponseEntity<byte[]>` + `Content-Disposition: attachment` pattern used project-wide.
  - [ ] T2.2 — Read `AwsConfig.java` to confirm `S3Client` and `S3Presigner` beans are available (they are — from Story 10.16 analysis). No new S3 config needed.
  - [ ] T2.3 — Read `AdminController.java` to understand the `/api/v1/admin` base path and existing backfill pattern.

### Phase 1: OpenAPI specification (MANDATORY FIRST per ADR-006)

- [ ] **T3 — Add 4 endpoints to `docs/api/events-api.openapi.yml`** (AC: #8)
  - [ ] T3.1 — Add new tag `Admin Export/Import` or append to existing `Admin` tag.
  - [ ] T3.2 — Add `GET /admin/export/legacy`:
    ```yaml
    /admin/export/legacy:
      get:
        tags: [Admin Export/Import]
        summary: Export all data in legacy BAT JSON format
        operationId: exportLegacyData
        security:
          - cognito: [ORGANIZER]
        responses:
          '200':
            description: Legacy JSON file download
            content:
              application/json:
                schema:
                  $ref: '#/components/schemas/LegacyExportEnvelope'
            headers:
              Content-Disposition:
                schema:
                  type: string
                  example: 'attachment; filename=batbern-export-2026-03-02.json'
          '403':
            description: Forbidden – organizer role required
    ```
  - [ ] T3.3 — Add `GET /admin/export/assets` with `AssetManifestResponse` schema.
  - [ ] T3.4 — Add `POST /admin/import/legacy` (multipart `file`, returns `LegacyImportResult`).
  - [ ] T3.5 — Add `POST /admin/import/assets` (multipart `file` ZIP, returns `AssetImportResult`).
  - [ ] T3.6 — Define schemas in `components/schemas`:
    - `LegacyExportEnvelope`: `{ version: string, exportedAt: string, events: [], companies: [], speakers: [], attendees: [] }`
    - `AssetManifestResponse`: `{ exportedAt: string, assetCount: integer, assets: [{ type, entityId, filename, presignedUrl }] }`
    - `LegacyImportResult`: `{ imported: { events: int, sessions: int, speakers: int, companies: int, attendees: int }, skipped: [string], errors: [string] }`
    - `AssetImportResult`: `{ importedAt: string, importedCount: integer, s3Prefix: string, errors: [string] }`
  - [ ] T3.7 — Regenerate frontend types: `cd web-frontend && npm run generate:api-types 2>&1 | tee /tmp/codegen-10-20.log`

### Phase 2: Backend DTOs

- [ ] **T4 — Create DTO classes** (AC: #1, #3)
  - [ ] T4.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/dto/export/LegacyExportEnvelope.java`:
    ```java
    @Data @Builder
    public class LegacyExportEnvelope {
        private String version;           // e.g., "2.0"
        private Instant exportedAt;
        private List<LegacyEventDto> events;
        private List<LegacyCompanyDto> companies;
        private List<LegacySpeakerDto> speakers;
        private List<LegacyAttendeeDto> attendees;
    }
    ```
  - [ ] T4.2 — Create nested DTO classes (`LegacyEventDto`, `LegacySessionDto`, `LegacyCompanyDto`, `LegacySpeakerDto`, `LegacyAttendeeDto`) matching the legacy BATspa JSON structure. **Research the legacy format**: examine any existing data in `apps/BATspa-old/` or ask Nissim for a sample export if needed.
  - [ ] T4.3 — Create `LegacyImportResult.java` and `AssetManifestResponse.java`.

### Phase 3: Backend Services (TDD)

- [ ] **T5 — Write LegacyExportServiceTest FIRST (RED phase)** (AC: #7)
  - [ ] T5.1 — Create `services/event-management-service/src/test/java/ch/batbern/events/service/LegacyExportServiceTest.java`
  - [ ] T5.2 — Use `@ExtendWith(MockitoExtension.class)` (unit test, no Spring context):
    ```java
    @ExtendWith(MockitoExtension.class)
    class LegacyExportServiceTest {
        @Mock EventRepository eventRepository;
        @Mock SessionRepository sessionRepository;
        @Mock RegistrationRepository registrationRepository;
        @Mock SpeakerRepository speakerRepository;
        @Mock UserApiClient userApiClient;
        @Mock S3Presigner s3Presigner;
        @InjectMocks LegacyExportService service;
    ```
  - [ ] T5.3 — Tests:
    - `exportAll() with 1 event, 2 sessions → envelope contains correct events and sessions`
    - `exportAll() with 1 speaker with portrait S3 key → speaker dto contains s3Key`
    - `exportAll() with no data → envelope has version, exportedAt, and empty lists (no NPE)`
    - `exportAssetManifest() with 1 speaker portrait, 1 company logo, 1 material → returns manifest with 3 entries, all with presignedUrl`
    - `exportAssetManifest() with entity missing s3Key → entity is skipped gracefully`
  - [ ] T5.4 — Run RED: `./gradlew :services:event-management-service:test --tests "*LegacyExportServiceTest" 2>&1 | tee /tmp/test-10-20-export-red.log`

- [ ] **T6 — Implement LegacyExportService** (AC: #1, #2)
  - [ ] T6.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/LegacyExportService.java`:
    ```java
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public class LegacyExportService {

        private static final String EXPORT_VERSION = "2.0";

        private final EventRepository eventRepository;
        private final SessionRepository sessionRepository;
        private final RegistrationRepository registrationRepository;
        private final SpeakerRepository speakerRepository;
        private final UserApiClient userApiClient;
        private final S3Presigner s3Presigner;

        @Value("${aws.s3.bucket-name}")
        private String bucketName;

        public LegacyExportEnvelope exportAll() {
            List<Event> events = eventRepository.findAll();
            // Map to LegacyEventDto with nested sessions
            // Map registrations per event for attendees list
            // Collect company IDs, fetch via userApiClient
            // Map speaker profiles
            return LegacyExportEnvelope.builder()
                .version(EXPORT_VERSION)
                .exportedAt(Instant.now())
                .events(mapEvents(events))
                .companies(mapCompanies(events))
                .speakers(mapSpeakers())
                .attendees(mapAttendees(events))
                .build();
        }

        public AssetManifestResponse exportAssetManifest() {
            List<AssetEntry> assets = new ArrayList<>();

            // 1. Speaker portraits
            speakerRepository.findAll().stream()
                .filter(s -> s.getProfilePictureS3Key() != null)
                .forEach(s -> assets.add(buildAssetEntry("portrait", s.getId(), s.getProfilePictureS3Key())));

            // 2. Company logos — fetch via user service (CompanyApiClient or UserApiClient)
            // 3. Session materials
            // sessionMaterialsRepository.findAll().forEach(m -> assets.add(...))

            return AssetManifestResponse.builder()
                .exportedAt(Instant.now())
                .assetCount(assets.size())
                .assets(assets)
                .build();
        }

        private AssetEntry buildAssetEntry(String type, UUID entityId, String s3Key) {
            GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(req -> req.bucket(bucketName).key(s3Key).build())
                .build();
            String presignedUrl = s3Presigner.presignGetObject(presign).url().toString();
            String filename = s3Key.substring(s3Key.lastIndexOf('/') + 1);
            return AssetEntry.builder()
                .type(type).entityId(entityId).filename(filename).presignedUrl(presignedUrl)
                .build();
        }
    }
    ```
  - [ ] T6.2 — Run GREEN: `./gradlew :services:event-management-service:test --tests "*LegacyExportServiceTest" 2>&1 | tee /tmp/test-10-20-export-green.log`

- [ ] **T7 — Write LegacyImportServiceTest FIRST (RED phase)** (AC: #7)
  - [ ] T7.1 — Create `LegacyImportServiceTest.java`
  - [ ] T7.2 — Use `@ExtendWith(MockitoExtension.class)`:
    ```java
    @ExtendWith(MockitoExtension.class)
    class LegacyImportServiceTest {
        @Mock EventRepository eventRepository;
        @Mock SessionRepository sessionRepository;
        @Mock RegistrationRepository registrationRepository;
        @Mock SpeakerRepository speakerRepository;
        @Mock UserApiClient userApiClient;
        @InjectMocks LegacyImportService service;
    ```
  - [ ] T7.3 — Tests:
    - `importAll() with 1 event (new) → eventRepository.save() called once; result.events == 1`
    - `importAll() with 1 event (existing eventCode) → event is UPDATED (upsert), not duplicated`
    - `importAll() idempotent → importing same envelope twice produces result.events == 1 both times`
    - `importAll() with invalid envelope (null events) → throws LegacyImportException or returns error list`
    - `importAll() with 3 attendees (registered status) → 3 registrations saved with status='registered'`
  - [ ] T7.4 — Run RED: `./gradlew :services:event-management-service:test --tests "*LegacyImportServiceTest" 2>&1 | tee /tmp/test-10-20-import-red.log`

- [ ] **T8 — Implement LegacyImportService** (AC: #3)
  - [ ] T8.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/LegacyImportService.java`:
    ```java
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public class LegacyImportService {

        private final EventRepository eventRepository;
        private final SessionRepository sessionRepository;
        private final RegistrationRepository registrationRepository;
        private final SpeakerRepository speakerRepository;
        private final UserApiClient userApiClient;

        @Transactional
        public LegacyImportResult importAll(LegacyExportEnvelope envelope) {
            LegacyImportResult.Builder result = LegacyImportResult.builder();

            // Upsert events by eventCode
            int eventCount = 0;
            for (LegacyEventDto dto : envelope.getEvents()) {
                Event existing = eventRepository.findByEventCode(dto.getEventCode()).orElse(null);
                if (existing != null) {
                    mapToExistingEvent(existing, dto);
                    eventRepository.save(existing);
                } else {
                    eventRepository.save(mapToNewEvent(dto));
                }
                eventCount++;
            }
            result.events(eventCount);

            // Upsert sessions, speakers, companies, attendees similarly...
            // Attendee registrations: always status = 'registered'

            return result.build();
        }
    }
    ```
  - [ ] T8.2 — Run GREEN: `./gradlew :services:event-management-service:test --tests "*LegacyImportServiceTest" 2>&1 | tee /tmp/test-10-20-import-green.log`

### Phase 4: Controller

- [ ] **T9 — Create AdminExportImportController** (AC: #1, #2, #3, #4)
  - [ ] T9.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/controller/AdminExportImportController.java`:
    ```java
    @RestController
    @RequestMapping("/api/v1/admin")
    @PreAuthorize("hasRole('ORGANIZER')")
    @RequiredArgsConstructor
    @Slf4j
    public class AdminExportImportController {

        private final LegacyExportService exportService;
        private final LegacyImportService importService;
        private final ObjectMapper objectMapper;

        @GetMapping("/export/legacy")
        public ResponseEntity<byte[]> exportLegacy() throws JsonProcessingException {
            LegacyExportEnvelope envelope = exportService.exportAll();
            byte[] json = objectMapper.writeValueAsBytes(envelope);
            String filename = "batbern-export-" + LocalDate.now() + ".json";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
        }

        @GetMapping("/export/assets")
        public ResponseEntity<AssetManifestResponse> exportAssets() {
            return ResponseEntity.ok(exportService.exportAssetManifest());
        }

        @PostMapping(value = "/import/legacy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<LegacyImportResult> importLegacy(
                @RequestParam("file") MultipartFile file) throws IOException {
            LegacyExportEnvelope envelope = objectMapper.readValue(
                file.getBytes(), LegacyExportEnvelope.class);
            return ResponseEntity.ok(importService.importAll(envelope));
        }

        @PostMapping(value = "/import/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<AssetImportResult> importAssets(
                @RequestParam("file") MultipartFile file) throws IOException {
            return ResponseEntity.ok(importService.importAssets(file));
        }
    }
    ```
  - [ ] T9.2 — **Multipart size**: Verify `spring.servlet.multipart.max-file-size` in `application.yml`. ZIP files can be large. Check existing value; may need to increase to `500MB` for asset import.
  - [ ] T9.3 — **Security**: `@PreAuthorize("hasRole('ORGANIZER')")` at class level covers all 4 endpoints.

### Phase 5: Integration Test

- [ ] **T10 — Write integration test (RED phase)** (AC: #7)
  - [ ] T10.1 — Create `services/event-management-service/src/test/java/ch/batbern/events/controller/AdminExportImportControllerIntegrationTest.java`
  - [ ] T10.2 — Extends `AbstractIntegrationTest`; uses `@SpringBootTest`, `@AutoConfigureMockMvc`
  - [ ] T10.3 — Test: `GET /api/v1/admin/export/legacy as ORGANIZER → 200 with Content-Disposition attachment`
  - [ ] T10.4 — Test: `GET /api/v1/admin/export/legacy as PARTNER → 403`
  - [ ] T10.5 — Test: `POST /api/v1/admin/import/legacy with valid JSON → 200 with import result`
  - [ ] T10.6 — Test: `POST /api/v1/admin/import/legacy with invalid JSON → 400 with error message`
  - [ ] T10.7 — Test (round-trip): seed 1 event → export → clear DB → import → verify event re-created
  - [ ] T10.8 — Run RED: `./gradlew :services:event-management-service:test --tests "*AdminExportImport*" 2>&1 | tee /tmp/test-10-20-integration-red.log`

- [ ] **T11 — Run integration tests GREEN** (AC: #7)
  - `./gradlew :services:event-management-service:test --tests "*AdminExportImport*" 2>&1 | tee /tmp/test-10-20-integration-green.log`

### Phase 6: Frontend — ExportImportTab

- [ ] **T12 — Create ExportImportTab.tsx** (AC: #5, #6)
  - [ ] T12.1 — Create `web-frontend/src/components/organizer/Admin/ExportImportTab.tsx`
  - [ ] T12.2 — Export section with two buttons (inline `<a>` download trick or direct fetch):
    ```tsx
    // JSON export — browser download
    const handleExportJson = async () => {
      const response = await fetch('/api/v1/admin/export/legacy', {
        headers: { Authorization: `Bearer ${token}` },
      });
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `batbern-export-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
    };
    ```
  - [ ] T12.3 — Import section:
    - File picker (`.json`) + "Import" button
    - `POST /api/v1/admin/import/legacy` via `axios.post` with `multipart/form-data`
    - Confirmation dialog before import (MUI `Dialog`): "This will upsert data. Existing records will be updated. This cannot be undone automatically."
    - After import: render result summary table (imported counts + skipped/error list)
  - [ ] T12.4 — Asset export: "Get Asset Manifest" button → fetches `/api/v1/admin/export/assets` → renders table of assets with presigned URLs (not auto-download; display manifest)
  - [ ] T12.5 — Asset import: file picker (`.zip`) + "Import Assets" → `POST /api/v1/admin/import/assets`
  - [ ] T12.6 — Use `useTranslation('common')` for all text → `admin.exportImport.*` keys
  - [ ] T12.7 — Use MUI: `Card`, `Button`, `Dialog`, `DialogContent`, `DialogActions`, `Table`, `Alert`

- [ ] **T13 — Update EventManagementAdminPage.tsx** (AC: #5)
  - [ ] T13.1 — Import `ExportImportTab`:
    ```tsx
    import { ExportImportTab } from '@/components/organizer/Admin/ExportImportTab';
    ```
  - [ ] T13.2 — Update tabIndex clamp: `Math.min(4, ...)` → `Math.min(5, ...)`
  - [ ] T13.3 — Add Tab 5 to the `tabs` array:
    ```tsx
    { label: t('admin.tabs.exportImport', 'Export / Import'), component: <ExportImportTab /> },
    ```

### Phase 7: i18n

- [ ] **T14 — Add i18n keys to all 10 locales** (AC: #9)
  - [ ] T14.1 — Add to `web-frontend/public/locales/en/common.json`:
    ```json
    "admin": {
      "tabs": {
        "exportImport": "Export / Import"
      },
      "exportImport": {
        "title": "Export / Import",
        "exportSection": "Export",
        "importSection": "Import",
        "exportJsonButton": "Export All Data (JSON)",
        "exportAssetsButton": "Get Asset Manifest",
        "importJsonLabel": "Import JSON File",
        "importAssetsLabel": "Import Assets (ZIP)",
        "importButton": "Import",
        "importAssetsButton": "Import Assets",
        "confirmTitle": "Confirm Import",
        "confirmMessage": "This will upsert data. Existing records will be updated. This cannot be undone automatically.",
        "confirmButton": "Proceed",
        "resultTitle": "Import Result",
        "resultEvents": "Events",
        "resultSessions": "Sessions",
        "resultSpeakers": "Speakers",
        "resultCompanies": "Companies",
        "resultAttendees": "Attendees",
        "resultSkipped": "Skipped",
        "resultErrors": "Errors",
        "successAlert": "Import completed successfully.",
        "errorAlert": "Import failed. See errors below."
      }
    }
    ```
  - [ ] T14.2 — Add `admin.tabs.exportImport` to all 9 other locales: de, fr, it, rm, es, fi, nl, ja, gsw-BE (translate or use EN as placeholder — mark with TODO comment if translation not available)
  - [ ] T14.3 — Bulk add `admin.exportImport.*` keys to all locales (EN values for all = functional placeholder until translated)

### Phase 8: Quality gates

- [ ] **T15 — Backend full test suite** (AC: #7)
  - `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-20-full.log && grep -E "FAILED|tests|errors|BUILD" /tmp/test-10-20-full.log | tail -10`

- [ ] **T16 — Checkstyle** (AC: #7)
  - `./gradlew :services:event-management-service:checkstyleMain 2>&1 | tee /tmp/checkstyle-10-20.log && grep -i "violation\|error" /tmp/checkstyle-10-20.log | head -20`

- [ ] **T17 — Frontend type-check** (AC: #9)
  - `cd web-frontend && npx tsc --noEmit 2>&1 | tee /tmp/typecheck-10-20.log && grep -c "error" /tmp/typecheck-10-20.log`

- [ ] **T18 — Frontend lint** (AC: #9)
  - `cd web-frontend && npx eslint src/components/organizer/Admin/ExportImportTab.tsx src/pages/organizer/EventManagementAdminPage.tsx 2>&1 | tee /tmp/lint-10-20.log`

---

## Dev Notes

### Architecture Overview

This story adds a **full-stack export/import feature** to the existing `/organizer/admin` Admin page:

```
Browser (Tab 5: ExportImportTab)
    → GET  /api/v1/admin/export/legacy        → LegacyExportService.exportAll()
    → GET  /api/v1/admin/export/assets        → LegacyExportService.exportAssetManifest()
    → POST /api/v1/admin/import/legacy        → LegacyImportService.importAll(envelope)
    → POST /api/v1/admin/import/assets        → LegacyImportService.importAssets(zip)
        ↳ All 4 endpoints: @PreAuthorize("hasRole('ORGANIZER')")
        ↳ AdminExportImportController (new, shares /api/v1/admin base path with AdminController)
```

### Critical Pre-checks Before Coding

**1. Company data access**: No `CompanyApiClient` exists. Company data (for export) must be fetched from company-user-management-service. Two options:
- Add `getAllCompanies()` method to `UserApiClient.java` (uses existing `user-service.base-url` config and JWT propagation pattern)
- Create a new `CompanyApiClient.java` following the same pattern as `UserApiClient`
- **Recommendation**: Add to `UserApiClient` since the service URL is already configured.

**2. Speaker profile S3 key**: `Speaker.java` stores `profilePictureUrl` (CloudFront URL). The raw S3 key may not be stored separately. Check `Speaker.java` for any `profilePictureS3Key` field. If only the CloudFront URL is stored, extract the S3 key from the URL by stripping the CloudFront domain prefix (it maps directly to S3 key path).

**3. Legacy JSON format**: The legacy BATspa format must be confirmed before implementing DTOs. Check `apps/BATspa-old/` for sample exports (CSV files are blocked by `.gitignore` but JSON data samples may exist in `docs/`). If no sample exists, ask Nissim before implementing DTOs.

**4. Admin tabIndex clamp**: `EventManagementAdminPage.tsx` line 32 caps at `Math.min(4, ...)`. Adding Tab 5 requires changing to `Math.min(5, ...)`.

**5. Multipart file size**: Asset ZIP import may be large. Check `spring.servlet.multipart.max-file-size` and `max-request-size` in `application.yml`. Increase if needed (current default may be `10MB`). Use `@Value("${spring.servlet.multipart.max-file-size:100MB}")` pattern or set explicitly.

**6. `@Transactional` on import**: The `importAll()` method must be `@Transactional` to ensure atomic DB operations (matches `RegistrationService`, `LegacyExportService` patterns).

### New Files to Create

```
Backend:
services/event-management-service/src/main/java/ch/batbern/events/controller/AdminExportImportController.java
services/event-management-service/src/main/java/ch/batbern/events/service/LegacyExportService.java
services/event-management-service/src/main/java/ch/batbern/events/service/LegacyImportService.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/LegacyExportEnvelope.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/LegacyEventDto.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/LegacySessionDto.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/LegacyCompanyDto.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/LegacySpeakerDto.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/LegacyAttendeeDto.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/LegacyImportResult.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/AssetManifestResponse.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/AssetEntry.java
services/event-management-service/src/main/java/ch/batbern/events/dto/export/AssetImportResult.java

Tests:
services/event-management-service/src/test/java/ch/batbern/events/service/LegacyExportServiceTest.java
services/event-management-service/src/test/java/ch/batbern/events/service/LegacyImportServiceTest.java
services/event-management-service/src/test/java/ch/batbern/events/controller/AdminExportImportControllerIntegrationTest.java

Frontend:
web-frontend/src/components/organizer/Admin/ExportImportTab.tsx
```

### Modified Files

```
docs/api/events-api.openapi.yml                          — 4 new endpoints + 4 new schemas (OpenAPI first!)
web-frontend/src/pages/organizer/EventManagementAdminPage.tsx   — add Tab 5 import + update clamp to Math.min(5, ...)
web-frontend/public/locales/de/common.json              — admin.exportImport.* + admin.tabs.exportImport
web-frontend/public/locales/en/common.json              — admin.exportImport.* + admin.tabs.exportImport
... (same for fr, it, rm, es, fi, nl, ja, gsw-BE)        — 8 more locale files
```

### Architecture Compliance

**Follow these patterns:**
- `@RestController` + `@RequestMapping` + `@PreAuthorize` at class level (matches `EmailTemplateController`)
- `@RequiredArgsConstructor` + `@Slf4j` on service and controller classes
- `@Transactional` on `importAll()` (atomic, idempotent upsert)
- `ResponseEntity<byte[]>` with `Content-Disposition: attachment` for JSON file download (matches `PartnerAttendanceExportService`)
- `S3Presigner.presignGetObject()` for read URLs — bean already in `AwsConfig.java`
- `UserApiClient` (RestTemplate + JWT propagation) for cross-service company data fetch
- `AbstractIntegrationTest` for integration tests (PostgreSQL via Testcontainers — never H2)
- `@ExtendWith(MockitoExtension.class)` for unit tests
- OpenAPI spec **before** implementation (ADR-006)

**DO NOT:**
- Add Flyway migration — no new DB tables needed (operates on existing data)
- Commit CSV files with real personal data (GDPR policy in CLAUDE.md)
- Use H2 for integration tests
- Add `process.env` access in frontend — use service layer via generated API types
- Skip the company data pre-check (T0) — wrong approach causes incomplete export silently

### Key Beans Available (no new config needed)

| Bean | Config File | Used For |
|------|-------------|----------|
| `S3Client` | `AwsConfig.java` | Asset ZIP upload to S3 |
| `S3Presigner` | `AwsConfig.java` | Presigned GET URLs for asset manifest |
| `RestTemplate` | `RestClientConfig.java` | Cross-service calls via `UserApiClient` |
| `ObjectMapper` | Spring Boot auto-config | JSON serialization of export envelope |

### Test Command Reference

```bash
# Unit tests
./gradlew :services:event-management-service:test --tests "*LegacyExportService*" 2>&1 | tee /tmp/test-10-20-export.log
./gradlew :services:event-management-service:test --tests "*LegacyImportService*" 2>&1 | tee /tmp/test-10-20-import.log

# Integration test
./gradlew :services:event-management-service:test --tests "*AdminExportImport*" 2>&1 | tee /tmp/test-10-20-integration.log

# Full EMS suite
./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-20-full.log && grep -E "FAILED|BUILD" /tmp/test-10-20-full.log

# Frontend
cd web-frontend && npx tsc --noEmit 2>&1 | tee /tmp/typecheck-10-20.log && grep -c "error" /tmp/typecheck-10-20.log
```

### References

- Export response pattern: [Source: services/event-management-service/.../service/PartnerAttendanceExportService.java]
- S3Presigner usage: [Source: services/event-management-service/.../service/MaterialsUploadService.java]
- Controller pattern: [Source: services/event-management-service/.../controller/AiAssistController.java]
- Cross-service JWT propagation: [Source: services/event-management-service/.../client/impl/UserApiClientImpl.java]
- Admin page tab structure: [Source: web-frontend/src/pages/organizer/EventManagementAdminPage.tsx]
- OpenAPI spec (tags, schemas, conventions): [Source: docs/api/events-api.openapi.yml]
- AbstractIntegrationTest: [Source: shared-kernel/src/testFixtures/.../AbstractIntegrationTest.java]
- application.yml S3/cross-service config: [Source: services/event-management-service/src/main/resources/application.yml]
- Story spec: [Source: docs/prd/epic-10-additional-stories.md#L1405-1479]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
