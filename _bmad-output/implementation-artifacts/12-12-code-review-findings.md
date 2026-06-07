# Code Review — Story 12-12 (Google Avatar Import) + follow-up c59579eb

**Date:** 2026-06-04
**Scope:** commits `74884700..c59579eb` (`feat(auth): import Google profile picture into S3 on federated sign-in (12.12)` + `fix(profile): align photo-upload types with backend allow-list`)
**Method:** 7 independent finder angles → dedup → 1 adversarial verifier per candidate (recall-biased, high effort)
**Result:** 9 findings survived verification (3 CONFIRMED, 6 PLAUSIBLE). 3 candidates refuted.

---

## Severity-ranked findings

### #1 — Transient failures permanently burn the one-and-only import attempt ⚠️ HIGH

`FederatedAvatarImportService.java:97-98` commits `pictureImportAttemptedAt` **before** the fetch
runs. Every failure after that point is terminal by construction — including failures that are
plainly *transient*:

- Google returns a one-off HTTP 5xx → `return` at the status check, no retry, ever.
- Network timeout / `IOException` → swallowed by `catch (Exception e)`, no retry, ever.
- The executor rejects the task (queue full) or **the service restarts** between the mark and
  the fetch → the fetch never even started, but the timestamp says "attempted".
- Google serves an unexpected `image/webp`/`image/gif` → `InvalidFileTypeException` from
  `ProfilePictureService`, swallowed, terminal.

AC3's stated rationale (don't clobber user uploads, don't loop on broken URLs, don't re-import
after delete) justifies *not retrying terminal outcomes*. It does **not** justify treating a 503
as terminal. A user whose first sign-in coincides with a Google blip silently never gets an
avatar, with only a WARN log nobody watches.

**Fix:** classify outcomes. Terminal (keep timestamp): success, 4xx, non-image content type,
validation rejections, SSRF-rejected URL. Transient (clear timestamp so a later request
retries): 5xx, timeout/IO errors, executor rejection.

### #2 — Lost-update race: async import does full-row save with no locking ⚠️ HIGH

`User` has **no `@Version` and no `@DynamicUpdate`** — so every `userRepository.save(user)`
emits an UPDATE that writes **all mapped columns** from the in-memory snapshot, even the ones
that writer never touched.

The diff adds a **background writer** (the avatar import on `avatarImportExecutor`) whose
final write path is `ProfilePictureService.uploadProfilePictureDirectly`
(`ProfilePictureService.java:241-270`: `findByUsername` → set 2 picture fields → `save`).
Meanwhile `UserService.updateCurrentUser` (`UserService.java:146`: sets `termsAcceptedAt` →
`save`) does the same unlocked load-modify-save — **and story 12-11's onboarding-completion
PATCH fires on exactly the same first authenticated request that dispatches the import.**

Collision timeline (all statements full-row UPDATEs):

```
t1  import thread:    SELECT user            (terms_accepted_at = NULL)
t2  onboarding PATCH: SELECT user, SET terms_accepted_at = now(), UPDATE … COMMIT ✓
t3  import thread:    SET profile_picture_url = cdn-url, UPDATE …
                      → writes ALL columns from its t1 snapshot,
                        including terms_accepted_at = NULL        ← terms acceptance ERASED
```

The symmetric direction also exists: the PATCH's full-row save can null a just-imported
`profile_picture_url`. The import re-reads fresh just before saving, so the window is the gap
between its last read and commit — small but real, and timed to coincide with onboarding.

**Fix options:** see "Locking fix — the decision" below.

### #3 — SVG is now a first-class upload format, served from cdn.batbern.ch with no XSS mitigation 🔶 MEDIUM (security)

`ProfilePhotoUpload.tsx:31` now advertises/accepts `image/svg+xml`; the avatar importer also
maps `image/svg+xml → svg`. The backend stores SVGs with `Content-Type: image/svg+xml`
(`ProfilePictureService.java:192/225`) and the CloudFront `ContentCacheHeaders` policy
(`storage-stack.ts:223-239`) sets **only Cache-Control** — no `Content-Disposition: attachment`,
no `X-Content-Type-Options: nosniff`, no CSP sandbox. A script-bearing SVG opened top-level at
its `cdn.batbern.ch` URL executes.

Mitigating context: tokens live on the `www.batbern.ch` origin, so impact is XSS on the *cdn*
origin (phishing/UI-redress), not token theft. Backend SVG acceptance pre-dates this story
(PR #174); the diff promotes it to an advertised flow and adds the import entry point.

**Fix:** add `Content-Security-Policy: sandbox` (and `X-Content-Type-Options: nosniff`,
`Content-Disposition: attachment` for svg) via the CloudFront response-headers policy — or drop
svg from the profile-picture allow-list everywhere.

### #4 — OpenAPI contract + generated types not updated (CONFIRMED) 🔶 MEDIUM (contract drift)

c59579eb synced 2 of 3 copies of the allow-list. Still stale:
- `docs/api/users-api.openapi.yml:1222` — enum `[image/png, image/jpeg, image/jpg]`: no svg,
  plus non-standard `image/jpg`.
- `web-frontend/src/types/generated/user-api.types.ts:2280` — same stale enum.

The commit was tagged `[no-doc]`, conflicting with the project rule "Update OpenAPI specs when
changing APIs / regenerate types". Three hand-maintained lists now disagree.

**Fix:** spec enum → `[image/jpeg, image/png, image/svg+xml]`, regenerate types.

### #5 — Double-dispatch race on the attempt claim 🔶 MEDIUM

`FederatedAvatarImportService.java:89-98`: guard-check and mark-attempt save are
read-then-write, unlocked. Two parallel first-sign-in API calls (the SPA does fire parallel
requests) both pass `attemptedAt == null`, both save, both dispatch → Google fetched twice, two
S3 puts under different UUID keys, one orphaned object, nondeterministic winner.

**Fix:** claim the attempt with one atomic conditional UPDATE (compare-and-set):
`UPDATE … SET picture_import_attempted_at = :now WHERE id = :id AND picture_import_attempted_at
IS NULL AND profile_picture_url IS NULL` — affected-row-count 1 ⇒ we won, 0 ⇒ someone else did.

### #6 — Permanent duplicate SELECT on every federated request (CONFIRMED) 🔷 LOW (efficiency)

`JITUserProvisioningInterceptor.java:101` runs `findByCognitoUserId` on every request; the
avatar service immediately re-runs the identical query (`FederatedAvatarImportService.java:81`).
The one-attempt guard can only short-circuit *after* the SELECT, so every federated user's
every API call pays a redundant SELECT forever.

**Fix:** JIT stashes the loaded `User` (or the resolved eligibility) in a request attribute the
avatar interceptor reads; or an in-process done-cache keyed by `sub`.

### #7 — Third hand-copy of the fetch-image-from-URL pipeline (CONFIRMED) 🔷 LOW (reuse)

`fetchAndImport` + `extensionFromContentType` duplicate `UserController.uploadProfilePictureFromUrl`
(:808-869) and `LogoController.uploadImageFromUrl` (:410-468), down to the byte-identical
`svg+xml→svg` special case. Already diverged: fetch-time size cap is 5MB / 10MB / **none**.

**Fix:** extract one shared fetch-and-validate helper (parameterized size cap); call it from
all three.

### #8 — Redirect-following defeats the SSRF guard's contract 🔷 LOW (defense-in-depth)

`AvatarImportConfig` builds the client with `followRedirects(NORMAL)`; only the *initial* URL's
host is validated. A cross-host https→https 3xx is followed un-revalidated. Exploitability is
low (claim is Google-controlled, sink is blind and image-only, http downgrade refused), but the
stated contract "only googleusercontent.com is ever fetched" is not enforced.

**Fix:** `Redirect.NEVER` + treat 3xx as a failure, or follow manually re-validating each hop.

### #9 — Size-variant upgrade regex silently misses non-canonical URL shapes 🔷 LOW

`SIZED_VARIANT_PATTERN = "=s\d+(-c)?$"` ($-anchored). A claim with a query string/fragment after
the size token, or legacy path-segment sizing (`/s96-c/`), is not upgraded → a 96px thumbnail is
stored as the permanent "original", no error. Today's canonical claims are bare `…=s96-c`
(handled); robustness gap only.

**Fix:** also handle the size token before `?`/`#` and the path-segment form, or strip
query/fragment before matching.

---

## Refuted candidates (for the record)

| Candidate | Why refuted |
|---|---|
| Case-sensitive `startsWith("image/")` | Google serves lowercase; direct egress, no re-casing proxy; downstream extension check lowercases anyway (`ProfilePictureService.java:182`). |
| gsw-BE locale shows stale "WebP" | gsw-BE never had the photo keys (pre-existing parity gap); EN fallback now shows the *correct* new text. |
| Silent ID-token coupling | Real coupling, but explicitly analyzed and documented in the 12-12 story artifact + code comments, and shared with the long-standing JIT convention — not a new, unflagged risk. |

---

## Locking fix — the decision (finding #2)

Two ways to stop the lost-update; they differ in *how conflicts are handled*:

### Option A — `@DynamicUpdate` + CAS  *(recommended)*

`@DynamicUpdate` is a single Hibernate annotation on the `User` entity. UPDATE statements then
contain **only the columns the writer actually changed**:

```sql
-- import thread, with @DynamicUpdate:
UPDATE user_profiles SET profile_picture_url = ?, profile_picture_s3_key = ?, updated_at = ?
 WHERE id = ?
-- it physically cannot touch terms_accepted_at anymore → nothing to erase
```

Combined with the CAS claim from finding #5, both diff-introduced races close:

- import ↔ onboarding/profile-edit: each writes disjoint columns → no clobbering in either
  direction;
- request ↔ request double-dispatch: the conditional UPDATE lets exactly one win.

Cost/limits: no migration, no API change, no new failure modes. Residual: two writers changing
the **same** column at the same instant are still last-writer-wins — a genuine conflict, rare,
and benign for these fields (worst case: one of two avatars wins).

### Option B — `@Version` (optimistic locking)

A `version` column (new Flyway migration) is checked-and-incremented on every UPDATE; a
conflicting save **throws** `OptimisticLockingFailureException` instead of silently overwriting.

- Pros: detects *every* conflict, including same-column ones. Textbook correctness.
- Cons: every save path in the service can now throw. The user-facing profile/onboarding PATCH
  would surface 409/500 unless catch-and-retry is added to each path; service-wide behavior
  change + migration + test churn — risky on a branch about to merge (PR #740) that also
  carries uncommitted 12-11 work.

The options compose: A now, B later as its own hardening story (A also shrinks B's future
conflict rate, since only true same-column races would ever throw).

---

## Fix pass — APPLIED 2026-06-04 (user selected: all 9 findings, Option A for #2)

All fixes applied to the working tree (uncommitted — the tree also carries Story 12-11
work). Verification: CUMS suite 664 PASSED / 0 FAILED (incl. new transient-retry + CAS
integration tests), storage-stack CDK tests 13/13, ProfilePhotoUpload 29/29, frontend
`tsc --noEmit` clean.

| # | Fix applied | Where |
|---|---|---|
| 1 | Transient (5xx/429, IO, executor rejection) → `releasePictureImportAttempt` frees the claim for a later retry; terminal (3xx/4xx, non-image, oversize, invalid claim) keeps it | `FederatedAvatarImportService`, `UserRepository`, unit + integration tests |
| 2 | **Option A**: `@DynamicUpdate` on `User` — UPDATEs carry only dirty columns; import can't clobber `terms_accepted_at`, profile edits can't null a fresh picture. (`@Version` deferred as a future hardening story.) | `User.java` |
| 5 | Atomic CAS claim: `claimPictureImportAttempt` conditional UPDATE (`WHERE attempted_at IS NULL AND picture IS NULL`), affected-rows==1 ⇒ single winner | `UserRepository`, `FederatedAvatarImportService` |
| 4 | Spec enum → `[image/png, image/jpeg, image/svg+xml]` (drops non-standard `image/jpg`); types regenerated (`user-api.types.ts:2280`) | `users-api.openapi.yml`, generated types |
| 3 | `CSP: sandbox` + `X-Content-Type-Options: nosniff` on the cdn ResponseHeadersPolicy — top-level SVG documents can't execute script; `<img>` embedding unaffected | `storage-stack.ts` + CDK test |
| 6 | JIT stashes the resolved `User` in `RESOLVED_USER_ATTRIBUTE`; avatar interceptor reuses it (fallback lookup only when JIT didn't resolve) — duplicate per-request SELECT eliminated | both interceptors, service overload |
| 7 | New `ImageUrlFetcher` (fetch → status → image/* → size cap → extension), single home for the pipeline + `svg+xml→svg` mapping; all three sites converted (size caps stay 5MB/10MB/5MB by parameter) | `ImageUrlFetcher.java`, `UserController`, `LogoController`, import service |
| 8 | `Redirect.NEVER` on `avatarFetchHttpClient`; a 3xx is a terminal failure — the validated host can no longer redirect the fetch elsewhere | `AvatarImportConfig` |
| 9 | Variant upgrade handles `=sNN(-c)` before `?`/`#` and the legacy `/s96-c/` path-segment form | `FederatedAvatarImportService` + unit tests |

DB note: V18 left byte-identical (may already be applied via PR CI deploy); new
`V19__update_picture_import_attempt_comment.sql` refreshes only the column COMMENT to the
new claim semantics. Docs updated: `06b-user-lifecycle-sync.md` Pattern 1c,
`sso-oidc-federation.md` §9.
