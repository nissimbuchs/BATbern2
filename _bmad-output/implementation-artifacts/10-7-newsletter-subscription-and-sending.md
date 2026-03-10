# Story 10.7: Newsletter Subscription & Sending

Status: done

## Story

As an **organizer**, I want to send event newsletters and reminder emails to subscribed community members directly from the platform,
so that we can replace Meetup.com / Hostpoint mailing lists with a fully in-house newsletter system that respects subscriber preferences and GDPR requirements.

As a **community member**, I want to subscribe to BATbern newsletters without creating an account, and unsubscribe instantly from a link in any email,
so that I can stay informed about upcoming events without friction.

As a **logged-in user**, I want to manage my newsletter subscription in the BATbern account settings page (not the organizer portal),
so that I have control over my email preferences in one place.

---

## Acceptance Criteria

### AC1 — Subscriber table and token-based unsubscribe

- A `newsletter_subscribers` table is created by Flyway migration **V67** in `event-management-service` with the fields:
  - `id` UUID PK, `email` VARCHAR(255) UNIQUE NOT NULL, `first_name` VARCHAR(100), `language` VARCHAR(5) DEFAULT 'de'
  - `source` VARCHAR(50): `'explicit'` | `'registration'` | `'account'`
  - `username` VARCHAR(100) nullable (links to `user_profiles.username` for known users; NULL for anonymous)
  - `unsubscribe_token` VARCHAR(255) UNIQUE NOT NULL (UUID, never expires)
  - `subscribed_at` TIMESTAMPTZ NOT NULL DEFAULT NOW()
  - `unsubscribed_at` TIMESTAMPTZ nullable (NULL = active; set on unsubscribe; cleared on re-subscribe)
  - `created_at`, `updated_at` TIMESTAMPTZ
- Supporting tables `newsletter_sends` and `newsletter_recipients` also created in V67 (see Dev Notes)
- Subscribing with an already-subscribed email returns HTTP 409
- Re-subscribing a previously unsubscribed email reactivates the record (clears `unsubscribed_at`), preserves original token

### AC2 — Public subscribe endpoint

- `POST /api/v1/newsletter/subscribe` — no authentication required (`permitAll`)
- Body: `{ email: string, firstName?: string, language?: "de" | "en" }`
- Response: `200 OK` on success, `409 Conflict` if already active subscriber, `400` for invalid email
- Endpoint is also exposed at the **API gateway** level (DomainRouter routes to event-management-service port 8002)

### AC3 — Token-based unsubscribe endpoints

- `GET /api/v1/newsletter/unsubscribe/verify?token={token}` — no auth required — returns `{ email }` or `404`
- `POST /api/v1/newsletter/unsubscribe` — no auth required — body `{ token }` — sets `unsubscribed_at = NOW()` — returns `200 OK` or `404`
- Both endpoints `permitAll` in EMS and API gateway SecurityConfig

### AC4 — Homepage footer subscribe widget

- A `NewsletterSubscribeWidget` component is added to the **footer section of the public homepage** (`components/public/HomePage.tsx`)
- The widget contains: headline text, email input, "Subscribe" button
- Uses shadcn `Input` + `Button` components (consistent with public site design)
- Success state: inline confirmation message replacing the form
- Already-subscribed (409): inline "You are already subscribed" message
- Client-side email validation before submit
- i18n keys under `newsletter.widget.*` in both `en.json` and `de.json`

### AC5 — Public unsubscribe page

- A `UnsubscribePage` is added at public route `/unsubscribe?token={token}` (no auth required)
- On page load: calls `GET /unsubscribe/verify?token={token}` to show "You are unsubscribing **email@example.com** from BATbern newsletters"
- "Confirm Unsubscribe" button: calls `POST /unsubscribe` — on success shows "You have been unsubscribed. [Subscribe again]" link
- Invalid/already-used token: shows "This unsubscribe link is invalid or has already been used." message
- Uses `PublicLayout` wrapper; styled with shadcn `Card` + `Button`
- i18n keys under `newsletter.unsubscribe.*`

### AC6 — Registration wizard wires newsletter subscription

- When a user completes event registration with `communicationPreferences.newsletterSubscribed = true`, the backend automatically creates (or reactivates) a subscriber record in `newsletter_subscribers`
- Source = `'registration'`; `language` detected from Accept-Language header or defaults to `'de'`
- This is a silent side effect — registration flow and confirmation email are unchanged
- If subscriber already exists (active), no error; if unsubscribed, it is reactivated

### AC7 — Authenticated user subscription management

- `GET /api/v1/newsletter/my-subscription` — requires authentication — returns `{ subscribed: boolean, email?: string }`
  - Looks up by `username` from JWT, then falls back to user's email address
- `PATCH /api/v1/newsletter/my-subscription` — requires authentication — body `{ subscribed: boolean }`
  - `subscribed: true` → upsert subscriber with `source='account'`, `username` from JWT
  - `subscribed: false` → sets `unsubscribed_at = NOW()`
- In `UserSettingsTab.tsx`, add a **Newsletter section** below the existing notification channel toggles (Notifications sub-tab, index 1):
  ```
  Newsletter
  [ ] Subscribe to BATbern newsletter
      Receive event announcements and updates
  ```
- Toggle is wired to `PATCH /api/v1/newsletter/my-subscription`
- Section is only shown when user is authenticated; loads current status on mount
- i18n keys: `newsletter.account.label`, `newsletter.account.description`

### AC8 — Newsletter email template (single template, dual use)

- Two classpath content templates are created:
  - `email-templates/newsletter-event-de.html`
  - `email-templates/newsletter-event-en.html`
- Both are **content-only fragments** (no HTML shell — `batbern-default` layout provides the wrapper)
- Both are **already authored** — they faithfully reproduce the existing BATbern Meetup newsletter structure (intro text, event details table, speakers section, registration link, upcoming events table, notes, closing, unsubscribe footer)
- Both are seeded into the DB by `EmailTemplateSeedService` under category `NEWSLETTER`
- DB subject pattern: `"{{reminderPrefix}}{{eventTitle}} — BATbern"` (DE) / `"{{reminderPrefix}}{{eventTitle}} — BATbern"` (EN)
- Template variables — all resolved by `NewsletterEmailService` before sending:

  | Variable | Description | Example |
  |----------|-------------|---------|
  | `{{reminderPrefix}}` | Empty for newsletter; localized "Reminder: " prefix for reminders | `""` / `"Erinnerung: "` |
  | `{{eventNumber}}` | Event sequence number | `"58"` |
  | `{{eventType}}` | Event format, localized | `"Abend-BAT"` / `"Evening BAT"` |
  | `{{eventTitle}}` | Event topic/title | `"AI in der Software Entwicklung"` |
  | `{{eventDate}}` | Formatted date | `"Freitag, 6. März 2026"` |
  | `{{eventTime}}` | Time range | `"16.00 – 18.30 Uhr"` |
  | `{{venue}}` | Venue name and city | `"Forum, Zentrum Paul Klee, Bern"` |
  | `{{venueDirectionsUrl}}` | Optional directions URL (Mustache conditional `{{#venueDirectionsUrl}}...{{/venueDirectionsUrl}}`) | `"https://maps.google.com/..."` or empty |
  | `{{speakersSection}}` | Optional HTML block (see Dev Notes for format); empty string if agenda not yet published | `<p>Mit folgenden Beiträgen:</p><ul>...` |
  | `{{eventDetailLink}}` | Full URL to event page | `{baseUrl}/events/BATbern58` |
  | `{{conferenceLanguage}}` | Spoken language(s) | `"Deutsch und Englisch"` |
  | `{{registrationLink}}` | Registration URL (replaces old Meetup link) | `{baseUrl}/register/BATbern58` |
  | `{{upcomingEventsSection}}` | Optional HTML table of future events (see Dev Notes); empty string if none | `<p>BAT-Termine:...` |
  | `{{unsubscribeLink}}` | **Per-recipient** unsubscribe URL — **REQUIRED** (GDPR) | `{baseUrl}/unsubscribe?token={uuid}` |
  | `{{preferencesLink}}` | Link to account settings | `{baseUrl}/account` |

- Every rendered email MUST contain a visible "Unsubscribe" link (already in the template footer)
- Newsletter templates are visible/editable in the Email Templates admin tab (Story 10.2) under the `NEWSLETTER` category

### AC9 — Newsletter tab on EventPage (organizer)

- A new **"Newsletter" tab** is added to the organizer EventPage (`EventPage.tsx`) with `id: 'newsletter'` and an `EmailOutlined` icon
- The tab contains `EventNewsletterTab.tsx` with three sections:

  **Section 1 — Subscriber summary:**
  - Shows total active subscriber count: "234 active subscribers"
  - Refreshes on tab open

  **Section 2 — Send history:**
  - Table listing all previous sends for this event: date, type (Newsletter / Reminder), recipient count
  - Empty state: "No newsletters sent for this event yet"

  **Section 3 — Compose and send:**
  - Language selector: DE (default) / EN
  - "Preview" button: renders the email with this event's data in a sandboxed `<iframe>` within the tab
  - "Send Newsletter" button: sends with `isReminder=false`
  - "Send Reminder" button: sends with `isReminder=true` (subject prefixed with "Erinnerung: " / "Reminder: ")
  - Both send buttons trigger a **confirmation dialog** showing: recipient count, send type, event title — user must confirm
  - Post-send: send history table refreshes; success toast notification

- i18n keys under `eventPage.newsletter.*` in organizer locale files

### AC10 — Newsletter send backend

- `POST /api/v1/events/{eventCode}/newsletter/send` — ORGANIZER role only
  - Body: `{ isReminder: boolean, locale: "de" | "en" }`
  - Loads event data (title, date, time, venue, topic, speakers if workflow state allows)
  - Fetches all active subscribers (`unsubscribed_at IS NULL`)
  - For each subscriber: replaces `{{unsubscribeLink}}` with their personal token URL
  - Sends via `EmailService.sendHtmlEmail()` (async, existing pattern)
  - Logs to `newsletter_sends` + `newsletter_recipients`
  - Returns: `{ recipientCount, sentAt }`
- `POST /api/v1/events/{eventCode}/newsletter/preview`
  - Body: `{ isReminder: boolean, locale: "de" | "en" }`
  - Same build logic but no sending; uses a placeholder `{{unsubscribeLink}}`
  - Returns: `{ subject: string, htmlPreview: string, recipientCount: int }`
- `GET /api/v1/events/{eventCode}/newsletter/history` — ORGANIZER role
  - Returns list of `NewsletterSendResponse`
- `GET /api/v1/newsletter/subscribers` — ORGANIZER role
  - Returns total count + first-page subscriber list

### AC11 — OpenAPI spec updated first (ADR-006)

- All new newsletter endpoints are added to `docs/api/events.openapi.yml` **before** any backend implementation
- DTOs match the spec

### AC12 — Tests

- **Backend TDD (Red-Green-Refactor)**:
  - `NewsletterSubscriberServiceTest` (unit): subscribe/upsert/reactivate/unsubscribe/verify
  - `NewsletterControllerIntegrationTest` (extends `AbstractIntegrationTest`): all endpoints, auth checks, 409 duplicate
  - `NewsletterEmailServiceTest` (unit): `reminderPrefix` substitution, `unsubscribeLink` present in every output
- **Frontend**:
  - `NewsletterSubscribeWidget.test.tsx`: success / 409 / error states
  - `UnsubscribePage.test.tsx`: verify + confirm flow, invalid token state
  - `EventNewsletterTab.test.tsx`: subscriber count display, history table, send confirmation dialog

---

## Dev Notes

### DB Migration V67

```sql
-- newsletter_subscribers
CREATE TABLE newsletter_subscribers (
  id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email             VARCHAR(255) NOT NULL,
  first_name        VARCHAR(100),
  language          VARCHAR(5)   NOT NULL DEFAULT 'de',
  source            VARCHAR(50)  NOT NULL DEFAULT 'explicit',
  username          VARCHAR(100),
  unsubscribe_token VARCHAR(255) NOT NULL,
  subscribed_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  unsubscribed_at   TIMESTAMPTZ,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_newsletter_email           UNIQUE (email),
  CONSTRAINT uq_newsletter_unsubscribe_tok UNIQUE (unsubscribe_token)
);

-- newsletter_sends
CREATE TABLE newsletter_sends (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id         UUID        NOT NULL REFERENCES events(id),
  template_key     VARCHAR(100) NOT NULL,
  is_reminder      BOOLEAN     NOT NULL DEFAULT FALSE,
  sent_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  sent_by_username VARCHAR(100) NOT NULL,
  recipient_count  INTEGER,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- newsletter_recipients (audit log per send)
CREATE TABLE newsletter_recipients (
  send_id         UUID        NOT NULL REFERENCES newsletter_sends(id) ON DELETE CASCADE,
  email           VARCHAR(255) NOT NULL,
  delivery_status VARCHAR(50)  NOT NULL DEFAULT 'sent',
  PRIMARY KEY (send_id, email)
);
```

### V67 also seeds newsletter email templates

The migration inserts placeholder rows (html_body = classpath marker). `EmailTemplateSeedService` overwrites `html_body` on startup from classpath, same as all other system templates.

Template keys: `newsletter-event` (locale=`de`) and `newsletter-event` (locale=`en`).

### Backend class locations

```
services/event-management-service/src/main/java/ch/batbern/events/
  domain/
    NewsletterSubscriber.java       ← JPA entity (Lombok @Data, @Builder)
    NewsletterSend.java             ← JPA entity
  repository/
    NewsletterSubscriberRepository.java
    NewsletterSendRepository.java
  service/
    NewsletterSubscriberService.java
    NewsletterEmailService.java
  controller/
    NewsletterController.java
  dto/
    NewsletterSubscribeRequest.java
    NewsletterUnsubscribeRequest.java
    NewsletterSubscriptionStatusResponse.java
    NewsletterSendRequest.java
    NewsletterSendResponse.java
    NewsletterPreviewResponse.java
    SubscriberResponse.java
```

### Reuse these existing components (do not reinvent)

| What | Where |
|------|-------|
| Template loading (DB-first + classpath fallback) | `service/EmailTemplateService.java` → `findByKeyAndLocale()` + `mergeWithLayout()` |
| Variable replacement | `shared-kernel/.../EmailService.replaceVariables(html, Map<String,String>)` |
| Async HTML email send | `shared-kernel/.../EmailService.sendHtmlEmail(to, subject, html)` |
| Token-based public page pattern | `pages/speaker-portal/InvitationResponsePage.tsx` + `Skip-Auth: true` header |
| Classpath template seeding | `service/EmailTemplateSeedService.java` — add `newsletter-event` to the seeding map |
| ShedLock (if scheduler added later) | `config/ShedLockConfig.java` |
| Public API route pattern | `DomainRouter.java` → add `/api/v1/newsletter/**` → event-management-service |

### Newsletter template files (already authored)

Both templates are already written and committed to the classpath. They faithfully reproduce the existing BATbern Meetup newsletter design:

```
services/event-management-service/src/main/resources/email-templates/
  newsletter-event-de.html   ← DONE (matches existing newsletter, German)
  newsletter-event-en.html   ← DONE (matches existing newsletter, English)
```

**Structure** (same in both locales):
1. "BAT-Newsletter" intro paragraph (who we are, why you receive this)
2. Event announcement: "Das {{eventNumber}}. Berner Architekten-Treffen findet als {{eventType}} wie folgt statt" with a bordered table: Datum / Thema / Ort (with optional Anfahrt link)
3. `{{speakersSection}}` — speaker bullet list (conditional, empty if not published)
4. Apéro note + event detail link + conference language
5. Registration section (Anmeldung) with `{{registrationLink}}`
6. `{{upcomingEventsSection}}` — upcoming dates table (conditional)
7. Notes section (Hinweise): in-person, Slideshare link
8. Closing: "Wir freuen uns auf deine Teilnahme. Das Organisationskomitee"
9. GDPR unsubscribe footer: `{{unsubscribeLink}}` + `{{preferencesLink}}`

**`{{speakersSection}}` format** — built by `NewsletterEmailService` when event has published speakers:
```html
<p><strong>Mit folgenden Beiträgen:</strong></p>
<ul>
  <li>"Session Title 1", <strong>Speaker Name</strong>, Company</li>
  <li>"Session Title 2", <strong>Speaker Name</strong>, Company</li>
</ul>
```
Empty string `""` when agenda is not yet published (no section rendered).

**`{{upcomingEventsSection}}` format** — built from future confirmed events in the DB:
```html
<p><strong>BAT-Termine:</strong><br>
Damit du dir bereits die Termine der nächsten Treffen reservieren kannst:</p>
<table style="border-collapse:collapse;width:100%;margin:8px 0 16px;">
  <tr>
    <td style="padding:6px 12px;border:1px solid #ccc;">BATbern59</td>
    <td style="padding:6px 12px;border:1px solid #ccc;">Freitag, 19.06.2026</td>
    <td style="padding:6px 12px;border:1px solid #ccc;font-style:italic;">Thema noch offen</td>
    <td style="padding:6px 12px;border:1px solid #ccc;">Ganztages-BAT</td>
  </tr>
</table>
```
Empty string `""` when no future events exist.

### Frontend files

```
web-frontend/src/
  services/
    newsletterService.ts                                ← NEW
  hooks/useNewsletter/
    useNewsletter.ts                                    ← NEW
  components/public/
    NewsletterSubscribeWidget.tsx                       ← NEW
  pages/public/
    UnsubscribePage.tsx                                 ← NEW
  components/organizer/EventPage/
    EventNewsletterTab.tsx                              ← NEW
```

### Modified files

```
services/event-management-service/
  src/main/java/.../service/RegistrationService.java   ← wire newsletter subscribe on registration
  src/main/java/.../config/SecurityConfig.java         ← add 3 public newsletter permitAll paths
  src/main/resources/db/migration/V67__create_newsletter_tables.sql  ← NEW
  src/main/resources/email-templates/newsletter-event-de.html        ← NEW
  src/main/resources/email-templates/newsletter-event-en.html        ← NEW

api-gateway/
  src/main/java/.../DomainRouter.java                  ← route /api/v1/newsletter/** → EMS
  src/main/java/.../SecurityConfig.java                ← same 3 public paths permitAll

web-frontend/src/
  components/organizer/EventPage/EventPage.tsx         ← add Newsletter tab
  components/user/UserSettingsTab/UserSettingsTab.tsx  ← add Newsletter section
  App.tsx                                              ← add /unsubscribe public route
  components/public/HomePage.tsx                       ← add <NewsletterSubscribeWidget /> in footer
  i18n/en.json                                         ← newsletter.* keys
  i18n/de.json                                         ← newsletter.* keys

docs/api/events.openapi.yml                            ← newsletter endpoints (FIRST — ADR-006)
```

### i18n key structure

```json
{
  "newsletter": {
    "widget": {
      "title": "Stay updated with BATbern",
      "placeholder": "your@email.com",
      "button": "Subscribe",
      "success": "Thank you! You are now subscribed.",
      "alreadySubscribed": "You are already subscribed.",
      "error": "Something went wrong. Please try again."
    },
    "unsubscribe": {
      "title": "Unsubscribe from BATbern Newsletter",
      "confirmMessage": "You are about to unsubscribe {{email}} from BATbern newsletters.",
      "confirmButton": "Confirm Unsubscribe",
      "success": "You have been successfully unsubscribed.",
      "resubscribeLink": "Subscribe again",
      "invalidToken": "This unsubscribe link is invalid or has already been used."
    },
    "account": {
      "label": "Subscribe to BATbern newsletter",
      "description": "Receive event announcements and updates"
    }
  }
}
```

For organizer EventPage newsletter tab, add under `eventPage.newsletter.*`:
```json
{
  "eventPage": {
    "newsletter": {
      "title": "Newsletter",
      "subscriberCount": "{{count}} active subscribers",
      "sendHistory": "Send History",
      "noHistory": "No newsletters sent for this event yet.",
      "composeTitle": "Compose & Send",
      "locale": "Language",
      "preview": "Preview",
      "sendNewsletter": "Send Newsletter",
      "sendReminder": "Send Reminder",
      "confirmSendTitle": "Confirm Send",
      "confirmSendBody": "Send {{type}} to {{count}} subscribers for event {{eventTitle}}?",
      "sendSuccess": "Newsletter sent to {{count}} recipients."
    }
  }
}
```

---

## Definition of Done

- [ ] Flyway V67 creates all 3 newsletter tables cleanly; `flywayMigrate` runs without errors
- [ ] `POST /api/v1/newsletter/subscribe` works without auth; duplicate returns 409
- [ ] Registration with newsletter checkbox subscribes user silently (no change to registration flow)
- [ ] `/unsubscribe?token=valid` shows email confirmation page; confirm unsubscribes successfully
- [ ] `/unsubscribe?token=invalid` shows error state gracefully
- [ ] Homepage footer displays `NewsletterSubscribeWidget`; submit works in browser
- [ ] `/account` Settings → Notifications shows Newsletter toggle for authenticated users
- [ ] EventPage → Newsletter tab shows subscriber count, send history, preview, send buttons
- [ ] Preview renders branded email with event data in iframe (correct `reminderPrefix` per mode)
- [ ] "Send Newsletter" confirmation dialog shows recipient count; send succeeds and adds to history
- [ ] "Send Reminder" sets `reminderPrefix` to "Erinnerung: " (DE) / "Reminder: " (EN) in subject + content
- [ ] Every sent email contains a working `{{unsubscribeLink}}` footer link
- [ ] Newsletter templates visible/editable in `/organizer/admin` Email Templates tab (NEWSLETTER category)
- [ ] OpenAPI spec committed **before** any backend implementation (ADR-006)
- [ ] All AC12 tests pass (unit + integration + frontend)
- [ ] Type-check passes: `npm run type-check` in web-frontend — zero errors
- [ ] Checkstyle passes: `./gradlew :services:event-management-service:checkstyleMain` — zero violations
- [ ] i18n: all `newsletter.*` keys present in both `en.json` and `de.json`

---

## Prerequisites

- ✅ Story 10.2 (Email Template Management) — provides `EmailTemplateService`, `EmailTemplateSeedService`, `NEWSLETTER` category scaffold, `batbern-default` layout
- ✅ Story 10.1 — EmailTemplates tab on `/organizer/admin` (for newsletter template editing)

---

## Robustness Addendum (2026-03-10): Async Background Job + Progress Tracking

### Problem

With 3000+ planned subscribers, the original `sendNewsletter()` implementation had a critical P0 bug:
`@Async` tasks were dispatched one per subscriber into a `ThreadPoolTaskExecutor` (core=5, max=10, queue=100).
After ~110 submissions, `TaskRejectedException` was thrown and caught as "failed" — meaning ~2890 emails were **never sent**.

Additional gaps: duplicate-send risk, no progress visibility, SES rate limit not respected, HTTP timeout risk.

### Fix: Fire-and-Forget + Background Job + Polling

**Migration V87** (`V87__add_newsletter_send_status.sql`): adds `status`, `sent_count`, `failed_count`, `started_at`, `completed_at` to `newsletter_sends`. Backfills existing rows as `COMPLETED`.

**`sendNewsletter()`** now:
1. Checks for IN_PROGRESS send on this event → 409 if duplicate
2. Creates PENDING audit record (committed immediately)
3. Launches single `@Async` background job
4. Returns immediately with `{sendId, status=PENDING}`

**`executeNewsletterSendAsync()` (background job)**:
- Paginates subscribers 50/page (`findByUnsubscribedAtIsNull(Pageable)`)
- Calls `emailService.sendHtmlEmailSync()` (new non-`@Async` method on shared-kernel `EmailService`)
- Sleeps 70ms between emails (≈14/s — SES default rate limit)
- Updates `sent_count`/`failed_count` after each page
- Terminal status: all OK → `COMPLETED`, some failed → `PARTIAL`, all failed → `FAILED`

**Retry**: `POST /sends/{sendId}/retry` re-sends only to `delivery_status='failed'` recipients,
updates the same send row (no duplicate history entries).

**Frontend polling**: `useSendStatus()` polls `GET /sends/{sendId}/status` every 3s while PENDING/IN_PROGRESS.
Shows `LinearProgress` bar with `sentCount / totalCount (percentComplete%)`.
On COMPLETED/PARTIAL/FAILED: shows appropriate Alert. Retry button on PARTIAL/FAILED history rows.

### New Files (Robustness Addendum)
- `V87__add_newsletter_send_status.sql`
- `services/.../dto/NewsletterSendStatusResponse.java`
- `services/.../exception/DuplicateNewsletterSendException.java`

### Modified Files (Robustness Addendum)
- `shared-kernel/.../EmailService.java` — added `sendHtmlEmailSync()`
- `services/.../domain/NewsletterSend.java` — added status/sentCount/failedCount/startedAt/completedAt fields
- `services/.../repository/NewsletterSendRepository.java` — added `findFirstByEventIdAndStatus`, `findByIdAndEventId`
- `services/.../repository/NewsletterSubscriberRepository.java` — added paginated `findByUnsubscribedAtIsNull(Pageable)`
- `services/.../repository/NewsletterRecipientRepository.java` — added `findByIdSendIdAndDeliveryStatus`
- `services/.../service/NewsletterEmailService.java` — redesigned `sendNewsletter()` + `executeNewsletterSendAsync()` + `retryFailedRecipients()` + `executeRetryAsync()` + `updateSendProgress()`
- `services/.../dto/NewsletterSendResponse.java` — added status/sentCount/failedCount/startedAt/completedAt
- `services/.../controller/NewsletterController.java` — added GET `/sends/{sendId}/status` + POST `/sends/{sendId}/retry`
- `services/.../exception/GlobalExceptionHandler.java` — added handler for `DuplicateNewsletterSendException` → 409
- `docs/api/events-api.openapi.yml` — added `NewsletterSendStatusResponse` schema, updated `NewsletterSendResponse`, added 2 new paths
- `web-frontend/src/types/generated/events-api.types.ts` — regenerated (includes new schemas/endpoints)
- `web-frontend/src/services/newsletterService.ts` — added `NewsletterSendStatusResponse`, `getSendStatus()`, `retryFailedRecipients()`, status fields on `NewsletterSendHistoryItem`
- `web-frontend/src/hooks/useNewsletter/useNewsletter.ts` — added `useSendStatus()` (3s polling), `useRetryFailedRecipients()`
- `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx` — progress bar, retry buttons, status column
- `web-frontend/public/locales/en/events.json` + `de/events.json` — added progress/retry i18n keys
- `services/.../controller/NewsletterControllerIntegrationTest.java` — added 4 auth/404 tests for new endpoints
- `web-frontend/src/components/organizer/EventPage/__tests__/EventNewsletterTab.test.tsx` — added 5 progress/retry/status tests

### Test Results (Robustness Addendum)
- `NewsletterEmailServiceTest`: 19/19 PASSED
- `NewsletterControllerIntegrationTest`: 26/26 PASSED
- `EventNewsletterTab.test.tsx`: 15/15 PASSED
- Checkstyle: 0 violations
- TypeScript type-check: 0 errors
- Frontend lint: 0 warnings

---

## Tasks / Subtasks

### Task 1: OpenAPI Spec — Add newsletter endpoints (AC11) [ADR-006: spec before code]
- [x] 1.1 Add `Newsletter` tag to `docs/api/events-api.openapi.yml`
- [x] 1.2 Add all newsletter endpoint paths + DTOs to OpenAPI spec

### Task 2: Flyway V67 migration (AC1)
- [x] 2.1 Create `V67__create_newsletter_tables.sql` with newsletter_subscribers, newsletter_sends, newsletter_recipients tables

### Task 3: Backend entities + repositories (AC1)
- [x] 3.1 Create `NewsletterSubscriber.java` JPA entity
- [x] 3.2 Create `NewsletterSend.java` JPA entity
- [x] 3.3 Create `NewsletterSubscriberRepository.java`
- [x] 3.4 Create `NewsletterSendRepository.java`

### Task 4: Backend DTOs (AC2, AC3, AC7, AC10)
- [x] 4.1 Create `NewsletterSubscribeRequest.java`
- [x] 4.2 Create `NewsletterUnsubscribeRequest.java`
- [x] 4.3 Create `NewsletterSubscriptionStatusResponse.java`
- [x] 4.4 Create `NewsletterSendRequest.java`
- [x] 4.5 Create `NewsletterSendResponse.java`
- [x] 4.6 Create `NewsletterPreviewResponse.java`
- [x] 4.7 Create `SubscriberResponse.java`

### Task 5: NewsletterSubscriberService (AC1, AC2, AC3, AC6, AC7)
- [x] 5.1 subscribe() — upsert; 409 if already active; reactivate if unsubscribed
- [x] 5.2 verifyToken() — returns email or 404
- [x] 5.3 unsubscribe() — sets unsubscribed_at
- [x] 5.4 getMySubscription() — lookup by username then email
- [x] 5.5 patchMySubscription() — subscribe/unsubscribe authenticated user
- [x] 5.6 getActiveCount() — for subscriber count display
- [x] 5.7 findActiveSubscribers() — fetch all for bulk send

### Task 6: NewsletterEmailService (AC8, AC10)
- [x] 6.1 buildVariables(event, locale, isReminder, unsubscribeToken) — all template vars
- [x] 6.2 buildSpeakersSection(event, locale) — conditional HTML block
- [x] 6.3 buildUpcomingEventsSection(locale) — future events table
- [x] 6.4 preview(event, isReminder, locale) — no send, returns subject + htmlPreview + recipientCount
- [x] 6.5 sendNewsletter(event, isReminder, locale, sentByUsername) — per-recipient send + audit log

### Task 7: NewsletterController (AC2, AC3, AC7, AC9, AC10)
- [x] 7.1 POST /api/v1/newsletter/subscribe (permitAll)
- [x] 7.2 GET /api/v1/newsletter/unsubscribe/verify?token= (permitAll)
- [x] 7.3 POST /api/v1/newsletter/unsubscribe (permitAll)
- [x] 7.4 GET /api/v1/newsletter/my-subscription (authenticated)
- [x] 7.5 PATCH /api/v1/newsletter/my-subscription (authenticated)
- [x] 7.6 GET /api/v1/newsletter/subscribers (ORGANIZER)
- [x] 7.7 POST /api/v1/events/{eventCode}/newsletter/send (ORGANIZER)
- [x] 7.8 POST /api/v1/events/{eventCode}/newsletter/preview (ORGANIZER)
- [x] 7.9 GET /api/v1/events/{eventCode}/newsletter/history (ORGANIZER)

### Task 8: EMS SecurityConfig — public newsletter paths (AC2, AC3)
- [x] 8.1 Add permitAll for POST /api/v1/newsletter/subscribe
- [x] 8.2 Add permitAll for GET + POST /api/v1/newsletter/unsubscribe/**

### Task 9: API Gateway routing + security (AC2, AC3, AC7, AC10)
- [x] 9.1 DomainRouter: add /api/v1/newsletter/** → event-management-service
- [x] 9.2 Gateway SecurityConfig: same 3 public paths permitAll

### Task 10: RegistrationService — wire newsletter subscribe (AC6)
- [x] 10.1 After registration created, if newsletterSubscribed=true → call NewsletterSubscriberService.subscribe()
  NOTE: CreateRegistrationRequest.getCommunicationPreferences() → newsletterSubscribed field

### Task 11: EmailTemplateSeedService — NEWSLETTER category (AC8)
- [x] 11.1 Add "newsletter-" prefix case to deriveCategory() → "NEWSLETTER"

### Task 12: Frontend — newsletterService.ts + useNewsletter.ts (AC2, AC3, AC7, AC10)
- [x] 12.1 Create `newsletterService.ts` with all API call functions
- [x] 12.2 Create `useNewsletter.ts` hook with React Query queries/mutations

### Task 13: Frontend — NewsletterSubscribeWidget.tsx (AC4)
- [x] 13.1 Widget with form / success / 409 / error states + i18n

### Task 14: Frontend — UnsubscribePage.tsx (AC5)
- [x] 14.1 Token-based unsubscribe page using PublicLayout + Card + Button

### Task 15: Frontend — EventNewsletterTab.tsx (AC9)
- [x] 15.1 Subscriber count section
- [x] 15.2 Send history table
- [x] 15.3 Compose & send section (language select, preview iframe, send buttons, confirmation dialog)

### Task 16: Frontend — Wiring (AC4, AC5, AC7, AC9)
- [x] 16.1 HomePage.tsx: add `<NewsletterSubscribeWidget />` in footer section
- [x] 16.2 UserSettingsTab.tsx: add Newsletter section under Notifications sub-tab (index 1)
- [x] 16.3 EventPage.tsx: add "newsletter" tab + EventNewsletterTab
- [x] 16.4 App.tsx: add `/unsubscribe` public route

### Task 17: i18n keys (AC4, AC5, AC7, AC9)
- [x] 17.1 Add `newsletter.*` keys to `en.json` and `de.json`
- [x] 17.2 Add `eventPage.newsletter.*` keys to events locale files

### Task 18: Backend tests — unit + integration (AC12)
- [x] 18.1 `NewsletterSubscriberServiceTest` (unit): subscribe/upsert/reactivate/unsubscribe/verify
- [x] 18.2 `NewsletterControllerIntegrationTest` (extends AbstractIntegrationTest): all endpoints, auth, 409
- [x] 18.3 `NewsletterEmailServiceTest` (unit): reminderPrefix substitution, unsubscribeLink present

### Task 19: Frontend tests (AC12)
- [x] 19.1 `NewsletterSubscribeWidget.test.tsx`: success / 409 / error states
- [x] 19.2 `UnsubscribePage.test.tsx`: verify + confirm flow, invalid token state
- [x] 19.3 `EventNewsletterTab.test.tsx`: count display, history table, send confirmation dialog

### Task 20: Robustness — Async background job + progress tracking (2026-03-10)
- [x] 20.1 V87 migration: add status/sent_count/failed_count/started_at/completed_at to newsletter_sends
- [x] 20.2 `EmailService.java` (shared-kernel): add `sendHtmlEmailSync()` non-@Async method
- [x] 20.3 `NewsletterSend.java`: add new status fields
- [x] 20.4 `NewsletterSendResponse.java`: extend with new fields
- [x] 20.5 `NewsletterSendStatusResponse.java`: NEW DTO for polling endpoint
- [x] 20.6 `DuplicateNewsletterSendException.java`: NEW 409 exception
- [x] 20.7 `NewsletterSendRepository.java`: add findFirstByEventIdAndStatus, findByIdAndEventId
- [x] 20.8 `NewsletterSubscriberRepository.java`: add paginated findByUnsubscribedAtIsNull
- [x] 20.9 `NewsletterRecipientRepository.java`: add findByIdSendIdAndDeliveryStatus
- [x] 20.10 `NewsletterEmailService.java`: redesign sendNewsletter + executeNewsletterSendAsync + retryFailedRecipients + executeRetryAsync + updateSendProgress
- [x] 20.11 `NewsletterController.java`: add GET /sends/{sendId}/status + POST /sends/{sendId}/retry
- [x] 20.12 `GlobalExceptionHandler.java`: add handler for DuplicateNewsletterSendException → 409
- [x] 20.13 OpenAPI spec: add NewsletterSendStatusResponse schema, update NewsletterSendResponse, add 2 new paths
- [x] 20.14 Frontend: regenerate TypeScript types
- [x] 20.15 `newsletterService.ts`: add getSendStatus, retryFailedRecipients, status fields on HistoryItem
- [x] 20.16 `useNewsletter.ts`: add useSendStatus (3s polling), useRetryFailedRecipients
- [x] 20.17 `EventNewsletterTab.tsx`: progress bar, retry buttons, status column
- [x] 20.18 i18n: add progress/retry keys to en/de events.json
- [x] 20.19 Tests: 4 new integration tests, 5 new frontend tests, verify all pass

---

## Dev Agent Record

### Implementation Plan
- ADR-006 compliance: OpenAPI spec first (Task 1), then backend (Tasks 2-11), then frontend (Tasks 12-19)
- EmailTemplateSeedService auto-seeds newsletter-event-de/en from classpath on startup (idempotent); V67 inserts placeholder rows
- Per-recipient unsubscribeLink injected at send time in NewsletterEmailService (GDPR compliance)
- NewsletterSubscriberService.subscribe() uses DuplicateSubscriberException (@ResponseStatus CONFLICT) for 409
- RegistrationService.createRegistration() wired to subscribe newsletter if request.getCommunicationPreferences().isNewsletterSubscribed()

### Debug Log
- Tasks 1-9 complete (session 1, 2026-02-25): OpenAPI spec, V67 migration, entities, repos, DTOs, NewsletterSubscriberService, NewsletterEmailService, NewsletterController, EMS SecurityConfig, API Gateway routing+SecurityConfig
- Tasks 10-19 complete (session 2, 2026-02-25): all frontend + backend wiring, tests, i18n
- See Completion Notes for bugs fixed and test results

### Completion Notes
Completed in 2 sessions (2026-02-25):
- Session 1: Tasks 1-9 (OpenAPI spec, V67 migration, entities, repos, DTOs, services, controller, security, routing)
- Session 2: Tasks 10-19 (frontend wiring, tests, i18n, fixes)

**Bugs caught and fixed during TDD:**
1. `EventType` enum values in `localizeEventType()` were wrong (`EVENING_BAT` → `EVENING`, etc.) — fixed by checking generated enum
2. `toResponse()` in `NewsletterEmailService` was package-private but called from controller — made public
3. `EventWorkflowState.AGENDA_DRAFT` doesn't exist — test used `CREATED` instead
4. `GlobalExceptionHandler.handleGenericException(Exception.class)` was catching `DuplicateSubscriberException` before `@ResponseStatus` processed — added explicit `@ExceptionHandler(DuplicateSubscriberException.class)` returning 409
5. Newsletter HTML templates missing `<!-- subject: ... -->` comment at line 1 — added to both newsletter-event-de.html and newsletter-event-en.html
6. `EmailTemplateResponse.CategoryEnum` in OpenAPI spec didn't include `NEWSLETTER` — added to both `EmailTemplateResponse` and `CreateEmailTemplateRequest` schemas; regenerated OpenAPI code
7. `NewsletterEmailService` had 9 Checkstyle violations (unused import, line length, indentation) — all fixed

**Test results:**
- `NewsletterSubscriberServiceTest`: 11/11 PASSED
- `NewsletterEmailServiceTest`: 8/8 PASSED (+ 4 additional from AGENDA_PUBLISHED test)
- `NewsletterControllerIntegrationTest`: 11/11 PASSED
- `EmailTemplateSeedServiceTest`: 14/14 PASSED (after adding subject comment to templates)
- `EmailTemplateControllerIntegrationTest`: 11/11 PASSED (after OpenAPI enum fix)
- Checkstyle: 0 violations
- Frontend type-check: 0 errors in newsletter files (pre-existing errors in EmailTemplateEditModal.tsx unrelated)

---

## File List

### New files (Tasks 1-9)
- `docs/api/events-api.openapi.yml` — added Newsletter tag, 8 paths, 6 schemas
- `services/event-management-service/src/main/resources/db/migration/V67__create_newsletter_tables.sql`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterSubscriber.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterSend.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSubscriberRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSendRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSubscribeRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterUnsubscribeRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSubscriptionStatusResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSendRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSendResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterPreviewResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SubscriberResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/exception/DuplicateSubscriberException.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterSubscriberService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/NewsletterController.java`

### Modified files (Tasks 8-11)
- `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java` — added 3 newsletter permitAll rules
- `api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java` — added /api/v1/newsletter/** → EMS
- `api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java` — added 3 newsletter permitAll rules
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java` — wire newsletter subscribe on registration
- `services/event-management-service/src/main/java/ch/batbern/events/service/EmailTemplateSeedService.java` — add NEWSLETTER category
- `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` — explicit 409 handler for DuplicateSubscriberException
- `services/event-management-service/src/main/resources/email-templates/newsletter-event-de.html` — added subject comment at line 1
- `services/event-management-service/src/main/resources/email-templates/newsletter-event-en.html` — added subject comment at line 1
- `docs/api/events-api.openapi.yml` — added NEWSLETTER to EmailTemplateResponse + CreateEmailTemplateRequest category enums

### New files (Tasks 12-19)
- `web-frontend/src/services/newsletterService.ts`
- `web-frontend/src/hooks/useNewsletter/useNewsletter.ts`
- `web-frontend/src/components/public/NewsletterSubscribeWidget.tsx`
- `web-frontend/src/pages/public/UnsubscribePage.tsx`
- `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx`
- `web-frontend/src/components/public/__tests__/NewsletterSubscribeWidget.test.tsx`
- `web-frontend/src/pages/public/__tests__/UnsubscribePage.test.tsx`
- `web-frontend/src/components/organizer/EventPage/__tests__/EventNewsletterTab.test.tsx`
- `services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterSubscriberServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterEmailServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/NewsletterControllerIntegrationTest.java`

### New files (CR fixes — 2026-02-28)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterRecipientId.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterRecipient.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterRecipientRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/PatchMySubscriptionRequest.java`
- `services/event-management-service/src/main/resources/db/migration/V72__add_newsletter_subscriber_username_index.sql`

### Modified files (CR fixes — 2026-02-28)
- `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterEmailService.java` — added recipientRepository, extracted createSendAuditRecord/recordRecipient, removed @Transactional from sendNewsletter
- `services/event-management-service/src/main/java/ch/batbern/events/controller/NewsletterController.java` — totalActive→totalCount, PatchMySubscriptionRequest DTO
- `services/event-management-service/src/test/java/ch/batbern/events/controller/NewsletterControllerIntegrationTest.java` — 6 new auth/404 tests for event-scoped endpoints
- `web-frontend/src/services/newsletterService.ts` — patchMySubscription accepts language param
- `web-frontend/src/hooks/useNewsletter/useNewsletter.ts` — usePatchMySubscription mutation var changed to {subscribed, language}
- `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx` — pass i18n.language in handleToggle

### Modified files (Tasks 12-19 wiring)
- `web-frontend/src/pages/public/HomePage.tsx` — added NewsletterSubscribeWidget in footer
- `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx` — added Newsletter toggle in Notifications tab
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` — added newsletter tab
- `web-frontend/src/App.tsx` — added /unsubscribe route
- `web-frontend/public/locales/en/events.json` — added newsletter.* and eventPage.newsletter.* keys
- `web-frontend/public/locales/de/events.json` — added newsletter.* and eventPage.newsletter.* keys

---

## Change Log
- 2026-03-10: Robustness addendum implemented (Amelia / dev agent):
  - P0 bug fix: replaced per-subscriber @Async dispatch (pool overflow → ~2890 of 3000 emails silently dropped) with single @Async background job + paginated processing
  - V87 migration adds status tracking columns to newsletter_sends; backfills existing rows as COMPLETED
  - Added sendHtmlEmailSync() to shared-kernel EmailService for use within background threads
  - Added duplicate-send prevention (409 on IN_PROGRESS), SES rate limiting (70ms/email), paginated processing (50/page)
  - Added retry capability for PARTIAL/FAILED sends (POST /sends/{sendId}/retry)
  - Added progress polling endpoint (GET /sends/{sendId}/status) + frontend LinearProgress UI
  - All tests green: 19/19 unit, 26/26 integration, 15/15 frontend; 0 checkstyle/typecheck errors
  - Plan saved to docs/plans/newsletter-robustness-v87-plan.md
- 2026-02-25: Story picked up for development (Amelia / dev agent)
- 2026-02-28: Code review fixes applied (Amelia / dev agent — CR session):
  - C1: Created NewsletterRecipientId.java + NewsletterRecipient.java + NewsletterRecipientRepository.java; wired recipientRepository into NewsletterEmailService.sendNewsletter() to populate newsletter_recipients per AC10
  - C1/M1: Extracted createSendAuditRecord() + recordRecipient() as @Transactional helpers; removed @Transactional from sendNewsletter() to prevent long-held DB connection during bulk SMTP sends
  - C2: Changed NewsletterController.listSubscribers() response key totalActive → totalCount to match frontend SubscriberCountResponse interface (subscriber count was always 0)
  - H1: Created PatchMySubscriptionRequest.java DTO with @NotNull Boolean subscribed; replaced raw Map<String,Object> in PATCH /newsletter/my-subscription for proper validation
  - H2: Added 6 integration tests to NewsletterControllerIntegrationTest covering send/preview/history auth (403) and unknown event (404) — total: 17 tests
  - H3: Updated patchMySubscription() in newsletterService.ts + usePatchMySubscription() hook to accept and forward language; UserSettingsTab.NewsletterSection now passes i18n.language
  - M2: Created V72__add_newsletter_subscriber_username_index.sql partial index on newsletter_subscribers.username
  - M3: Fixed story Status inconsistency (footer now matches header: review)

---

## Status

review
