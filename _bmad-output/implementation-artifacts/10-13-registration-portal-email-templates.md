# Story 10.13: Registration & Portal Email Templates — Editable in Admin

Status: ready-for-dev

<!-- Prerequisite: Story 10.2 (Email Template Management — DB storage, seed service, admin UI)
     Soft dependency: Stories 10.11 and 10.12 should be merged first so their classpath
     template files exist for seeding; Story 10.13 will still work independently via
     deriveCategory() fix alone. -->

## Story

As an **organizer**,
I want the registration confirmation email and all speaker portal emails to be editable from the Email Templates admin tab — just like speaker invitation emails —
so that I can update their subject and content without a code deploy.

## Acceptance Criteria

1. **AC1 — `deriveCategory()` extended**: `EmailTemplateSeedService.deriveCategory()` updated to handle three new prefixes:
   - `waitlist-` → `"REGISTRATION"` (covers `waitlist-promotion-*` from Story 10.11)
   - `deregistration-` → `"REGISTRATION"` (covers `deregistration-link-*` from Story 10.12)
   - `portal-registration` → `"SPEAKER"` (new portal account creation email)

2. **AC2 — `portal-registration` classpath templates created**:
   - `portal-registration-de.html` and `portal-registration-en.html` in `email-templates/`
   - Each starts with `<!-- subject: ... -->` comment (required by test `should_seedFromClasspath_andFindLayoutTemplates` which asserts all content templates have a subject)
   - Content-only HTML (no `<html>`/`<body>` — merged with `batbern-default` layout at render time)
   - Variables: `{{recipientName}}`, `{{portalUrl}}`, `{{eventTitle}}`, `{{eventDate}}`

3. **AC3 — All registration/portal templates visible in admin tab REGISTRATION / SPEAKER filter**:
   - `registration-confirmation-de/en` — already seeded and visible (prefix `registration-` maps correctly; no code change needed)
   - `registration-waitlist-confirmation-de/en` — visible after 10.11 + AC1 fix (prefix `registration-` → already correct)
   - `deregistration-link-de/en` — visible after 10.12 + AC1 fix
   - `waitlist-promotion-de/en` — visible after 10.11 + AC1 fix
   - `portal-registration-de/en` — visible under SPEAKER filter (AC2)

4. **AC4 — `EmailTemplateEditModal` already supports REGISTRATION templates**: No changes needed — the modal's TinyMCE editor works for any content template; preview merges with `batbern-default` layout.

5. **AC5 — Quick-edit drawer from EventParticipantsTab**:
   - New `EmailTemplateQuickEditDrawer.tsx` component (right-side MUI Drawer, 480px wide)
   - `EventParticipantsTab.tsx` gains 3 icon-buttons top-right:
     - Edit Registration Email → opens drawer with `registration-confirmation-{locale}`
     - Edit Waitlist Confirmation → opens drawer with `registration-waitlist-confirmation-{locale}`
     - Edit Deregistration Email → opens drawer with `deregistration-link-{locale}`
   - Drawer header: template key label + DE/EN locale toggle + "Open in Admin" link
   - Drawer body: subject text field (read-only display) + TinyMCE editor + Save button

6. **AC6 — Idempotency preserved**: All new classpath templates are only seeded if `(templateKey, locale)` not already in DB (existing `existsByTemplateKeyAndLocale()` check — no change needed).

7. **AC7 — TDD compliance**: New `deriveCategory()` test cases added to `EmailTemplateSeedServiceTest.java` BEFORE modifying the service (RED → GREEN). Existing tests must continue to pass.

8. **AC8 — i18n**: Icon-button tooltips and drawer heading use `emailTemplates.quickEdit.*` keys in `en/organizer.json` and `de/organizer.json`. No hardcoded strings. `npm run type-check` passes; `npm run lint` passes.

---

## Tasks / Subtasks

### Phase 1: Backend — TDD first on `deriveCategory()`

- [ ] **T1 — Add `deriveCategory()` tests FIRST (RED phase)** (AC: #1, #7)
  - [ ] T1.1 — Open `services/event-management-service/src/test/java/ch/batbern/events/service/EmailTemplateSeedServiceTest.java`
  - [ ] T1.2 — Add test: `should_deriveCategory_forWaitlistKey`:
    ```java
    @Test
    @DisplayName("should derive REGISTRATION category for waitlist-* template keys")
    void should_deriveCategory_forWaitlistKey() {
        assertThat(emailTemplateSeedService.deriveCategory("waitlist-promotion")).isEqualTo("REGISTRATION");
        assertThat(emailTemplateSeedService.deriveCategory("waitlist-confirmation")).isEqualTo("REGISTRATION");
    }
    ```
  - [ ] T1.3 — Add test: `should_deriveCategory_forDeregistrationKey`:
    ```java
    @Test
    @DisplayName("should derive REGISTRATION category for deregistration-* template keys")
    void should_deriveCategory_forDeregistrationKey() {
        assertThat(emailTemplateSeedService.deriveCategory("deregistration-link")).isEqualTo("REGISTRATION");
    }
    ```
  - [ ] T1.4 — Add test: `should_deriveCategory_forPortalRegistrationKey`:
    ```java
    @Test
    @DisplayName("should derive SPEAKER category for portal-registration template key")
    void should_deriveCategory_forPortalRegistrationKey() {
        assertThat(emailTemplateSeedService.deriveCategory("portal-registration")).isEqualTo("SPEAKER");
    }
    ```
  - [ ] T1.5 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests EmailTemplateSeedServiceTest 2>&1 | tee /tmp/test-10-13-red.log && grep -E "FAILED|BUILD" /tmp/test-10-13-red.log`

- [ ] **T2 — Fix `EmailTemplateSeedService.deriveCategory()`** (AC: #1, #7)
  - [ ] T2.1 — Open `services/event-management-service/src/main/java/ch/batbern/events/service/EmailTemplateSeedService.java`
  - [ ] T2.2 — In `deriveCategory(String templateKey)`, add before the final `return "LAYOUT"`:
    ```java
    if (templateKey.startsWith("waitlist-")) {
        return "REGISTRATION";
    }
    if (templateKey.startsWith("deregistration-")) {
        return "REGISTRATION";
    }
    if (templateKey.startsWith("portal-registration")) {
        return "SPEAKER";
    }
    ```
    **ORDER MATTERS**: Add the `portal-registration` check BEFORE any generic `portal-` check to avoid future ambiguity.
  - [ ] T2.3 — Run tests GREEN: `./gradlew :services:event-management-service:test --tests EmailTemplateSeedServiceTest 2>&1 | tee /tmp/test-10-13-green.log && grep -E "BUILD|FAILED|passed" /tmp/test-10-13-green.log`

### Phase 2: Backend — classpath templates for portal-registration

- [ ] **T3 — Create `portal-registration-de.html`** (AC: #2, #6)
  - [ ] T3.1 — Create `services/event-management-service/src/main/resources/email-templates/portal-registration-de.html`
  - [ ] T3.2 — Content:
    ```html
    <!-- subject: Willkommen im BATbern Speaker Portal -->
    <h2>Willkommen im BATbern Speaker Portal</h2>

    <p>Hallo {{recipientName}},</p>

    <p>Ihr BATbern Speaker Portal Konto wurde erfolgreich erstellt. Sie können sich ab sofort über den folgenden Link einloggen:</p>

    <div style="text-align: center; margin: 24px 0;">
        <a href="{{portalUrl}}" class="cta-button">Speaker Portal öffnen</a>
    </div>

    <div class="event-details">
        <h2>{{eventTitle}}</h2>
        <div class="detail-row">
            <span class="detail-label">Datum:</span>
            <span class="detail-value">{{eventDate}}</span>
        </div>
    </div>

    <p style="font-size: 12px; color: #666; margin-top: 20px;">
        Falls der Button nicht funktioniert, kopieren Sie bitte diesen Link:<br>
        <a href="{{portalUrl}}" style="color: #0066cc; word-break: break-all;">{{portalUrl}}</a>
    </p>

    <p>Mit freundlichen Grüssen,<br>
    <strong>Das BATbern Team</strong></p>
    ```

- [ ] **T4 — Create `portal-registration-en.html`** (AC: #2, #6)
  - [ ] T4.1 — Create `services/event-management-service/src/main/resources/email-templates/portal-registration-en.html`
  - [ ] T4.2 — Content:
    ```html
    <!-- subject: Welcome to the BATbern Speaker Portal -->
    <h2>Welcome to the BATbern Speaker Portal</h2>

    <p>Hello {{recipientName}},</p>

    <p>Your BATbern Speaker Portal account has been created. You can now log in using the following link:</p>

    <div style="text-align: center; margin: 24px 0;">
        <a href="{{portalUrl}}" class="cta-button">Open Speaker Portal</a>
    </div>

    <div class="event-details">
        <h2>{{eventTitle}}</h2>
        <div class="detail-row">
            <span class="detail-label">Date:</span>
            <span class="detail-value">{{eventDate}}</span>
        </div>
    </div>

    <p style="font-size: 12px; color: #666; margin-top: 20px;">
        If the button above doesn't work, copy this link into your browser:<br>
        <a href="{{portalUrl}}" style="color: #0066cc; word-break: break-all;">{{portalUrl}}</a>
    </p>

    <p>Kind regards,<br>
    <strong>The BATbern Team</strong></p>
    ```

- [ ] **T5 — Backend test: verify portal-registration seeds as SPEAKER** (AC: #2, #3, #6)
  - [ ] T5.1 — Add to `EmailTemplateSeedServiceTest.java`:
    ```java
    @Test
    @DisplayName("should seed portal-registration templates with SPEAKER category from classpath")
    void should_seedPortalRegistration_withSpeakerCategory() {
        emailTemplateSeedService.seedTemplatesFromClasspath();
        verify(emailTemplateRepository, org.mockito.Mockito.atLeast(1))
                .save(templateCaptor.capture());
        List<EmailTemplate> portalTemplates = templateCaptor.getAllValues().stream()
                .filter(t -> "portal-registration".equals(t.getTemplateKey()))
                .toList();
        assertThat(portalTemplates).hasSize(2); // de + en
        assertThat(portalTemplates).allMatch(t -> "SPEAKER".equals(t.getCategory()));
        assertThat(portalTemplates).allMatch(t -> t.getSubject() != null && !t.getSubject().isBlank());
    }
    ```
  - [ ] T5.2 — Also update the existing `contentCount >= 18` assertion (line 189) since we're adding 2 more templates. Update to: `assertThat(contentCount).isGreaterThanOrEqualTo(20);`
  - [ ] T5.3 — Run full backend test suite: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-13-backend.log && grep -E "BUILD|FAILED|passed|tests" /tmp/test-10-13-backend.log`

### Phase 3: Frontend — add `useEmailTemplate` hook

- [ ] **T6 — Add `useEmailTemplate` (single template) hook** (AC: #5)
  - [ ] T6.1 — Open `web-frontend/src/hooks/useEmailTemplates.ts`
  - [ ] T6.2 — Add after the existing hooks:
    ```typescript
    export function useEmailTemplate(templateKey: string, locale: string) {
      return useQuery({
        queryKey: [QUERY_KEY_BASE, templateKey, locale],
        queryFn: () => emailTemplateService.getTemplate(templateKey, locale),
        enabled: !!templateKey && !!locale,
      });
    }
    ```
  - [ ] T6.3 — Export `useEmailTemplate` is automatically included via the existing `export function` pattern (no barrel change needed)

### Phase 4: Frontend — `EmailTemplateQuickEditDrawer.tsx`

- [ ] **T7 — Create `EmailTemplateQuickEditDrawer.tsx`** (AC: #5)
  - [ ] T7.1 — Create `web-frontend/src/components/organizer/EventPage/EmailTemplateQuickEditDrawer.tsx`
  - [ ] T7.2 — Props interface:
    ```typescript
    interface EmailTemplateQuickEditDrawerProps {
      open: boolean;
      onClose: () => void;
      templateKey: string;
      eventCode: string;
    }
    ```
  - [ ] T7.3 — State: `locale: 'de' | 'en'` (initialises to `'de'`)
  - [ ] T7.4 — Data loading: `useEmailTemplate(templateKey, locale)` — loads the specific template when drawer opens
  - [ ] T7.5 — Mutation: `useUpdateEmailTemplate()` — saves changes
  - [ ] T7.6 — Local state: `htmlBody: string` (mirrored from loaded template, editable)
  - [ ] T7.7 — Layout:
    ```tsx
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      sx={{ '& .MuiDrawer-paper': { width: 480 } }}
    >
      <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Header */}
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
          <Typography variant="subtitle1" fontWeight={600}>
            {t('emailTemplates.quickEdit.title', { templateKey })}
          </Typography>
          <Stack direction="row" spacing={1}>
            <ToggleButtonGroup value={locale} exclusive onChange={(_, v) => { if (v) setLocale(v); }} size="small">
              <ToggleButton value="de">DE</ToggleButton>
              <ToggleButton value="en">EN</ToggleButton>
            </ToggleButtonGroup>
            <IconButton onClick={onClose} size="small"><CloseIcon /></IconButton>
          </Stack>
        </Stack>

        {/* Open in Admin link */}
        <Link
          href={`/organizer/admin?tab=email-templates`}
          variant="caption"
          color="text.secondary"
          sx={{ mb: 2, display: 'block' }}
        >
          {t('emailTemplates.quickEdit.openInAdmin')} ↗
        </Link>

        {/* Editor body */}
        {isLoading ? <BATbernLoader /> : (
          <Box sx={{ flex: 1, overflow: 'hidden' }}>
            <Editor  /* TinyMCE */
              value={htmlBody}
              onEditorChange={(content) => setHtmlBody(content)}
              init={{ height: 380, menubar: false, plugins: ['link', 'lists'], toolbar: 'bold italic | link | bullist numlist' }}
            />
          </Box>
        )}

        {/* Footer */}
        <Stack direction="row" justifyContent="flex-end" spacing={1} sx={{ mt: 2 }}>
          <Button onClick={onClose}>{t('common:actions.cancel')}</Button>
          <Button variant="contained" onClick={handleSave} disabled={isSaving}>
            {isSaving ? <CircularProgress size={18} /> : t('common:actions.save')}
          </Button>
        </Stack>
      </Box>
    </Drawer>
    ```
  - [ ] T7.8 — `handleSave()`: calls `updateMutation.mutateAsync({ templateKey, locale, req: { htmlBody, subject: template.subject } })` → invalidates queries → shows snackbar success → does NOT call `onClose` (user may want to keep editing)
  - [ ] T7.9 — Reset `htmlBody` to loaded template content whenever `templateKey` or `locale` changes (use `useEffect([template?.htmlBody])`)
  - [ ] T7.10 — Add `data-testid="email-template-quick-edit-drawer"` to root `<Drawer>` element

### Phase 5: Frontend — Update `EventParticipantsTab.tsx`

- [ ] **T8 — Update `EventParticipantsTab.tsx`** (AC: #5)
  - [ ] T8.1 — Read the current file at `web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx` fully before modifying
  - [ ] T8.2 — Add imports:
    ```typescript
    import { useState } from 'react';
    import { IconButton, Tooltip, Stack } from '@mui/material';
    import { Email as EmailIcon } from '@mui/icons-material';
    import { EmailTemplateQuickEditDrawer } from './EmailTemplateQuickEditDrawer';
    ```
  - [ ] T8.3 — Add state:
    ```typescript
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [drawerTemplateKey, setDrawerTemplateKey] = useState('');
    const openDrawer = (templateKey: string) => {
      setDrawerTemplateKey(templateKey);
      setDrawerOpen(true);
    };
    ```
  - [ ] T8.4 — In the header `Stack`, add a second `Stack direction="row"` on the right side with 3 icon-buttons:
    ```tsx
    <Stack direction="row" spacing={0.5}>
      <Tooltip title={t('emailTemplates.quickEdit.editRegistrationEmail')}>
        <IconButton size="small" onClick={() => openDrawer('registration-confirmation')} data-testid="edit-registration-email-btn">
          <EmailIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t('emailTemplates.quickEdit.editWaitlistEmail')}>
        <IconButton size="small" onClick={() => openDrawer('registration-waitlist-confirmation')} data-testid="edit-waitlist-email-btn">
          <EmailIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t('emailTemplates.quickEdit.editDeregistrationEmail')}>
        <IconButton size="small" onClick={() => openDrawer('deregistration-link')} data-testid="edit-deregistration-email-btn">
          <EmailIcon fontSize="small" />
        </IconButton>
      </Tooltip>
    </Stack>
    ```
  - [ ] T8.5 — Add drawer at bottom of JSX (before closing `</Box>`):
    ```tsx
    <EmailTemplateQuickEditDrawer
      open={drawerOpen}
      onClose={() => setDrawerOpen(false)}
      templateKey={drawerTemplateKey}
      eventCode={event.eventCode}
    />
    ```
  - [ ] T8.6 — Note: `useTranslation` is already imported with `'events'` namespace; add `'organizer'` namespace to access `emailTemplates.quickEdit.*` keys:
    ```typescript
    const { t } = useTranslation(['events', 'organizer']);
    // ...usage: t('organizer:emailTemplates.quickEdit.editRegistrationEmail')
    ```

### Phase 6: i18n

- [ ] **T9 — English i18n keys** (AC: #8)
  - [ ] T9.1 — Add to `web-frontend/public/locales/en/organizer.json` (under `emailTemplates` key, merge with existing object if present or create new):
    ```json
    "emailTemplates": {
      "quickEdit": {
        "title": "Email Template: {{templateKey}}",
        "openInAdmin": "Open in Admin",
        "editRegistrationEmail": "Edit Registration Confirmation Email",
        "editWaitlistEmail": "Edit Waitlist Confirmation Email",
        "editDeregistrationEmail": "Edit Deregistration Email"
      }
    }
    ```
  - [ ] T9.2 — Check if `emailTemplates` key already exists in `organizer.json` (it does NOT based on analysis — file only has keys: `navigation`, `topicBacklog`, `speakerBrainstorm`, etc.). Add as new top-level key.

- [ ] **T10 — German i18n keys** (AC: #8)
  - [ ] T10.1 — Add to `web-frontend/public/locales/de/organizer.json`:
    ```json
    "emailTemplates": {
      "quickEdit": {
        "title": "E-Mail-Vorlage: {{templateKey}}",
        "openInAdmin": "In Admin öffnen",
        "editRegistrationEmail": "Registrierungsbestätigung bearbeiten",
        "editWaitlistEmail": "Wartelisten-Bestätigung bearbeiten",
        "editDeregistrationEmail": "Abmeldungs-E-Mail bearbeiten"
      }
    }
    ```

- [ ] **T11 — Other locale placeholders** (AC: #8)
  - [ ] T11.1 — Add `[MISSING]` prefix translations to all 8 other locale files:
    - `fr/organizer.json`, `it/organizer.json`, `rm/organizer.json`, `es/organizer.json`
    - `fi/organizer.json`, `nl/organizer.json`, `ja/organizer.json`, `gsw-BE/organizer.json`
  - [ ] T11.2 — Template (copy de values with `[MISSING]` prefix for each):
    ```json
    "emailTemplates": {
      "quickEdit": {
        "title": "[MISSING] E-Mail-Vorlage: {{templateKey}}",
        "openInAdmin": "[MISSING] In Admin öffnen",
        "editRegistrationEmail": "[MISSING] Registrierungsbestätigung bearbeiten",
        "editWaitlistEmail": "[MISSING] Wartelisten-Bestätigung bearbeiten",
        "editDeregistrationEmail": "[MISSING] Abmeldungs-E-Mail bearbeiten"
      }
    }
    ```

### Phase 7: Final verification

- [ ] **T12 — Full frontend tests** (AC: #8)
  - [ ] T12.1 — `cd web-frontend && npm run test -- --run 2>&1 | tee /tmp/test-10-13-frontend.log && grep -E "pass|fail|error" /tmp/test-10-13-frontend.log | tail -20`
  - [ ] T12.2 — `npm run type-check 2>&1 | tee /tmp/typecheck-10-13.log && grep -E "error|Error" /tmp/typecheck-10-13.log | head -20`
  - [ ] T12.3 — `npm run lint 2>&1 | tee /tmp/lint-10-13.log && grep -E "error|warning" /tmp/lint-10-13.log | head -20`

---

## Dev Notes

### Architecture Compliance

**ADR-006 (Contract-First)**: No API changes in this story — all endpoints already exist. Email template API (`GET/PUT /api/v1/email-templates/{key}/{locale}`) was introduced in Story 10.2. No OpenAPI spec update needed.

**ADR-003 (Meaningful Identifiers)**: Template keys follow kebab-case pattern (e.g., `registration-confirmation`, `waitlist-promotion`). Consistent with all existing template keys.

**TDD Mandate**: Write new `deriveCategory()` unit test cases FIRST (T1), then modify `EmailTemplateSeedService` (T2). Tests run as fast pure unit tests — no Spring context needed.

### Critical Implementation Details

#### `deriveCategory()` — Pattern Check Order Is Critical
The `deriveCategory()` method uses `startsWith()` checks in order. The `portal-registration` check must come BEFORE any future `portal-` generic check. Add the three new branches in this exact order:
```java
if (templateKey.startsWith("waitlist-")) return "REGISTRATION";
if (templateKey.startsWith("deregistration-")) return "REGISTRATION";
if (templateKey.startsWith("portal-registration")) return "SPEAKER";
// ... existing return "LAYOUT" at end
```
The `registration-*` prefix (for `registration-waitlist-confirmation-*`) already maps correctly — no change needed for that prefix.

#### EmailTemplatesTab.tsx — REGISTRATION Filter Already Present
`EmailTemplatesTab.tsx` line 190 ALREADY has `<ToggleButton value="REGISTRATION">`. The `emailTemplates.categories.REGISTRATION = "Registration"` key is ALREADY in `common.json`. **Do not add a REGISTRATION filter button** — it will create a duplicate. The story PRD description is slightly out of date.

#### `speaker-acceptance` vs `portal-registration`
- `speaker-acceptance-de/en.html` is the email sent when speaker **accepts** an invitation via the speaker response portal (Story 6.2). Category: `SPEAKER` (prefix `speaker-` ✓).
- `portal-registration-de/en.html` is a NEW template for when a speaker **creates a portal account** (Story 9.x feature — seeded here so organizers can customize it in advance). The story PRD mentions `SpeakerAcceptanceEmailService` and `PortalRegistrationEmailService` but the actual sending service is not part of Story 10.13 scope. This story only seeds the template so it's visible and editable in the admin tab.

#### `EmailTemplateSeedServiceTest` — `contentCount` Assertion
The existing test `should_seedFromClasspath_andFindLayoutTemplates` (line 189) asserts `contentCount >= 18`. Current classpath has 22 content templates (11 speaker + 1 registration-confirmation + 1 newsletter-event + 9 speaker-reminder). Adding `portal-registration-de.html` + `portal-registration-en.html` = 24 total. Update assertion to `>= 20` (or `>= 24` if you prefer exact). Using `>= 20` keeps it flexible.

#### `EmailTemplateQuickEditDrawer` — TinyMCE Import
Import from `@tinymce/tinymce-react` using the same pattern as `EmailTemplateEditModal.tsx`:
```typescript
import { Editor as TinyMCEEditor } from '@tinymce/tinymce-react';
```
Check `EmailTemplateEditModal.tsx` for the TinyMCE `init` configuration (plugins, toolbar) and mirror it for consistency.

#### `useTranslation` Namespace in `EventParticipantsTab.tsx`
The file currently uses `const { t } = useTranslation('events');`. To access `organizer:emailTemplates.quickEdit.*` keys, update to multi-namespace pattern:
```typescript
const { t } = useTranslation(['events', 'organizer']);
```
Then use: `t('organizer:emailTemplates.quickEdit.editRegistrationEmail')`.

Per the i18n patterns from Story 10.9: `useTranslation(['events', 'organizer'])` is the correct multi-namespace pattern.

#### Quick-edit Drawer — No `emailCode` filtering
The drawer opens `registration-confirmation`, `registration-waitlist-confirmation`, or `deregistration-link` templates — these are event-agnostic system templates (same for all events). The `eventCode` prop is passed through for future use (e.g., linking back to the event) but the drawer loads the template by key only.

#### `registration-waitlist-confirmation-*` and `deregistration-link-*` May Not Exist Yet
If Stories 10.11 and 10.12 haven't been merged, the quick-edit buttons for waitlist/deregistration will fail gracefully (the `useEmailTemplate` query will return 404, which shows an error state in the drawer). The buttons should be rendered regardless — they'll work once those stories are merged.

#### `portal-registration-de/en.html` Template Variables
Variables available from `SpeakerAcceptanceEmailService` pattern (check that service for what's typically available):
- `{{recipientName}}` — speaker's full name
- `{{portalUrl}}` — the speaker portal magic-link URL
- `{{eventTitle}}` — event name
- `{{eventDate}}` — formatted event date (dd.MM.yyyy)

#### `registration-confirmation-de/en.html` — Already Seeded
These templates already exist in classpath and are already seeded with `REGISTRATION` category. They are already visible in the admin tab under the `REGISTRATION` filter. No backend changes needed for these.

### Key New Files

```
services/event-management-service/src/main/resources/email-templates/portal-registration-de.html
services/event-management-service/src/main/resources/email-templates/portal-registration-en.html
web-frontend/src/components/organizer/EventPage/EmailTemplateQuickEditDrawer.tsx
```

### Key Modified Files

| File | Change |
|------|--------|
| `services/event-management-service/.../service/EmailTemplateSeedService.java` | Add `waitlist-`, `deregistration-`, `portal-registration` to `deriveCategory()` |
| `services/event-management-service/src/test/.../EmailTemplateSeedServiceTest.java` | Add 3 new `deriveCategory()` tests + portal-registration seeding test; update contentCount assertion to `>= 20` |
| `web-frontend/src/hooks/useEmailTemplates.ts` | Add `useEmailTemplate(templateKey, locale)` hook |
| `web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx` | Add 3 quick-edit icon-buttons + import `EmailTemplateQuickEditDrawer`; switch to multi-namespace `useTranslation` |
| `web-frontend/public/locales/en/organizer.json` | Add `emailTemplates.quickEdit.*` keys |
| `web-frontend/public/locales/de/organizer.json` | Same in German |
| `web-frontend/public/locales/{fr,it,rm,es,fi,nl,ja,gsw-BE}/organizer.json` | `[MISSING]` placeholders |

### Project Structure Notes

- Backend service root: `services/event-management-service/src/main/java/ch/batbern/events/`
- Email template classpath: `services/event-management-service/src/main/resources/email-templates/` (HTML files auto-scanned at startup)
- No Flyway migration needed — `category` is a plain VARCHAR(50) column with no DB-level enum constraint
- Frontend component location: `web-frontend/src/components/organizer/EventPage/` (co-located with `EventParticipantsTab.tsx`)
- Frontend hook location: `web-frontend/src/hooks/useEmailTemplates.ts` (add `useEmailTemplate` here)
- i18n: 10 locales — de, en, fr, it, rm, es, fi, nl, ja, gsw-BE — in `web-frontend/public/locales/{lang}/organizer.json`

### References

- Story spec: [Source: docs/prd/epic-10-additional-stories.md#Story-10.13]
- `EmailTemplateSeedService` (current deriveCategory): [Source: services/event-management-service/.../service/EmailTemplateSeedService.java]
- `EmailTemplateSeedServiceTest` (existing tests to extend): [Source: services/event-management-service/src/test/java/.../service/EmailTemplateSeedServiceTest.java]
- `EmailTemplatesTab.tsx` (REGISTRATION filter already present): [Source: web-frontend/src/components/organizer/Admin/EmailTemplatesTab.tsx:190]
- `EmailTemplateEditModal.tsx` (TinyMCE config to mirror): [Source: web-frontend/src/components/organizer/Admin/EmailTemplateEditModal.tsx]
- `emailTemplateService.getTemplate()` (API for single template): [Source: web-frontend/src/services/emailTemplateService.ts:31]
- `EventParticipantsTab.tsx` (file to update): [Source: web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx]
- `RegistrationEmailService` (DB-first loading pattern — already implemented): [Source: services/event-management-service/.../service/RegistrationEmailService.java:169-187]
- `SpeakerAcceptanceEmailService` (email service pattern reference): [Source: services/event-management-service/.../service/SpeakerAcceptanceEmailService.java]
- `common.json` (emailTemplates.categories.REGISTRATION already present): [Source: web-frontend/public/locales/en/common.json]
- Story 10.2 (email template foundation): [Source: CLAUDE.md — Epic 10 history]
- Story 10.11 (waitlist templates context): [Source: _bmad-output/implementation-artifacts/10-11-venue-capacity-enforcement-waitlist-management.md]
- Story 10.12 (deregistration templates context): [Source: _bmad-output/implementation-artifacts/10-12-self-service-deregistration.md]
- i18n multi-namespace pattern: [Source: _bmad-output/implementation-artifacts/10-9-i18n-cleanup.md]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/test-10-13-red.log`
- `/tmp/test-10-13-green.log`
- `/tmp/test-10-13-backend.log`
- `/tmp/test-10-13-frontend.log`
- `/tmp/typecheck-10-13.log`
- `/tmp/lint-10-13.log`

### Completion Notes List

### File List
