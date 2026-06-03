# Story 12.6a: Split the registration full-name field into first + last name (SSO Phase 2 follow-up)

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **person self-registering for a BATbern account**,
I want **to enter my first name and last name in two separate fields** instead of one "Full Name" field,
so that **my name is split correctly (no `firstName="Anna"`, `lastName="Maria Schmidt"` guesswork) and the generated `username = firstname.lastname` + the `user_profiles` first/last columns are populated accurately.**

This is a small **frontend-only data-quality** follow-up surfaced during SSO Epic 12 work. Today the registration wizard collects a single `fullName` field and splits it with a fragile heuristic (`fullName.trim().split(/\s+/)` in `useRegistration.ts:31-33`): the first whitespace token becomes `firstName` and the remainder becomes `lastName`. That mangles multi-word given names ("Anna Maria"), compound surnames ("von der Berg"), and mononyms. The DB stores `first_name`/`last_name` separately (NOT NULL) and derives `username = firstname.lastname`, so a bad split produces a permanently bad username.

**Scope guard (decided 2026-06-03):** this story does **NOT** change where names are stored in Cognito — they continue to be packed into the `custom:preferences` JSON exactly as today (`authService.signUp` already accepts `firstName`/`lastName`). Moving names out of `custom:preferences` into standard `given_name`/`family_name` was explicitly **deferred** (auth-path churn, marginal benefit, the JSON stays for `language` anyway, and the JIT/PostConfirmation fallback chain must remain for migration). This story is purely "capture two clean fields instead of one and stop guessing the split."

## Acceptance Criteria

1. **(Two fields replace one — i18n-correct labelling.)** `RegistrationStep1.tsx` renders **two** required text inputs in place of the single `fullName` field (`RegistrationStep1.tsx:89-116`). The form field / react-hook-form register names stay **`firstName`** and **`lastName`** (to match `authService.signUp`'s existing contract + the DB columns), but the user-facing **labels are "Given name" / "Family name"** (i18n key `register.step1.givenNameLabel` / `familyNameLabel`) — NOT "First/Last", which is order-presumptive (see AC5b + the i18n Dev Note). Both carry the same validation the `fullName` field had, applied per field: required, `minLength: 2`, `maxLength: 100` (per field), and the existing Unicode-letter pattern `/^[\p{L}\s.'-]+$/u` (preserve it verbatim — added for the 2026-05-18 "René Strauss"/"Renée Gressly" diacritics incident; preserve the comment at `:101-105`). The `handleContinue` `trigger([...])` list (`:55`) is updated from `'fullName'` to `'firstName', 'lastName'`.

2. **(Form data carries firstName/lastName, not fullName.)** `RegistrationFormData` (`useRegistration.ts:11-18`) replaces `fullName: string` with `firstName: string` + `lastName: string`. The `RegistrationWizard` `useForm` `defaultValues` (`RegistrationWizard/RegistrationWizard.tsx:24`) replaces `fullName: ''` with `firstName: ''`, `lastName: ''`.

3. **(Drop the split heuristic.)** `useRegistration.ts:30-33` no longer splits a full name. It passes `data.firstName.trim()` and `data.lastName.trim()` straight into `authService.signUp({ ..., firstName, lastName, ... })`. **No change to `authService.signUp`** — it already takes `firstName`/`lastName` (`authService.ts:321-322`) and packs them into `custom:preferences` (no Cognito storage-scheme change; AC scope guard).

4. **(Confirmation step shows the assembled name.)** `RegistrationStep2.tsx` currently `watch('fullName')` and renders it (`:45, :103`). Update it to `watch('firstName')` + `watch('lastName')` and render them (e.g. `` `${firstName} ${lastName}` `` for the existing single-line display, or two rows — pick the lower-churn option and keep the existing label/markup). No behavioural change beyond the field source.

5. **(i18n — all 10 locales, given/family terminology, culturally-appropriate placeholders.)** Add `register.step1.givenNameLabel`, `register.step1.givenNamePlaceholder`, `register.step1.familyNameLabel`, `register.step1.familyNamePlaceholder` and `register.errors.givenNameRequired/givenNameTooShort/givenNameTooLong/givenNameInvalid` + the `familyName*` equivalents to **all 10** locale files (`public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/auth.json`) — per the CLAUDE.md "Frontend UI i18n — all 10 locales" rule. EN + DE first-class; the other 8 may be straight translations. The now-unused `fullName*` keys (`register.step1.fullNameLabel`, `register.step1.fullNamePlaceholder`, `register.errors.fullName*`) are **removed from all 10 locales** (no dangling keys). **Placeholders must be locale-appropriate, not transliterated** — e.g. EN given="Anna"/family="Schmidt"; DE/gsw-BE a German-Swiss name; **ja a Japanese name (e.g. given「花子」/ family「山田」)**; fr/it/etc. a regionally plausible name. Do NOT reuse the old "John Doe" full-name placeholder.

5b. **(Locale-aware field order.)** The two fields render in the order conventional for the active UI locale: **family-name-first for locales where that is the norm (`ja`)**, given-name-first for all others. Implement as a tiny order helper keyed off `i18n.language` (e.g. a `FAMILY_NAME_FIRST_LOCALES = new Set(['ja'])` check) — the **data** mapping (`firstName`→given, `lastName`→family) is unchanged regardless of display order; only the visual sequence flips. This is the substance of "think on i18n": labels + order, not just translated strings.

6. **(Tests updated + green.)** Update the existing Vitest suites that reference `fullName`: `RegistrationStep1.test.tsx` (`:23, :69, :93`), `RegistrationStep2.test.tsx` (`:27`), `useRegistration.test.ts` (`:59, :98, :131, :163, :195, :231`), and `RegistrationWizard.test.tsx` if it seeds `fullName`. Add a positive assertion that a multi-word entry no longer mis-splits — e.g. firstName "Anna Maria" + lastName "Schmidt" reaches `authService.signUp` as `firstName: 'Anna Maria'`, `lastName: 'Schmidt'` (the old heuristic would have produced `firstName: 'Anna'`, `lastName: 'Maria Schmidt'`). `npm run type-check` + `npm run lint` clean; the registration suites pass.

7. **(No backend / no Cognito / no infra change.)** Touches **only** `web-frontend/`. No change to `authService.signUp`'s contract, no `cognito-stack.ts`/`writeAttributes` change, no Lambda change, no API/OpenAPI change. `[no-doc]` applies (UI form change; no business-logic/contract/state-machine change mapped in `.github/doc-drift-mappings.yml`).

## Tasks / Subtasks

- [x] **Task 1 — i18n keys in all 10 locales (AC: 5)** *(do first so the form can reference them)*
  - [x] In each of `public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/auth.json`: add `register.step1.givenNameLabel/givenNamePlaceholder/familyNameLabel/familyNamePlaceholder` and `register.errors.{givenName,familyName}{Required,TooShort,TooLong,Invalid}`. Remove `register.step1.fullNameLabel/fullNamePlaceholder` + `register.errors.fullName*`. EN + DE first-class wording; **placeholders locale-appropriate** (ja → Japanese name, de/gsw-BE → German-Swiss name, etc. — not transliterated "Anna").

- [x] **Task 2 — Split the form fields + locale-aware order (AC: 1, 5b)**
  - [x] `RegistrationStep1.tsx`: replace the single `fullName` `<TextField>` (`:89-116`) with two `<TextField>`s registered as `firstName` (label `givenNameLabel`) and `lastName` (label `familyNameLabel`), each with required + minLength 2 + maxLength 100 + the verbatim `/^[\p{L}\s.'-]+$/u` pattern (preserve the `:101-105` comment). Update `trigger([...])` (`:55`) to `['firstName', 'lastName', 'email', 'password', 'confirmPassword']`.
  - [x] Render the two fields in locale-aware order: a small `FAMILY_NAME_FIRST_LOCALES = new Set(['ja'])` (compare against `i18n.language`) flips the **visual** order to family-then-given for `ja`; given-then-family otherwise. Data mapping unchanged. Keep both as their own `<TextField>` so only the JSX sequence differs.

- [x] **Task 3 — Wizard defaults + form-data type (AC: 2)**
  - [x] `RegistrationWizard/RegistrationWizard.tsx:24`: `fullName: ''` → `firstName: '', lastName: ''`.
  - [x] `useRegistration.ts:11-18`: `RegistrationFormData` drop `fullName`, add `firstName: string; lastName: string;`.

- [x] **Task 4 — Drop the split, pass through (AC: 3)**
  - [x] `useRegistration.ts:30-33`: delete the `split(/\s+/)` block; call `authService.signUp({ ..., firstName: data.firstName.trim(), lastName: data.lastName.trim(), ... })`. Leave every other field in the signUp call unchanged.

- [x] **Task 5 — Confirmation step (AC: 4)**
  - [x] `RegistrationStep2.tsx:45,103`: replace `watch('fullName')` with `watch('firstName')` + `watch('lastName')`; render the combined name in the existing markup.

- [x] **Task 6 — Tests (AC: 6)** *(TDD: write/adjust the failing assertions first)*
  - [x] Update `RegistrationStep1.test.tsx`, `RegistrationStep2.test.tsx`, `useRegistration.test.ts`, `RegistrationWizard.test.tsx` (anything seeding `fullName`).
  - [x] Add the multi-word no-mis-split assertion (AC6): given "Anna Maria" + family "Schmidt" → `authService.signUp` receives `firstName: 'Anna Maria'`, `lastName: 'Schmidt'` (old heuristic produced `'Anna'`/`'Maria Schmidt'`).
  - [x] Add a locale-order test: with `i18n.language='ja'` the family-name field renders before the given-name field; with a given-first locale (e.g. `en`) the order is given-then-family (AC5b).
  - [x] `npm run type-check`, `npm run lint`, and the registration Vitest suites green (tee to a temp file + grep per CLAUDE.md).

## Dev Notes

### Exact touch points (verified 2026-06-03)
| File | Current | Change |
|---|---|---|
| `web-frontend/src/components/auth/RegistrationStep1/RegistrationStep1.tsx` | single `fullName` TextField `:89-116`; `trigger(['fullName',...])` `:55` | two fields firstName/lastName; updated trigger list |
| `web-frontend/src/components/auth/RegistrationStep2/RegistrationStep2.tsx` | `watch('fullName')` `:45`, render `:103` | watch firstName+lastName, render combined |
| `web-frontend/src/components/auth/RegistrationWizard/RegistrationWizard.tsx` | `defaultValues { fullName: '' }` `:24` | `{ firstName: '', lastName: '' }` |
| `web-frontend/src/hooks/useRegistration/useRegistration.ts` | `RegistrationFormData.fullName` `:11-18`; split `:30-33` | firstName/lastName fields; pass-through, no split |
| `web-frontend/src/services/auth/authService.ts` | `signUp` already takes firstName/lastName `:321-322`, packs `custom:preferences` | **NO CHANGE** |
| `public/locales/*/auth.json` (×10) | `register.step1.fullName*`, `register.errors.fullName*` | replace with firstName/lastName keys |
| Tests: `RegistrationStep1.test.tsx`, `RegistrationStep2.test.tsx`, `useRegistration.test.ts`, `RegistrationWizard.test.tsx` | seed/assert `fullName` | update to firstName/lastName + add no-mis-split assertion |

### Why no Cognito/storage change (scope guard)
The names still ride in `custom:preferences` JSON (PostConfirmation reads them for native users; canonical JIT reads `given_name`/`family_name` first then `custom:preferences` as fallback — `JITUserProvisioningInterceptor.java:114-125`). Moving native names onto standard `given_name`/`family_name` was deliberately deferred: it would touch the client `writeAttributes` (`cognito-stack.ts`), `authService.signUp`, `post-confirmation.ts`, and the JSON, while `custom:preferences` must stay anyway (for `language`, which has no standard claim) and the fallback chain must remain for already-registered users. Low reward, auth-path churn — out of scope here.

### i18n rationale & known tradeoff (the "think on i18n" decision)
Splitting one name field into two is **not** an i18n-neutral change — a given/family split is a Western
structural assumption (W3C *Personal names around the world*). We do it anyway because the **backend hard-requires**
`first_name` + `last_name` (NOT NULL) and derives `username = firstname.lastname`; a single field only pushed the
split into a fragile `split(/\s+/)` guess. To do the split *correctly* for our 10-locale audience (which includes
**`ja`**, a family-name-first culture):
- **Labels say "Given name" / "Family name"**, not "First/Last" — order-neutral, unambiguous across cultures.
- **Field order is locale-aware** — family-first for `ja` (AC5b). The data mapping (`firstName`=given, `lastName`=family)
  never changes; only the visual sequence flips. (`gsw-BE`, `de`, `fr`, `it`, `rm`, `es`, `fi`, `nl`, `en` are all
  given-first.)
- **Placeholders are locale-appropriate**, not a transliterated default.
- **Known tradeoff — mononyms:** both fields are required (DB NOT NULL), so a single-name user (some cultures) can't
  represent themselves cleanly. Accepted for the Bern-architects audience (overwhelmingly given/family-structured);
  documented rather than hidden. If this ever matters, the inclusive fix is a backend change (relax `last_name`
  NOT NULL + a single-field UI) — explicitly out of scope here.
- This is why the keys are `givenName*`/`familyName*` (label semantics) while the register/data names stay
  `firstName`/`lastName` (the existing `authService.signUp` + DB contract).

### Conventions
- All user-visible strings via `useTranslation('auth')` (namespace `auth`). Missing keys silently fall back to the key string — so populate all 10 locales (CLAUDE.md).
- Use `screen` queries + `userEvent` (project-context testing rules). Keep the existing field order: name fields first, then email/password.
- `language` for the signup still comes from `i18n.language` (`useRegistration.ts:46`) — unchanged.

### References
- [Source: web-frontend/src/components/auth/RegistrationStep1/RegistrationStep1.tsx:55,89-116] — fullName field + trigger list + verbatim Unicode pattern/comment
- [Source: web-frontend/src/hooks/useRegistration/useRegistration.ts:11-18,30-33,36-47] — RegistrationFormData, the split to drop, the signUp call
- [Source: web-frontend/src/services/auth/authService.ts:313-341] — signUp packs firstName/lastName into custom:preferences (NO CHANGE)
- [Source: web-frontend/src/components/auth/RegistrationStep2/RegistrationStep2.tsx:45,103] — confirmation step reads fullName
- [Source: web-frontend/src/components/auth/RegistrationWizard/RegistrationWizard.tsx:24] — useForm defaultValues
- [Source: public/locales/en/auth.json → register.step1 / register.errors] — keys to add/remove (×10 locales)
- [Source: services/.../interceptor/JITUserProvisioningInterceptor.java:114-125] — why custom:preferences stays (scope guard); CLAUDE.md "Frontend UI i18n — all 10 locales"

## Dev Agent Record

### Agent Model Used

claude-opus-4-8 (1M context) — bmad-dev-story, 2026-06-03

### Debug Log References

- `/tmp/12-6a-vitest.txt` — registration suites: 56/56 passed (Step1 24, Step2 16, Wizard 8, useRegistration 8)
- `/tmp/12-6a-tsc.txt` — `tsc --noEmit` clean
- `/tmp/12-6a-lint.txt` — `eslint src` clean (0 warnings)

### Completion Notes List

- **Task 1 (AC5):** Added `givenName*`/`familyName*` step1 + error keys to all 10 locales; removed every `fullName*` key (verified `grep` → zero dangling refs in `src/` and `public/`). Placeholders are locale-appropriate (en Anna/Schmidt, de Max/Mustermann, ja 太郎/山田, fr Jean/Dupont, etc.) — not transliterated. JSON written via script to keep formatting + key order stable (diff = 18 lines/file, name keys only).
- **Task 2 (AC1, AC5b):** `RegistrationStep1.tsx` — two `<TextField>`s registered as `firstName`/`lastName` with labels `givenNameLabel`/`familyNameLabel`; required + minLength 2 + maxLength 100 + the verbatim `/^[\p{L}\s.'-]+$/u` pattern (comment preserved on **both** fields). `trigger([...])` updated to `['firstName','lastName','email','password','confirmPassword']`. Module-level `FAMILY_NAME_FIRST_LOCALES = new Set(['ja'])`; fields built as JSX consts and rendered family-then-given for `ja`, given-then-family otherwise — only the visual sequence flips, the firstName=given/lastName=family data mapping is unchanged.
- **Task 3 (AC2):** `RegistrationFormData` drops `fullName`, adds `firstName`/`lastName`; wizard `defaultValues` updated.
- **Task 4 (AC3):** Deleted the `split(/\s+/)` heuristic; passes `data.firstName.trim()`/`data.lastName.trim()` straight into `authService.signUp` (contract untouched — names still packed into `custom:preferences` per the scope guard).
- **Task 5 (AC4):** `RegistrationStep2.tsx` now `watch('firstName')`+`watch('lastName')` and renders the combined `${firstName} ${lastName}` in the existing single-line summary markup (lower-churn option).
- **Task 6 (AC6):** Updated all 4 Vitest suites. Wizard test used the human label `/full name/i` (not the field name `fullName`) — caught on first run and fixed. Added the AC6 no-mis-split assertion (`firstName: 'Anna Maria'`, `lastName: 'Schmidt'`) and two AC5b order tests (en given-first, ja family-first via `compareDocumentPosition` on placeholders). Repurposed the old single-word test into a per-field trim test.
- **AC7:** Frontend-only — no backend/Cognito/infra/OpenAPI touched. `[no-doc]` applies.

### File List

- `web-frontend/src/components/auth/RegistrationStep1/RegistrationStep1.tsx` (modified)
- `web-frontend/src/components/auth/RegistrationStep1/RegistrationStep1.test.tsx` (modified)
- `web-frontend/src/components/auth/RegistrationStep2/RegistrationStep2.tsx` (modified)
- `web-frontend/src/components/auth/RegistrationStep2/RegistrationStep2.test.tsx` (modified)
- `web-frontend/src/components/auth/RegistrationWizard/RegistrationWizard.tsx` (modified)
- `web-frontend/src/components/auth/RegistrationWizard/RegistrationWizard.test.tsx` (modified)
- `web-frontend/src/hooks/useRegistration/useRegistration.ts` (modified)
- `web-frontend/src/hooks/useRegistration/useRegistration.test.ts` (modified)
- `web-frontend/public/locales/de/auth.json` (modified)
- `web-frontend/public/locales/en/auth.json` (modified)
- `web-frontend/public/locales/fr/auth.json` (modified)
- `web-frontend/public/locales/it/auth.json` (modified)
- `web-frontend/public/locales/rm/auth.json` (modified)
- `web-frontend/public/locales/es/auth.json` (modified)
- `web-frontend/public/locales/fi/auth.json` (modified)
- `web-frontend/public/locales/nl/auth.json` (modified)
- `web-frontend/public/locales/ja/auth.json` (modified)
- `web-frontend/public/locales/gsw-BE/auth.json` (modified)

### Change Log

| Date | Change |
|---|---|
| 2026-06-03 | Story 12.6a drafted (registration name split; frontend-only, no Cognito storage change). i18n-aware per "think on i18n": given/family labels (not First/Last), locale-aware field order (family-first for `ja`), locale-appropriate placeholders, all 10 locales; mononym NOT-NULL tradeoff documented. Status: ready-for-dev. |
| 2026-06-03 | Implemented all 6 tasks (8 ACs). Two-field given/family capture replaces single `fullName`; `split(/\s+/)` heuristic removed; locale-aware order (`ja` family-first); 10-locale i18n keys swapped (zero dangling `fullName*`). 56/56 registration tests + type-check + lint green. Status: review. |
