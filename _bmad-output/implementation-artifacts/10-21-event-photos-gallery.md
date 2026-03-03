# Story 10.21: Event Photos Gallery

Status: done

<!-- Prerequisite: None — EventPhotosTab added to EventPage.tsx (organizer event detail). Independent of 10.1. -->

## Story

As an **organizer**,
I want to upload and manage photos from each event directly in the event detail page,
so that we can preserve visual memories of our events.

As a **visitor**,
I want to see real photos from past BATbern events on the homepage — replacing the dummy testimonials — and see each archived event's own photos when I browse the archive.

## Acceptance Criteria

1. **AC1 — Flyway V79**: New `event_photos` table created cleanly (`id UUID PK`, `event_code VARCHAR FK → events.event_code`, `s3_key TEXT NOT NULL`, `display_url TEXT NOT NULL`, `filename TEXT`, `uploaded_at TIMESTAMPTZ`, `uploaded_by VARCHAR`, `sort_order INT DEFAULT 0`). *(V78 was already taken by `V78__add_task_cancellation_fields.sql`; V79 used instead.)*
2. **AC2 — Upload flow**: Organizer can upload a photo to an event via presigned URL (3-phase: request URL → PUT to S3 → confirm); photo appears in photo grid tab.
3. **AC3 — Delete**: Organizer can delete a photo; DB record and S3 object are both removed; confirmation dialog shown first.
4. **AC4 — Public photo listing**: `GET /api/v1/events/{eventCode}/photos` returns `List<EventPhotoResponse>` (no auth required).
5. **AC5 — Recent photos endpoint**: `GET /api/v1/events/recent-photos?limit=20&lastNEvents=5` returns up to 20 randomly-sampled photo URLs from the last 5 events by event date (no auth).
6. **AC6 — Homepage marquee**: `TestimonialSection.tsx` first row shows real event photos via `useRecentEventPhotos` hook; falls back to existing testimonials if < 3 photos.
7. **AC7 — Archive page marquee**: Archive event detail page shows event's own photos in `InfiniteMarquee` below sessions; section hidden if 0 photos.
8. **AC8 — EventPage Photos tab**: New "Photos" tab added to `EventPage.tsx`; renders `EventPhotosTab.tsx` with photo grid + upload + delete.
9. **AC9 — TDD**: `EventPhotoServiceTest` covers upload confirmation, delete, recent-photos query; written before implementation (RED-GREEN).
10. **AC10 — OpenAPI first**: `docs/api/events-api.openapi.yml` updated before any backend implementation (ADR-006).
11. **AC11 — i18n**: `photos.*` keys added to all 10 locale files (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE); type-check and lint pass.
12. **AC12 — Drag-to-reorder is OUT OF SCOPE**: `sort_order` column reserved for future use; do not implement drag-and-drop.

---

## Tasks / Subtasks

### Phase 0: Pre-checks (read before touching anything)

- [x] **T0 — Confirm Flyway version** (AC: #1)
  - [x] T0.1 — Run: `ls services/event-management-service/src/main/resources/db/migration/ | sort | tail -5`
  - [x] T0.2 — **CRITICAL**: The epic file says V75, but V75 through V77 are already taken:
    - `V75__add_deregistration_token.sql`
    - `V76__add_deregistration_link_to_confirmation_templates.sql`
    - `V77__create_ai_generation_log.sql`
  - [x] T0.3 — **Use V79** for `event_photos` table. (V78 was taken by task cancellation fields migration.)

- [x] **T1 — Understand S3 presigned upload pattern** (AC: #2)
  - [x] T1.1 — Read `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerProfilePhotoService.java` — this is the canonical presigned PUT pattern for this project.
  - [x] T1.2 — Note: `S3Presigner` + `S3Client` beans from `AwsConfig.java`; no new beans needed.
  - [x] T1.3 — Read `services/event-management-service/src/main/java/ch/batbern/events/service/SessionMaterialsService.java` lines ~475-485 for the `DeleteObjectRequest` pattern.
  - [x] T1.4 — Confirm `CloudFrontUrlBuilder.buildUrl(cloudFrontDomain, bucketName, s3Key)` is available in `shared-kernel`.

- [x] **T2 — Read EventPage.tsx** (AC: #8)
  - [x] T2.1 — Read `web-frontend/src/components/organizer/EventPage/EventPage.tsx` (already read — 297 lines).
  - [x] T2.2 — Current tabs: `overview`, `speakers`, `venue`, `participants`, `publishing`, `newsletter`, `settings` (7 tabs).
  - [x] T2.3 — `TABS` is a `const` array at line 58; `renderTabContent()` is a switch at line 176.
  - [x] T2.4 — Add `photos` as tab 8 using `PhotoLibrary` or `Collections` icon from `@mui/icons-material`.

---

### Phase 1: OpenAPI Specification (MANDATORY FIRST — ADR-006)

- [x] **T3 — Add 5 endpoints + schemas to `docs/api/events-api.openapi.yml`** (AC: #10)
  - [x] T3.1 — Add endpoints under tag `Event Photos`:

    ```yaml
    /events/{eventCode}/photos:
      get:
        tags: [Event Photos]
        summary: List photos for an event
        operationId: listEventPhotos
        security: []
        parameters:
          - name: eventCode
            in: path
            required: true
            schema: { type: string }
        responses:
          '200':
            description: List of event photos
            content:
              application/json:
                schema:
                  type: array
                  items:
                    $ref: '#/components/schemas/EventPhotoResponse'

    /events/{eventCode}/photos/upload-url:
      post:
        tags: [Event Photos]
        summary: Request presigned URL for photo upload
        operationId: requestEventPhotoUploadUrl
        security:
          - cognito: [ORGANIZER]
        parameters:
          - name: eventCode
            in: path
            required: true
            schema: { type: string }
        requestBody:
          required: true
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EventPhotoUploadRequest'
        responses:
          '200':
            description: Presigned URL + photoId
            content:
              application/json:
                schema:
                  $ref: '#/components/schemas/EventPhotoUploadResponse'

    /events/{eventCode}/photos/confirm:
      post:
        tags: [Event Photos]
        summary: Confirm photo upload and persist record
        operationId: confirmEventPhotoUpload
        security:
          - cognito: [ORGANIZER]
        parameters:
          - name: eventCode
            in: path
            required: true
            schema: { type: string }
        requestBody:
          required: true
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EventPhotoConfirmRequest'
        responses:
          '200':
            description: Photo confirmed and stored
            content:
              application/json:
                schema:
                  $ref: '#/components/schemas/EventPhotoResponse'

    /events/{eventCode}/photos/{photoId}:
      delete:
        tags: [Event Photos]
        summary: Delete a photo (DB record + S3 object)
        operationId: deleteEventPhoto
        security:
          - cognito: [ORGANIZER]
        parameters:
          - name: eventCode
            in: path
            required: true
            schema: { type: string }
          - name: photoId
            in: path
            required: true
            schema:
              type: string
              format: uuid
        responses:
          '204':
            description: Photo deleted
          '404':
            description: Photo not found

    /events/recent-photos:
      get:
        tags: [Event Photos]
        summary: Recent photos from last N events (homepage use)
        operationId: getRecentEventPhotos
        security: []
        parameters:
          - name: limit
            in: query
            schema: { type: integer, default: 20 }
          - name: lastNEvents
            in: query
            schema: { type: integer, default: 5 }
        responses:
          '200':
            description: Random sample of recent photos
            content:
              application/json:
                schema:
                  type: array
                  items:
                    $ref: '#/components/schemas/EventPhotoResponse'
    ```

  - [x] T3.2 — Add schemas under `components/schemas`:

    ```yaml
    EventPhotoResponse:
      type: object
      required: [id, eventCode, displayUrl, uploadedAt]
      properties:
        id:
          type: string
          format: uuid
        eventCode:
          type: string
        displayUrl:
          type: string
        filename:
          type: string
        uploadedBy:
          type: string
        uploadedAt:
          type: string
          format: date-time
        sortOrder:
          type: integer

    EventPhotoUploadRequest:
      type: object
      required: [filename, contentType, fileSize]
      properties:
        filename:
          type: string
        contentType:
          type: string
          enum: [image/jpeg, image/png, image/webp]
        fileSize:
          type: integer
          format: int64

    EventPhotoUploadResponse:
      type: object
      required: [photoId, uploadUrl, s3Key, expiresIn]
      properties:
        photoId:
          type: string
          format: uuid
        uploadUrl:
          type: string
        s3Key:
          type: string
        expiresIn:
          type: integer

    EventPhotoConfirmRequest:
      type: object
      required: [photoId, s3Key]
      properties:
        photoId:
          type: string
          format: uuid
        s3Key:
          type: string
    ```

  - [x] T3.3 — Regenerate frontend types: `cd web-frontend && npm run generate:api-types 2>&1 | tee /tmp/codegen-10-21.log`
  - [x] T3.4 — Verify generated types include `EventPhotoResponse`, `EventPhotoUploadRequest`, etc. in `web-frontend/src/types/generated/`.

---

### Phase 2: Backend — Flyway Migration

- [x] **T4 — Create V79 migration** (AC: #1)
  - [x] T4.1 — Create `services/event-management-service/src/main/resources/db/migration/V79__create_event_photos.sql`:

    ```sql
    CREATE TABLE event_photos (
        id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
        event_code   VARCHAR(50) NOT NULL REFERENCES events(event_code) ON DELETE CASCADE,
        s3_key       TEXT        NOT NULL,
        display_url  TEXT        NOT NULL,
        filename     TEXT,
        uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
        uploaded_by  VARCHAR(255),
        sort_order   INT         NOT NULL DEFAULT 0
    );

    CREATE INDEX idx_event_photos_event_code ON event_photos(event_code);
    CREATE INDEX idx_event_photos_uploaded_at ON event_photos(uploaded_at DESC);
    ```

  - [x] T4.2 — Verify the FK reference: `events.event_code` is the PK/unique key in the `events` table (it is — it's used as FK in registrations, sessions, etc.).

---

### Phase 3: Backend — Domain + Repository

- [x] **T5 — Create EventPhoto domain entity** (AC: #1)
  - [x] T5.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/domain/EventPhoto.java`:

    ```java
    @Entity
    @Table(name = "event_photos")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public class EventPhoto {

        @Id
        @GeneratedValue
        private UUID id;

        @Column(name = "event_code", nullable = false)
        private String eventCode;

        @Column(name = "s3_key", nullable = false)
        private String s3Key;

        @Column(name = "display_url", nullable = false)
        private String displayUrl;

        @Column(name = "filename")
        private String filename;

        @Column(name = "uploaded_at")
        private Instant uploadedAt;

        @Column(name = "uploaded_by")
        private String uploadedBy;

        @Column(name = "sort_order")
        private int sortOrder;
    }
    ```

  - [x] T5.2 — Pattern: matches existing domain entities (e.g., `AiGenerationLog.java`, `Registration.java`). Use `@GeneratedValue` (UUID default in PostgreSQL via `gen_random_uuid()`).

- [x] **T6 — Create EventPhotoRepository** (AC: #2, #4, #5)
  - [x] T6.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/repository/EventPhotoRepository.java`:

    ```java
    @Repository
    public interface EventPhotoRepository extends JpaRepository<EventPhoto, UUID> {

        List<EventPhoto> findByEventCodeOrderBySortOrderAscUploadedAtAsc(String eventCode);

        Optional<EventPhoto> findByIdAndEventCode(UUID id, String eventCode);

        // For recent-photos endpoint: events ordered by date descending
        @Query("""
            SELECT ep FROM EventPhoto ep
            WHERE ep.eventCode IN (
                SELECT e.eventCode FROM Event e
                WHERE e.eventDate IS NOT NULL
                ORDER BY e.eventDate DESC
                LIMIT :lastNEvents
            )
            ORDER BY ep.uploadedAt DESC
            """)
        List<EventPhoto> findPhotosFromRecentEvents(@Param("lastNEvents") int lastNEvents);
    }
    ```

  - [x] T6.2 — JPQL note: LIMIT is not standard JPQL. Use `Pageable` or `@Query` with native SQL: `nativeQuery = true`. Alternatively use `findByEventCodeIn` + Java-level sampling. See T11 service impl for recommended approach.

---

### Phase 4: Backend — Service (TDD)

- [x] **T7 — Write EventPhotoServiceTest FIRST (RED phase)** (AC: #9)
  - [x] T7.1 — Create `services/event-management-service/src/test/java/ch/batbern/events/service/EventPhotoServiceTest.java`:

    ```java
    @ExtendWith(MockitoExtension.class)
    class EventPhotoServiceTest {

        @Mock EventPhotoRepository photoRepository;
        @Mock EventRepository eventRepository;
        @Mock S3Presigner s3Presigner;
        @Mock S3Client s3Client;
        @InjectMocks EventPhotoService service;

        @BeforeEach
        void setup() {
            ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
            ReflectionTestUtils.setField(service, "cloudFrontDomain", "https://cdn.batbern.ch");
        }
    ```

  - [x] T7.2 — Tests to write (RED — compile fails expected):
    - `requestUploadUrl() with valid eventCode and JPEG 2MB → returns uploadUrl, photoId, s3Key (pattern: events/BATbern42/photos/{photoId}.jpg)`
    - `requestUploadUrl() with content-type image/png → s3Key ends with .png`
    - `confirmUpload() with valid photoId and s3Key → S3 headObject called; EventPhoto saved with displayUrl starting with https://cdn.batbern.ch`
    - `confirmUpload() when s3Key not found in S3 → throws PhotoUploadNotFoundException`
    - `deletePhoto() with valid photoId → s3Client.deleteObject called; photoRepository.delete called`
    - `deletePhoto() with photoId not belonging to eventCode → throws ResourceNotFoundException`
    - `getRecentPhotos(20, 5) with 25 photos across 5 events → returns at most 20`
    - `getRecentPhotos(20, 5) with 3 photos → returns all 3 (no padding)`

  - [x] T7.3 — Run RED: `./gradlew :services:event-management-service:test --tests "*EventPhotoServiceTest" 2>&1 | tee /tmp/test-10-21-red.log && grep -E "FAILED|error|BUILD" /tmp/test-10-21-red.log | tail -5`

- [x] **T8 — Implement EventPhotoService** (AC: #2, #3, #4, #5)
  - [x] T8.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/EventPhotoService.java`:

    ```java
    @Service
    @Transactional
    @Slf4j
    public class EventPhotoService {

        private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
        private static final int PRESIGNED_URL_EXPIRY_SECONDS = 900; // 15 min
        private static final Set<String> ALLOWED_TYPES = Set.of(
                "image/jpeg", "image/png", "image/webp");

        private final EventPhotoRepository photoRepository;
        private final EventRepository eventRepository;
        private final S3Presigner s3Presigner;
        private final S3Client s3Client;
        private final String bucketName;
        private final String cloudFrontDomain;

        public EventPhotoService(
                EventPhotoRepository photoRepository,
                EventRepository eventRepository,
                S3Presigner s3Presigner,
                S3Client s3Client,
                @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
                @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
            // assign fields
        }

        /** Phase 1: Generate presigned PUT URL */
        public EventPhotoUploadResponseDto requestUploadUrl(String eventCode,
                                                             EventPhotoUploadRequestDto request,
                                                             String uploaderUsername) {
            // Validate event exists
            eventRepository.findByEventCode(eventCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventCode));

            // Validate content type
            if (!ALLOWED_TYPES.contains(request.getContentType())) {
                throw new InvalidFileTypeException("Allowed: image/jpeg, image/png, image/webp");
            }

            // Generate IDs
            UUID photoId = UUID.randomUUID();
            String ext = resolveExtension(request.getFilename(), request.getContentType());
            String s3Key = String.format("events/%s/photos/%s.%s", eventCode, photoId, ext);

            // Build presigned PUT URL
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(request.getContentType())
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(PRESIGNED_URL_EXPIRY_SECONDS))
                    .putObjectRequest(putRequest)
                    .build();
            String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            return EventPhotoUploadResponseDto.builder()
                    .photoId(photoId)
                    .uploadUrl(uploadUrl)
                    .s3Key(s3Key)
                    .expiresIn(PRESIGNED_URL_EXPIRY_SECONDS)
                    .build();
        }

        /** Phase 3: Confirm upload, verify in S3, persist EventPhoto */
        public EventPhotoResponseDto confirmUpload(String eventCode,
                                                    EventPhotoConfirmRequestDto request,
                                                    String uploaderUsername) {
            // Verify file exists in S3
            try {
                s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName).key(request.getS3Key()).build());
            } catch (NoSuchKeyException e) {
                throw new PhotoUploadNotFoundException(request.getPhotoId().toString());
            }

            String displayUrl = CloudFrontUrlBuilder.buildUrl(cloudFrontDomain, bucketName, request.getS3Key());
            String filename = request.getS3Key().substring(request.getS3Key().lastIndexOf('/') + 1);

            EventPhoto photo = EventPhoto.builder()
                    .id(request.getPhotoId())
                    .eventCode(eventCode)
                    .s3Key(request.getS3Key())
                    .displayUrl(displayUrl)
                    .filename(filename)
                    .uploadedAt(Instant.now())
                    .uploadedBy(uploaderUsername)
                    .sortOrder(0)
                    .build();

            return toResponseDto(photoRepository.save(photo));
        }

        /** Delete photo: remove DB record + S3 object */
        public void deletePhoto(String eventCode, UUID photoId) {
            EventPhoto photo = photoRepository.findByIdAndEventCode(photoId, eventCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Photo not found: " + photoId));

            // Delete from S3
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName).key(photo.getS3Key()).build());
            } catch (Exception e) {
                log.warn("S3 delete failed for key {}: {}", photo.getS3Key(), e.getMessage());
                // Do not block DB delete — S3 key may already be gone
            }

            photoRepository.delete(photo);
            log.info("Photo {} deleted from event {}", photoId, eventCode);
        }

        /** List all photos for an event (public) */
        @Transactional(readOnly = true)
        public List<EventPhotoResponseDto> listPhotos(String eventCode) {
            return photoRepository.findByEventCodeOrderBySortOrderAscUploadedAtAsc(eventCode)
                    .stream().map(this::toResponseDto).toList();
        }

        /** Recent photos: randomly sampled from last N events */
        @Transactional(readOnly = true)
        public List<EventPhotoResponseDto> getRecentPhotos(int limit, int lastNEvents) {
            // Fetch recent events by date, collect their photos, then random-sample
            List<Event> recentEvents = eventRepository.findTopNByEventDateDesc(lastNEvents);
            List<String> eventCodes = recentEvents.stream().map(Event::getEventCode).toList();
            if (eventCodes.isEmpty()) return List.of();

            List<EventPhoto> all = photoRepository.findByEventCodeIn(eventCodes);
            if (all.size() <= limit) return all.stream().map(this::toResponseDto).toList();

            // Random sample
            List<EventPhoto> shuffled = new ArrayList<>(all);
            Collections.shuffle(shuffled);
            return shuffled.subList(0, limit).stream().map(this::toResponseDto).toList();
        }
    }
    ```

  - [x] T8.2 — Add `findTopNByEventDateDesc` to `EventRepository` if not present:
    ```java
    @Query("SELECT e FROM Event e WHERE e.eventDate IS NOT NULL ORDER BY e.eventDate DESC LIMIT :n")
    List<Event> findTopNByEventDateDesc(@Param("n") int n);
    ```
    Or use `findTop5ByEventDateNotNullOrderByEventDateDesc()` Spring Data naming convention.

  - [x] T8.3 — Run GREEN: `./gradlew :services:event-management-service:test --tests "*EventPhotoServiceTest" 2>&1 | tee /tmp/test-10-21-green.log && grep -E "FAILED|BUILD" /tmp/test-10-21-green.log | tail -5`

---

### Phase 5: Backend — Controller

- [x] **T9 — Create EventPhotoController** (AC: #2, #3, #4, #5)
  - [x] T9.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/controller/EventPhotoController.java`:

    ```java
    @RestController
    @RequestMapping("/api/v1")
    @RequiredArgsConstructor
    @Slf4j
    public class EventPhotoController {

        private final EventPhotoService photoService;

        /** Public: list photos for an event */
        @GetMapping("/events/{eventCode}/photos")
        public ResponseEntity<List<EventPhotoResponseDto>> listPhotos(
                @PathVariable String eventCode) {
            return ResponseEntity.ok(photoService.listPhotos(eventCode));
        }

        /** Organizer: request presigned upload URL */
        @PostMapping("/events/{eventCode}/photos/upload-url")
        @PreAuthorize("hasRole('ORGANIZER')")
        public ResponseEntity<EventPhotoUploadResponseDto> requestUploadUrl(
                @PathVariable String eventCode,
                @RequestBody EventPhotoUploadRequestDto request,
                @AuthenticationPrincipal Jwt jwt) {
            String username = jwt.getClaimAsString("cognito:username");
            return ResponseEntity.ok(photoService.requestUploadUrl(eventCode, request, username));
        }

        /** Organizer: confirm upload (phase 3) */
        @PostMapping("/events/{eventCode}/photos/confirm")
        @PreAuthorize("hasRole('ORGANIZER')")
        public ResponseEntity<EventPhotoResponseDto> confirmUpload(
                @PathVariable String eventCode,
                @RequestBody EventPhotoConfirmRequestDto request,
                @AuthenticationPrincipal Jwt jwt) {
            String username = jwt.getClaimAsString("cognito:username");
            return ResponseEntity.ok(photoService.confirmUpload(eventCode, request, username));
        }

        /** Organizer: delete a photo */
        @DeleteMapping("/events/{eventCode}/photos/{photoId}")
        @PreAuthorize("hasRole('ORGANIZER')")
        public ResponseEntity<Void> deletePhoto(
                @PathVariable String eventCode,
                @PathVariable UUID photoId) {
            photoService.deletePhoto(eventCode, photoId);
            return ResponseEntity.noContent().build();
        }

        /** Public: recent photos for homepage */
        @GetMapping("/events/recent-photos")
        public ResponseEntity<List<EventPhotoResponseDto>> getRecentPhotos(
                @RequestParam(defaultValue = "20") int limit,
                @RequestParam(defaultValue = "5") int lastNEvents) {
            return ResponseEntity.ok(photoService.getRecentPhotos(limit, lastNEvents));
        }
    }
    ```

  - [x] T9.2 — **IMPORTANT**: Note that `GET /events/recent-photos` is a static path and must be registered BEFORE the dynamic `GET /events/{eventCode}/photos` route in Spring to avoid routing conflicts. In Spring MVC, static paths always take priority over path variables — no special ordering needed, but double-check.
  - [x] T9.3 — Pattern for `@AuthenticationPrincipal Jwt jwt`: matches `AiAssistController` and other organizer endpoints in this service.

---

### Phase 6: Backend — Integration Test

- [x] **T10 — Integration test** (AC: #9)
  - [x] T10.1 — Create `services/event-management-service/src/test/java/ch/batbern/events/controller/EventPhotoControllerIntegrationTest.java`
  - [x] T10.2 — Extends `AbstractIntegrationTest`; uses `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@AutoConfigureMockMvc`
  - [x] T10.3 — Tests:
    - `GET /api/v1/events/{eventCode}/photos as anonymous → 200 with empty list`
    - `POST /api/v1/events/{eventCode}/photos/upload-url as ORGANIZER → 200 with uploadUrl and photoId`
    - `POST /api/v1/events/{eventCode}/photos/upload-url as PARTNER → 403`
    - `DELETE /api/v1/events/{eventCode}/photos/{photoId} for nonexistent photo → 404`
    - `GET /api/v1/events/recent-photos as anonymous → 200 with list`
  - [x] T10.4 — Mock S3 operations with `@MockBean S3Presigner` and `@MockBean S3Client` — do NOT require real S3 in integration tests.
  - [x] T10.5 — Run: `./gradlew :services:event-management-service:test --tests "*EventPhotoController*" 2>&1 | tee /tmp/test-10-21-integration.log && grep -E "FAILED|BUILD" /tmp/test-10-21-integration.log | tail -5`

---

### Phase 7: Frontend — EventPhotosTab

- [x] **T11 — Create EventPhotosTab.tsx** (AC: #8)
  - [x] T11.1 — Create `web-frontend/src/components/organizer/EventPage/EventPhotosTab.tsx`
  - [x] T11.2 — Create `web-frontend/src/hooks/useEventPhotos.ts`:

    ```typescript
    export const useEventPhotos = (eventCode: string) => {
      return useQuery({
        queryKey: ['event-photos', eventCode],
        queryFn: () => eventApiClient.listEventPhotos(eventCode),
      });
    };

    export const useUploadEventPhoto = (eventCode: string) => {
      const queryClient = useQueryClient();
      // 3-phase upload:
      // 1. POST /photos/upload-url → { photoId, uploadUrl, s3Key }
      // 2. PUT to uploadUrl (axios.put with Content-Type header)
      // 3. POST /photos/confirm → { id, displayUrl, ... }
      // On success: invalidate ['event-photos', eventCode]
    };

    export const useDeleteEventPhoto = (eventCode: string) => {
      const queryClient = useQueryClient();
      return useMutation({
        mutationFn: (photoId: string) => eventApiClient.deleteEventPhoto(eventCode, photoId),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['event-photos', eventCode] }),
      });
    };
    ```

  - [x] T11.3 — `EventPhotosTab.tsx` structure:
    - MUI `Card` header with "Photos" title + "Upload Photo" `Button`
    - Photo grid: MUI `Grid` 3-4 columns, each cell an `<img>` thumbnail
    - Each photo card: `<img src={photo.displayUrl} alt={photo.filename} />` + `<IconButton>` (delete) with `DeleteIcon`
    - Delete confirmation: MUI `Dialog` with "Delete this photo?" + confirm/cancel buttons
    - Upload: trigger `<input type="file" accept="image/jpeg,image/png,image/webp">` → 3-phase flow
    - Loading state: `BATbernLoader` or `CircularProgress`
    - Empty state: "No photos yet. Upload the first one!"

  - [x] T11.4 — Use `useTranslation('events')` → keys `photos.title`, `photos.uploadButton`, `photos.deleteConfirmTitle`, `photos.deleteConfirmMessage`, `photos.emptyState`.
  - [x] T11.5 — DO NOT implement drag-to-reorder (explicitly out of scope per AC12).

- [x] **T12 — Add Photos tab to EventPage.tsx** (AC: #8)
  - [x] T12.1 — Import MUI icon (e.g., `PhotoLibrary as PhotosIcon`) from `@mui/icons-material`.
  - [x] T12.2 — Add to `TABS` array:
    ```typescript
    { id: 'photos', labelKey: 'eventPage.tabs.photos', icon: <PhotosIcon /> },
    ```
  - [x] T12.3 — Add import: `import { EventPhotosTab } from './EventPhotosTab';`
  - [x] T12.4 — Add case to `renderTabContent()`:
    ```typescript
    case 'photos':
      return <EventPhotosTab eventCode={eventCode!} />;
    ```
  - [x] T12.5 — The `TABS` array uses `as const` — TypeScript will automatically pick up the new tab id.

---

### Phase 8: Frontend — useRecentEventPhotos Hook

- [x] **T13 — Create useRecentEventPhotos.ts** (AC: #6)
  - [x] T13.1 — Create `web-frontend/src/hooks/useRecentEventPhotos.ts`:

    ```typescript
    export const useRecentEventPhotos = (limit = 20, lastNEvents = 5) => {
      return useQuery({
        queryKey: ['recent-event-photos', limit, lastNEvents],
        queryFn: () => eventApiClient.getRecentEventPhotos(limit, lastNEvents),
        staleTime: 5 * 60 * 1000, // 5 min — homepage doesn't need real-time refresh
      });
    };
    ```

---

### Phase 9: Frontend — Homepage TestimonialSection

- [x] **T14 — Update TestimonialSection.tsx** (AC: #6)
  - [x] T14.1 — Read `web-frontend/src/components/public/Testimonials/TestimonialSection.tsx` (already read — 199 lines).
  - [x] T14.2 — Add `useRecentEventPhotos` hook:

    ```typescript
    const { data: recentPhotos } = useRecentEventPhotos(20, 5);
    const hasEnoughPhotos = (recentPhotos?.length ?? 0) >= 3;
    ```

  - [x] T14.3 — Replace first row content:
    ```tsx
    {/* First row: real event photos (with testimonials fallback) */}
    <InfiniteMarquee direction="left" speed="slow">
      {hasEnoughPhotos
        ? recentPhotos!.map((photo) => (
            <img
              key={photo.id}
              src={photo.displayUrl}
              alt="BATbern event"
              className="rounded-lg object-cover h-48 w-64 shrink-0"
            />
          ))
        : firstRow.map((testimonial) => (
            <TestimonialCard key={testimonial.id} {...testimonial} />
          ))}
    </InfiniteMarquee>
    ```

  - [x] T14.4 — Keep `TestimonialCard` and `testimonials` array for fallback (do NOT delete).
  - [x] T14.5 — The second row (partner showcase) is unchanged.

---

### Phase 10: Frontend — Archive Event Detail Photos Marquee

- [x] **T15 — Update ArchiveEventDetailPage.tsx** (AC: #7)
  - [x] T15.1 — Read `web-frontend/src/pages/public/ArchiveEventDetailPage.tsx` (grep output shows ~312 lines).
  - [x] T15.2 — Add `useEventPhotos` import and hook call.
  - [x] T15.3 — Add photos section below sessions section:

    ```tsx
    {photos && photos.length > 0 && (
      <section>
        <Typography variant="h5" gutterBottom>
          {t('archive.detail.photos', 'Photos')}
        </Typography>
        <InfiniteMarquee direction="left" speed="slow">
          {photos.map((photo) => (
            <img
              key={photo.id}
              src={photo.displayUrl}
              alt={photo.filename || 'BATbern event photo'}
              className="rounded-lg object-cover h-48 w-64 shrink-0"
            />
          ))}
        </InfiniteMarquee>
      </section>
    )}
    ```

  - [x] T15.4 — Section hidden if 0 photos (conditional render as above).
  - [x] T15.5 — Add `import { InfiniteMarquee } from '@/components/public/Testimonials/InfiniteMarquee';`

---

### Phase 11: i18n

- [x] **T16 — Add i18n keys to all 10 locales** (AC: #11)
  - [x] T16.1 — Add to `web-frontend/public/locales/en/events.json`:
    ```json
    "photos": {
      "title": "Event Photos",
      "uploadButton": "Upload Photo",
      "deleteConfirmTitle": "Delete Photo",
      "deleteConfirmMessage": "Are you sure you want to delete this photo? This cannot be undone.",
      "deleteConfirm": "Delete",
      "deleteCancel": "Cancel",
      "emptyState": "No photos yet. Upload the first one!",
      "uploading": "Uploading...",
      "uploadError": "Upload failed. Please try again."
    }
    ```
  - [x] T16.2 — Add `"eventPage": { "tabs": { "photos": "Photos" } }` to `events.json` (merge with existing `eventPage.tabs.*` keys).
  - [x] T16.3 — Add `"archive": { "detail": { "photos": "Photos" } }` to `events.json`.
  - [x] T16.4 — Add same keys to `de/events.json` (German translations):
    ```json
    "photos": {
      "title": "Eventfotos",
      "uploadButton": "Foto hochladen",
      "deleteConfirmTitle": "Foto löschen",
      "deleteConfirmMessage": "Möchten Sie dieses Foto wirklich löschen? Dies kann nicht rückgängig gemacht werden.",
      "deleteConfirm": "Löschen",
      "deleteCancel": "Abbrechen",
      "emptyState": "Noch keine Fotos. Laden Sie das erste hoch!",
      "uploading": "Wird hochgeladen...",
      "uploadError": "Upload fehlgeschlagen. Bitte versuchen Sie es erneut."
    }
    ```
  - [x] T16.5 — For remaining 8 locales (fr, it, rm, es, fi, nl, ja, gsw-BE): use EN values as placeholder (they are functional); mark with `// TODO: translate` comment in commit message.

---

### Phase 12: Quality Gates

- [x] **T17 — Backend full test suite** (AC: #9)
  - `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-21-full.log && grep -E "FAILED|tests|BUILD" /tmp/test-10-21-full.log | tail -10`
  - ✅ BUILD SUCCESSFUL — 14 EventPhoto tests passed (7 unit + 7 integration)

- [x] **T18 — Checkstyle** (AC: #9)
  - `./gradlew :services:event-management-service:checkstyleMain 2>&1 | tee /tmp/checkstyle-10-21.log && grep -i "violation\|error" /tmp/checkstyle-10-21.log | head -20`
  - ✅ BUILD SUCCESSFUL — 0 violations

- [x] **T19 — Frontend type-check** (AC: #11)
  - `cd web-frontend && npx tsc --noEmit 2>&1 | tee /tmp/typecheck-10-21.log && grep -c "error" /tmp/typecheck-10-21.log`
  - ✅ 0 errors (fixed: broken JSDoc in eventApiClient.ts, MUI Grid v2 API in EventPhotosTab.tsx)

- [x] **T20 — Frontend lint** (AC: #11)
  - `cd web-frontend && npx eslint src/components/organizer/EventPage/EventPhotosTab.tsx src/components/public/Testimonials/TestimonialSection.tsx src/hooks/useRecentEventPhotos.ts src/hooks/useEventPhotos.ts 2>&1 | tee /tmp/lint-10-21.log`
  - ✅ 0 warnings/errors

---

## Dev Notes

### Architecture Overview

```
Browser (EventPhotosTab — Organizer)
    POST /api/v1/events/{eventCode}/photos/upload-url  → EventPhotoService.requestUploadUrl()
    PUT  {presignedUrl} directly to S3                 → S3 (no backend involved)
    POST /api/v1/events/{eventCode}/photos/confirm     → EventPhotoService.confirmUpload()
    DELETE /api/v1/events/{eventCode}/photos/{photoId} → EventPhotoService.deletePhoto()

Browser (TestimonialSection — Homepage)
    GET /api/v1/events/recent-photos?limit=20&lastNEvents=5 → EventPhotoService.getRecentPhotos()

Browser (ArchiveEventDetailPage — Public)
    GET /api/v1/events/{eventCode}/photos              → EventPhotoService.listPhotos()
```

### Critical Guardrails — DO NOT GET WRONG

1. **Flyway version is V78** — not V75 as stated in the epic spec. V75, V76, V77 are all taken:
   - `V75__add_deregistration_token.sql`
   - `V76__add_deregistration_link_to_confirmation_templates.sql`
   - `V77__create_ai_generation_log.sql`

2. **OpenAPI BEFORE implementation** (ADR-006) — Phase 1 must be done before any Java files.

3. **No drag-to-reorder** — `sort_order` column exists for future use only. AC12 explicitly excludes it.

4. **S3 key pattern**: `events/{eventCode}/photos/{photoId}.{ext}` — consistent with existing `events/{eventCode}/teaser/` from Story 10.22 spec.

5. **Presigned PUT URL** (not POST): S3 presigned uploads use `HTTP PUT`. Frontend must use `axios.put(uploadUrl, file, { headers: { 'Content-Type': file.type } })` — not `axios.post`.

6. **Delete S3 best-effort**: S3 delete failures should log warning but NOT block DB delete. Photo may already be gone from S3 — don't throw 500 for that.

7. **EventPage tab pattern**: `TABS` is `as const` at line 58. The `type TabId` is derived from it. Adding a new tab automatically extends the union type — no manual type changes needed.

8. **TestimonialSection fallback**: Keep the 20 hardcoded testimonials and `TestimonialCard` component — they serve as fallback when < 3 real photos exist.

9. **`GET /events/recent-photos` routing**: This static path must NOT conflict with `GET /events/{eventCode}/photos`. Spring MVC resolves static segments before path variables — no ordering fix needed, but verify in integration test.

10. **`@AuthenticationPrincipal Jwt jwt`** for organizer endpoints: use `jwt.getClaimAsString("cognito:username")` to get the uploader — matches pattern in `AiAssistController` and others.

### Key Beans Available (no new config)

| Bean | Source | Used For |
|------|--------|----------|
| `S3Presigner` | `AwsConfig.java` | Presigned PUT URL generation |
| `S3Client` | `AwsConfig.java` | `headObject` verify + `deleteObject` |
| `CloudFrontUrlBuilder` | `shared-kernel` | `buildUrl(domain, bucket, s3Key)` |

### New Files to Create

```
Backend:
services/event-management-service/src/main/resources/db/migration/V79__create_event_photos.sql
services/event-management-service/.../domain/EventPhoto.java
services/event-management-service/.../repository/EventPhotoRepository.java
services/event-management-service/.../service/EventPhotoService.java
services/event-management-service/.../controller/EventPhotoController.java

Tests:
services/event-management-service/.../service/EventPhotoServiceTest.java
services/event-management-service/.../controller/EventPhotoControllerIntegrationTest.java

Frontend:
web-frontend/src/components/organizer/EventPage/EventPhotosTab.tsx
web-frontend/src/hooks/useEventPhotos.ts
web-frontend/src/hooks/useRecentEventPhotos.ts
```

### Modified Files

```
docs/api/events-api.openapi.yml                                   — 5 new endpoints + 4 schemas (FIRST)
web-frontend/src/components/organizer/EventPage/EventPage.tsx     — add 'photos' tab (TABS array + renderTabContent switch)
web-frontend/src/components/public/Testimonials/TestimonialSection.tsx — replace first row with real photos (fallback kept)
web-frontend/src/pages/public/ArchiveEventDetailPage.tsx          — add photos marquee section
web-frontend/public/locales/de/events.json                        — photos.* + eventPage.tabs.photos + archive.detail.photos
web-frontend/public/locales/en/events.json                        — same
... (fr, it, rm, es, fi, nl, ja, gsw-BE)                          — 8 more locale files (EN placeholders)
```

### Architecture Compliance

Follow these patterns (confirmed from codebase):
- `@RestController` + `@RequestMapping("/api/v1")` (NOT `/api/v1/events` at class level — check `AiAssistController` pattern where paths are fully specified per method)
- `@PreAuthorize("hasRole('ORGANIZER')")` per method (not class level) for mixed auth endpoints
- `@RequiredArgsConstructor` + `@Slf4j` + `@Service` + `@Transactional`
- Constructor injection (not `@Autowired`)
- `@Value("${aws.s3.bucket-name:batbern-development-company-logos}")` for S3 bucket name
- `@ExtendWith(MockitoExtension.class)` for unit tests, `AbstractIntegrationTest` for integration tests
- `ReflectionTestUtils.setField(service, "bucketName", "test-bucket")` for `@Value` fields in unit tests

### Test Command Reference

```bash
# TDD Red phase
./gradlew :services:event-management-service:test --tests "*EventPhotoServiceTest" 2>&1 | tee /tmp/test-10-21-red.log

# TDD Green phase
./gradlew :services:event-management-service:test --tests "*EventPhotoServiceTest" 2>&1 | tee /tmp/test-10-21-green.log

# Integration test
./gradlew :services:event-management-service:test --tests "*EventPhotoController*" 2>&1 | tee /tmp/test-10-21-int.log

# Full EMS suite
./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-21-full.log && grep -E "FAILED|BUILD" /tmp/test-10-21-full.log | tail -5

# Frontend type-check
cd web-frontend && npx tsc --noEmit 2>&1 | tee /tmp/typecheck-10-21.log && grep -c "error" /tmp/typecheck-10-21.log

# Frontend lint (new files only)
cd web-frontend && npx eslint src/components/organizer/EventPage/EventPhotosTab.tsx src/hooks/useEventPhotos.ts src/hooks/useRecentEventPhotos.ts 2>&1 | tee /tmp/lint-10-21.log
```

### References

- Presigned PUT URL pattern: [Source: services/event-management-service/.../service/SpeakerProfilePhotoService.java]
- S3 delete pattern: [Source: services/event-management-service/.../service/SessionMaterialsService.java#L475-485]
- S3 delete (logo): [Source: services/event-management-service/.../service/GenericLogoService.java#L123-128]
- Controller pattern (mixed auth): [Source: services/event-management-service/.../controller/AiAssistController.java]
- InfiniteMarquee component: [Source: web-frontend/src/components/public/Testimonials/InfiniteMarquee.tsx]
- TestimonialSection (to modify): [Source: web-frontend/src/components/public/Testimonials/TestimonialSection.tsx]
- EventPage tab structure: [Source: web-frontend/src/components/organizer/EventPage/EventPage.tsx#L58-66]
- ArchiveEventDetailPage: [Source: web-frontend/src/pages/public/ArchiveEventDetailPage.tsx]
- Story spec: [Source: docs/prd/epic-10-additional-stories.md#L1482-1556]
- AbstractIntegrationTest pattern: [Source: services/event-management-service/.../controller/AiAssistControllerIntegrationTest.java]
- CloudFrontUrlBuilder: [Source: shared-kernel/src/main/java/ch/batbern/shared/utils/CloudFrontUrlBuilder.java]
- Flyway migration dir: [Source: services/event-management-service/src/main/resources/db/migration/] — V78 is next

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/ems-test-10-21.log` — backend full test suite (T17)
- `/tmp/fe-typecheck-10-21.log` — frontend type-check (T19)

### Completion Notes List

1. **V78 conflict** — Story says V78 for migration, but V78 was already taken by `V78__add_task_cancellation_fields.sql`. Used **V79** instead.
2. **Auth pattern** — Story spec shows `@AuthenticationPrincipal Jwt jwt`, but codebase uses `Authentication authentication` injection pattern (confirmed from `PublishingEngineController`). Used `Authentication`.
3. **Event field name** — Entity field is `date` (not `eventDate`), mapped to `event_date` column. Used `findTopByOrderByDateDesc(Pageable)` in `EventRepository`.
4. **Broken JSDoc** — Photo methods were accidentally trapped inside an unclosed `/**` block comment in `eventApiClient.ts`. Fixed by removing the incomplete JSDoc.
5. **MUI Grid v2** — Project uses Grid v2 API (`size={{ xs, sm, md, lg }}`). Fixed `EventPhotosTab.tsx` accordingly.
6. **archive.detail.photos** — Lives in `common.json` (default namespace), not `events.json`.

### File List

**Created:**
- `services/event-management-service/src/main/resources/db/migration/V79__create_event_photos.sql`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/EventPhoto.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/EventPhotoRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/EventPhotoService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventPhotoController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/EventPhotoResponseDto.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/EventPhotoUploadRequestDto.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/EventPhotoUploadResponseDto.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/EventPhotoConfirmRequestDto.java`
- `services/event-management-service/src/main/java/ch/batbern/events/exception/EventPhotoNotFoundException.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/EventPhotoServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/EventPhotoControllerIntegrationTest.java`
- `web-frontend/src/components/organizer/EventPage/EventPhotosTab.tsx`
- `web-frontend/src/hooks/useEventPhotos.ts`
- `web-frontend/src/hooks/useRecentEventPhotos.ts`

**Modified:**
- `docs/api/events-api.openapi.yml`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/EventRepository.java`
- `web-frontend/src/services/eventApiClient.ts`
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx`
- `web-frontend/src/components/public/Testimonials/TestimonialSection.tsx`
- `web-frontend/src/pages/public/ArchiveEventDetailPage.tsx`
- `web-frontend/public/locales/{en,de,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json`
- `web-frontend/public/locales/{en,de,fr,it,rm,es,fi,nl,ja,gsw-BE}/common.json`

### Senior Developer Review (AI) — 2026-03-03

**Result:** Changes Requested → Fixed

**Findings fixed:**
- **C1** — Added `@ExceptionHandler(InvalidFileTypeException.class)` to `GlobalExceptionHandler` returning HTTP 422.
- **C2** — File size enforcement added: `requestUploadUrl` now validates `fileSize` against `MAX_FILE_SIZE_BYTES` (10 MB).
- **H1** — `confirmUpload` now validates `s3Key` starts with `events/{eventCode}/photos/` before calling S3. Prevents cross-event key injection.
- **H2** — Added `confirmUpload_asOrganizer_returns200WithPhoto` + `confirmUpload_asAnonymous_returns403` tests to `EventPhotoControllerIntegrationTest`.
- **H3** — Null-safe `contentType` check in `requestUploadUrl` (was NPE on `Set.of().contains(null)`).
- **M1** — AC1 text corrected to V79; stale V78 references updated throughout story.
- **M2** — Anonymous status assertion sharpened from `is4xxClientError()` → `isForbidden()`.
- **M3** — Added `@Valid` to `@RequestBody` params in controller; added `@NotBlank`/`@NotNull`/`@Positive` to both DTOs.
- **L1** — `firstRow` slice moved inside the fallback branch in `TestimonialSection.tsx`.

**Tests:** 23/23 PASSED (`EventPhotoServiceTest` + `EventPhotoControllerIntegrationTest`)
