# Story 16.11: One mail pipeline and variable catalog

Status: review

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

- [x] **Task 1 — RED: golden tests first** (AC: 6)
  - [x] `MailGoldenTest` (EMS, Testcontainers, real `EmailService` via `CALLS_REAL_METHODS`, fixed event/recipient fixtures, `currentYear` normalised): renders every template-based mail through today's service code and compares with `src/test/resources/mail-golden/{service}-{template}-{locale}.html|.subject`. First run writes the files (`-Dmail.golden.update=true`), committed as their own commit.
- [x] **Task 2 — Catalog** (AC: 1–4)
  - [x] `MailVariableCatalog` + `MailVariables` builder: `system()`, `event(Event, locale)`, `recipient(name…)`, `registration(...)`, `speaker(...)`, `subscriber(...)`, `venue(...)`, `task(...)`, `qna(...)`; aliases applied when the map is built (both names present).
  - [x] `MailFormats`: long date per locale, time, links.
  - [x] Unit tests: every seeded template placeholder known; aliases; formats.
- [x] **Task 3 — Pipeline** (AC: 5, 8)
  - [x] `MailContent`, `MailContentResolver`, `MailRenderer.render(MailContent, Map)`; keep `MailRenderer.body` delegating until the last caller is moved, then remove.
  - [x] Newsletter: `NewsletterDraft` carries `MailContent`; job and retry resolve once (AC8 test).
- [x] **Task 4 — Move the services, one commit each, goldens green after each** (AC: 2, 5, 6, 7)
  - [x] Registration (confirmation, organizer-added, waitlist confirmation), deregistration, waitlist promotion, inbound-confirmation, task reminder, Q&A notification, speaker invitation, speaker acceptance, speaker reminders, newsletter, registrant notices, venue. **Partners: not here.** The partner adapter exists only on the 16.9b branch; it moves onto the catalog when 16.9 is rebased onto this story.
- [x] **Task 5 — Adapters** (AC: 9)
  - [x] `AudienceAdapter.variables(...)`; `allowedVariables()` from catalog scopes.
- [x] **Task 6 — Visible changes** (AC: 4, 6)
  - [x] Apply long `eventDate` and `supportUrl`; update the goldens in ONE commit whose diff shows exactly those changes.
- [ ] **Task 7 — Verify**
  - [x] Full EMS suite, shared-kernel suite, checkstyle. Vitest unaffected (no frontend change).
  - [ ] Local dev: one mail of each kind in `/dev/emails`. **Not done**; the goldens render every template-based mail through the real service code against the seeded store (Testcontainers), which covers the same render path.
  - [x] Docs: `docs/architecture/06-backend-architecture.md` (pipeline + catalog), admin user guide (variable list).

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

Claude Opus 5.5 (Amelia, bmad-dev-story), 2026-11-01/02.

### Completion Notes List

- Names differ from the story text: the catalog is the enum `MailVariable` plus `MailVariables` (builder) and `MailVariableFactory` (SYSTEM and EVENT values, formats); the resolver is `MailPipeline.template` / `storedTemplate`, not a separate `MailContentResolver`. `MailFormats` was not needed as its own class (`MailVariableFactory.longDate`).
- AC6: 42 goldens written before the refactor (commit `test(mail): golden files…`). Every service move kept them byte-identical, except three production defects the goldens exposed and the moves fixed, each in its own commit with the golden diff: deregistration mail (`{{logoUrl}}` and `{{currentYear}}` unfilled), waitlist promotion (`{{logoUrl}}` unfilled), unsubscribe confirmation (no layout, `{{baseUrl}}` unfilled).
- AC4 visible commit: `eventDate` long in every mail. English is "Friday, 5 March 2027"; the newsletter used to print "Friday, 5. March 2027" (German ordinal dot) and is corrected with it. `supportUrl = {baseUrl}/support` everywhere; **no seeded template uses `supportUrl` today**, so it changes no mail yet.
- Speaker reminder goldens normalise the deadline (today + 5 days) to `{DEADLINE}`; the first version was date-dependent and failed a day later.
- AC9: adapters declare `scopes()`; `allowedVariables()` and `credentialVariables()` are interface defaults from the catalog; `variables(context, recipient, test)` returns the map `render` uses (newsletter IT proves equality). `MailRenderer.body` removed.
- Extra in this PR (peer request, owner decision 2026-11-02): `fix(email)` masks every address of a joined group-mail To: in logs (`LoggingUtils.maskEmail`, `maskAll`).
- **Open unification decisions for the owner** (kept as overrides, each marked in code): `eventTime` with "Uhr" also in English (registration confirmation, invitation, acceptance) and "ab X Uhr" / "from X" in the newsletter; `venueAddress` empty instead of "TBA" (waitlist), `venueName` empty instead of "TBA" (venue mail, newsletter); speaker invitation HTML-escapes values and derives `eventUrl`/`supportUrl`/`logoUrl` from the login URL host; `dashboardLink` differs by audience; the invitation still throws when its template is missing.
- Results: full EMS suite 2249 tests, 0 failed, 18 skipped (before rebase onto 0a9591bc); shared-kernel suite green after the masking fix.

### File List

New (EMS `services/event-management-service/src/`): `main/java/ch/batbern/events/mailcenter/render/{MailContent,MailPipeline,RenderedMail}.java`, `main/java/ch/batbern/events/mailcenter/variables/{MailVariable,MailVariables,MailVariableFactory}.java`, `test/java/ch/batbern/events/mailcenter/MailGoldenTest.java`, `test/java/ch/batbern/events/mailcenter/compose/AudienceAdapterVariablesTest.java`, `test/java/ch/batbern/events/mailcenter/render/MailPipelines.java`, `test/java/ch/batbern/events/mailcenter/variables/MailVariableCatalogTest.java`, `test/resources/mail-golden/*` (42 files).

Modified (EMS main): `mailcenter/compose/{AudienceAdapter,MailRenderer,NewsletterAudienceAdapter,RegistrantAudienceAdapter,SpeakerAudienceAdapter,VenueAudienceAdapter}.java`, `service/{Deregistration,InboundEmailConfirmation,Newsletter,RegistrationEmail,SlidesOnline,SpeakerAcceptance,SpeakerInvitation,SpeakerReminder,TaskReminder,WaitlistPromotion}EmailService.java` (names abbreviated), `service/QnaNotificationService.java`, `service/VenueCoordinationService.java`.

Modified (EMS test): `controller/VenueCoordinationControllerIntegrationTest.java`, `mailcenter/MailComposerControllerIntegrationTest.java`, `service/{DeregistrationEmailService,NewsletterEmailService,RegistrationEmailService,SlidesOnlineEmailService,SpeakerAcceptanceEmailService,SpeakerInvitationEmailService,SpeakerModeratorReplyTo,TaskReminderEmailService}Test.java`.

Shared kernel: `src/main/java/ch/batbern/shared/utils/LoggingUtils.java`, `src/test/java/ch/batbern/shared/service/EmailServiceTest.java`, `src/test/java/ch/batbern/shared/unit/utils/LoggingUtilsTest.java`.

Docs: `docs/architecture/06-backend-architecture.md`, `docs/user-guide/administration/email-templates.md`, `docs/user-guide/appendix/changelog.md`, `docs/prd/epic-16-mail-center.md`, `_bmad-output/implementation-artifacts/sprint-status.yaml`.

## Change Log

- 2026-11-01: Story written from the owner discussion and Winston's review.
- 2026-11-02: Implemented; status review. Masking fix added on request.
