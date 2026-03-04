# Story 10.25: Partner Meeting iCal Auto-creation & Year-End Reminders

Status: ready-for-dev

## Story

As an **organizer**,
I want a partner meeting to be automatically created whenever I create a new BATbern event,
so that I don't have to set it up manually each time.

As an **organizer**,
I want the partner meeting invite email body to be editable from the admin Email Templates section (like all other system emails),
so that I can customize the content without a code deploy.

As an **organizer**,
I want a task to be automatically created on 31 December each year reminding me to send iCal invitations to partners for the coming year's event,
so we never forget partner coordination.

## Acceptance Criteria

1. Creating a new BATbern event automatically creates a partner meeting (correct date, 12:00–14:00 defaults, empty location, SPRING type)
2. Auto-creation is idempotent — if a `PartnerMeeting` with `eventCode` already exists, skip; creating the same event twice does not produce duplicates
3. Auto-creation failure (partner service down) does NOT fail or roll back event creation; only logged as a warning
4. `PartnerInviteEmailService` loads invite body from DB template with classpath fallback; hardcoded `buildEmailBody()` removed
5. `partner-meeting-invite-de/en` templates seeded and appear in Admin → Email Templates under category `PARTNER`
6. `YearEndReminderScheduler` fires on 31 December at 08:00 Zurich time; creates two tasks for next year
7. Year-end tasks have due dates of 31 January of next year and are assigned to all active organizers
8. Year-end task creation is idempotent — checks for existing tasks with same title + year before inserting
9. TDD: `PartnerMeetingAutoCreateListenerTest` (mocked client, idempotency, failure isolation); `YearEndReminderSchedulerTest` (task creation, dedup); `PartnerInviteEmailServiceTest` (template load, fallback)
10. No OpenAPI changes needed (no new public endpoints)
11. Checkstyle passes; Type-check passes (frontend unchanged)

## Tasks / Subtasks

### Phase 1: EMS — `PartnerCoordinationClient` (new HTTP client in event-management-service) (AC: 1, 3)

- [ ] Create interface: `services/event-management-service/src/main/java/ch/batbern/events/client/PartnerCoordinationClient.java`
  ```java
  public interface PartnerCoordinationClient {
      void createMeetingForEvent(String eventCode);
  }
  ```
- [ ] Create implementation: `.../client/impl/PartnerCoordinationClientImpl.java`
  - Inject `RestTemplate restTemplate`, `@Value("${services.partner-coordination.url}") String partnerServiceUrl`
  - Extract and propagate JWT: `SecurityContextHolder.getContext().getAuthentication()` → `JwtAuthenticationToken.getToken().getTokenValue()`
  - Call: `POST {partnerServiceUrl}/api/v1/partner-meetings/auto-create/{eventCode}`
  - Timeout: no dedicated timeout config needed (default from RestTemplate)
  - On any exception: catch all, `log.warn("Auto-create partner meeting failed for {}: {}", eventCode, e.getMessage())` — do NOT rethrow
  - Annotate with `@Cacheable` NOT needed (write operation)
  - Follow same JWT propagation pattern as `EventManagementClientImpl` in partner-coordination-service

### Phase 2: PCS — Auto-create endpoint (AC: 1, 2, 3)

- [ ] Add `existsByEventCode(String eventCode)` to `PartnerMeetingRepository.java`
  ```java
  boolean existsByEventCode(String eventCode);
  ```
- [ ] Add new method `autoCreateForEvent(String eventCode)` to `PartnerMeetingService.java`:
  ```java
  @Transactional
  public void autoCreateForEvent(String eventCode) {
      if (partnerMeetingRepository.existsByEventCode(eventCode)) {
          log.info("Partner meeting already exists for event {}, skipping auto-create", eventCode);
          return;
      }
      // Fetch event date from EventManagementClient (already injected)
      EventSummaryDTO eventSummary = eventManagementClient.getEventSummary(eventCode);
      PartnerMeeting meeting = new PartnerMeeting();
      meeting.setEventCode(eventCode);
      meeting.setMeetingDate(eventSummary.getEventDate());  // LocalDate from EMS
      meeting.setStartTime(LocalTime.parse(defaultStartTime));  // @Value("${partner.meeting.default-start-time:12:00}")
      meeting.setEndTime(LocalTime.parse(defaultEndTime));      // @Value("${partner.meeting.default-end-time:14:00}")
      meeting.setLocation("");
      meeting.setMeetingType(MeetingType.SPRING);
      meeting.setCreatedBy("system");
      partnerMeetingRepository.save(meeting);
      log.info("Auto-created partner meeting for event {}", eventCode);
  }
  ```
- [ ] Add new endpoint to `PartnerMeetingController.java`:
  ```java
  @PostMapping("/auto-create/{eventCode}")
  @PreAuthorize("hasRole('ORGANIZER')")  // EMS calls as organizer context
  public ResponseEntity<Void> autoCreateForEvent(@PathVariable String eventCode) {
      partnerMeetingService.autoCreateForEvent(eventCode);
      return ResponseEntity.ok().build();
  }
  ```
- [ ] Add `@Value` fields to `PartnerMeetingService`:
  ```java
  @Value("${partner.meeting.default-start-time:12:00}")
  private String defaultStartTime;
  @Value("${partner.meeting.default-end-time:14:00}")
  private String defaultEndTime;
  ```

### Phase 3: EMS — `PartnerMeetingAutoCreateListener` (Spring ApplicationEvent pattern) (AC: 1, 3)

The simplest pattern: use Spring's internal `ApplicationEvent` to decouple `EventService.createEvent()` from the HTTP call.

- [ ] Create `services/event-management-service/src/main/java/ch/batbern/events/event/EventCreatedApplicationEvent.java`
  - Extends `ApplicationEvent` (Spring, not DomainEvent — internal to EMS only)
  ```java
  public class EventCreatedApplicationEvent extends ApplicationEvent {
      private final String eventCode;
      public EventCreatedApplicationEvent(Object source, String eventCode) {
          super(source);
          this.eventCode = eventCode;
      }
      public String getEventCode() { return eventCode; }
  }
  ```
  **NOTE:** The shared-kernel already has `EventCreatedEvent extends DomainEvent<String>` for cross-service EventBridge events. This is a separate in-process Spring ApplicationEvent. Name distinctly to avoid confusion.

- [ ] Update `EventService.createEvent()` to publish the event AFTER successful save:
  ```java
  // After: event = eventRepository.save(event);
  applicationEventPublisher.publishEvent(new EventCreatedApplicationEvent(this, savedEvent.getEventCode()));
  ```
  - Inject `ApplicationEventPublisher applicationEventPublisher` via constructor (Lombok `@RequiredArgsConstructor`)

- [ ] Create `services/event-management-service/src/main/java/ch/batbern/events/listener/PartnerMeetingAutoCreateListener.java`:
  ```java
  @Component
  @Slf4j
  @RequiredArgsConstructor
  public class PartnerMeetingAutoCreateListener {
      private final PartnerCoordinationClient partnerCoordinationClient;

      @EventListener
      public void onEventCreated(EventCreatedApplicationEvent event) {
          try {
              partnerCoordinationClient.createMeetingForEvent(event.getEventCode());
              log.info("Auto-create partner meeting triggered for event {}", event.getEventCode());
          } catch (Exception e) {
              // Defensive: PartnerCoordinationClientImpl already swallows, but belt-and-suspenders
              log.warn("Partner meeting auto-create listener caught unexpected error for event {}: {}",
                      event.getEventCode(), e.getMessage());
          }
      }
  }
  ```
  - **IMPORTANT:** `@EventListener` runs synchronously in the same thread by default. If the transaction in `EventService.createEvent()` has not committed yet when the listener fires, the HTTP call to PCS will happen before the EMS event is visible in DB. For this use case this is fine — PCS only calls `getEventSummary()` which reads EMS, and the event IS saved before the publisher fires (after `repository.save()`). If this causes issues, annotate with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` instead.

### Phase 4: PCS — Email Template Infrastructure (AC: 4, 5)

This mirrors the EMS email template pattern exactly.

- [ ] **Migration `V8__create_email_templates_table.sql`** in `services/partner-coordination-service/src/main/resources/db/migration/`:
  ```sql
  CREATE TABLE email_templates (
      id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
      template_key       VARCHAR(100) NOT NULL,
      category           VARCHAR(50)  NOT NULL,
      locale             VARCHAR(5)   NOT NULL,
      subject            VARCHAR(500),
      html_body          TEXT         NOT NULL,
      variables          JSONB,
      is_layout          BOOLEAN      NOT NULL DEFAULT FALSE,
      layout_key         VARCHAR(100),
      is_system_template BOOLEAN      NOT NULL DEFAULT TRUE,
      created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
      updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
      CONSTRAINT email_templates_key_locale_unique UNIQUE (template_key, locale)
  );
  ```

- [ ] **Create `EmailTemplate.java` entity** in `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/EmailTemplate.java`
  - Port exactly from `services/event-management-service/src/main/java/ch/batbern/events/domain/EmailTemplate.java`
  - Change package to `ch.batbern.partners.domain`
  - All fields/annotations identical

- [ ] **Create `EmailTemplateRepository.java`** in `services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/EmailTemplateRepository.java`:
  ```java
  public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
      Optional<EmailTemplate> findByTemplateKeyAndLocale(String templateKey, String locale);
      boolean existsByTemplateKeyAndLocale(String templateKey, String locale);
      List<EmailTemplate> findByCategory(String category);
  }
  ```

- [ ] **Create template HTML files:**
  - `services/partner-coordination-service/src/main/resources/email-templates/partner-meeting-invite-de.html`
    ```html
    <!-- subject: Einladung: BATbern Partner-Meeting {{meetingYear}} -->
    <!DOCTYPE html>
    <html>
    <body style="font-family: sans-serif; color: #333; line-height: 1.5;">
    <p>Sehr geehrte Damen und Herren,</p>
    <p>wir laden Sie herzlich zu unserem BATbern Partner-Meeting ein.</p>
    <table style="border-collapse: collapse; margin: 16px 0;">
      <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Datum:</td><td>{{meetingDate}}</td></tr>
      <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Zeit:</td><td>{{meetingStartTime}} – {{meetingEndTime}} Uhr</td></tr>
      {{#location}}<tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Ort:</td><td>{{location}}</td></tr>{{/location}}
      <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Anlass:</td><td>{{eventTitle}}</td></tr>
    </table>
    <p>Im Anschluss findet das BATbern Event statt.</p>
    <p>Bitte importieren Sie den beigefügten Kalendertermin (.ics) in Ihren Kalender.</p>
    <p>Mit freundlichen Grüssen,<br>{{organizerName}}<br>BATbern Organisationsteam</p>
    </body>
    </html>
    ```
  - `services/partner-coordination-service/src/main/resources/email-templates/partner-meeting-invite-en.html`
    ```html
    <!-- subject: Invitation: BATbern Partner Meeting {{meetingYear}} -->
    <!DOCTYPE html>
    <html>
    <body style="font-family: sans-serif; color: #333; line-height: 1.5;">
    <p>Dear Sir or Madam,</p>
    <p>We cordially invite you to our BATbern Partner Meeting.</p>
    <table style="border-collapse: collapse; margin: 16px 0;">
      <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Date:</td><td>{{meetingDate}}</td></tr>
      <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Time:</td><td>{{meetingStartTime}} – {{meetingEndTime}}</td></tr>
      {{#location}}<tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Location:</td><td>{{location}}</td></tr>{{/location}}
      <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Event:</td><td>{{eventTitle}}</td></tr>
    </table>
    <p>The BATbern event follows immediately after.</p>
    <p>Please import the attached calendar entry (.ics) into your calendar.</p>
    <p>Kind regards,<br>{{organizerName}}<br>BATbern Organisation Team</p>
    </body>
    </html>
    ```

- [ ] **Create `EmailTemplateSeedService.java`** in `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/EmailTemplateSeedService.java`:
  - Port from `services/event-management-service/src/main/java/ch/batbern/events/service/EmailTemplateSeedService.java`
  - Change package to `ch.batbern.partners.service`
  - Change `EmailTemplateRepository` import to the PCS one
  - Adapt `deriveCategory()`: `partner-*` → `"PARTNER"` (plus keep layout handling)
  - Keep `@PostConstruct seedTemplatesFromClasspath()` — scans `classpath*:email-templates/*.html`
  - Filename pattern: `^(?:(layout)-)?(.+)-(de|en)\.html$`
  - Subject extraction: first-line comment `<!-- subject: ... -->`
  - Idempotent upsert: `existsByTemplateKeyAndLocale()` check before insert

- [ ] **Update `PartnerInviteEmailService.java`** — replace `buildEmailBody()` with DB template load:
  - Inject `EmailTemplateRepository emailTemplateRepository` (add to constructor)
  - Replace the hardcoded `buildEmailBody()` call:
    ```java
    private String loadEmailBody(PartnerMeeting meeting, EventSummaryDTO eventSummary, String language) {
        String html = emailTemplateRepository
            .findByTemplateKeyAndLocale("partner-meeting-invite", language)
            .map(EmailTemplate::getHtmlBody)
            .orElseGet(() -> {
                log.warn("partner-meeting-invite template not in DB for locale '{}', loading from classpath", language);
                ClassPathResource resource = new ClassPathResource(
                    "email-templates/partner-meeting-invite-" + language + ".html");
                try {
                    return resource.getContentAsString(StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Email template missing for language: " + language, e);
                }
            });
        Map<String, String> vars = Map.of(
            "meetingDate", meeting.getMeetingDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            "meetingStartTime", meeting.getStartTime().toString(),
            "meetingEndTime", meeting.getEndTime().toString(),
            "location", meeting.getLocation() != null ? meeting.getLocation() : "",
            "eventTitle", eventSummary.getTitle(),
            "organizerName", "BATbern Team",
            "meetingYear", String.valueOf(meeting.getMeetingDate().getYear())
        );
        return emailService.replaceVariables(html, vars);
    }
    ```
  - `language`: use `"de"` by default (partner users' language from `userPreferences` if available from UserServiceClient, otherwise `"de"`)
  - **Remove the entire `buildEmailBody()` method** after refactoring

- [ ] **Derive subject from DB template** (templates store subject in the `subject` column via `EmailTemplateSeedService.parseSubject()`):
  ```java
  String subject = emailTemplateRepository
      .findByTemplateKeyAndLocale("partner-meeting-invite", language)
      .map(EmailTemplate::getSubject)
      .orElse("Einladung: BATbern Partner-Meeting + " + eventSummary.getTitle());
  ```

### Phase 5: EMS — Migration + `UserApiClient` + `YearEndReminderScheduler` (AC: 6, 7, 8)

ShedLock is already configured in EMS via `V31__Add_shedlock_table.sql` + `shedlock-spring:5.10.0`. The existing scheduler pattern is in `TaskDeadlineReminderScheduler` (Story 10.3) — follow it exactly.

**⚠️ CRITICAL SCHEMA FACT:** `EventTask.event_id` is currently `NOT NULL REFERENCES events(id)`. Global year-end tasks cannot be created without a real event. A migration is required.

**⚠️ CRITICAL FIELD NAMES (from actual `EventTask.java`):**
- Field: `taskName` (column: `task_name`) — NOT `title`
- Field: `triggerState` (column: `trigger_state`) — NOT NULL, must supply a value
- Field: `assignedOrganizerUsername` — NOT `assignedTo`
- Field: `status` — String, values: `'todo'`, `'in_progress'`, `'completed'` — NOT an enum
- Field: `eventId` (UUID) — currently NOT NULL, must be made nullable for global tasks

- [ ] **EMS Migration `V78__make_event_task_global.sql`**:
  ```sql
  -- Allow global tasks (not tied to a specific event) — e.g., year-end reminders
  ALTER TABLE event_tasks ALTER COLUMN event_id DROP NOT NULL;
  ALTER TABLE event_tasks ALTER COLUMN trigger_state DROP NOT NULL;
  -- Drop cascade delete constraint (can't cascade from a nullable FK)
  ALTER TABLE event_tasks DROP CONSTRAINT IF EXISTS event_tasks_event_id_fkey;
  ALTER TABLE event_tasks ADD CONSTRAINT event_tasks_event_id_fkey
      FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;
  -- Re-add as nullable FK (keep CASCADE for event-bound tasks, NULLable for global)
  ```
  **Simpler approach** (recommended): just drop NOT NULL and drop the FK constraint entirely:
  ```sql
  ALTER TABLE event_tasks ALTER COLUMN event_id DROP NOT NULL;
  ALTER TABLE event_tasks DROP CONSTRAINT IF EXISTS event_tasks_event_id_fkey;
  ALTER TABLE event_tasks ALTER COLUMN trigger_state DROP NOT NULL;
  ```
  Then also update `EventTask.java` to allow nullable `eventId`:
  ```java
  @Column(name = "event_id", columnDefinition = "UUID")  // remove nullable = false
  private UUID eventId;
  ```

- [ ] **Add `UserApiClient` interface in EMS** (calls CUMS to fetch organizer usernames):
  ```
  services/event-management-service/.../client/UserApiClient.java
  services/event-management-service/.../client/impl/UserApiClientImpl.java
  ```
  Interface:
  ```java
  public interface UserApiClient {
      List<String> getOrganizerUsernames();
  }
  ```
  Implementation (follow `EventManagementClientImpl` in PCS as the JWT propagation pattern model):
  ```java
  @Service
  @Slf4j
  @RequiredArgsConstructor
  public class UserApiClientImpl implements UserApiClient {
      private final RestTemplate restTemplate;
      @Value("${services.company-user-management.url:http://localhost:8001}") String cumsUrl;

      @Override
      public List<String> getOrganizerUsernames() {
          try {
              // GET /api/v1/users?role=ORGANIZER&limit=100
              // Returns PaginatedUserResponse — check existing CUMS UserResponse shape
              // Extract .getData().stream().map(u -> u.getUsername()).toList()
              ResponseEntity<PaginatedUserResponse> response = restTemplate.exchange(
                  cumsUrl + "/api/v1/users?role=ORGANIZER&limit=100",
                  HttpMethod.GET, buildRequest(), PaginatedUserResponse.class);
              return response.getBody().getData().stream()
                  .map(UserResponse::getUsername).toList();
          } catch (Exception e) {
              log.warn("Failed to fetch organizer usernames from CUMS: {}", e.getMessage());
              return List.of();
          }
      }
      // buildRequest() — add Bearer JWT from SecurityContext if available
  }
  ```
  **NOTE:** For scheduler context, the SecurityContext is empty (no HTTP request). The year-end scheduler runs as a system/batch job. Either:
  a) Skip JWT (if CUMS endpoint for listing users by role is accessible without a user JWT — check if it requires organizer role or allows service-to-service)
  b) Use a system service account token
  c) **Simplest alternative:** Store known organizer usernames in `application.yml` as a comma-separated config property `batbern.year-end-reminder.organizers: nissim,other-organizer` — avoids the HTTP call entirely

  **Recommended: Use config-based organizer list** (simplest, most reliable for a yearly cron):
  ```yaml
  batbern:
    year-end-reminder:
      organizer-usernames: ${YEAR_END_ORGANIZER_USERNAMES:nissim}
  ```
  ```java
  @Value("${batbern.year-end-reminder.organizer-usernames:}") String organizerUsernamesConfig;
  ```

- [ ] Create `services/event-management-service/src/main/java/ch/batbern/events/scheduler/YearEndReminderScheduler.java`:
  ```java
  @Component
  @Slf4j
  @RequiredArgsConstructor
  public class YearEndReminderScheduler {

      private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");
      private static final String TRIGGER_STATE = "year_end_reminder";

      private final EventTaskRepository eventTaskRepository;

      @Value("${batbern.year-end-reminder.organizer-usernames:}")
      private String organizerUsernamesConfig;

      @Scheduled(cron = "${batbern.year-end-reminder.cron:0 0 8 31 12 *}")
      @SchedulerLock(
              name = "YearEndReminderScheduler_createTasks",
              lockAtLeastFor = "PT1M",
              lockAtMostFor = "PT10M"
      )
      @Transactional
      public void createYearEndReminderTasks() {
          int nextYear = LocalDate.now(SWISS_ZONE).getYear() + 1;
          LocalDate dueDate = LocalDate.of(nextYear, 1, 31);
          log.info("YearEndReminderScheduler: creating tasks for year {}", nextYear);

          List<String> organizers = parseOrganizerUsernames();
          if (organizers.isEmpty()) {
              log.warn("No organizer usernames configured. Set batbern.year-end-reminder.organizer-usernames");
              return;
          }

          String[] taskNames = {
              "Send partner meeting iCal invitations for " + nextYear,
              "Create BATbern event calendar entry for " + nextYear
          };
          for (String taskName : taskNames) {
              createTaskIfNotExists(taskName, dueDate, organizers);
          }
          log.info("YearEndReminderScheduler: completed for year {}", nextYear);
      }

      private void createTaskIfNotExists(String taskName, LocalDate dueDate, List<String> organizers) {
          // Idempotency: check if task with this exact name already exists (name includes the year)
          if (eventTaskRepository.existsByTaskName(taskName)) {
              log.info("Year-end task '{}' already exists, skipping", taskName);
              return;
          }
          Instant dueDateInstant = dueDate.atStartOfDay(SWISS_ZONE).toInstant();
          for (String username : organizers) {
              EventTask task = new EventTask();
              task.setEventId(null);           // global task — NOT tied to specific event (V78 migration required)
              task.setTaskName(taskName);
              task.setTriggerState(TRIGGER_STATE);  // "year_end_reminder" — nullable after V78 migration
              task.setAssignedOrganizerUsername(username);
              task.setDueDate(dueDateInstant);
              task.setStatus("todo");
              task.setTemplateId(null);
              eventTaskRepository.save(task);
          }
          log.info("Created year-end task '{}' for {} organizers", taskName, organizers.size());
      }

      private List<String> parseOrganizerUsernames() {
          if (organizerUsernamesConfig == null || organizerUsernamesConfig.isBlank()) {
              return List.of();
          }
          return Arrays.stream(organizerUsernamesConfig.split(","))
              .map(String::trim).filter(s -> !s.isEmpty()).toList();
      }
  }
  ```

- [ ] Add `existsByTaskName(String taskName)` to `EventTaskRepository.java`:
  ```java
  boolean existsByTaskName(String taskName);
  ```

### Phase 6: Tests (AC: 9)

- [ ] **`PartnerMeetingAutoCreateListenerTest.java`** in `services/event-management-service/src/test/java/.../listener/`:
  - Extends `AbstractIntegrationTest`
  - Mock `PartnerCoordinationClient` with `@MockitoBean` / `@MockBean`
  - Test: `should_callPartnerCoordinationClient_when_eventCreatedEventFired`
    - Publish `EventCreatedApplicationEvent` manually → verify `client.createMeetingForEvent()` called
  - Test: `should_notPropagateException_when_partnerServiceFails`
    - Mock client to throw → verify no exception escapes the listener
  - Test: `should_createEvent_successfully_even_when_partnerServiceThrows`
    - Full `EventService.createEvent()` call → even if client throws, event saved in DB

- [ ] **`YearEndReminderSchedulerTest.java`** in `services/event-management-service/src/test/java/.../scheduler/`:
  - Extends `AbstractIntegrationTest` (uses Testcontainers PostgreSQL — required since V78 migration must run)
  - **IMPORTANT:** V78 migration must run for tests to pass (it makes `event_id` nullable)
  - Set property in test: `@TestPropertySource(properties = "batbern.year-end-reminder.organizer-usernames=testuser1,testuser2")`
  - Test: `should_createTwoTasksForNextYear_when_organizersConfigured`
    - Call `scheduler.createYearEndReminderTasks()` directly
    - Assert 4 `EventTask` records in DB (2 tasks × 2 organizers)
    - Assert `taskName` contains next year, `assignedOrganizerUsername` is set, `dueDate` = 31 Jan next year, `status = 'todo'`
    - Assert `eventId = null`, `triggerState = 'year_end_reminder'`
  - Test: `should_notCreateDuplicates_when_calledTwice` (idempotency)
    - Call `createYearEndReminderTasks()` twice → assert count still 4 (not 8)
  - Test: `should_skipCreation_when_noOrganizersConfigured`
    - Override property to blank → 0 tasks created, no exception
  - **Note:** Test business method directly (no need for `@SpyBean` / cron trigger testing)

- [ ] **`PartnerInviteEmailServiceTest.java`** in `services/partner-coordination-service/src/test/java/.../service/`:
  - Extends `AbstractIntegrationTest`
  - Mock `EmailService`, `EventManagementClient`, `UserServiceClient`
  - Test: `should_loadTemplateFromDB_when_templateExists`
    - Seed a `partner-meeting-invite` template in DB → verify `emailService.sendHtmlEmailWithAttachments()` called with DB-loaded body
  - Test: `should_fallbackToClasspath_when_templateNotInDB`
    - Empty template table → verify classpath fallback used, email still sent
  - Test: `should_notCallBuildEmailBody_after_refactor` — just ensure the old method is gone

### Phase 7: Validation

- [ ] Run EMS migration check: `./gradlew :services:event-management-service:flywayMigrate 2>&1 | tee /tmp/flyway-10-25-ems.log && grep -E "SUCCESS|ERROR" /tmp/flyway-10-25-ems.log`
- [ ] Run PCS migration check: `./gradlew :services:partner-coordination-service:flywayMigrate 2>&1 | tee /tmp/flyway-10-25-pcs.log && grep -E "SUCCESS|ERROR" /tmp/flyway-10-25-pcs.log`
- [ ] Run EMS tests: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-25-ems.log && grep -E "PASS|FAIL|BUILD" /tmp/test-10-25-ems.log`
- [ ] Run PCS tests: `./gradlew :services:partner-coordination-service:test 2>&1 | tee /tmp/test-10-25-pcs.log && grep -E "PASS|FAIL|BUILD" /tmp/test-10-25-pcs.log`
- [ ] Checkstyle EMS: `./gradlew :services:event-management-service:checkstyleMain 2>&1 | tee /tmp/check-10-25-ems.log && grep -E "error|warning|BUILD" /tmp/check-10-25-ems.log`
- [ ] Checkstyle PCS: `./gradlew :services:partner-coordination-service:checkstyleMain 2>&1 | tee /tmp/check-10-25-pcs.log && grep -E "error|warning|BUILD" /tmp/check-10-25-pcs.log`
- [ ] Verify `PartnerMeetingsPage` shows auto-created meeting after creating a new event (manual smoke test or Playwright)

## Dev Notes

### Which Service Gets What

| Component | Service | Reason |
|---|---|---|
| `PartnerCoordinationClient` (HTTP call to PCS) | EMS | Reverse direction of existing `EventManagementClient` in PCS |
| `PartnerMeetingAutoCreateListener` (`@EventListener`) | EMS | Reacts to EMS `EventCreatedApplicationEvent` |
| `EventCreatedApplicationEvent` | EMS | Internal Spring ApplicationEvent — different from shared-kernel `EventCreatedEvent` (cross-service EventBridge) |
| `YearEndReminderScheduler` | EMS | ShedLock already in EMS (V31); Tasks table is in EMS |
| `EmailTemplateSeedService` | PCS | Seeds templates into PCS's own `email_templates` table |
| `email_templates` table (V8 migration) | PCS | PCS's own template storage; same schema as EMS's |
| `EmailTemplate` entity + `EmailTemplateRepository` | PCS | Mirrors EMS pattern, separate PCS table |
| Template HTML files | PCS resources | `classpath:email-templates/partner-meeting-invite-{de,en}.html` |

### Auto-Create: Spring ApplicationEvent vs Cross-Service EventBridge

The story recommends the **simplest pattern**: in-process Spring `ApplicationEvent`. This avoids:
- SQS/EventBridge latency
- Cross-service event schema coordination
- Failure in event dispatch breaking event creation

The `EventCreatedEvent` in `shared-kernel` is for EventBridge (external messaging). **Do NOT use it here** — use a separate `EventCreatedApplicationEvent extends ApplicationEvent` (Spring framework class, synchronous in-process).

**`@TransactionalEventListener` consideration:** The default `@EventListener` fires within the transaction. If this causes issues (e.g., the partner service queries EMS and the event is not yet committed), switch to:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```
This fires after the EMS transaction commits, ensuring PCS can read the event via its own `EventManagementClient.getEventSummary()`.

### PartnerCoordinationClient URL Configuration

Add to `services/event-management-service/src/main/resources/application.yml` (or `application-dev.yml`):
```yaml
services:
  partner-coordination:
    url: ${PARTNER_COORDINATION_SERVICE_URL:http://localhost:8004}
```

Check existing `services.*` config pattern — other EMS HTTP clients (UserServiceClient via `COMPANY_USER_MANAGEMENT_SERVICE_URL`) use the same pattern. Follow it exactly.

### JWT Propagation for EMS → PCS Call

The existing `EventManagementClientImpl` in PCS extracts JWT as:
```java
// Source: services/partner-coordination-service/.../client/impl/EventManagementClientImpl.java
JwtAuthenticationToken authentication = (JwtAuthenticationToken) SecurityContextHolder
    .getContext().getAuthentication();
String token = authentication.getToken().getTokenValue();
restTemplate.setInterceptors(List.of(
    (request, body, execution) -> {
        request.getHeaders().setBearerAuth(token);
        return execution.execute(request, body);
    }
));
```
Apply the identical pattern in `PartnerCoordinationClientImpl`. The organizer's JWT from the original request is in the SecurityContext when `EventService.createEvent()` runs.

**`@TransactionalEventListener` JWT caveat:** If switching to `AFTER_COMMIT`, the SecurityContext may be cleared. In that case, capture the JWT token before publishing the event and pass it in `EventCreatedApplicationEvent`.

### EmailTemplateSeedService — Category Derivation for PCS

In PCS's `EmailTemplateSeedService.deriveCategory()`, the logic is simpler than EMS's:
```java
private String deriveCategory(String templateKey, boolean isLayout) {
    if (isLayout) return "LAYOUT";
    if (templateKey.startsWith("partner-")) return "PARTNER";
    return "GENERAL";
}
```

### Email Template Variable Substitution

`EmailService.replaceVariables()` (shared-kernel) supports:
- `{{variable}}` — simple substitution
- `{{#variable}}...{{/variable}}` — conditional block (renders if variable is non-empty)

Use `{{#location}}...{{/location}}` in the template to conditionally show the location row only when `location` is not empty.

### Year-End Reminder Tasks — Task Domain

The `Task` entity in EMS has:
- `title` (String) — the task description
- `assignedTo` (String) — organizer username (ADR-003)
- `dueDate` (Instant) — deadline
- `eventCode` (String, nullable) — null for global tasks
- `status` (TaskStatus enum: OPEN, IN_PROGRESS, DONE)

Check the exact `Task` entity and `TaskRepository` in `services/event-management-service/src/main/java/ch/batbern/events/domain/Task.java` before writing the scheduler. Verify field names match exactly.

### YearEndReminderScheduler — Cron Testing

For unit tests, call `scheduler.createYearEndReminderTasks()` directly (don't rely on the cron trigger). To verify the cron expression is correct, use:
```java
CronExpression cronExpression = CronExpression.parse("0 0 8 31 12 *");
assertThat(cronExpression.next(LocalDateTime.of(2026, 12, 30, 0, 0)))
    .isEqualTo(LocalDateTime.of(2026, 12, 31, 8, 0));
```

### PCS Migration Version — V8

Next PCS migration after V7 is **V8**:
```
services/partner-coordination-service/src/main/resources/db/migration/V8__create_email_templates_table.sql
```

Existing PCS migrations:
- V1: Initial baseline
- V2: Partner coordination schema (partner_meetings, topic_votes)
- V3: Add partnership cost
- V4: Topic tables update (story 8.2)
- V5: Partner meetings update (story 8.3)
- V6: Drop partner_contacts table
- V7: Create partner_notes table

### EMS — V78 Migration IS Required

The `event_tasks` table (created in V22) has `event_id UUID NOT NULL REFERENCES events(id)` and `trigger_state VARCHAR(50) NOT NULL`. Year-end tasks are GLOBAL (not tied to a specific event), so these constraints must be relaxed. **V78 migration is mandatory before the scheduler can work.**

**EventTask Correct Field Names (from actual `EventTask.java`):**
```
Entity field          → DB column                 → Setter method
taskName              → task_name                 → setTaskName(String)
triggerState          → trigger_state             → setTriggerState(String)  [set to "year_end_reminder"]
assignedOrganizerUsername → assigned_organizer_username → setAssignedOrganizerUsername(String)
eventId               → event_id                 → setEventId(UUID)         [null for global tasks]
status                → status (String, NOT enum) → setStatus("todo")
dueDate               → due_date                 → setDueDate(Instant)
templateId            → template_id              → setTemplateId(null)
```

**DO NOT invent a `Task` or `GlobalTask` class** — the existing `EventTask` entity is the only task domain class in EMS. After V78 migration, `eventId` will be nullable and `triggerState` will be nullable.

### Previous Story Intelligence (10.24)

Story 10.24 established:
- CUMS gets its own `email_templates` table (mirrors EMS pattern) — same pattern used here for PCS
- `EmailTemplateSeedService` port from EMS to CUMS — same port for PCS (third service)
- `EmailService.replaceVariables()` + `sendHtmlEmail()` pattern in shared-kernel
- `@MockitoBean` (Spring Boot 3.4+) pattern for mocking infrastructure beans in tests
- Template HTML subject comment format: `<!-- subject: ... -->`
- Classpath fallback pattern when DB template absent

### Recent Commits Context

```
3d8c7631 docs(stories): add context story files for epic 10 stories 19-23
3f054987 fix(10.16): improve AI prompt quality, fix DALL-E download, add inline description button
8809524c fix(10.16): replace Mockito star import with explicit imports
619ba1ad fix(10.16): correct double /api/v1 prefix and gateway routing for AI endpoints
99e34fac feat(10.16): AI-assisted event content creation (RestClient pattern, AiAssistController)
```

Story 10.16 established the `RestClient` / `RestTemplate` HTTP client pattern with `@Value`-injected service URLs. Follow it when building `PartnerCoordinationClientImpl`.

### Project Structure Notes

**New files:**
```
services/event-management-service/.../event/EventCreatedApplicationEvent.java
services/event-management-service/.../listener/PartnerMeetingAutoCreateListener.java
services/event-management-service/.../client/PartnerCoordinationClient.java
services/event-management-service/.../client/impl/PartnerCoordinationClientImpl.java
services/event-management-service/.../scheduler/YearEndReminderScheduler.java
services/event-management-service/src/main/resources/db/migration/V78__make_event_task_global.sql
services/event-management-service/src/test/.../listener/PartnerMeetingAutoCreateListenerTest.java
services/event-management-service/src/test/.../scheduler/YearEndReminderSchedulerTest.java
services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/EmailTemplate.java
services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/EmailTemplateRepository.java
services/partner-coordination-service/src/main/java/ch/batbern/partners/service/EmailTemplateSeedService.java
services/partner-coordination-service/src/main/resources/email-templates/partner-meeting-invite-de.html
services/partner-coordination-service/src/main/resources/email-templates/partner-meeting-invite-en.html
services/partner-coordination-service/src/main/resources/db/migration/V8__create_email_templates_table.sql
services/partner-coordination-service/src/test/.../service/PartnerInviteEmailServiceTest.java
```

**Modified files:**
```
services/event-management-service/.../service/EventService.java
  — inject ApplicationEventPublisher; publish EventCreatedApplicationEvent after save
services/event-management-service/.../domain/EventTask.java
  — make eventId nullable (remove nullable=false); make triggerState nullable
services/event-management-service/.../repository/EventTaskRepository.java
  — add existsByTaskName(String taskName) for idempotency check
services/event-management-service/src/main/resources/application.yml
  — add services.partner-coordination.url config + batbern.year-end-reminder.organizer-usernames
services/partner-coordination-service/.../service/PartnerMeetingService.java
  — add autoCreateForEvent(), inject @Value default times, inject EmailTemplateRepository
services/partner-coordination-service/.../controller/PartnerMeetingController.java
  — add POST /auto-create/{eventCode} endpoint
services/partner-coordination-service/.../repository/PartnerMeetingRepository.java
  — add existsByEventCode(String eventCode)
services/partner-coordination-service/.../service/PartnerInviteEmailService.java
  — replace buildEmailBody() with DB template load + variable substitution; inject EmailTemplateRepository
```

### References

- Epic 10 story definition: [Source: docs/prd/epic-10-additional-stories.md#story-1025 (lines 1809-1924)]
- **EventTask entity (CRITICAL — correct field names):** [Source: services/event-management-service/src/main/java/ch/batbern/events/domain/EventTask.java]
- **EventTaskRepository (add existsByTaskName):** [Source: services/event-management-service/src/main/java/ch/batbern/events/repository/EventTaskRepository.java]
- **TaskDeadlineReminderScheduler (ShedLock + @Scheduled pattern to follow):** [Source: services/event-management-service/src/main/java/ch/batbern/events/scheduler/TaskDeadlineReminderScheduler.java]
- V22__Add_task_system.sql (current event_tasks schema — event_id NOT NULL): [Source: services/event-management-service/src/main/resources/db/migration/V22__Add_task_system.sql]
- PartnerMeetingService.java: [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerMeetingService.java]
- PartnerInviteEmailService.java (current buildEmailBody): [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerInviteEmailService.java]
- IcsGeneratorService.java (RFC 5545, already implemented): [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/service/IcsGeneratorService.java]
- PartnerMeetingRepository.java: [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/PartnerMeetingRepository.java]
- EventManagementClientImpl.java (JWT propagation pattern to replicate): [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/client/impl/EventManagementClientImpl.java]
- EmailTemplateSeedService.java (EMS pattern to port): [Source: services/event-management-service/src/main/java/ch/batbern/events/service/EmailTemplateSeedService.java]
- EmailTemplate.java (EMS entity to port): [Source: services/event-management-service/src/main/java/ch/batbern/events/domain/EmailTemplate.java]
- V62__create_email_templates_table.sql (schema to copy): [Source: services/event-management-service/src/main/resources/db/migration/V62__create_email_templates_table.sql]
- EventWorkflowScheduledService.java (ShedLock + @Scheduled pattern): [Source: services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowScheduledService.java]
- EventWorkflowTransitionListener.java (@EventListener pattern): [Source: services/event-management-service/src/main/java/ch/batbern/events/listener/EventWorkflowTransitionListener.java]
- EmailService.java (replaceVariables, sendHtmlEmailWithAttachments): [Source: shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java]
- DomainEvent.java (NOT to use for in-process events): [Source: shared-kernel/src/main/java/ch/batbern/shared/events/DomainEvent.java]
- Story 10.24 (EmailTemplateSeedService port pattern established): [Source: _bmad-output/implementation-artifacts/10-24-aws-cognito-user-provisioning-from-user-management.md]
- Coding standards (TDD, Testcontainers): [Source: docs/architecture/coding-standards.md]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
