# Plan: Newsletter Robustness & Progress Tracking for 3000+ Subscribers

## Context

Story 10.7 implemented the newsletter subscription and sending system. With 3000+ planned subscribers, a code audit reveals **one critical bug** that would cause the majority of emails to never be sent, plus several resilience gaps around partial failures, duplicate sends, and visibility.

---

## Critical Bug: Thread Pool Overflow (P0)

**Root cause:** `NewsletterEmailService.sendNewsletter()` loops over all subscribers and calls `emailService.sendHtmlEmail()` (which is `@Async`) for each one. The `taskExecutor` (AsyncConfig) is configured with:
- Core: 5 threads, Max: 10, Queue capacity: 100

With 3000 subscribers, only the first ~110 submissions fit in the pool. After that, `TaskRejectedException` is thrown per subscriber (caught by the try-catch as "failed"). **~2890 subscribers never receive the email.**

All issues found:

| # | Issue | Severity |
|---|-------|----------|
| 1 | Thread pool overflow: only ~110/3000 emails actually sent | P0 CRITICAL |
| 2 | No duplicate-send protection: double-clicking "Send" emails everyone twice | P1 HIGH |
| 3 | No resume after interruption (ECS Fargate Spot interruptions are mentioned as common) | P1 HIGH |
| 4 | HTTP request blocks for entire loop duration; risk of timeout | P1 HIGH |
| 5 | SES rate limit not respected (default: 14/s) | P2 MEDIUM |
| 6 | No progress visibility for organizer | P2 MEDIUM |

---

## Recommended Approach: Async Background Job + Progress Polling

### Architectural Change

**Before:**
```
HTTP POST /send → loop 3000 async dispatches → response (broken: only ~110 sent)
```

**After:**
```
HTTP POST /send → create send record (PENDING) → launch @Async background job → return {sendId, status=PENDING}
Background thread → paginate 50/batch → sendHtmlEmailSync() → update sentCount every batch
Frontend → polls GET /sends/{sendId}/status every 3s → shows progress bar → handles COMPLETED/PARTIAL/FAILED
```

---

## DB Changes (New Migration V74)

File: `services/event-management-service/src/main/resources/db/migration/V74__add_newsletter_send_status.sql`

```sql
ALTER TABLE newsletter_sends
  ADD COLUMN status         VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
  ADD COLUMN sent_count     INTEGER      NOT NULL DEFAULT 0,
  ADD COLUMN failed_count   INTEGER      NOT NULL DEFAULT 0,
  ADD COLUMN started_at     TIMESTAMPTZ,
  ADD COLUMN completed_at   TIMESTAMPTZ;

-- Backfill: treat all existing rows as completed
UPDATE newsletter_sends SET
  status       = 'COMPLETED',
  started_at   = sent_at,
  completed_at = sent_at,
  sent_count   = COALESCE(recipient_count, 0);
```

---

## Backend Changes

### 1. `shared-kernel` — Add sync email method
**File:** `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java`

Add `sendHtmlEmailSync(String to, String subject, String htmlBody)` — identical implementation to `sendHtmlEmail()` but **without `@Async`**. Called from within an already-background thread to avoid inner thread pool dispatch.

### 2. `NewsletterSend.java` — Add status fields
**File:** `services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterSend.java`

Add fields: `status` (String, default `"PENDING"`), `sentCount` (int), `failedCount` (int), `startedAt` (Instant), `completedAt` (Instant).

### 3. `NewsletterSendRepository.java` — Add queries
**File:** `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSendRepository.java`

Add:
- `Optional<NewsletterSend> findFirstByEventIdAndStatus(UUID eventId, String status)` — for duplicate-send prevention
- `Optional<NewsletterSend> findByIdAndEventId(UUID id, UUID eventId)` — for status endpoint

### 4. `NewsletterSubscriberRepository.java` — Add paginated query
**File:** `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSubscriberRepository.java`

Add: `Page<NewsletterSubscriber> findByUnsubscribedAtIsNull(Pageable pageable)` — to process subscribers in pages of 50 instead of loading all 3000 into memory at once.

### 5. `NewsletterEmailService.java` — Core redesign
**File:** `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterEmailService.java`

**`sendNewsletter()` — change to fire-and-forget:**
```
1. Check for IN_PROGRESS send on this event → throw 409 Conflict if found
2. Create send record with status='PENDING' (committed in own @Transactional)
3. Call executeNewsletterSendAsync(sendId, event, isReminder, locale, sentByUsername, effectiveKey)
4. Return immediately with {sendId, status=PENDING, recipientCount=activeCount}
```

**New `executeNewsletterSendAsync(...)` — `@Async` background method:**
```
1. Update send: status=IN_PROGRESS, startedAt=now  (own @Transactional)
2. page=0, size=50; sentCount=0; failedCount=0
3. Loop: fetch page of active subscribers
4.   For each subscriber in page:
       - Build per-recipient vars (unsubscribeLink)
       - Render HTML
       - Call emailService.sendHtmlEmailSync(to, subject, html)
       - recordRecipient(sendId, email, "sent" or "failed")
       - increment sentCount or failedCount
       - sleep 70ms (≈14 emails/sec, respects SES default rate limit)
5.   After each page: updateSendProgress(sendId, sentCount, failedCount)  (own @Transactional)
6.   If page had results → next page; else break
7. Final status:
     - failedCount == 0 → COMPLETED
     - failedCount > 0 && sentCount > 0 → PARTIAL
     - sentCount == 0 → FAILED
8. Update send: status, completedAt=now  (own @Transactional)
9. Log result
```

**Exception handler wrapping step 3-8:** On uncaught exception → mark send as FAILED, log error.

**New `@Transactional updateSendProgress(sendId, sentCount, failedCount)`** — updates counts in DB mid-send for progress polling.

### 6. `NewsletterSendStatusResponse.java` — New DTO
**File:** `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSendStatusResponse.java`

Fields: `id`, `status`, `sentCount`, `failedCount`, `totalCount` (=recipientCount), `startedAt`, `completedAt`, `percentComplete` (derived).

### 7. `NewsletterSendResponse.java` — Extend
**File:** `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSendResponse.java`

Add: `status`, `sentCount`, `failedCount`, `startedAt`, `completedAt`.

### 8. `NewsletterController.java` — Add status endpoint
**File:** `services/event-management-service/src/main/java/ch/batbern/events/controller/NewsletterController.java`

Add: `GET /api/v1/events/{eventCode}/newsletter/sends/{sendId}/status` (ORGANIZER role)
- Returns `NewsletterSendStatusResponse`
- Validates that `sendId` belongs to `eventCode` (security: no cross-event leakage)

---

## OpenAPI Spec Changes (ADR-006: spec first)
**File:** `docs/api/events-api.openapi.yml`

1. Update `NewsletterSendResponse` schema: add `status`, `sentCount`, `failedCount`, `startedAt`, `completedAt`
2. Add `NewsletterSendStatusResponse` schema with `id`, `status`, `sentCount`, `failedCount`, `totalCount`, `percentComplete`, `startedAt`, `completedAt`
3. Add new path: `GET /events/{eventCode}/newsletter/sends/{sendId}/status`
4. Regenerate TypeScript types: `npm run generate:api-types`

---

## Frontend Changes

### `useNewsletter.ts`
**File:** `web-frontend/src/hooks/useNewsletter/useNewsletter.ts`

Add `useSendStatus(eventCode, sendId)` query:
- `enabled` when `sendId != null && status is not terminal`
- `refetchInterval: 3000` (poll every 3s while IN_PROGRESS/PENDING)
- Stops polling when status is COMPLETED/PARTIAL/FAILED

### `newsletterService.ts`
**File:** `web-frontend/src/services/newsletterService.ts`

Add `getSendStatus(eventCode, sendId): Promise<NewsletterSendStatusResponse>`

### `EventNewsletterTab.tsx` — Add progress UI
**File:** `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx`

Changes:
1. After successful "Send Newsletter" / "Send Reminder" call → store `sendId` in local state
2. Render progress section while `status === 'IN_PROGRESS' | 'PENDING'`:
   ```
   Sending newsletter...  847 / 3000 recipients (28%)
   [████████░░░░░░░░░░░░░░░░]
   ```
   Use shadcn `Progress` component
3. On COMPLETED → success toast, clear progress, refresh history table
4. On PARTIAL → warning toast: "Sent to 2998 / 3000 subscribers (2 failed)"
5. On FAILED → error toast
6. Disable Send/Reminder buttons while a send is PENDING or IN_PROGRESS (check `newsletter_sends` history for active send on load too)

### i18n additions
**Files:** `web-frontend/public/locales/en/events.json`, `de/events.json`

Add keys under `eventPage.newsletter.*`:
- `sendInProgress`, `sendProgress`, `sendCompleted`, `sendPartial`, `sendFailed`

---

## Retry Failed Recipients

### Backend
**`NewsletterController.java`** — add:
`POST /api/v1/events/{eventCode}/newsletter/sends/{sendId}/retry` (ORGANIZER)
- Validates send exists and belongs to `eventCode`
- Validates `status` is `PARTIAL` or `FAILED` (returns 409 if COMPLETED or IN_PROGRESS)
- Delegates to `NewsletterEmailService.retryFailedRecipients(sendId, event, sentByUsername)`

**`NewsletterEmailService.java`** — add `retryFailedRecipients()`:
1. Load `newsletter_recipients WHERE send_id=? AND delivery_status='failed'` (via `NewsletterRecipientRepository`)
2. Load subscriber records for those emails to get their `unsubscribeToken`
3. Update send: `status=IN_PROGRESS, startedAt=now` (own `@Transactional`)
4. Launch `@Async` loop over failed recipients only — same paginated/rate-limited logic as the main send
5. For each success: update recipient row `delivery_status='sent'`, increment `sentCount` on the send record
6. For remaining failures: leave as `failed`, increment `failedCount`
7. Terminal status: all cleared → `COMPLETED`; some remain → `PARTIAL`; none cleared → `FAILED`

**Key constraint:** Retry re-uses the SAME `newsletter_sends` row (updates in place) so history table shows one clean final row, not a confusing duplicate.

**New `NewsletterRecipientRepository` query:**
`List<NewsletterRecipient> findBySendIdAndDeliveryStatus(UUID sendId, String deliveryStatus)`

### Frontend (`EventNewsletterTab.tsx`)
- In the send history table, show a **"Retry Failed"** button on rows with `status=PARTIAL` or `FAILED`
- Clicking it calls `POST /sends/{sendId}/retry` then starts the same progress-polling flow
- After retry completes, history table refreshes

### OpenAPI
Add `POST /events/{eventCode}/newsletter/sends/{sendId}/retry` path to `events-api.openapi.yml`.

---

## Tests (TDD — Red-Green-Refactor)

### Backend unit tests
**`NewsletterEmailServiceTest.java`** — add:
- `sendNewsletter_returnsImmediately_withPendingStatus`
- `sendNewsletter_throws409_whenSendAlreadyInProgress`
- `executeNewsletterSendAsync_updatesStatusToCompleted_onSuccess`
- `executeNewsletterSendAsync_updatesStatusToPartial_onSomeFailed`
- `executeNewsletterSendAsync_updatesStatusToFailed_onAllFailed`
- `executeNewsletterSendAsync_processesInPages_notAllAtOnce` (verifies Pageable used)

### Backend integration tests
**`NewsletterControllerIntegrationTest.java`** — add:
- `sendNewsletter_returns202_withSendId`
- `getSendStatus_returns200_withProgress` (after send)
- `sendNewsletter_returns409_whenSendInProgress`
- `retryFailedRecipients_returns409_whenSendIsCompleted`
- `retryFailedRecipients_resends_onlyToFailedRecipients`

### Frontend tests
**`EventNewsletterTab.test.tsx`** — add:
- Progress bar renders when status=IN_PROGRESS
- Polls status endpoint while IN_PROGRESS
- Success toast shown on COMPLETED
- Warning toast shown on PARTIAL with counts
- Send button disabled while IN_PROGRESS

---

## Files to Modify

| File | Change |
|------|--------|
| `docs/api/events-api.openapi.yml` | Add status endpoint + new/updated schemas (FIRST) |
| `shared-kernel/.../EmailService.java` | Add `sendHtmlEmailSync()` |
| `V74__add_newsletter_send_status.sql` | NEW — add status columns |
| `NewsletterSend.java` | Add status, sentCount, failedCount, startedAt, completedAt |
| `NewsletterSendRepository.java` | Add findFirstByEventIdAndStatus, findByIdAndEventId |
| `NewsletterSubscriberRepository.java` | Add paginated findByUnsubscribedAtIsNull |
| `NewsletterEmailService.java` | Redesign sendNewsletter() + add executeNewsletterSendAsync() |
| `NewsletterSendResponse.java` | Add status + count fields |
| `NewsletterSendStatusResponse.java` | NEW DTO |
| `NewsletterController.java` | Add GET /sends/{sendId}/status + POST /sends/{sendId}/retry |
| `NewsletterRecipientRepository.java` | Add findBySendIdAndDeliveryStatus |
| `newsletterService.ts` | Add getSendStatus() |
| `useNewsletter.ts` | Add useSendStatus() with polling |
| `EventNewsletterTab.tsx` | Add progress bar + polling logic |
| `en/events.json`, `de/events.json` | Add progress i18n keys |
| `NewsletterEmailServiceTest.java` | Add async/status/pagination tests |
| `NewsletterControllerIntegrationTest.java` | Add status endpoint tests |
| `EventNewsletterTab.test.tsx` | Add progress UI tests |

---

## Verification

1. **Unit tests:** `./gradlew :services:event-management-service:test --tests NewsletterEmailServiceTest`
2. **Integration tests:** `./gradlew :services:event-management-service:test --tests NewsletterControllerIntegrationTest`
3. **Frontend tests:** `cd web-frontend && npx vitest run --reporter=verbose EventNewsletterTab`
4. **Manual verify:**
   - Start native services; send newsletter to test subscribers
   - Confirm `newsletter_sends` row shows status=COMPLETED with correct counts
   - Confirm UI shows progress bar during send
   - Confirm double-click returns 409
   - Kill and restart service mid-send (simulate Fargate Spot): confirm send resumes with correct partial counts visible
5. **Type-check:** `cd web-frontend && npm run type-check` — zero errors
6. **Checkstyle:** `./gradlew :services:event-management-service:checkstyleMain` — zero violations
