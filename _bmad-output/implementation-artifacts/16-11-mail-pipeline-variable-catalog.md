# Story 16.11: One mail pipeline and variable catalog

Status: ready-for-dev

## Story

As an **organizer**,
I want **every BATbern mail to be rendered the same way, from the same set of variables with the same formatting**,
so that **any template can use any variable that its mail has the data for, a preview always equals what goes out, and the own text of Story 16.5 needs no audience-specific rendering**.

> Source: owner discussion 2026-11-01 (after Winston's review of the 16.5 render design). Owner decisions the same evening: `eventDate` in the long form everywhere; `supportUrl` = `{baseUrl}/support` everywhere; 16.11 before 16.5.
> **Depends on** 16.8 (composer, `MailRenderer`, adapters; on `develop` since #1174). **Blocks** 16.5 (own text renders through this pipeline, chips come from the catalog).
> Ships as its own PR (`feature/16-11-mail-pipeline`), after #1176 (partner invite multi-recipient hotfix) has merged.

## Why (state of `develop` at 000276a9, verified 2026-11-01)

- 17 classes in event-management send mail, built five ways: 8 hand-copied `loadHtmlContent` methods (DB template → merge with the template's layout key → replace variables once; classpath fallback), 4 through `MailRenderer` (replace → merge `batbern-default` → replace), Q&A notification with its own variant, 2 with HTML in Java (`NotificationService`, `QualityReviewService`), plus the composer.
- The 8 classpath fallbacks are dead in practice: every fallback file is also in `email-templates/`, which `EmailTemplateSeedService` seeds into the DB on every start.
- 61 placeholders in 25 templates, each service building its own map. Synonyms: `eventUrl`/`eventDetailLink`, `recipientName`/`attendeeFirstName`, `venue`/`venueName`, `deregistrationUrl`/`deregistrationLink`. Same name, different value: `eventDate` is `dd.MM.yyyy` in 8 services and `EEEE, d. MMMM yyyy` in 3; `supportUrl` is `baseUrl` in one and `baseUrl/support` in four. `logoUrl` is built in 9 places.
- `NewsletterEmailService.renderBody` reads the template from the DB for every subscriber (send loops :407, :464; retry :572, :596): an admin edit during a send splits it, and a retry sends today's template.

## Acceptance Criteria

1. **AC1 (one catalog)** — `MailVariableCatalog` (EMS, `mailcenter.variables`) is the only place that defines variable names. Each entry: canonical name, scope (`SYSTEM`, `EVENT`, `RECIPIENT`, `REGISTRATION`, `SPEAKER`, `SUBSCRIBER`, `VENUE`, `TASK`, `QNA`, `PARTNER`), credential flag, and the aliases it answers to. A unit test fails when a seeded template uses a name the catalog does not know.
2. **AC2 (everything that has data is available)** — Every mail gets all `SYSTEM` and `EVENT` variables (when the mail has an event), plus the scopes its context provides. A template may use any variable of those scopes, not only the ones its service used to set.
3. **AC3 (aliases keep templates working)** — Old names (`eventDetailLink`, `venue`, `deregistrationLink`, `attendeeFirstName`, …) resolve to the canonical value. No template file and no DB template needs to change.
4. **AC4 (one formatting rule)** — `eventDate` = long form in the recipient's language (`EEEE, d. MMMM yyyy`, as the newsletter renders it today) in every mail. `supportUrl` = `{baseUrl}/support`. `logoUrl`, `eventUrl`, `currentYear`, `eventTime` built once. These are the only intended visible changes; each is listed in the change log of this story.
5. **AC5 (one pipeline)** — `MailContent(subject, bodyHtml, locale, contentSource, templateKey)` is resolved once per send by `MailContentResolver` (DB template, DE fallback, classpath fallback in ONE place); `MailRenderer.render(MailContent, variables) → RenderedMail(subject, html)` is the only renderer. The 8 `loadHtmlContent` copies and the Q&A variant are gone.
6. **AC6 (same bytes)** — Golden-file tests written BEFORE the refactor pin today's subject and HTML of every template-based mail (all 25 templates, DE and EN where they exist). After the refactor, the output is byte-identical except the AC4 changes, which are applied as a separate, visible commit updating the goldens.
7. **AC7 (speakers like all others)** — Speaker invitation, acceptance and the six reminders render through the pipeline. The composer preview of speaker reminders still equals the sent mail (16.8 AC3 test stays green).
8. **AC8 (content fixed per send)** — A newsletter send and its retry render from the content resolved at send start. Test: edit the template after the job started → all recipients of that send get the original text.
9. **AC9 (adapter variables)** — `AudienceAdapter.variables(context, recipient, test)` returns the map the adapter renders with (catalog-built). `allowedVariables()` derives from the catalog scopes of the audience. No variable map in `Rendering` (it is the redacted "as sent" value).
10. **AC10 (no behaviour change elsewhere)** — Recording (16.1a `MailContext`), redaction, ADR-015 walls, reply-to, CC of additional emails, attachments (registration ICS, newsletter calendar), the inventory guard: unchanged; full EMS suite green.

## Tasks / Subtasks

- [ ] **Task 1 — RED: golden tests first** (AC: 6)
  - [ ] `MailGoldenTest` (EMS, Testcontainers, real `EmailService` via `CALLS_REAL_METHODS`, fixed event/recipient fixtures, `currentYear` normalised): renders every template-based mail through today's service code and compares with `src/test/resources/mail-golden/{service}-{template}-{locale}.html|.subject`. First run writes the files (`-Dmail.golden.update=true`), committed as their own commit.
- [ ] **Task 2 — Catalog** (AC: 1–4)
  - [ ] `MailVariableCatalog` + `MailVariables` builder: `system()`, `event(Event, locale)`, `recipient(name…)`, `registration(...)`, `speaker(...)`, `subscriber(...)`, `venue(...)`, `task(...)`, `qna(...)`; aliases applied when the map is built (both names present).
  - [ ] `MailFormats`: long date per locale, time, links.
  - [ ] Unit tests: every seeded template placeholder known; aliases; formats.
- [ ] **Task 3 — Pipeline** (AC: 5, 8)
  - [ ] `MailContent`, `MailContentResolver`, `MailRenderer.render(MailContent, Map)`; keep `MailRenderer.body` delegating until the last caller is moved, then remove.
  - [ ] Newsletter: `NewsletterDraft` carries `MailContent`; job and retry resolve once (AC8 test).
- [ ] **Task 4 — Move the services, one commit each, goldens green after each** (AC: 2, 5, 6, 7)
  - [ ] Registration (confirmation, organizer-added, waitlist confirmation), deregistration, waitlist promotion, inbound-confirmation, task reminder, Q&A notification, speaker invitation, speaker acceptance, speaker reminders, newsletter, registrant notices, venue, partners (16.9b, after rebase).
- [ ] **Task 5 — Adapters** (AC: 9)
  - [ ] `AudienceAdapter.variables(...)`; `allowedVariables()` from catalog scopes.
- [ ] **Task 6 — Visible changes** (AC: 4, 6)
  - [ ] Apply long `eventDate` and `supportUrl`; update the goldens in ONE commit whose diff shows exactly those changes.
- [ ] **Task 7 — Verify**
  - [ ] Full EMS suite, Vitest unaffected, checkstyle; local dev: one mail of each kind in `/dev/emails`.
  - [ ] Docs: `docs/architecture/06-backend-architecture.md` (pipeline + catalog), admin user guide (variable list).

## Out of scope (follow-ups)

- **16.12** HTML-in-Java mails into the template store (`NotificationService` digest, `QualityReviewService`); partner invite once partner-coordination can ask EMS to render.
- **16.13** One `Mail` value for `EmailService` (6 send variants + the 2 list variants of #1176).
- Additional-email verification (company-user-management) keeps its classpath template.

## Dev Notes

- Organizer variables (`organizerName`, `organizerEmail`) come from configuration (`app.email.organizer-*`), so they are `SYSTEM` scope.
- Render order: `MailRenderer` replaces variables in the content, merges `batbern-default`, replaces again. The copied services merged first and replaced once. Different bytes only when a value contains `{{…}}` or a template has no layout key (V63 set `batbern-default` on all seeded templates); the goldens show it.
- Credential variables (`deregistrationUrl`, `confirmationUrl`, `unsubscribeLink`, `temporaryPassword`, `registrationCode`): flagged in the catalog, so a test send gets dummies and 16.5's preview hint can name them.

## Dev Agent Record

### Agent Model Used

### Completion Notes List

### File List

## Change Log

- 2026-11-01: Story written from the owner discussion and Winston's review.
