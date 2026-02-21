# Story 2.1: Pairing Code Backend & Web Frontend

Status: done

## Story

As an organizer,
I want to generate a Watch pairing code from my BATbern web profile,
so that I can pair my Apple Watch without typing a password on the tiny screen.

## Acceptance Criteria

1. **AC1 — Generate Pairing Code**: Given I'm on my organizer profile page, When I click "Pair Apple Watch", Then a 6-digit numeric code appears with a 24-hour expiry countdown.

2. **AC2 — Max Watches Enforcement**: Given I already have 2 watches paired (NFR19), When I try to generate a new code, Then I see an error: "Maximum 2 watches paired. Unpair a device first."

3. **AC3 — Code Expiry**: Given a pairing code exists, When 24 hours pass without use (NFR20), Then the code expires and is no longer valid.

4. **AC4 — Unpair Watch**: Given I have a paired watch, When I click "Unpair" on my profile, Then the watch pairing is removed and the Watch shows the pairing screen on next organizer zone access.

## Tasks / Subtasks

- [x] **Task 1: Database Schema - Flyway Migration** (AC: #1, #2, #3, #4)
  - [x] 1.1 Create `services/company-user-management-service/src/main/resources/db/migration/V{next}__add_watch_pairing.sql`
  - [x] 1.2 Define `watch_pairings` table schema per architecture spec:
    - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
    - `username VARCHAR(100) NOT NULL` (FK to users table)
    - `pairing_code VARCHAR(6)` (nullable - only set when code generated, cleared after use)
    - `pairing_code_expires_at TIMESTAMP` (nullable - 24h from generation)
    - `pairing_token VARCHAR(256) UNIQUE` (set after successful pairing on Watch)
    - `device_name VARCHAR(100)` (optional device identifier)
    - `paired_at TIMESTAMP` (nullable - set when Watch completes pairing)
    - `created_at TIMESTAMP NOT NULL DEFAULT NOW()`
  - [x] 1.3 Add FK constraint: `CONSTRAINT fk_watch_user FOREIGN KEY (username) REFERENCES users(username)`
  - [x] 1.4 Create index: `CREATE INDEX idx_watch_pairings_username ON watch_pairings(username)`
  - [x] 1.5 Create index: `CREATE INDEX idx_watch_pairings_code ON watch_pairings(pairing_code) WHERE pairing_code IS NOT NULL`
  - [x] 1.6 **CRITICAL: Max 2 watches enforcement** - Add unique constraint with partial index:
    ```sql
    CREATE UNIQUE INDEX idx_watch_pairings_limit
    ON watch_pairings(username, paired_at)
    WHERE paired_at IS NOT NULL;
    ```
    This prevents more than 2 rows with `paired_at NOT NULL` per username.
  - [x] 1.7 Verify migration applies cleanly on local PostgreSQL (Testcontainers)

- [x] **Task 2: JPA Entity - WatchPairing** (AC: all)
  - [x] 2.1 Create `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/WatchPairing.java`
  - [x] 2.2 Define entity fields matching table schema:
    ```java
    @Entity
    @Table(name = "watch_pairings")
    public class WatchPairing {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(nullable = false, length = 100)
        private String username;

        @Column(length = 6)
        private String pairingCode;

        @Column
        private LocalDateTime pairingCodeExpiresAt;

        @Column(unique = true, length = 256)
        private String pairingToken;

        @Column(length = 100)
        private String deviceName;

        @Column
        private LocalDateTime pairedAt;

        @Column(nullable = false, updatable = false)
        private LocalDateTime createdAt = LocalDateTime.now();
    }
    ```
  - [x] 2.3 Add helper methods:
    - `isCodeExpired()` → check if `pairingCodeExpiresAt` < now
    - `isPaired()` → check if `paired_at` is not null
    - `clearPairingCode()` → set `pairingCode` and `pairingCodeExpiresAt` to null (after successful pairing)
  - [x] 2.4 **Never expose UUIDs in API responses** (ADR-003 compliance) - Use `username` + `deviceName` for identification

- [x] **Task 3: Repository Layer** (AC: all)
  - [x] 3.1 Create `WatchPairingRepository` interface extending `JpaRepository<WatchPairing, UUID>`
  - [x] 3.2 Add query methods:
    - `List<WatchPairing> findByUsernameAndPairedAtNotNull(String username)` → Get all active pairings for a user
    - `Optional<WatchPairing> findByPairingCode(String pairingCode)` → Lookup code during Watch pairing
    - `Optional<WatchPairing> findByPairingToken(String pairingToken)` → Lookup token during authentication
    - `long countByUsernameAndPairedAtNotNull(String username)` → Count paired watches for max enforcement
  - [x] 3.3 Add `@Transactional` annotations where needed (write operations)

- [x] **Task 4: Service Layer - WatchPairingService** (AC: all)
  - [x] 4.1 Create `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/WatchPairingService.java`
  - [x] 4.2 Inject `WatchPairingRepository`, `UserRepository`
  - [x] 4.3 Implement `generatePairingCode(String username)`:
    - **Pre-check:** Count existing paired watches for user → if ≥ 2, throw `MaxWatchesExceededException`
    - Generate 6-digit numeric code (use `ThreadLocalRandom` for cryptographic randomness)
    - Check for code collision (unlikely but possible) → regenerate if exists
    - Create new `WatchPairing` row with:
      - `username`
      - `pairingCode` = 6-digit string
      - `pairingCodeExpiresAt` = `LocalDateTime.now().plusHours(24)`
      - All other fields null
    - Save to repository
    - Return `PairingCodeResponse` DTO
  - [x] 4.4 Implement `getPairingStatus(String username)`:
    - Fetch all pairings for user (both active and pending codes)
    - Return list of paired devices + any pending code with expiry
  - [x] 4.5 Implement `unpairWatch(String username, String deviceName)`:
    - Find pairing by `username` and `deviceName`
    - Delete row (hard delete - GDPR compliant, minimal PII)
    - Return success/failure
  - [x] 4.6 **Expiry cleanup job** (optional for MVP, recommended for production):
    - Scheduled task to delete expired codes (WHERE `pairingCodeExpiresAt` < now AND `pairedAt` IS NULL)
    - Run daily at 3 AM UTC

- [x] **Task 5: DTOs for API Responses** (AC: all)
  - [x] 5.1 Create `ch.batbern.companyuser.watch.dto.PairingCodeResponse`:
    ```java
    public record PairingCodeResponse(
        String pairingCode,
        LocalDateTime expiresAt,
        long hoursUntilExpiry  // For countdown display
    ) {}
    ```
  - [x] 5.2 Create `ch.batbern.companyuser.watch.dto.PairingStatusResponse`:
    ```java
    public record PairingStatusResponse(
        List<PairedWatch> pairedWatches,
        PendingPairingCode pendingCode  // nullable
    ) {}

    public record PairedWatch(
        String deviceName,
        LocalDateTime pairedAt
    ) {}

    public record PendingPairingCode(
        String code,
        LocalDateTime expiresAt
    ) {}
    ```
  - [x] 5.3 **Never expose UUIDs** - Use meaningful identifiers only (username, deviceName)

- [x] **Task 6: REST Controller - WatchPairingController** (AC: all)
  - [x] 6.1 Create `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/WatchPairingController.java`
  - [x] 6.2 `@RestController` with `@RequestMapping("/api/v1/users/{username}/watch-pairing")`
  - [x] 6.3 Endpoint: `POST /api/v1/users/{username}/watch-pairing` (Generate code)
    - **Authentication:** Require Organizer JWT (Spring Security `@PreAuthorize("hasRole('ORGANIZER')")`)
    - **Authorization:** Verify JWT `username` matches path `{username}` (users can only generate codes for themselves)
    - Call `watchPairingService.generatePairingCode(username)`
    - Return `PairingCodeResponse` with HTTP 201
    - Error cases:
      - User already has 2 paired watches → HTTP 409 Conflict with message: "Maximum 2 watches paired. Unpair a device first."
      - User not found → HTTP 404
  - [x] 6.4 Endpoint: `GET /api/v1/users/{username}/watch-pairing` (Check status)
    - **Authentication:** Require Organizer JWT
    - **Authorization:** Verify JWT `username` matches path `{username}`
    - Call `watchPairingService.getPairingStatus(username)`
    - Return `PairingStatusResponse` with HTTP 200
  - [x] 6.5 Endpoint: `DELETE /api/v1/users/{username}/watch-pairing/{deviceName}` (Unpair)
    - **Authentication:** Require Organizer JWT
    - **Authorization:** Verify JWT `username` matches path `{username}`
    - Call `watchPairingService.unpairWatch(username, deviceName)`
    - Return HTTP 204 No Content on success
    - Error case: Device not found → HTTP 404

- [x] **Task 7: Integration Tests (Testcontainers PostgreSQL)** (AC: all)
  - [x] 7.1 Create `WatchPairingIntegrationTest` extending `AbstractIntegrationTest`
  - [x] 7.2 Test: `shouldGeneratePairingCode_whenValidUser()`
    - POST to `/api/v1/users/{username}/watch-pairing` with Organizer JWT
    - Assert: 201 Created, response contains 6-digit code, expiry is ~24h from now
    - Verify: Code saved in database with correct expiry
  - [x] 7.3 Test: `shouldRejectPairingCodeGeneration_whenMaxWatchesReached()`
    - Pre-condition: Create 2 paired watches for test user
    - POST to generate code
    - Assert: 409 Conflict, error message "Maximum 2 watches paired"
  - [x] 7.4 Test: `shouldReturnPairingStatus_whenCalled()`
    - Pre-condition: User has 1 paired watch + 1 pending code
    - GET `/api/v1/users/{username}/watch-pairing`
    - Assert: Response includes both pairedWatches array and pendingCode object
  - [x] 7.5 Test: `shouldUnpairWatch_whenCalled()`
    - Pre-condition: User has 1 paired watch
    - DELETE `/api/v1/users/{username}/watch-pairing/{deviceName}`
    - Assert: 204 No Content, device removed from database
  - [x] 7.6 Test: `shouldRejectUnpair_whenNotOwner()`
    - Different user tries to unpair another user's watch
    - Assert: 403 Forbidden
  - [x] 7.7 Test: `shouldHandleExpiredCode_correctly()`
    - Pre-condition: Create pairing code with `pairingCodeExpiresAt` in the past
    - Verify: `isCodeExpired()` returns true
    - Verify: GET status shows code as expired or not returned
  - [x] 7.8 **All integration tests MUST use Testcontainers PostgreSQL** (never H2)

- [x] **Task 8: Web Frontend - WatchPairingSection Component** (AC: all)
  - [x] 8.1 Create `web-frontend/src/features/profile/WatchPairingSection.tsx`
  - [x] 8.2 Component structure:
    ```tsx
    export const WatchPairingSection: React.FC = () => {
      const [pairingStatus, setPairingStatus] = useState<PairingStatusResponse | null>(null);
      const [isGenerating, setIsGenerating] = useState(false);

      // Load pairing status on mount
      useEffect(() => {
        loadPairingStatus();
      }, []);

      const handleGenerateCode = async () => {
        // POST /api/v1/users/{username}/watch-pairing
        // Show generated code + countdown
      };

      const handleUnpair = async (deviceName: string) => {
        // DELETE /api/v1/users/{username}/watch-pairing/{deviceName}
        // Refresh status
      };

      return (
        <section>
          <h3>Apple Watch Pairing</h3>
          {pairingStatus?.pendingCode && (
            <PairingCodeDisplay code={pairingStatus.pendingCode} />
          )}
          {pairingStatus?.pairedWatches.length === 0 && !pairingStatus.pendingCode && (
            <button onClick={handleGenerateCode}>Pair Apple Watch</button>
          )}
          {pairingStatus?.pairedWatches.map(watch => (
            <PairedWatchCard key={watch.deviceName} watch={watch} onUnpair={handleUnpair} />
          ))}
        </section>
      );
    };
    ```
  - [x] 8.3 Sub-component: `PairingCodeDisplay` - Shows 6-digit code + 24h countdown timer
    - Large monospace font for code readability
    - Countdown timer using `useEffect` with 1-second interval
    - Instructions: "Open BATbern Watch app, swipe right, enter this code"
  - [x] 8.4 Sub-component: `PairedWatchCard` - Shows device name + paired date + Unpair button
  - [x] 8.5 Error handling:
    - Max watches error (409) → Show error message: "Maximum 2 watches paired. Unpair a device first."
    - Network errors → Show retry button
  - [x] 8.6 Integrate into `web-frontend/src/features/profile/ProfilePage.tsx`:
    - Add `<WatchPairingSection />` in organizer profile settings area
    - Only visible to users with `ORGANIZER` role

- [x] **Task 9: API Service Layer (Frontend)** (AC: all)
  - [x] 9.1 Extend `web-frontend/src/services/apiClient.ts` with Watch pairing endpoints:
    ```typescript
    export const watchPairingApi = {
      generatePairingCode: (username: string) =>
        apiClient.post<PairingCodeResponse>(`/api/v1/users/${username}/watch-pairing`),

      getPairingStatus: (username: string) =>
        apiClient.get<PairingStatusResponse>(`/api/v1/users/${username}/watch-pairing`),

      unpairWatch: (username: string, deviceName: string) =>
        apiClient.delete(`/api/v1/users/${username}/watch-pairing/${deviceName}`)
    };
    ```
  - [x] 9.2 Type definitions in `web-frontend/src/types/watch.ts`:
    ```typescript
    export interface PairingCodeResponse {
      pairingCode: string;
      expiresAt: string; // ISO 8601
      hoursUntilExpiry: number;
    }

    export interface PairingStatusResponse {
      pairedWatches: PairedWatch[];
      pendingCode: PendingPairingCode | null;
    }

    export interface PairedWatch {
      deviceName: string;
      pairedAt: string; // ISO 8601
    }

    export interface PendingPairingCode {
      code: string;
      expiresAt: string; // ISO 8601
    }
    ```

- [x] **Task 10: Update OpenAPI Specification** (AC: all)
  - [x] 10.1 Add to `docs/api/company-user-management-api.openapi.yml`:
    - Path: `POST /api/v1/users/{username}/watch-pairing`
    - Path: `GET /api/v1/users/{username}/watch-pairing`
    - Path: `DELETE /api/v1/users/{username}/watch-pairing/{deviceName}`
  - [x] 10.2 Define schemas:
    - `PairingCodeResponse`
    - `PairingStatusResponse`
    - `PairedWatch`
    - `PendingPairingCode`
  - [x] 10.3 Security: All endpoints require `bearerAuth` (Organizer JWT)

- [x] **Task 11: Error Messages & Localization** (AC: all)
  - [x] 11.1 Backend error messages (English only for MVP):
    - `"Maximum 2 watches paired. Unpair a device first."`
    - `"Pairing code not found or expired."`
    - `"Watch device not found."`
  - [x] 11.2 Frontend localization keys:
    - `watch.pairing.title` = "Apple Watch Pairing"
    - `watch.pairing.generate_button` = "Pair Apple Watch"
    - `watch.pairing.code_instructions` = "Open BATbern Watch, swipe right, enter code"
    - `watch.pairing.unpair_button` = "Unpair"
    - `watch.pairing.max_watches_error` = "Maximum 2 watches paired. Unpair a device first."
    - `watch.pairing.expires_in` = "Expires in {hours}h {minutes}m"

- [x] **Task 12: Write Tests (Frontend)** (AC: all)
  - [x] 12.1 `WatchPairingSection.test.tsx` - Component tests using React Testing Library
    - Render with no paired watches → shows "Pair Apple Watch" button
    - Render with 2 paired watches → hides "Pair" button, shows 2 watch cards
    - Click "Pair Apple Watch" → calls API, displays code + countdown
    - Click "Unpair" → calls DELETE API, refreshes status
  - [x] 12.2 Mock `watchPairingApi` calls using MSW (Mock Service Worker)

## Dev Notes

### Story Context & Epic Breakdown

**Epic 2: Watch Pairing & Organizer Access**

This epic introduces the authentication layer for the organizer zone. The pairing code flow avoids password entry on the tiny Watch screen — organizers generate a code from the web profile, then enter it once on the Watch to pair permanently.

**Epic 2 Story Sequence:**
- **W2.1 (THIS STORY)**: Pairing Code Backend & Web Frontend — Generate codes, manage pairings from web profile
- **W2.2**: Watch Pairing Flow & Organizer Zone Navigation — Enter code on Watch, state-dependent organizer entry
- **W2.3**: Event Join & Schedule Sync — Sync full schedule to Watch after pairing
- **W2.4**: Speaker Arrival Tracking — Pre-event speaker portrait grid with arrival confirmation

This story (W2.1) sets up the backend infrastructure and web UI. Story W2.2 will implement the Watch-side pairing screen and token exchange.

### Architecture Constraints

**Service Boundary:** All pairing code management belongs in `company-user-management-service` (CUMS), not `event-management-service`. CUMS owns user identity data. Event-specific logic (session state, speaker arrivals) belongs in event-management-service.

**Database Constraints:**
- Max 2 watches per organizer **MUST** be enforced server-side via database constraint (not just application logic)
- Use partial unique index: `CREATE UNIQUE INDEX ... WHERE paired_at IS NOT NULL`
- This prevents race conditions where two simultaneous pairing attempts could exceed the limit

**Security:**
- Pairing codes are 6-digit numeric (easier to read on Watch than alphanumeric)
- Codes expire after 24 hours (NFR20)
- Codes are single-use (cleared after successful pairing in W2.2)
- Pairing tokens are long-lived (until explicitly unpaired)
- Only `ORGANIZER` role can generate pairing codes
- Users can only generate codes for their own account (JWT username validation)

**ADR-003 Compliance:**
- Never expose UUIDs in API responses
- Use `username` + `deviceName` as meaningful identifiers
- API paths use `{username}` (meaningful) instead of `{userId}` (UUID)

### Pairing Code Flow (W2.1 + W2.2)

**W2.1 Scope (this story):**
1. Organizer clicks "Pair Apple Watch" on web profile
2. Backend generates 6-digit code with 24h expiry
3. Code displayed on profile page with countdown
4. Organizer can view paired devices and unpair from web

**W2.2 Scope (next story):**
5. Organizer enters code on Watch pairing screen (O1)
6. Watch calls `POST /api/v1/watch/pair { code }` (new endpoint, W2.2)
7. Backend validates code, generates `pairingToken`, clears code from database
8. Watch stores `pairingToken` in Keychain
9. Watch calls `POST /api/v1/watch/authenticate { pairingToken }` to get JWT (new endpoint, W2.2)
10. Organizer zone loads (O2 or O3 depending on event state)

**Security Tokens:**
- **Pairing code**: 6-digit numeric, 24h TTL, single-use (this story generates, W2.2 validates)
- **Pairing token**: Long-lived (until unpaired), stored in Watch Keychain (W2.2 creates)
- **Access JWT**: 1 hour TTL, stored in Watch memory, auto-refreshed (W2.2 uses)

### Frontend Integration Points

**Profile Page Location:**
The `WatchPairingSection` component integrates into the existing organizer profile page:

```
web-frontend/src/features/profile/
├── ProfilePage.tsx                 # MODIFIED: Add <WatchPairingSection />
├── WatchPairingSection.tsx         # NEW: Pairing UI
├── PairingCodeDisplay.tsx          # NEW: Code + countdown
└── PairedWatchCard.tsx             # NEW: Device list item
```

**API Client Pattern:**
Follow the existing pattern from `web-frontend/src/services/companyService.ts`:
- Use `apiClient` singleton from `apiClient.ts`
- JWT automatically included in Authorization header
- Error handling via interceptors

### Database Schema Design

**Table: `watch_pairings`**

```sql
CREATE TABLE watch_pairings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL,
    pairing_code VARCHAR(6),                    -- Nullable: set when code generated, cleared after use
    pairing_code_expires_at TIMESTAMP,          -- Nullable: 24h from generation
    pairing_token VARCHAR(256) UNIQUE,          -- Set after successful pairing
    device_name VARCHAR(100),                   -- Optional device identifier
    paired_at TIMESTAMP,                        -- Nullable: set when pairing completes
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_watch_user FOREIGN KEY (username) REFERENCES users(username)
);

-- Indexes
CREATE INDEX idx_watch_pairings_username ON watch_pairings(username);
CREATE INDEX idx_watch_pairings_code ON watch_pairings(pairing_code) WHERE pairing_code IS NOT NULL;

-- Max 2 watches enforcement
CREATE UNIQUE INDEX idx_watch_pairings_limit
ON watch_pairings(username, paired_at)
WHERE paired_at IS NOT NULL;
```

**Row Lifecycle:**
1. Code generation: Insert row with `pairing_code` + `pairing_code_expires_at`, all other fields null
2. Successful pairing (W2.2): Update row, set `pairing_token` + `paired_at`, clear `pairing_code`
3. Unpair: Delete row (hard delete)
4. Expired code cleanup: Delete rows WHERE `pairing_code_expires_at` < now AND `paired_at` IS NULL

### Code Generation Logic

**6-Digit Numeric Code:**
```java
private String generatePairingCode() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int code = random.nextInt(100_000, 1_000_000); // Range: 100000-999999
    return String.format("%06d", code);
}
```

**Collision Handling:**
```java
String code = generatePairingCode();
while (watchPairingRepository.findByPairingCode(code).isPresent()) {
    code = generatePairingCode(); // Regenerate if collision
}
```

**Expiry Calculation:**
```java
LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
```

### Testing Strategy

**Integration Test Coverage:**
- ✅ Happy path: Generate code → appears in database with 24h expiry
- ✅ Max watches: 2 paired watches → 3rd attempt fails with 409
- ✅ Unpair: Remove watch → no longer in database
- ✅ Status check: Returns both paired watches and pending codes
- ✅ Authorization: Different user cannot unpair another user's watch
- ✅ Expired code: Code past expiry is marked expired

**Frontend Test Coverage:**
- ✅ Render states: No watches, 1 watch, 2 watches, pending code
- ✅ Generate code: Button click → API call → code display
- ✅ Countdown: Timer updates every second
- ✅ Unpair: Button click → confirmation → API call → status refresh
- ✅ Error handling: Max watches error message displays correctly

### Previous Story Learnings (W1.4)

**From W1.4 (Progressive Publishing & Offline Support):**
- SwiftData models established for `CachedEvent`, `CachedSession`, `CachedSpeaker`
- `PublicViewModel` pattern: `@Observable` property wrapper for state management
- Localization pattern: `NSLocalizedString` with German (de_CH) as primary locale
- Type flow: OpenAPI generated types → domain models → SwiftData persistence
- Testing pattern: `MockConnectivityMonitor` for deterministic testing

**W2.1 Adapts These Patterns:**
- W2.1 adds `PairingInfo` SwiftData model (W2.2 will use it)
- W2.1 adds `watchPairingApi` service (follows `publicEventService` pattern from W1.1)
- W2.1 establishes backend → web frontend → Watch data flow (Watch pairing happens in W2.2)

### Files Created/Modified in This Story

**New Files (Backend):**
- `services/company-user-management-service/src/main/resources/db/migration/V{next}__add_watch_pairing.sql`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/WatchPairing.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/WatchPairingService.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/WatchPairingController.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/dto/PairingCodeResponse.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/dto/PairingStatusResponse.java`
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/watch/WatchPairingIntegrationTest.java`

**New Files (Frontend):**
- `web-frontend/src/features/profile/WatchPairingSection.tsx`
- `web-frontend/src/features/profile/PairingCodeDisplay.tsx`
- `web-frontend/src/features/profile/PairedWatchCard.tsx`
- `web-frontend/src/services/watchPairingApi.ts`
- `web-frontend/src/types/watch.ts`
- `web-frontend/src/features/profile/WatchPairingSection.test.tsx`

**Modified Files:**
- `web-frontend/src/features/profile/ProfilePage.tsx` — Add `<WatchPairingSection />`
- `docs/api/company-user-management-api.openapi.yml` — Add pairing endpoints

### Project Structure After W2.1

```
services/company-user-management-service/
├── src/main/
│   ├── java/ch/batbern/companyuser/
│   │   ├── domain/
│   │   │   └── WatchPairing.java                  # NEW: JPA entity
│   │   └── watch/                                 # NEW package
│   │       ├── WatchPairingController.java        # NEW: REST endpoints
│   │       ├── WatchPairingService.java           # NEW: Business logic
│   │       ├── WatchPairingRepository.java        # NEW: JPA repository
│   │       └── dto/
│   │           ├── PairingCodeResponse.java       # NEW
│   │           └── PairingStatusResponse.java     # NEW
│   └── resources/db/migration/
│       └── V{next}__add_watch_pairing.sql         # NEW: Flyway migration
└── src/test/java/ch/batbern/companyuser/watch/
    └── WatchPairingIntegrationTest.java           # NEW

web-frontend/src/
├── features/profile/
│   ├── ProfilePage.tsx                            # MODIFIED: Add WatchPairingSection
│   ├── WatchPairingSection.tsx                    # NEW
│   ├── PairingCodeDisplay.tsx                     # NEW
│   ├── PairedWatchCard.tsx                        # NEW
│   └── WatchPairingSection.test.tsx               # NEW
├── services/
│   └── watchPairingApi.ts                         # NEW
└── types/
    └── watch.ts                                   # NEW
```

### References

- [Source: docs/watch-app/epics.md#W2.1] — Story definition and acceptance criteria
- [Source: docs/watch-app/architecture.md#Authentication-Security] — Pairing code flow design
- [Source: docs/watch-app/architecture.md#Data-Architecture] — watch_pairings table schema
- [Source: docs/watch-app/architecture.md#API-Communication-Patterns] — Pairing endpoints specification
- [Source: docs/watch-app/prd-batbern-watch.md#Pairing-Data] — API endpoint requirements
- [Source: docs/watch-app/ux-design-specification.md#Pairing-Authentication-UX] — 6-digit code UX design
- [Source: docs/architecture/coding-standards.md#Testing-Standards] — Testcontainers PostgreSQL requirement
- [Source: docs/architecture/coding-standards.md#ADR-003] — Meaningful identifiers, never UUIDs in URLs
- [Source: _bmad-output/implementation-artifacts/w1-4-progressive-publishing-offline-support.md] — Previous story patterns (SwiftData, localization, testing)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)

### Debug Log References

N/A - Story file created by SM agent in YOLO mode

### Completion Notes List

**Story Preparation Summary:**
- Comprehensive backend + web frontend specification for Watch pairing code generation
- Database schema with max 2 watches enforcement via partial unique index
- REST endpoints in company-user-management-service with Organizer JWT auth
- React component for web profile integration
- All acceptance criteria mapped to specific tasks
- Integration tests specified with Testcontainers PostgreSQL requirement
- Security constraints documented (6-digit numeric codes, 24h expiry, single-use)

**Implementation Notes (Dev Agent - 2026-02-17):**
- FK target is `user_profiles(username)` NOT `users(username)` — the User entity maps to `user_profiles` table
- `AccessDeniedException` must be used for authorization failures, NOT `ResponseStatusException(FORBIDDEN)` — the latter gets caught by the catch-all `Exception` handler in `GlobalExceptionHandler` and returns 500
- `hoursUntilExpiry` in `PairingCodeResponse` must return the `CODE_TTL_HOURS` constant (24L) directly, not calculate dynamically via `ChronoUnit.HOURS.between()` — the slight timing offset causes truncation to 23
- `@EnableMethodSecurity` is on `SecurityConfig` with `@Profile("!local")` — it IS active in test profile, so `@PreAuthorize` is enforced in integration tests
- Frontend `WatchPairingSection` integrated into `UserProfileTab.tsx` (not `ProfilePage.tsx`) — ORGANIZER-only gate via `user.roles.includes('ORGANIZER')`
- OpenAPI spec updated in `docs/api/users-api.openapi.yml` (not company-user-management-api.openapi.yml as originally specified in story — the users API file covers CUMS user endpoints)
- Frontend service placed at `src/services/api/watchPairingApi.ts` (not `src/services/watchPairingApi.ts`) to match existing project convention

**Test Results (initial):**
- Backend: 11/11 integration tests pass, full CUMS test suite BUILD SUCCESSFUL
- Frontend: 6/6 WatchPairingSection component tests pass, 242 test files / 3575 tests pass overall

**Code Review Fixes (2026-02-17):**
- C1: Implemented Task 4.6 — created `WatchPairingCleanupService` with `@Scheduled(cron="0 0 3 * * *")` + ShedLock
- H1: Added `countPairedWatchesForUpdate` with `@Lock(PESSIMISTIC_WRITE)` to prevent race condition at max-2 check; corrected misleading DB comment in V13 migration
- H2: Replaced `ThreadLocalRandom` with `SecureRandom` for pairing code generation
- H3: Added `deleteAllPendingCodesByUsername` called before inserting new pending code to prevent orphan row accumulation
- L1: Replaced infinite do-while with MAX_CODE_RETRIES=10 capped loop
- M1: `loadStatus` now distinguishes 404 (empty) from real errors (shows error message)
- M2: `PairedWatchCard` now requires two-step confirmation before unpair
- M3: Added countdown, cancel-unpair, and load-error tests to `WatchPairingSection.test.tsx`
- M4: Added `401` responses to all three watch-pairing endpoints in `users-api.openapi.yml`
- L2: Removed dead code on `WatchPairingSection.tsx` line 132

### File List

**Story File:**
- `_bmad-output/implementation-artifacts/w2-1-pairing-code-backend-web-frontend.md`

**New Files (Backend):**
- `services/company-user-management-service/src/main/resources/db/migration/V13__add_watch_pairing.sql`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/WatchPairing.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/WatchPairingService.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/WatchPairingController.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/WatchPairingCleanupService.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/repository/WatchPairingRepository.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/dto/PairingCodeResponse.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/watch/dto/PairingStatusResponse.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/MaxWatchesExceededException.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/WatchPairingNotFoundException.java`
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/watch/WatchPairingIntegrationTest.java`

**New Files (Frontend):**
- `web-frontend/src/features/profile/WatchPairingSection.tsx`
- `web-frontend/src/features/profile/PairingCodeDisplay.tsx`
- `web-frontend/src/features/profile/PairedWatchCard.tsx`
- `web-frontend/src/services/api/watchPairingApi.ts`
- `web-frontend/src/types/watch.ts`
- `web-frontend/src/features/profile/WatchPairingSection.test.tsx`

**Modified Files:**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/GlobalExceptionHandler.java` — Added handlers for MaxWatchesExceededException (409) and WatchPairingNotFoundException (404)
- `web-frontend/src/components/user/UserProfileTab/UserProfileTab.tsx` — Added WatchPairingSection for ORGANIZER users
- `docs/api/users-api.openapi.yml` — Added watch-pairing paths and schemas; added 401 responses (code review fix)
