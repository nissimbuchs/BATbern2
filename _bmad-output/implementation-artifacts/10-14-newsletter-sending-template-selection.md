# Story 10.14: Newsletter Sending with Template Selection

Status: review

<!-- Prerequisite: Story 10.2 (email template management), Story 10.7 (newsletter sending infrastructure) -->

## Story

As an **organizer**,
I want to choose which email template to use when sending a newsletter,
so that I can send different types of communications (general newsletter, event reminder, partner announcement) without code deploys.

## Acceptance Criteria

1. **AC1 — Backend: `templateKey` optional on send/preview**: `NewsletterSendRequest` gains an optional `templateKey` field. If provided → use that template from DB; if omitted/null → use default `newsletter-event` with the existing locale (backward compatible).

2. **AC2 — Backend: `NewsletterEmailService` respects templateKey**: `sendNewsletter()` and `preview()` accept an optional `templateKey` param; `renderContent()` and `buildSubject()` use provided key or fall back to `TEMPLATE_KEY = "newsletter-event"`.

3. **AC3 — Frontend: Template dropdown in `EventNewsletterTab`**: Before the Send/Preview buttons, a "Template" select dropdown is populated by `GET /api/v1/email-templates?category=NEWSLETTER` filtered to the selected locale. Default selection: `newsletter-event`.

4. **AC4 — Locale toggle also filters templates**: When locale changes (DE↔EN), the dropdown re-filters to show only templates with `locale === selectedLocale`.

5. **AC5 — Preview uses selected template**: When "Preview" is clicked, the request includes `templateKey` from the dropdown selection.

6. **AC6 — Confirmation dialog shows template name**: "Send newsletter using '**[Template Name]**' to 234 subscribers?" — uses the selected template's key as the display name.

7. **AC7 — "Create new template" link**: Below the dropdown, a link opens `/organizer/admin?tab=email-templates` (admin Email Templates tab with NEWSLETTER filter).

8. **AC8 — Existing Story 10.7 tests pass**: `NewsletterEmailServiceTest`, `NewsletterControllerIntegrationTest`, frontend `EventNewsletterTab` snapshot tests all pass without modification.

9. **AC9 — i18n**: `newsletter.templateSelect.*` keys in `de/organizer.json` and `en/organizer.json`; `[MISSING]` prefix in all 8 other locales. No hardcoded strings. `npm run type-check` and `npm run lint` pass.

10. **AC10 — OpenAPI updated FIRST**: `NewsletterSendRequest` schema in `events-api.openapi.yml` updated before backend implementation.

---

## Tasks / Subtasks

### Phase 1: OpenAPI Contract Update (FIRST — ADR-006)

- [x] **T1 — Update `events-api.openapi.yml`** (AC: #10, #1)
  - [x] T1.1 — Open `docs/api/events-api.openapi.yml`
  - [x] T1.2 — Locate `NewsletterSendRequest` schema (~line 4372)
  - [x] T1.3 — Add optional `templateKey` field:
    ```yaml
    NewsletterSendRequest:
      type: object
      required: [isReminder, locale]
      properties:
        isReminder:
          type: boolean
        locale:
          type: string
          enum: [de, en]
        templateKey:
          type: string
          nullable: true
          description: >
            Optional template key override. If omitted, uses default 'newsletter-event'.
            Must be a NEWSLETTER category template key present in DB.
    ```
  - [x] T1.4 — NOTE: `newsletterService.ts` defines `NewsletterSendRequest` **manually** (not from generated types). No type regeneration needed. The OpenAPI update is for contract documentation only.

### Phase 2: Backend — TDD first

- [x] **T2 — Add unit tests FIRST (RED phase)** (AC: #2, #8)
  - [x] T2.1 — Open `services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterEmailServiceTest.java`
  - [x] T2.2 — Add test: `sendNewsletter_withCustomTemplateKey_usesProvidedKey`:
    ```java
    @Test
    @DisplayName("preview: when templateKey provided → renderContent uses that key")
    void preview_withCustomTemplateKey_usesCustomTemplate() {
        // Arrange: only custom template returns content, default returns empty
        when(emailTemplateService.findByKeyAndLocale("custom-newsletter", "de"))
            .thenReturn(Optional.of(mockTemplate("custom-newsletter", "de", "Custom content")));
        when(emailTemplateService.findByKeyAndLocale("newsletter-event", "de"))
            .thenReturn(Optional.empty());
        when(emailTemplateService.mergeWithLayout(any(), eq("batbern-default"), eq("de")))
            .thenReturn("merged");
        when(emailService.replaceVariables(any(), any())).thenReturn("final");
        when(emailTemplateService.resolveSubject("custom-newsletter", "de"))
            .thenReturn(Optional.of("Custom Subject"));
        when(subscriberService.getActiveCount()).thenReturn(5L);

        // Act
        NewsletterPreviewResponse response = newsletterEmailService.preview(testEvent, false, "de", "custom-newsletter");

        // Assert: custom template was used
        verify(emailTemplateService).findByKeyAndLocale("custom-newsletter", "de");
        verify(emailTemplateService, never()).findByKeyAndLocale("newsletter-event", "de");
    }
    ```
  - [x] T2.3 — Add test: `preview_withNullTemplateKey_usesDefaultKey`:
    ```java
    @Test
    @DisplayName("preview: when templateKey null → renderContent uses default 'newsletter-event'")
    void preview_withNullTemplateKey_usesDefaultTemplate() {
        when(emailTemplateService.findByKeyAndLocale("newsletter-event", "de"))
            .thenReturn(Optional.of(mockTemplate("newsletter-event", "de", "Default content")));
        when(emailTemplateService.mergeWithLayout(any(), any(), any())).thenReturn("merged");
        when(emailService.replaceVariables(any(), any())).thenReturn("final");
        when(emailTemplateService.resolveSubject("newsletter-event", "de"))
            .thenReturn(Optional.of("Subject"));
        when(subscriberService.getActiveCount()).thenReturn(3L);

        newsletterEmailService.preview(testEvent, false, "de", null);

        verify(emailTemplateService).findByKeyAndLocale("newsletter-event", "de");
    }
    ```
  - [x] T2.4 — Add `private EmailTemplate mockTemplate(String key, String locale, String html)` helper returning a mocked `EmailTemplate`
  - [x] T2.5 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests NewsletterEmailServiceTest 2>&1 | tee /tmp/test-10-14-red.log && grep -E "FAILED|BUILD" /tmp/test-10-14-red.log`

- [x] **T3 — Update `NewsletterSendRequest.java`** (AC: #1)
  - [x] T3.1 — Open `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSendRequest.java`
  - [x] T3.2 — Add field (nullable, no `@NotNull`):
    ```java
    /** Optional template key override. Null → use default 'newsletter-event'. */
    private String templateKey;
    ```

- [x] **T4 — Update `NewsletterEmailService.java`** (AC: #2)
  - [x] T4.1 — Open `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterEmailService.java`
  - [x] T4.2 — Update `preview()` signature to accept `templateKey`:
    ```java
    public NewsletterPreviewResponse preview(Event event, boolean isReminder, String locale,
                                              @Nullable String templateKey) {
        String effectiveKey = resolveTemplateKey(templateKey);
        Map<String, String> vars = buildVariables(event, locale, isReminder, baseUrl + "/unsubscribe?token=PREVIEW");
        String contentHtml = renderContent(locale, vars, effectiveKey);
        String mergedHtml = emailService.replaceVariables(
                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), vars);
        String subject = buildSubject(event, isReminder, locale, vars, effectiveKey);
        int count = (int) subscriberService.getActiveCount();
        return NewsletterPreviewResponse.builder()
                .subject(subject)
                .htmlPreview(mergedHtml)
                .recipientCount(count)
                .build();
    }
    ```
  - [x] T4.3 — Update `sendNewsletter()` signature to accept `templateKey`:
    ```java
    public NewsletterSendResponse sendNewsletter(Event event, boolean isReminder,
                                                  String locale, String sentByUsername,
                                                  @Nullable String templateKey) {
        String effectiveKey = resolveTemplateKey(templateKey);
        // ... rest of method, replace TEMPLATE_KEY with effectiveKey
        String subject = buildSubject(event, isReminder, locale, baseVars, effectiveKey);
        // ...
        NewsletterSend saved = createSendAuditRecord(event, isReminder, locale, sentByUsername,
                subscribers.size(), effectiveKey);
        // ... per-recipient loop: replace renderContent(locale, recipientVars) call
        String contentHtml = renderContent(locale, recipientVars, effectiveKey);
    }
    ```
  - [x] T4.4 — Add private helper `resolveTemplateKey()`:
    ```java
    private String resolveTemplateKey(@Nullable String templateKey) {
        return (templateKey != null && !templateKey.isBlank()) ? templateKey : TEMPLATE_KEY;
    }
    ```
  - [x] T4.5 — Update `renderContent()` to accept `effectiveKey`:
    ```java
    private String renderContent(String locale, Map<String, String> vars, String templateKey) {
        Optional<ch.batbern.events.domain.EmailTemplate> templateOpt =
                emailTemplateService.findByKeyAndLocale(templateKey, locale);
        // ...
    }
    ```
  - [x] T4.6 — Update `buildSubject()` to accept `effectiveKey`:
    ```java
    private String buildSubject(Event event, boolean isReminder, String locale,
                                Map<String, String> vars, String templateKey) {
        Optional<String> subjectTemplate = emailTemplateService.resolveSubject(templateKey, locale);
        // ...
    }
    ```
  - [x] T4.7 — Update `createSendAuditRecord()` to store the actual used `templateKey`:
    ```java
    protected NewsletterSend createSendAuditRecord(Event event, boolean isReminder, String locale,
                                                   String sentByUsername, int recipientCount,
                                                   String templateKey) {
        NewsletterSend send = NewsletterSend.builder()
                .eventId(event.getId())
                .templateKey(templateKey)  // actual key, not hardcoded TEMPLATE_KEY
                .reminder(isReminder)
                .locale(locale)
                // ...
    }
    ```
  - [x] T4.8 — Add import: `import org.springframework.lang.Nullable;`

- [x] **T5 — Update `NewsletterController.java`** (AC: #1, #2)
  - [x] T5.1 — Open `services/event-management-service/src/main/java/ch/batbern/events/controller/NewsletterController.java`
  - [x] T5.2 — Update `previewNewsletter()` to pass `templateKey`:
    ```java
    NewsletterPreviewResponse preview = emailService.preview(
            event,
            Boolean.TRUE.equals(request.getIsReminder()),
            request.getLocale(),
            request.getTemplateKey()  // nullable — service handles fallback
    );
    ```
  - [x] T5.3 — Update `sendNewsletter()` to pass `templateKey`:
    ```java
    NewsletterSendResponse response = emailService.sendNewsletter(
            event,
            Boolean.TRUE.equals(request.getIsReminder()),
            request.getLocale(),
            sentByUsername,
            request.getTemplateKey()  // nullable — service handles fallback
    );
    ```

- [x] **T6 — Run GREEN tests** (AC: #2, #8)
  - [x] T6.1 — `./gradlew :services:event-management-service:test --tests NewsletterEmailServiceTest 2>&1 | tee /tmp/test-10-14-green.log && grep -E "BUILD|FAILED|passed" /tmp/test-10-14-green.log`
  - [x] T6.2 — `./gradlew :services:event-management-service:test --tests NewsletterControllerIntegrationTest 2>&1 | tee /tmp/test-10-14-integration.log && grep -E "BUILD|FAILED|passed" /tmp/test-10-14-integration.log`
  - [x] T6.3 — Full backend: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-14-backend.log && grep -E "BUILD|FAILED|tests" /tmp/test-10-14-backend.log`

### Phase 3: Frontend — update newsletterService types

- [x] **T7 — Update `newsletterService.ts`** (AC: #1)
  - [x] T7.1 — Open `web-frontend/src/services/newsletterService.ts`
  - [x] T7.2 — Add `templateKey?: string` to `NewsletterSendRequest` interface:
    ```typescript
    export interface NewsletterSendRequest {
      isReminder: boolean;
      locale: 'de' | 'en';
      templateKey?: string;  // Optional override; service defaults to 'newsletter-event' if omitted
    }
    ```
  - [x] T7.3 — No other changes to `newsletterService.ts` needed — all functions already accept and pass through `NewsletterSendRequest`.

### Phase 4: Frontend — update `EventNewsletterTab.tsx`

- [x] **T8 — Update `EventNewsletterTab.tsx`** (AC: #3, #4, #5, #6, #7)
  - [x] T8.1 — Read the FULL current file before editing
  - [x] T8.2 — Add imports:
    ```typescript
    import { useEmailTemplates } from '@/hooks/useEmailTemplates';
    import type { EmailTemplateResponse } from '@/services/emailTemplateService';
    import { Link } from '@mui/material';  // MUI Link (already imported from @mui/material above)
    ```
    NOTE: `useEmailTemplates` is already exported from `web-frontend/src/hooks/useEmailTemplates.ts`. Import it from there — do NOT create a new hook.
  - [x] T8.3 — Switch `useTranslation` to multi-namespace pattern:
    ```typescript
    const { t } = useTranslation(['events', 'organizer']);
    // Access organizer keys: t('organizer:newsletter.templateSelect.label')
    ```
  - [x] T8.4 — Add state for selected template key:
    ```typescript
    const [selectedTemplateKey, setSelectedTemplateKey] = useState<string>('newsletter-event');
    ```
  - [x] T8.5 — Fetch NEWSLETTER templates:
    ```typescript
    const newsletterTemplatesQuery = useEmailTemplates({ category: 'NEWSLETTER' });
    ```
  - [x] T8.6 — Derive filtered template list (re-filters when locale changes):
    ```typescript
    const filteredTemplates: EmailTemplateResponse[] = React.useMemo(
      () => (newsletterTemplatesQuery.data ?? []).filter(t => t.locale === locale),
      [newsletterTemplatesQuery.data, locale]
    );
    ```
  - [x] T8.7 — Add `useEffect` to reset `selectedTemplateKey` when locale changes (pick first available or default):
    ```typescript
    useEffect(() => {
      const defaultKey = 'newsletter-event';
      const hasDefault = filteredTemplates.some(t => t.templateKey === defaultKey);
      setSelectedTemplateKey(hasDefault ? defaultKey : (filteredTemplates[0]?.templateKey ?? defaultKey));
    }, [locale, filteredTemplates]);
    ```
  - [x] T8.8 — Update `handlePreview()` to include `templateKey`:
    ```typescript
    function handlePreview() {
      const request: NewsletterSendRequest = { isReminder: false, locale, templateKey: selectedTemplateKey };
      // ...
    }
    ```
  - [x] T8.9 — Update `handleConfirmSend()` to include `templateKey`:
    ```typescript
    const request: NewsletterSendRequest = {
      isReminder: pendingSendType === 'reminder',
      locale,
      templateKey: selectedTemplateKey,
    };
    ```
  - [x] T8.10 — Add template dropdown UI in **Section 3 — Compose & send**, BEFORE the button stack. Insert after the locale `<FormControl>` and before `<Stack direction="row" spacing={1} ...>`:
    ```tsx
    {/* Template selector */}
    <FormControl size="small">
      <InputLabel>{t('organizer:newsletter.templateSelect.label')}</InputLabel>
      <Select
        value={selectedTemplateKey}
        label={t('organizer:newsletter.templateSelect.label')}
        onChange={(e) => setSelectedTemplateKey(e.target.value)}
        disabled={newsletterTemplatesQuery.isLoading}
      >
        {filteredTemplates.map((tpl) => (
          <MenuItem key={tpl.templateKey} value={tpl.templateKey}>
            {tpl.templateKey}
          </MenuItem>
        ))}
      </Select>
    </FormControl>

    {/* Create new template link */}
    <Link
      href="/organizer/admin?tab=email-templates"
      variant="caption"
      color="text.secondary"
      underline="hover"
    >
      {t('organizer:newsletter.templateSelect.createNew')} ↗
    </Link>
    ```
  - [x] T8.11 — Update confirmation dialog text to show template name. Find `confirmSendBody` usage and add `templateKey` to interpolation vars:
    ```tsx
    {t('eventPage.newsletter.confirmSendBody', {
      type: sendType,
      count: activeCount,
      eventTitle,
      templateKey: selectedTemplateKey,  // NEW
    })}
    ```
    Also update the i18n key value (T10 below) to include `{{templateKey}}`.
  - [x] T8.12 — Add `data-testid="newsletter-template-select"` on the template `<Select>` element

### Phase 5: i18n

- [x] **T9 — English i18n** (AC: #9)
  - [x] T9.1 — Add to `web-frontend/public/locales/en/organizer.json` (no `newsletter` key exists yet — add at top level):
    ```json
    "newsletter": {
      "templateSelect": {
        "label": "Template",
        "createNew": "Create new template"
      }
    }
    ```
  - [x] T9.2 — Also update `web-frontend/public/locales/en/events.json` `eventPage.newsletter.confirmSendBody` to include template name:
    ```json
    "confirmSendBody": "Send {{type}} using '{{templateKey}}' to {{count}} subscribers for event «{{eventTitle}}»?"
    ```

- [x] **T10 — German i18n** (AC: #9)
  - [x] T10.1 — Add to `web-frontend/public/locales/de/organizer.json`:
    ```json
    "newsletter": {
      "templateSelect": {
        "label": "Vorlage",
        "createNew": "Neue Vorlage erstellen"
      }
    }
    ```
  - [x] T10.2 — Update `web-frontend/public/locales/de/events.json` `eventPage.newsletter.confirmSendBody`:
    ```json
    "confirmSendBody": "{{type}} mit Vorlage '{{templateKey}}' an {{count}} Abonnenten für den Event «{{eventTitle}}» senden?"
    ```

- [x] **T11 — Other locale placeholders** (AC: #9)
  - [x] T11.1 — Add `[MISSING]` prefix translations to all 8 other locales: `fr, it, rm, es, fi, nl, ja, gsw-BE`
  - [x] T11.2 — In each `{lang}/organizer.json`:
    ```json
    "newsletter": {
      "templateSelect": {
        "label": "[MISSING] Vorlage",
        "createNew": "[MISSING] Neue Vorlage erstellen"
      }
    }
    ```
  - [x] T11.3 — In each `{lang}/events.json`, update `eventPage.newsletter.confirmSendBody` with `[MISSING]` prefix (copy DE value with `[MISSING]` prefix)

### Phase 6: Final verification

- [x] **T12 — Frontend tests** (AC: #8, #9)
  - [x] T12.1 — `cd web-frontend && npm run test -- --run 2>&1 | tee /tmp/test-10-14-frontend.log && grep -E "pass|fail|error" /tmp/test-10-14-frontend.log | tail -20`
  - [x] T12.2 — `npm run type-check 2>&1 | tee /tmp/typecheck-10-14.log && grep -E "error|Error" /tmp/typecheck-10-14.log | head -20`
  - [x] T12.3 — `npm run lint 2>&1 | tee /tmp/lint-10-14.log && grep -E "error" /tmp/lint-10-14.log | head -20`

---

## Dev Notes

### Architecture Compliance

**ADR-006 (Contract-First)**: Update `events-api.openapi.yml` **BEFORE** backend DTO changes. The `NewsletterSendRequest` schema gets a new optional `templateKey` field.

**NOTE — No Type Regeneration Needed**: `NewsletterSendRequest` in `newsletterService.ts` is **manually defined** (not from generated types `@/types/generated/events-api.types`). Adding `templateKey?: string` directly to the interface is correct. Do NOT run `npm run generate:api-types` as it won't affect this interface.

**TDD Mandate**: Write failing tests in `NewsletterEmailServiceTest` FIRST (T2), then update the service (T4). Fast unit tests — no Spring context needed.

### Critical Implementation Details

#### `NewsletterEmailService` — Backward Compatibility
The `private static final String TEMPLATE_KEY = "newsletter-event"` constant is retained as the default. Add `resolveTemplateKey()` helper. All four methods that use it (`preview`, `sendNewsletter`, `renderContent`, `buildSubject`) must accept `templateKey` and use `resolveTemplateKey()` to determine the effective key. `createSendAuditRecord()` must store the **effective** key (not hardcoded constant) in `newsletter_sends.template_key` for audit accuracy.

#### Frontend — Reuse `useEmailTemplates`, Do NOT Create New Hook
`web-frontend/src/hooks/useEmailTemplates.ts` already exports `useEmailTemplates(params?)` that accepts `{ category: 'NEWSLETTER' }`. Use it directly — DO NOT create `useNewsletterTemplates` or any new hook.

#### Frontend — `filteredTemplates` Filtering Logic
`EmailTemplateResponse` has separate `templateKey: string` and `locale: string` fields. Locale filtering: `templates.filter(t => t.locale === locale)`. The `selectedTemplateKey` state holds just the key (e.g., `"newsletter-event"`), NOT `"newsletter-event-de"`.

#### Frontend — `useTranslation` Multi-Namespace
Switch from `useTranslation('events')` to `useTranslation(['events', 'organizer'])`. Existing keys continue to work unchanged (still accessed as `t('eventPage.newsletter.title')`). New organizer keys accessed as `t('organizer:newsletter.templateSelect.label')`.

This is the established pattern from Story 10.13 (`EventParticipantsTab.tsx` uses `['events', 'organizer']`).

#### `confirmSendBody` i18n Key Update
The existing `eventPage.newsletter.confirmSendBody` key in all 10 locales must be updated to include `{{templateKey}}`. For fr/it/rm/es/fi/nl/ja/gsw-BE: add `[MISSING]` prefix to keep non-blocking. DE and EN get proper translations.

#### No New Backend Endpoints
`GET /api/v1/email-templates?category=NEWSLETTER` already works via `EmailTemplateController` (Story 10.2). The frontend `useEmailTemplates({ category: 'NEWSLETTER' })` calls it directly. Zero backend work needed for template listing.

#### `newsletterService.ts` — No New Functions
The existing `previewNewsletter()` and `sendNewsletter()` functions already pass through `NewsletterSendRequest` as-is. Adding `templateKey?` to the interface is the only change — both functions automatically include it in the request body.

#### Existing Test Backward Compatibility
`NewsletterControllerIntegrationTest` and `NewsletterEmailServiceTest` call the old 4-param signatures. After the change, `sendNewsletter()` and `preview()` have 5 params. Update all existing test call sites to add `null` as the last param (or use named/overloaded pattern). Check for existing tests that call these methods.

#### `EventNewsletterTab` — `Link` Component
Use MUI `Link` (already imported from `@mui/material` in the existing import block — check if it's in the destructured list, add if not). Do NOT use React Router `Link` for external admin navigation.

### Key Modified Files

| File | Change |
|------|--------|
| `docs/api/events-api.openapi.yml` | Add optional `templateKey` to `NewsletterSendRequest` schema |
| `services/event-management-service/.../dto/NewsletterSendRequest.java` | Add `private String templateKey` (nullable) |
| `services/event-management-service/.../service/NewsletterEmailService.java` | Accept `templateKey` in `preview()`, `sendNewsletter()`, add `resolveTemplateKey()`; propagate to `renderContent()`, `buildSubject()`, `createSendAuditRecord()` |
| `services/event-management-service/.../controller/NewsletterController.java` | Pass `request.getTemplateKey()` in both send + preview calls |
| `services/event-management-service/src/test/.../service/NewsletterEmailServiceTest.java` | Add `preview_withCustomTemplateKey_usesCustomTemplate` + `preview_withNullTemplateKey_usesDefaultTemplate` tests; update existing calls to 5-param signatures |
| `web-frontend/src/services/newsletterService.ts` | Add `templateKey?: string` to `NewsletterSendRequest` interface |
| `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx` | Add template select dropdown + locale filtering + update preview/send requests; multi-namespace useTranslation |
| `web-frontend/public/locales/en/organizer.json` | Add `newsletter.templateSelect.*` keys |
| `web-frontend/public/locales/de/organizer.json` | Same in German |
| `web-frontend/public/locales/{fr,it,rm,es,fi,nl,ja,gsw-BE}/organizer.json` | `[MISSING]` placeholders |
| `web-frontend/public/locales/en/events.json` | Update `eventPage.newsletter.confirmSendBody` to include `{{templateKey}}` |
| `web-frontend/public/locales/de/events.json` | Same in German |
| `web-frontend/public/locales/{fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` | Add `[MISSING]` prefix to updated `confirmSendBody` values |

### Project Structure Notes

- Backend service root: `services/event-management-service/src/main/java/ch/batbern/events/`
- Backend DTOs: `.../dto/NewsletterSendRequest.java`
- Backend service: `.../service/NewsletterEmailService.java`
- Backend controller: `.../controller/NewsletterController.java`
- Frontend component: `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx`
- Frontend hooks: `web-frontend/src/hooks/useEmailTemplates.ts` (reuse — do NOT create new files)
- Frontend service: `web-frontend/src/services/newsletterService.ts`
- i18n: 10 locales — de, en, fr, it, rm, es, fi, nl, ja, gsw-BE (9 namespaces each, always include gsw-BE)
- OpenAPI spec: `docs/api/events-api.openapi.yml` (single file, no sharding)

### Previous Story (10.13) Learnings

- Multi-namespace pattern `useTranslation(['events', 'organizer'])` confirmed working — use for `EventNewsletterTab.tsx`
- i18n key style in organizer.json: camelCase keys nested under component/feature name
- `[MISSING]` prefix used for non-DE/EN locales — copy DE values with prefix
- When adding keys to `organizer.json` in 8 non-primary locales, check if `emailTemplates` key already exists to merge rather than overwrite

### References

- Story spec: [Source: docs/prd/epic-10-additional-stories.md#Story-10.14]
- `NewsletterEmailService` (current hardcoded TEMPLATE_KEY): [Source: services/event-management-service/.../service/NewsletterEmailService.java:48]
- `NewsletterSendRequest.java` (current fields): [Source: services/event-management-service/.../dto/NewsletterSendRequest.java]
- `NewsletterController.java` (send + preview endpoints): [Source: services/event-management-service/.../controller/NewsletterController.java:174-205]
- `EventNewsletterTab.tsx` (current component — reads it fully before modifying): [Source: web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx]
- `useEmailTemplates.ts` (NEWSLETTER filter hook — reuse): [Source: web-frontend/src/hooks/useEmailTemplates.ts:18]
- `emailTemplateService.ts` (listTemplates already works for category filter): [Source: web-frontend/src/services/emailTemplateService.ts:26]
- `newsletterService.ts` (NewsletterSendRequest — manually defined, NOT from generated types): [Source: web-frontend/src/services/newsletterService.ts:24]
- `useNewsletter.ts` (useNewsletterPreview + useSendNewsletter already pass request through): [Source: web-frontend/src/hooks/useNewsletter/useNewsletter.ts:107-129]
- `NewsletterControllerIntegrationTest.java` (existing integration tests to keep passing): [Source: services/event-management-service/src/test/.../controller/NewsletterControllerIntegrationTest.java]
- `NewsletterEmailServiceTest.java` (unit test file to extend): [Source: services/event-management-service/src/test/.../service/NewsletterEmailServiceTest.java]
- OpenAPI `NewsletterSendRequest` schema (update FIRST): [Source: docs/api/events-api.openapi.yml:4372]
- Story 10.7 (newsletter infrastructure foundation): [Source: sprint-status.yaml — 10-7-newsletter-subscription-and-sending: done]
- Story 10.2 (email template management — EmailTemplatesTab + emailTemplateService): [Source: sprint-status.yaml — 10-2-email-template-management: done]
- Story 10.13 multi-namespace useTranslation pattern: [Source: _bmad-output/implementation-artifacts/10-13-registration-portal-email-templates.md#T8.6]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/test-10-14-red.log`
- `/tmp/test-10-14-green.log`
- `/tmp/test-10-14-integration.log`
- `/tmp/test-10-14-backend.log`
- `/tmp/test-10-14-frontend.log`
- `/tmp/typecheck-10-14.log`
- `/tmp/lint-10-14.log`

### Completion Notes List

- AC1/AC10: `NewsletterSendRequest` schema in `events-api.openapi.yml` updated first per ADR-006. Added optional nullable `templateKey` field. No type regeneration needed (interface manually defined in `newsletterService.ts`).
- AC2: `NewsletterEmailService` refactored — `preview()` and `sendNewsletter()` accept `@Nullable String templateKey`; `resolveTemplateKey()` helper encapsulates default fallback; `renderContent()`, `buildSubject()`, `createSendAuditRecord()` all propagate effective key. Backward compatible: null/omitted uses `"newsletter-event"` default.
- AC1: `NewsletterSendRequest.java` gains `private String templateKey` (no `@NotNull` — nullable).
- AC1/AC2: `NewsletterController.java` passes `request.getTemplateKey()` to both `preview()` and `sendNewsletter()`.
- AC8: TDD — 2 new unit tests added (`preview_withCustomTemplateKey_usesCustomTemplate`, `preview_withNullTemplateKey_usesDefaultTemplate`). All 12 `NewsletterEmailServiceTest` pass. All 19 `NewsletterControllerIntegrationTest` pass. Full backend BUILD SUCCESSFUL.
- AC3-AC7: `EventNewsletterTab.tsx` fully updated — `useEmailTemplates({ category: 'NEWSLETTER' })` fetches templates; `filteredTemplates` memo filters by locale; `useEffect` resets selection on locale change; template `<Select>` with `data-testid`; MUI `Link` to admin email-templates tab; `handlePreview()` and `handleConfirmSend()` include `templateKey`; confirmation dialog shows `{{templateKey}}`.
- AC9: i18n — `newsletter.templateSelect.*` keys added to `de` and `en` organizer.json; `confirmSendBody` updated with `{{templateKey}}` in all 10 locales; 8 non-primary locales have `[MISSING]` prefix in organizer.json and events.json.
- Pre-existing failures in `EventLogistics.test.tsx` and `EventParticipantsTab.test.tsx` (3 tests) confirmed unrelated to this story (same failures present on unmodified HEAD).
- type-check: ✅ (exit 0), lint: ✅ (exit 0), frontend: 3855/3858 pass (3 pre-existing failures unrelated to this story).

### File List

- `docs/api/events-api.openapi.yml`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSendRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/NewsletterController.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterEmailServiceTest.java`
- `web-frontend/src/services/newsletterService.ts`
- `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx`
- `web-frontend/public/locales/en/organizer.json`
- `web-frontend/public/locales/en/events.json`
- `web-frontend/public/locales/de/organizer.json`
- `web-frontend/public/locales/de/events.json`
- `web-frontend/public/locales/fr/organizer.json`
- `web-frontend/public/locales/fr/events.json`
- `web-frontend/public/locales/it/organizer.json`
- `web-frontend/public/locales/it/events.json`
- `web-frontend/public/locales/rm/organizer.json`
- `web-frontend/public/locales/rm/events.json`
- `web-frontend/public/locales/es/organizer.json`
- `web-frontend/public/locales/es/events.json`
- `web-frontend/public/locales/fi/organizer.json`
- `web-frontend/public/locales/fi/events.json`
- `web-frontend/public/locales/nl/organizer.json`
- `web-frontend/public/locales/nl/events.json`
- `web-frontend/public/locales/ja/organizer.json`
- `web-frontend/public/locales/ja/events.json`
- `web-frontend/public/locales/gsw-BE/organizer.json`
- `web-frontend/public/locales/gsw-BE/events.json`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
