---
title: 'gsw-BE can be chosen and saved, dates follow the UI language'
type: 'bugfix'
ticket: '6'
created: '2026-11-18'
status: 'done'
baseline_revision: '359c6e8ccc1b8f6cf8939cb9c2c1dd67ea0dc4a5'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** (#1222 items 5, 6; #1223 item 4) `gsw-BE` ships as a UI locale but is missing from the language switchers and cannot be saved as profile language: `user_profiles.pref_language` is `VARCHAR(2)`, the entity column has `length = 2`, and both users-api enums (`UserSettings.language`, `UpdateUserSettingsRequest.language`) list only two-letter codes. Dates show in German in non-German UIs ("22. Okt. 2026" on the Registrations tab, "Freitag, 6. November 2026" on the public homepage) because components format with a hard-coded German locale.

**Approach:** Widen the column (new CUMS migration, `VARCHAR(10)`; widening is safe in a rolling deploy), the entity, both spec enums and the internal spec's language field if it restricts it; regenerate types; add `gsw-BE` to every language switcher. One helper maps the UI language to an `Intl` locale and every date the UI formats goes through it, no hard-coded locale left.

## Boundaries & Constraints

**Always:** Spec first, then `npm run generate:api-types`. One UI-language → Intl-locale function (e.g. in `src/utils/date/`), used by `dateFormat.ts` and every component that formats a date; mapping: `en` → `en-US` (expected "Oct 22, 2026"), `de` → `de-CH`, `gsw-BE` → `de-CH`, `fr`/`it`/`rm` → their `-CH` form where `Intl` supports it (`rm` falls back to `de-CH` if unsupported), others their plain tag. Tests through real i18next and the MSW harness (C6), touched tests leave own-code mocks. Audit the class: every `toLocaleDateString`, `toLocaleString`, `toLocaleTimeString`, `Intl.DateTimeFormat`, date-fns `format` with a fixed locale in `src` (46 files call these today); fix each hard-coded or wrongly derived locale.

**Never:** Change backend mail date formatting (mail language rule is story 5's `MailLocales`). Edit an applied migration.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Save gsw-BE | `PATCH /users/me/settings {language:"gsw-BE"}` | 200, GET returns `gsw-BE` | — |
| Unknown language | `{language:"xx"}` | 400 | — |
| EN date | UI `en`, 2026-10-22 | "Oct 22, 2026" on Registrations tab and homepage | — |
| DE date | UI `de` | German format | — |
| gsw-BE date | UI `gsw-BE` | Swiss German/German format, no crash | — |
| Switcher | language menu | lists gsw-BE (label from locales) | — |

</frozen-after-approval>

## Code Map

- `services/company-user-management-service/.../domain/UserPreferences.java:30-32` -- `@Column(name="pref_language", length=2)`; check `UserSettings.java` for the same; new migration after `V33__upload_file_name.sql` (next free number: check the folder) `ALTER TABLE user_profiles ALTER COLUMN pref_language TYPE VARCHAR(10)`; V15 already dropped the CHECK.
- `docs/api/users-api.openapi.yml:3483,3558` -- language enums; also grep `docs/api/internal/cums-internal.openapi.yml` and other specs for a two-letter language pattern/enum (`CumsContractHarness` tests on the EMS side validate against it; story 5 noted gsw-BE could not pass there).
- EMS side: `MailLocales` already maps `gsw*` to German; with the contract open, add the deferred SesRecorder check: a `gsw-BE` CUMS user gets the German mail (story 5 left it at unit level).
- Language switchers: `web-frontend/src/App.tsx`, `components/auth/LoginForm/LoginForm.tsx`, `components/public/Navigation/PublicNavigation.tsx` (grep `LanguageSwitcher|languageSwitcher|SUPPORTED_LANGUAGES`), the profile settings language select (`UserSettingsTab.tsx`), `src/i18n/config` supported languages, `types/auth.ts` `UserContextPreferences.language` (`'en'|'de'|'fr'|'it'`, too narrow) and `AuthContext` `normalizeLanguage`.
- Dates: `src/utils/date/dateFormat.ts` (`formatDate/formatTime/formatDateTime/formatRelativeTime`, take a locale); the Registrations tab of the event page and the public homepage event card (find where "22. Okt." / weekday-long formats are produced); then the 46 files that format dates.
- Docs: `docs/architecture/05-frontend-architecture.md` i18n section, `04-api-user-management.md` language field.

## Tasks & Acceptance

**Execution:**
- [x] RED: CUMS IT saves and reads back `gsw-BE` and rejects `xx`; component tests for the Registrations tab and the homepage in `en` show "Oct 22, 2026"; a unit test for the locale mapping
- [x] spec, types, migration, entity
- [x] switchers and auth/context language types
- [x] locale helper; every date formatter in `src` through it
- [x] EMS: gsw-BE CUMS user → German mail through the SesRecorder
- [x] docs

**Acceptance Criteria:**
- Given a grep over `web-frontend/src` (tests excluded), when looking for a hard-coded date locale (`'de-CH'`, `'de'` passed to a date formatter), then only the mapping helper names one.

## Design Notes

Architecture note: modules touched: CUMS user settings (schema, entity, spec), frontend i18n, date utils and the screens that format dates, EMS mail test. No new dependencies or endpoints; `PATCH /users/me/settings` and the internal user read accept a longer language. Reused: `dateFormat.ts`, MSW harness, `CumsContractHarness`, `SesRecorder`. Deviations: none.

## Verification

**Commands:**
- `./gradlew --max-workers=4 :services:company-user-management-service:test`, then `:services:event-management-service:test` (one at a time, tee'd) -- green.
- `cd web-frontend && npm run generate:api-types && npm run type-check && npm run lint && npx vitest run --maxWorkers=4` on every touched folder (and the full Vitest suite once at the end) -- green.
- `make check-test-ratchet`, `make check-i18n-keys` -- clean.

**Rack rules:** one heavy build at a time (never two Gradle/Vitest runs in parallel), Gradle `--max-workers=4`, Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Run checkstyleMain and checkstyleTest for every service touched. Do not commit or push; leave the changes in the working tree. A bug found on the way is fixed too and reported (initiative C9).

## Implementation Notes

- One CUMS rule `domain/UserLanguages` replaces seven places that chose a user's language (JIT and reconciliation stored `gsw-BE` as `de`; five mail paths sent Swiss German users English). The post-confirmation Lambda normalizes the same way; `UserSettingsService.toDto` reads an unknown stored value as its normalized code instead of throwing.
- Frontend: `i18n/languages.ts` lists the 10 UI languages for every switcher; `utils/date/intlLocale.ts` (+ `dateFnsLocale.ts`) is the one UI-language → date-locale mapping, applied to ~60 files incl. date pickers and display times (English UI now shows 12-hour times, en-US). `mailLanguage()` fixes three `startsWith('de')` choices that sent gsw-BE English.
- `tsconfig.test-harness.json` still excluded `TaskTemplateEditModal.test.tsx` after story 3 (type-check red on HEAD): removed from the list.
- Verification after review fixes (orchestrator): CUMS 989, EMS 3056 (before the fixes, untouched since), full Vitest 474 files / 5994 tests, type-check, lint, ratchet, i18n keys green; post-confirmation Lambda 51 tests (implementer).

## Review Triage Log

Quick review, 9 findings: 8 patched, 1 rejected.
- high, patched: the post-confirmation Lambda wrote the raw sign-up language (e.g. `de-CH`) into the widened column and `toDto` threw on it. Lambda normalizes; read tolerant; tests on both sides.
- low, patched: `types/user.ts` language type lacked gsw-BE.
- low, patched: `EventCard.test.tsx` kept `vi.mock('react-i18next')` (touch rule); now real i18next.
- medium, patched: partnership date pickers had no `adapterLocale` (English everywhere).
- low, patched: `EmailTemplatesTab` fixed `dd.MM.yyyy`; re-grep fixed three `HH:mm` display times.
- low, patched: `EventsParticipatedTable` formatted into state, stale after a language switch.
- low, patched: V34 now updates the column comment.
- low, patched: `UserLanguages` one list, tested against both generated `LanguageEnum`s.
- false, rejected: "homepage not tested with Oct 22, 2026". The homepage uses the long format by design ("Friday, November 6, 2026"), tested in en.
