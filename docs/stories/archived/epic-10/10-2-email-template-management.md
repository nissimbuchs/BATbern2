# Story 10.2: Email Template Management

Status: ready

## Story

As an **organizer**,
I want to view and edit email templates directly from the Administration page,
so that I can update email content and wording without requiring a code deploy,
and so that all emails automatically reflect the latest BATbern branding through a shared layout template.

## Context

All 22 outgoing email templates are currently hardcoded classpath HTML files in the event-management-service. Each file is a full standalone HTML document — it contains its own `<head>` CSS (colors, fonts, button styles), a header section, the email-specific content, and a shared footer. This means:

- Changing a subject line or paragraph requires a code deploy
- Updating the BATbern logo or color scheme requires editing all 22 files individually
- There is no management UI — organizers cannot touch templates at all

This story introduces two concepts:

**1. DB-backed template storage** — 22 existing templates seeded from classpath, full CRUD REST API, editable via the Admin UI.

**2. L&F (Look & Feel) layout templates** — a separate `batbern-default` layout template owns the full HTML shell: `<html>`, `<head>` with all CSS, BATbern logo/header, and footer. Individual content templates contain only the email-specific body (greeting, details, CTAs). At send time, content is injected into the layout at the `{{content}}` placeholder before variable substitution.

```
At send time:
  1. Load content template  (e.g. speaker-invitation, locale=de)
  2. If layout_key is set: load layout template (e.g. batbern-default, locale=de)
  3. Merge:  layout.htmlBody.replace("{{content}}", contentTemplate.htmlBody)
  4. Substitute all {{variables}} on the merged result
  5. Send
```

**Phased adoption (Story 10.2):** The 22 existing system templates are seeded as standalone full-HTML (`layout_key=null`) — no migration required in this story. The `batbern-default` layout template is introduced as a new classpath file. All new custom templates created by organizers default to `layout_key='batbern-default'` and use content-only editing. Migration of the 22 system templates to content-only is deferred to Story 10.3.

**Editor split:** Layout templates (complex, technical, rarely changed) use Monaco Editor. Content templates (simple paragraphs + CTAs, frequently updated) use TinyMCE WYSIWYG.

**Prerequisite:** Story 10.1 must be complete — the `/organizer/admin` page with 3 tabs must exist before this story adds the 4th tab.

---

## Acceptance Criteria

### AC1 — Email Templates Backend

DB-backed `email_templates` table (Flyway V62) with layout support:

```sql
CREATE TABLE email_templates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  template_key  VARCHAR(100) NOT NULL,
  category      VARCHAR(50)  NOT NULL,  -- SPEAKER | REGISTRATION | TASK_REMINDER | LAYOUT
  locale        VARCHAR(5)   NOT NULL,  -- 'de' | 'en'
  subject       VARCHAR(500),           -- NULL for layout templates (they have no subject)
  html_body     TEXT         NOT NULL,
  variables     JSONB,
  is_layout     BOOLEAN      NOT NULL DEFAULT FALSE,
  layout_key    VARCHAR(100),           -- NULL = standalone; non-null = uses this layout
  is_system_template BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT email_templates_key_locale_unique UNIQUE (template_key, locale)
);
```

**Seed service** (`EmailTemplateSeedService`, `@PostConstruct`, idempotent):
- Reads all classpath HTML files from `email-templates/` directory
- Filename convention:
  - `{key}-{locale}.html` → content template (`is_layout=false`, category derived from key prefix)
  - `layout-{key}-{locale}.html` → layout template (`is_layout=true`, `category='LAYOUT'`)
- Seeds 22 existing content templates as standalone (`layout_key=null`, `is_layout=false`)
- Seeds 2 new layout templates from `layout-batbern-default-de.html` + `layout-batbern-default-en.html` (`is_layout=true`, `layout_key=null`)
- Inserts only if `(template_key, locale)` not yet in DB; updates nothing (idempotent)
- Seed failure: log ERROR per template, continue seeding remaining templates

**Category derivation from key prefix:**
- `speaker-*` → SPEAKER
- `registration-*` → REGISTRATION
- `task-reminder-*` → TASK_REMINDER
- `layout-*` → LAYOUT (strip the `layout-` prefix to get the actual `template_key`)

**REST API** at `/api/v1/email-templates` (ORGANIZER role, ADR-003 meaningful path identifiers):
- `GET /api/v1/email-templates?category=&isLayout=` — list with optional filters
- `GET /api/v1/email-templates/{templateKey}/{locale}` — single template
- `POST /api/v1/email-templates` — create custom template
- `PUT /api/v1/email-templates/{templateKey}/{locale}` — update subject/htmlBody/layoutKey
- `DELETE /api/v1/email-templates/{templateKey}/{locale}` — custom only (400 for system templates)

**`EmailTemplateResponse`** includes: `templateKey`, `locale`, `category`, `subject`, `htmlBody`, `variables`, `isLayout`, `layoutKey`, `isSystemTemplate`, `updatedAt`

**Email senders** (`SpeakerInvitationEmailService`, `SpeakerReminderEmailService`, `SpeakerAcceptanceEmailService`, `RegistrationEmailService`) updated to:
1. Load content template from DB by `(templateKey, locale)`; fall back to classpath if absent
2. If `layoutKey` is set: load layout template from DB by `(layoutKey, locale)`; merge → `layout.htmlBody.replace("{{content}}", contentHtml)`
3. If layout DB lookup fails: log WARN + use content HTML directly (no layout — graceful degradation)
4. Apply all variable substitutions on the merged result
5. If double fallback fails (no DB + no classpath): log ERROR + use empty body (email sent with subject only)

**System templates** (`isSystemTemplate=true`) cannot be deleted (returns 400). Layout templates (`isLayout=true`) cannot be deleted regardless of `isSystemTemplate` (returns 400 — would break all linked content templates).

### AC2 — Email Templates Tab (4th tab on `/organizer/admin`)

A 4th tab "Email Templates" added to the `/organizer/admin` page (built in Story 10.1).

**Layout Templates section** (shown at top, visually distinct):
- Lists layout templates (currently just `batbern-default-de/en`)
- Edit button → `EmailTemplateEditModal` in **layout mode** — uses **Monaco Editor** (`@monaco-editor/react`) for the `htmlBody` field (full HTML with `{{content}}` placeholder)
- `{{content}}` placeholder shown prominently as a chip/badge so it's not accidentally deleted
- Cannot be deleted (system-level)
- No "create new layout" button in this story (single layout for now)

**Content Templates section**:
- Category filter: MUI ToggleButtonGroup (Speaker | Registration | Task Reminders)
- Language filter: DE | EN toggle (client-side filter)
- Template list: key, subject (truncated), last updated, [Preview] [Edit] [Delete if custom]
- `[+ New Template]` → `EmailTemplateEditModal` in **create mode**, `layoutKey` defaults to `'batbern-default'`

**`EmailTemplateEditModal`** — two modes:
- **Layout mode** (`isLayout=true`): subject field hidden; `htmlBody` edited with Monaco Editor (syntax highlighting, line numbers); `{{content}}` placeholder chip shown as warning if missing from body
- **Content mode** (`isLayout=false`): subject TextField (required, max 500 chars); `htmlBody` edited with **TinyMCE** (WYSIWYG with source-code toggle); detected `{{variable}}` chips for reference; `layoutKey` selector (dropdown showing available layout keys, default `batbern-default`)

**TinyMCE configuration** (content mode):
- Plugins: `code table lists link`
- Toolbar: `code | bold italic underline | bullist numlist | link | table`
- `entity_encoding: 'raw'` — preserves `{{variable}}` placeholders unescaped
- `valid_elements: '*[*]'` — allow all elements/attributes (email HTML classes must survive)
- `height: 400`

**`EmailTemplatePreviewModal`**:
- Fetches layout template (`batbern-default`, matching locale) and merges with content template's `htmlBody` client-side for preview
- Renders merged HTML in `<iframe srcDoc={mergedHtml} sandbox="allow-same-origin" title="Email Preview" />`
- Shows subject, `variables` chips, `layoutKey` badge
- For layout templates: previews the raw layout HTML with a `[CONTENT AREA]` placeholder shown

**Validation**: non-empty `htmlBody` required for all templates; non-empty `subject` required for content templates. `{{content}}` placeholder required in layout template body (show inline warning if absent, but still allow save). Delete confirmation uses `window.confirm()`.

---

## What Was Deliberately Cut

| Removed | Reason |
|---------|--------|
| Migration of 22 system templates to content-only | Deferred to Story 10.3 — system templates work as standalone; no user-visible regression |
| Multiple layout templates | Single `batbern-default` sufficient for BATbern's needs; multiple layouts can be added as custom templates |
| Newsletter category in UI | No seeded templates at launch; category reserved for future story |
| Rich text toolbar beyond basics | TinyMCE limited to bold/italic/lists/links/table — email templates don't need more |
| `layout_key` FK integrity constraint at DB level | Composite unique makes FK complex; application-level constraint sufficient |
| Template version history / rollback | Phase 3 |
| Audit log for template edits | Phase 3 |

---

## Tasks / Subtasks

### Task 1: Author Layout Classpath Files

Before any backend work: the developer must author the layout template HTML files. These will be seeded into the DB on first service startup.

- [ ] Create `services/event-management-service/src/main/resources/email-templates/layout-batbern-default-de.html`
  - Full `<!DOCTYPE html><html><head>` with all shared CSS (colors, fonts, button styles, `.email-container`, `.header`, `.cta-button`, `.footer`, `.event-details`, etc.)
  - Extract CSS from existing `speaker-invitation-de.html` as the baseline — it has the complete stylesheet
  - BATbern logo/header at top (use a `{{logoUrl}}` variable or hardcode a placeholder image URL)
  - `<div class="email-container">{{content}}</div>` — the injection point
  - Shared footer: navigation links (`{{dashboardLink}}`, `{{eventUrl}}`, `{{supportUrl}}`), copyright `&copy; {{currentYear}} BATbern`
  - Subject: none (layout has no subject)
- [ ] Create `services/event-management-service/src/main/resources/email-templates/layout-batbern-default-en.html`
  - Same structure; footer links use English labels

> **Note:** After authoring the layout files, verify that the CSS classes used by existing content templates (`.event-details`, `.session-info`, `.cta-button`, `.cta-accept`, `.cta-decline`, `.deadline-box`, `.contact-box`) are all present in the layout CSS. Content templates reference these classes without defining them.

---

### Task 2: Backend — Email Template Entity + API (AC: 1)

> **ADR-006 Contract-First Order**: OpenAPI spec written first (2a). Backend interfaces generated from spec.

#### 2a. OpenAPI spec (FIRST — ADR-006)
- [ ] Add to `docs/api/events.openapi.yml`:
  - Schemas: `EmailTemplateResponse` (with `isLayout`, `layoutKey`, nullable `subject`), `CreateEmailTemplateRequest` (with `layoutKey`), `UpdateEmailTemplateRequest` (with `layoutKey`)
  - Query params: `isLayout` boolean on `GET /api/v1/email-templates`
  - Endpoints: same 5 as before (GET list, GET single, POST, PUT, DELETE) — update paths to `/{templateKey}/{locale}`
- [ ] Regenerate frontend types: `cd web-frontend && npm run generate:api-types`
- [ ] Run `./gradlew :services:event-management-service:build` → generates `EmailTemplatesApi` interface

#### 2b. Flyway migration
- [ ] Create `V62__create_email_templates_table.sql` with the schema from AC1 (includes `is_layout`, `layout_key`, nullable `subject`)

#### 2c. Domain + Repository
- [ ] `EmailTemplate.java` JPA entity — fields: `id`, `templateKey`, `category`, `locale`, `subject` (nullable), `htmlBody`, `variables` (JSONB), `isLayout`, `layoutKey` (nullable), `isSystemTemplate`, `createdAt`, `updatedAt`
- [ ] `EmailTemplateRepository.java`:
  - `findByTemplateKeyAndLocale(String key, String locale): Optional<EmailTemplate>`
  - `findByCategory(String category): List<EmailTemplate>`
  - `findByIsLayoutTrue(): List<EmailTemplate>`

#### 2d. Seed service (TDD — test first)
- [ ] Write `EmailTemplateSeedServiceTest`:
  - Verify 22 content templates seeded (`is_layout=false`)
  - Verify 2 layout templates seeded from `layout-batbern-default-{de,en}.html` (`is_layout=true`, `category='LAYOUT'`)
  - Verify idempotency (second `@PostConstruct` call leaves DB unchanged)
- [ ] `EmailTemplateSeedService.java`:
  - Scans classpath `email-templates/*.html`
  - Filename `layout-{key}-{locale}.html` → `isLayout=true`, `templateKey=key`, `category='LAYOUT'`
  - Filename `{key}-{locale}.html` → `isLayout=false`, category from key prefix
  - All 22 existing: `layoutKey=null` (standalone)
  - Inserts only if `(templateKey, locale)` absent (idempotent); never updates existing rows
  - Seed failure: log ERROR per template, continue

#### 2e. Service (TDD — test first)
- [ ] Write `EmailTemplateServiceTest`
- [ ] `EmailTemplateService.java`:
  - `findAll(category?, isLayout?)` → list
  - `findByKeyAndLocale(key, locale)` → Optional
  - `create(CreateEmailTemplateRequest)` → validates: content templates must have subject; layout templates must not be `isSystemTemplate=true`
  - `update(templateKey, locale, UpdateEmailTemplateRequest)` → updates subject, htmlBody, layoutKey
  - `delete(templateKey, locale)` → 400 if `isSystemTemplate=true` OR `isLayout=true`

#### 2f. Mapper (ADR-006)
- [ ] `EmailTemplateMapper.java` in `service/mapper/` — pure mapper:
  - `toResponse(EmailTemplate) → EmailTemplateResponse`
  - `toEntity(CreateEmailTemplateRequest) → EmailTemplate`

#### 2g. Controller (TDD — integration test first)
- [ ] Write `EmailTemplateControllerIntegrationTest extends AbstractIntegrationTest`
  - Test: list returns 24 templates after seed (22 content + 2 layout)
  - Test: create content template with `layoutKey='batbern-default'`
  - Test: delete system template returns 400
  - Test: delete layout template returns 400
- [ ] `EmailTemplateController.java` — **implements generated `EmailTemplatesApi`**
  - `GET /api/v1/email-templates?category=&isLayout=`
  - `GET /api/v1/email-templates/{templateKey}/{locale}`
  - `POST /api/v1/email-templates`
  - `PUT /api/v1/email-templates/{templateKey}/{locale}`
  - `DELETE /api/v1/email-templates/{templateKey}/{locale}`

#### 2h. API Gateway routing verification (ADR-008)
- [ ] Verify `/api/v1/email-templates` routes to `event-management-service` in API Gateway config
- [ ] Add routing entry if not covered by existing wildcard

#### 2i. Update email senders
- [ ] `SpeakerInvitationEmailService` — load content template → if `layoutKey` set, load layout + merge → substitute variables → fall back to classpath if DB absent
- [ ] `SpeakerReminderEmailService` — same pattern
- [ ] `SpeakerAcceptanceEmailService` — same pattern
- [ ] `RegistrationEmailService` — same pattern
- [ ] Extract shared merger logic to `EmailTemplateService.mergeWithLayout(contentHtml, layoutKey, locale)` to avoid duplication across senders
- [ ] Update email service tests to mock DB lookup + verify merger is called when `layoutKey` is set

---

### Task 3: Frontend — Email Templates Tab (AC: 2)

#### 3a. Dependencies
- [ ] `npm install @monaco-editor/react @tinymce/tinymce-react`
- [ ] Add TinyMCE self-hosted or configure free-tier API key (register at tiny.cloud for domain)
- [ ] Run `npm run generate:api-types` (generated types from Task 2a)

#### 3b. Service + hooks
- [ ] Create `web-frontend/src/services/emailTemplateService.ts`
  - `listTemplates(params?: { category?: string; isLayout?: boolean })` — maps to `GET /api/v1/email-templates`
  - `getTemplate(templateKey, locale)` — maps to `GET /{templateKey}/{locale}`
  - `createTemplate(req)`, `updateTemplate(templateKey, locale, req)`, `deleteTemplate(templateKey, locale)`
  - Uses `apiClient`; URL paths ADR-003 compliant (`/{templateKey}/{locale}`)
- [ ] Create `web-frontend/src/hooks/useEmailTemplates.ts`
  - `useEmailTemplates(params?)` — query key `['emailTemplates', params]`
  - `useLayoutTemplates()` — convenience: `useEmailTemplates({ isLayout: true })`
  - `useUpdateEmailTemplate()`, `useDeleteEmailTemplate()`, `useCreateEmailTemplate()` mutations

#### 3c. EmailTemplateEditModal
- [ ] Create `web-frontend/src/components/organizer/Admin/EmailTemplateEditModal.tsx`
  - Props: `template?: EmailTemplateResponse` (edit) | undefined (create), `isLayoutMode: boolean`, `onClose: () => void`
  - **Layout mode** (`isLayoutMode=true`):
    - No subject field
    - `htmlBody` → `<Editor>` from `@monaco-editor/react`, language=`html`, height=500px, `wordWrap: 'on'`, `minimap: { enabled: false }`
    - Warning chip if `{{content}}` not found in body: `"Missing {{content}} placeholder — email body will be empty"`
  - **Content mode** (`isLayoutMode=false`):
    - `subject` TextField (required, max 500 chars)
    - `htmlBody` → `<Editor>` from `@tinymce/tinymce-react` (see TinyMCE config in AC2)
    - `layoutKey` Select — options from `useLayoutTemplates()` (shows `template_key` values, default `batbern-default`); option "None (standalone)" → `null`
    - Detected `{{var}}` chips (regex on body) shown as read-only reference
  - Save → `updateTemplate` or `createTemplate` → invalidate `['emailTemplates']` + close
  - Inline validation: htmlBody required; subject required for content mode

#### 3d. EmailTemplatePreviewModal
- [ ] Create `web-frontend/src/components/organizer/Admin/EmailTemplatePreviewModal.tsx`
  - Props: `template: EmailTemplateResponse`
  - For content templates with `layoutKey` set:
    - Fetches layout via `getTemplate(template.layoutKey, template.locale)`
    - Client-side merge: `layout.htmlBody.replace('{{content}}', template.htmlBody)`
    - Renders merged HTML in `<iframe srcDoc={mergedHtml} sandbox="allow-same-origin" />`
  - For standalone content templates or layout templates:
    - Renders `template.htmlBody` directly in iframe
  - Shows subject (if present), `variables` chips, `layoutKey` badge
  - For layout templates: replaces `{{content}}` in preview with a grey placeholder `<div>` so the layout structure is visible

#### 3e. EmailTemplatesTab
- [ ] Create `web-frontend/src/components/organizer/Admin/EmailTemplatesTab.tsx`
  - **Layout Templates section** (shown above the divider, distinct background):
    - Title: "Layout Templates" with info tooltip: "Defines the BATbern look & feel for all emails. Changes here affect all templates using this layout."
    - Lists layout templates from `useLayoutTemplates()`
    - Per row: key, locale chip, updated date, [Preview] [Edit]
    - No delete button, no "New Layout" button
  - **Content Templates section**:
    - Category filter: MUI ToggleButtonGroup (Speaker | Registration | Task Reminders)
    - Language filter: DE | EN toggle (client-side)
    - Template list: key, subject (truncated), layoutKey badge (`batbern-default` chip or "standalone"), updated date, [Preview] [Edit] [Delete if custom]
    - `[+ New Template]` → `EmailTemplateEditModal` in create mode (content, `layoutKey='batbern-default'`)
- [ ] Add 4th tab "Email Templates" to `EventManagementAdminPage` — renders `<EmailTemplatesTab />`
- [ ] i18n: `emailTemplates.*` keys in `de/organizer.json` + `en/organizer.json`:
  ```json
  {
    "emailTemplates": {
      "title": "Email Templates",
      "layoutTemplates": "Layout Templates",
      "layoutTemplatesInfo": "Definiert das Look & Feel für alle BATbern-E-Mails.",
      "contentTemplates": "E-Mail-Inhalte",
      "categories": {
        "SPEAKER": "Referenten-E-Mails",
        "REGISTRATION": "Registrierungs-E-Mails",
        "TASK_REMINDER": "Aufgaben-Erinnerungen"
      },
      "layoutKey": "Layout",
      "standalone": "Eigenständig",
      "systemTemplate": "System-Vorlage",
      "subject": "Betreff",
      "htmlBody": "HTML-Inhalt",
      "variables": "Verfügbare Variablen",
      "preview": "Vorschau",
      "edit": "Bearbeiten",
      "newTemplate": "+ Neue Vorlage",
      "missingContentPlaceholder": "{{content}}-Platzhalter fehlt — E-Mail-Inhalt wird leer sein",
      "cannotDeleteSystem": "System-Vorlagen können nicht gelöscht werden",
      "cannotDeleteLayout": "Layout-Vorlagen können nicht gelöscht werden"
    }
  }
  ```

---

## Key Components

| Asset | Role |
|-------|------|
| `@monaco-editor/react` | Layout template HTML editing (NEW dependency) |
| `@tinymce/tinymce-react` | Content template WYSIWYG editing (NEW dependency) |
| `EventManagementAdminPage.tsx` (Story 10.1) | Add 4th tab |
| `AbstractIntegrationTest` | Email template controller integration tests |
| `apiClient` | All new HTTP calls |
| `EmailService` (shared-kernel) | No change — individual senders updated |

---

## Backend Files (event-management-service)

**New classpath files (authored in Task 1 — before any backend code):**
```
src/main/resources/email-templates/
├── layout-batbern-default-de.html                               NEW  ← authored by developer
└── layout-batbern-default-en.html                               NEW  ← authored by developer
```

**New Java files:**
```
src/main/java/ch/batbern/events/
├── domain/EmailTemplate.java                                    NEW
├── repository/EmailTemplateRepository.java                      NEW
├── service/EmailTemplateService.java                            NEW  ← includes mergeWithLayout()
├── service/EmailTemplateSeedService.java                        NEW
├── service/mapper/EmailTemplateMapper.java                      NEW  ← ADR-006
└── controller/EmailTemplateController.java                      NEW  ← implements EmailTemplatesApi
└── dto/  ← GENERATED from OpenAPI spec (not hand-coded)
src/main/resources/db/migration/
└── V62__create_email_templates_table.sql                        NEW
```

**Modified:**
- `docs/api/events.openapi.yml` — new schemas + endpoints (FIRST — ADR-006)
- `service/SpeakerInvitationEmailService.java` — DB lookup + layout merger
- `service/SpeakerReminderEmailService.java` — DB lookup + layout merger
- `service/SpeakerAcceptanceEmailService.java` — DB lookup + layout merger
- `service/RegistrationEmailService.java` — DB lookup + layout merger

---

## Frontend Files

**New:**
```
web-frontend/src/
├── services/emailTemplateService.ts                             NEW
├── hooks/useEmailTemplates.ts                                   NEW
└── components/organizer/Admin/
    ├── EmailTemplatesTab.tsx                                    NEW
    ├── EmailTemplatePreviewModal.tsx                            NEW
    └── EmailTemplateEditModal.tsx                               NEW  ← Monaco (layout) + TinyMCE (content)
```

**Modified:**
- `src/pages/organizer/EventManagementAdminPage.tsx` — add 4th "Email Templates" tab
- `public/locales/de/organizer.json` + `en/organizer.json` — `emailTemplates.*` keys
- `package.json` — `@monaco-editor/react`, `@tinymce/tinymce-react`

---

## Testing Strategy

### Backend (TDD — red first)
```bash
./gradlew :services:event-management-service:test --tests "EmailTemplateSeedServiceTest" 2>&1 | tee /tmp/seed-test.log
./gradlew :services:event-management-service:test --tests "EmailTemplateControllerIntegrationTest" 2>&1 | tee /tmp/email-ctrl-test.log
./gradlew :services:event-management-service:test --tests "SpeakerInvitationEmailServiceTest" 2>&1 | tee /tmp/invitation-test.log
```

### Frontend
```bash
cd web-frontend && npm run type-check 2>&1 | tee /tmp/typecheck.log
npm test -- --run 2>&1 | tee /tmp/fe-tests.log
```

### Manual verification
1. Navigate to `/organizer/admin` → tab 3 → 4 tabs now visible
2. Layout Templates section shows `batbern-default-de` and `batbern-default-en`
3. Edit `batbern-default-de` → Monaco editor opens with full HTML; `{{content}}` visible; save → persists
4. [Preview] layout template → shows HTML shell with grey `[CONTENT AREA]` placeholder
5. Content template [Edit] → TinyMCE opens; subject + body editable; `layoutKey` shows `batbern-default`
6. Content template [Preview] → iframe shows fully merged branded email (layout shell + content)
7. Update subject of `speaker-invitation-de` → save → trigger a speaker invitation → email subject reflects update
8. [+ New Template] → TinyMCE opens in create mode; `layoutKey` defaults to `batbern-default`; save → new template appears in list; preview shows branded email
9. [Delete] custom template → `window.confirm()` → removed; [Delete] system template → error toast
10. Restart service → 24 templates still present (22 content + 2 layout), no duplicates (idempotent)
11. Delete `batbern-default-de` → 400 (layout deletion blocked)

---

## References

- Epic 10: `docs/prd/epic-10-additional-stories.md`
- Story 10.1 (prerequisite): `docs/stories/archived/epic-10/10-1-event-management-administration-page.md`
- Implementation Readiness Report: `docs/implementation-readiness-report-2026-02-24.md`
- `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md`
- `docs/architecture/ADR-006-openapi-contract-first-code-generation.md`
- `docs/architecture/ADR-008-simplified-api-gateway-routing.md`
- `docs/guides/openapi-code-generation.md`
- `docs/guides/flyway-migration-guide.md` — migration naming (V62)
- Email template classpath resources: `services/event-management-service/src/main/resources/email-templates/`
- Existing template CSS reference: `speaker-invitation-de.html` — use as baseline for layout extraction
