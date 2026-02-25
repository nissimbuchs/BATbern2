# Story 10.7: Newsletter Subscription & Sending

Status: ready-for-dev

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
- Both are seeded into the DB by `EmailTemplateSeedService` under category `NEWSLETTER`
- DB subject pattern: `"{{reminderPrefix}}{{eventTitle}} — BATbern"` (DE) / `"{{reminderPrefix}}{{eventTitle}} — BATbern"` (EN)
- Template variables supported:
  - `{{reminderPrefix}}` — `""` for newsletter, `"Erinnerung: "` / `"Reminder: "` for reminder
  - `{{eventTitle}}`, `{{eventDate}}`, `{{eventTime}}`, `{{venue}}`, `{{topic}}`
  - `{{speakersSection}}` — rendered HTML list of confirmed speakers (omitted/empty when not yet published)
  - `{{registrationLink}}` — `{baseUrl}/register/{eventCode}`
  - `{{unsubscribeLink}}` — unique per recipient: `{baseUrl}/unsubscribe?token={subscriber.unsubscribeToken}`
  - `{{preferencesLink}}` — `{baseUrl}/account`
- Every rendered email MUST contain a visible "Unsubscribe" link in the footer (GDPR requirement)
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

### Newsletter template design (content fragment)

The templates are content-only (no HTML shell). The `batbern-default` layout provides header/footer/CSS. Template body should contain:

```html
<h2>{{reminderPrefix}}{{eventTitle}}</h2>
<p><strong>📅 {{eventDate}}</strong> um {{eventTime}} Uhr | 📍 {{venue}}</p>
<p><strong>Thema:</strong> {{topic}}</p>

{{speakersSection}}

<p><a href="{{registrationLink}}" class="cta-button">Jetzt anmelden</a></p>

<p style="font-size: 12px; color: #999;">
  Sie erhalten diese E-Mail, weil Sie den BATbern-Newsletter abonniert haben.<br>
  <a href="{{unsubscribeLink}}">Newsletter abbestellen</a> |
  <a href="{{preferencesLink}}">Einstellungen verwalten</a>
</p>
```

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
