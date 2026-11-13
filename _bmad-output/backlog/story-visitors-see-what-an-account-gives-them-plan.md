---
title: 'Visitors see what a BATbern account gives them before they create one'
type: 'feature'
ticket: '1'
created: '2026-11-13'
status: 'built'
baseline_revision: '60a0923dd58f96e7175060a80104598bcccdfb18'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/backlog/story-visitors-see-what-an-account-gives-them.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The account teaser in the confirmation mail, the wizard's last step and the success pages promises vague or untrue things ("exclusive content", "personalized event recommendations").

**Approach:** Replace it everywhere with the same four concrete benefits, add one public page that lists them, and link that page from the mail and the wizard. The mail rows already in the production database are updated by a migration.

**Wording (owner approves before commit):**

| | EN | DE |
|---|---|---|
| Heading | What a free account gives you | Was Ihnen ein kostenloses Konto bietet |
| 1 | Ask questions and comment on the talks, from two weeks before to two weeks after the event | Fragen und Kommentare zu den Vorträgen, von zwei Wochen vor bis zwei Wochen nach dem Event |
| 2 | Suggest topics for coming BATbern events | Themen für kommende BATbern-Events vorschlagen |
| 3 | All your BATbern visits in one place, including registrations from before you had an account | Alle Ihre BATbern-Besuche an einem Ort, auch Anmeldungen aus der Zeit vor Ihrem Konto |
| 4 | Your company's logo next to your name | Das Logo Ihrer Firma neben Ihrem Namen |
| Button | Create a free account | Kostenloses Konto erstellen |
| Link | More about the account | Mehr zum Konto |

## Boundaries & Constraints

**Always:**
- One source of the four benefits in the frontend (one i18n block, all 10 locales); mail templates DE and EN only (other locales fall back to EN).
- The migration changes only the account block, and only in rows that still hold the shipped wording. A row an admin has edited stays as it is.
- The public page is reachable without a login, prerendered and in the sitemap like `/about`.

**Never:**
- No change to the confirmation link, the 48-hour rule, the calendar text or anything else in the mail.
- No change to registration steps, fields or validation.
- No teaser on the general website (owner 2026-11-13).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| New registration, DE | anonymous registration | mail shows the four DE benefits, button to create account, link to the page | — |
| Admin-edited template | DB row differs from shipped wording | migration leaves it unchanged | — |
| Fresh DB | no rows yet | seeder inserts the new file wording | — |
| Wizard last step / success page | any locale | four benefits in that locale, link to the page | — |
| Logged-in visitor opens the page | logged in | page shows; button leads to their profile instead of sign-up | — |

</frozen-after-approval>

## Code Map

- Mail: `services/event-management-service/src/main/resources/email-templates/registration-confirmation-{en,de}.html` L34-42 (`info-box`, `{{createAccountUrl}}`). DB copies in `email_templates` (V62); `EmailTemplateSeedService` (@PostConstruct, L42) only inserts missing rows, never updates.
- Migration: EMS next free is **V155** (V154 is #1309's logos drop). Pattern: `V76__add_deregistration_link_to_confirmation_templates.sql` (`UPDATE ... SET html_body = replace(...) WHERE template_key='registration-confirmation' AND locale=... AND html_body LIKE '%<old block>%'`).
- Mail tests: `mailcenter/MailGoldenTest.java:179` (golden files to update), `service/RegistrationEmailServiceTest.java:811`, `service/EmailTemplateSeedServiceTest.java`.
- Wizard last step: `web-frontend/src/components/public/Registration/ConfirmRegistrationStep.tsx` L175-184, keys `confirmStep.account.{title,message}` (`registration.json` L126-129). Test: `__tests__/ConfirmRegistrationStep.test.tsx` (no mocks).
- Success page: `web-frontend/src/pages/public/RegistrationSuccessPage.tsx` L107-124, keys `successPage.createAccount{Title,Text}`; button label hardcoded "Create Account". Confirm page: `ConfirmRegistrationPage.tsx` L127-128 (`confirmation.success.createAccount`; "Back to Home" hardcoded). No tests for either page.
- Public page pattern: `pages/AboutPage.tsx` (`PublicLayout`), route `App.tsx` L763, prerender `scripts/prerender.mjs` L48-52, sitemap `vite.config.ts` L185, `authRules.ts` `PUBLIC_PATHS` L58-66 (prefix match: `/account` is a role path, so the new path must not start with it).

## Tasks & Acceptance

**Execution:**
- [x] EMS test first: a migration test (pattern `V154DropLogosTableMigrationIntegrationTest`) inserts a row with the old block and one with an edited block, migrates, asserts the first has the new wording and the second is unchanged; update golden files.
- [x] `registration-confirmation-{en,de}.html` -- new block (heading, four items, button, link to `https://www.batbern.ch/why-an-account`, or the existing base-URL variable if templates have one).
- [x] `V155__account_benefits_in_confirmation_mail.sql` -- guarded replace of the old block, DE and EN.
- [x] Frontend: shared `AccountBenefits` component (list + link) used by `ConfirmRegistrationStep`, `RegistrationSuccessPage`, `ConfirmRegistrationPage` and the new page; keys in all 10 locales; translate the hardcoded labels on the two pages.
- [x] New page `pages/public/WhyAnAccountPage.tsx` at `/why-an-account`: route, prerender, sitemap, `PUBLIC_PATHS`; footer link next to About.
- [x] Tests (MSW where data is fetched): the step and both pages show the four benefits and the link; the page renders logged out and logged in (button target differs).
- [x] Remove the now unused old keys (`confirmStep.account.message`, `successPage.createAccountText`, ...) in all locales; changelog entry.

**Acceptance Criteria:**
- Given a registration without account, when the confirmation mail arrives in local dev (`/dev/mails`), then it shows the four benefits in the registrant's language (DE or EN) and links to the page.
- Given the page `/why-an-account`, when opened logged out in any of the 10 locales, then it lists the four benefits and offers sign-up.

## Implementation Notes

**Built (2026-11-13):**
- Mail: `registration-confirmation-{de,en}.html` account block now has the approved heading, four items, button "Create a free account" / "Kostenloses Konto erstellen" (still `{{createAccountUrl}}`, email prefilled) and a link "More about the account" / "Mehr zum Konto" to `{{baseUrl}}/why-an-account`. `{{baseUrl}}` is the existing SYSTEM mail variable (`MailVariableFactory`, `app.base-url` = `APP_BASE_URL` = `https://<frontendDomain>` in the EMS stack), so no URL is hardcoded. Nothing else in the mail changed.
- `V155__account_benefits_in_confirmation_mail.sql`: per locale, `replace(html_body, <old block>, <new block>)` guarded by `strpos(html_body, <old block>) > 0` (exact match, dollar-quoted; `strpos` instead of `LIKE` so `_`/`%` in the block are not wildcards). Rows whose account block differs are left alone; idempotent.
- Old block guarded on: the info-box as in the classpath files since #809 (354b81692), which states it "resyncs every seed file to the live prod content". `git diff 354b81692 HEAD` on both files shows only V124's three lines changed since, none inside the block. The local dev DB (`batbern-dev-postgres`, `batbern_development`) rows equal the files byte for byte after the subject line (checked with `diff`). That DB is a long-lived local DB, not a prod snapshot (V124 installed there 2026-10-18 16:38 UTC), so this does not prove the prod bytes; the production rows were not read. If a prod row differs, the guard matches nothing and the row keeps the old wording (safe, but then the change would not reach prod mail).
- Frontend: `components/public/AccountBenefits/AccountBenefits.tsx` is the one source (keys `registration:accountBenefits.*`, all 10 locales). Used by `ConfirmRegistrationStep` (no sign-up button, page link opens in a new tab so the form is kept), `RegistrationSuccessPage` (sign-up with the email prefilled), `ConfirmRegistrationPage` (sign-up) and the new `pages/public/WhyAnAccountPage.tsx` (h1; logged out: sign-up to `/auth/register`; logged in: "Go to your profile" to `/profile`).
- `/why-an-account`: route in `App.tsx` next to `/about`, `PUBLIC_PATHS`, `scripts/prerender.mjs`, `scripts/preview-prerender.mjs`, sitemap in `vite.config.ts`, footer link (label = the heading key) next to About.
- Hardcoded labels translated: "Back to Home", "We've sent a confirmation email to", the 48-hour line (success page); "Back to Home", the already-confirmed line and the five error messages (confirm page).
- Old keys removed in all locales: `confirmStep.account`, `successPage.createAccount{Title,Text}`, `confirmation.success.createAccount`; their two lines removed from the `RegistrationWizard.test.tsx` i18n stub.
- Docs: changelog entry; `notifications.md` (mail content); `05-frontend-architecture.md` and `02-infrastructure-deployment.md` (route lists).

**Tests added:** `V155AccountBenefitsInConfirmationMailMigrationIntegrationTest` (4: shipped rows get the new wording and equal today's file; edited block left alone; edit elsewhere in the mail kept while the block is replaced; no rows, no-op); `MailGoldenTest` goldens for both locales updated; `WhyAnAccountPage.test.tsx` (13: logged out, logged in, each of the 10 locales has its own block, approved DE wording); `RegistrationSuccessPage.test.tsx` (2), `ConfirmRegistrationPage.test.tsx` (2, MSW `confirmRegistration`), `ConfirmRegistrationStep.test.tsx` (benefits + new-tab link; obsolete CTA test removed), `PublicFooter.test.tsx` (link), `authRules.whyAnAccount.test.ts` (2). No `vi.mock` of our own code.

**Verified (run 2026-11-13):**
- `./gradlew :services:event-management-service:test --max-workers=4`: BUILD SUCCESSFUL, 2794 tests, 0 failures, 0 errors, 15 skipped (V155 IT red first: "No migration with a target version 155", then 4/4 green).
- `npx vitest run --maxWorkers=4`: 468 files, 5929 passed, 36 skipped, 20 todo, 0 failed.
- `npm run type-check`, `npm run lint`, `npm run check:api-paths` (276/276), `npm run check:i18n-keys` (0 missing): pass.
- `make check-test-ratchet`: no RAISED, no STALE.
- `infrastructure`: `npx jest test/unit/frontend-stack.test.ts`: 18/18 (red first on the new route).

**Not verified:** the manual checks (register anonymously in local dev and read `/dev/mails`; open `/why-an-account` in a browser logged out and in) were not run. The production rows were not read (see above). The prerender crawl (`npm run build:prerender`) was not run.

**Deviations:**
- Migration IT follows `V150...MigrationIntegrationTest` (own Testcontainers PostgreSQL, Flyway to V154, insert rows, migrate to V155) instead of `V154...` (`AbstractIntegrationTest`): the V154 pattern only sees a fully migrated, freshly seeded DB and cannot stage the pre-V155 rows.
- CloudFront `RouterFunction` (`infrastructure/lib/stacks/frontend-stack.ts`) gets `/why-an-account` in its prerendered list, with its unit test. Without it the prerendered HTML is never served (the page would still work as CSR). The plan's Code Map did not name this file.
- Page and footer: the profile button label ("Go to your profile" / "Zu Ihrem Profil") is not in the approved wording table; the footer link reuses the approved heading.
- Success page: the sign-up button now also shows without an email (to `/auth/register`); before, it was hidden.

## Plan Change Log

- 2026-11-13: owner approved plan and wording ("Approved and continue"); baseline moved to 60a0923dd after the rebase onto develop with #1309.

## Review Triage Log

| # | Finding | Verdict | Action |
|---|---|---|---|
| 1 | Production rows were never read; the guard may match nothing | checked | Read both production rows 2026-11-13 through `GET /email-templates/registration-confirmation/{de,en}` (read-only): the exact old block is present in each (LF, updated 2026-10-18). V155 will apply. |
| 2 | `WhyAnAccountPage.test` logged-in case counted every `listitem` on the page (footer included) | patch | Scoped to `account-benefits-list`, exactly 4. |
| 3 | gsw-BE lacks ~28 older `successPage.*` / `confirmation.*` keys | reject | Existed before; gsw-BE falls back to `de` (`i18n/config.ts`), which Bernese readers read. Not this story. |
| 4 | Confirm page stored the translated error text, so it ignored a later language switch | patch | Stores the key; test switches to DE after a failure (red without the fix). |
| 5 | Prerendered page shows sign-up briefly to a logged-in visitor | defer | Cosmetic, by design of the prerender (logged-out HTML). |

Reviewer verified by reading: migration replace output equals the new template files byte for byte; `{{baseUrl}}` is set for this template at send time; `/why-an-account` is in every route list (App, PUBLIC_PATHS, prerender, preview-prerender, sitemap, CloudFront router); no removed key is still referenced; no own-code `vi.mock`.

## Design Notes

Architecture note:
- Modules: EMS mail templates + migration; frontend registration components, a new public page.
- New dependencies: none.
- New endpoints: none.
- Reused: `PublicLayout`, the About page wiring, the V76 guarded-replace migration pattern.
- Deviations: none.

Risk medium (not low as in the story): a migration rewrites the production confirmation mail every new registrant gets; the guard keeps admin edits.

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --max-workers=4` -- 0 failures
- `cd web-frontend && npx vitest run --maxWorkers=4` -- 0 failures; `npm run type-check`, `npm run lint`, `npm run check:api-paths`, i18n key check pass
- `make check-test-ratchet` -- no RAISED line

**Manual checks:**
- Local dev: register anonymously in DE and EN, read the mail in `/dev/mails`; open `/why-an-account` logged out and logged in.
