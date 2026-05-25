# users-api collection

Bruno API contract tests for `UserController` (CUMS) — covers GET-me, GDPR
delete, provisioning, role updates, search/list filters, profile picture
uploads, and the Story-10.32 additional-emails sub-flow.

## Audit pass (PR 5, plan §D.3 + F1 + F2 + F3 + F4)

| Audit / fix | Status |
|-------------|--------|
| Verbs vs OpenAPI | ✅ Broad CRUD + list-filter + GDPR-delete + additional-emails CRUD all covered. |
| Normalize prefix (B1) | ✅ `04-create-user` already used canonical `bruno-test-<ts>@e2e.batbern.invalid` (email) + auto-derived `bruno.test.<ts>` (username). `15-add-additional-email` migrated from `bruno-additional-NNN@example.com` to canonical `bruno-test-<ts>@e2e.batbern.invalid`. |
| 00-/99- cleanup hooks (B3) | ✅ Added 4 files: pretest/posttest for `users` + pretest/posttest for `additional_emails`. The `additional_emails` entityType is new in PR 5 (see plan §F4). |
| DELETE-then-GET-404 | ✅ `14-delete-test-user` → followed by `15-add-additional-email` which depends on the auth user existing; if 14 had really destroyed the auth user, every test after it would 401/403. The F2 fix below preserves this invariant. |
| Run twice | ⏳ Pending — services were stopped during PR 1 CI; will validate before opening the PR. |

## F1 — `{{authUserEmail}}` was never defined

`18-add-additional-email-duplicate` referenced `{{authUserEmail}}` which no env
file or earlier test ever set. Bruno sent the literal string `{{authUserEmail}}`,
the server rejected it as an invalid email shape, the test got 400 instead of
the intended 409 duplicate-error branch.

**Fix:** `01-get-current-user` now captures `authUserEmail` alongside its
existing `currentUserEmail` capture. Test 18's body stays unchanged.

## F2 — `14-delete-test-user` could delete the auth user

`development.bru` sets `testUsername: batbern.organizer`. `04-create-user`
overwrites the same env var with a disposable `bruno.test.<ts>` user — BUT
only if `04` succeeds. On any env where `04` returns 403 (e.g. the auth user
isn't an organizer), the env var stays as the auth user. Test 14 then DELETEs
`batbern.organizer`, breaking every test that comes after it (15, 17, 18, 20)
in subtle ways (Pattern 3b silently re-hydrates the user on the next authed
call, so symptoms cascade unpredictably — see plan §F2).

**Fix:**
- `04-create-user` ALSO sets a new env var `disposableUsername` (set only on 201).
- `14-delete-test-user` uses `disposableUsername` and has a hard precondition
  check: if it's unset OR doesn't match `^bruno\.test\.`, the test throws
  before issuing the DELETE.

## F3 — `20-public-user-by-username` 404 cascading from F2

Was a downstream effect of F2 — falls out automatically once F2 is fixed.

## F4 — `15-add-additional-email` leaked rows on the auth user

The 15→17 add/delete pair would leak when 17 failed (typically because of F2).
After 5 leaks the per-user cap (`ADDITIONAL_EMAIL_LIMIT_REACHED`) blocked
every future test 15 with 422.

**Three-part fix landed in this PR:**

1. **CUMS cleanup-endpoint extension** — new `ADDITIONAL_EMAILS` entityType
   (see commit `274012f6`). Sweeps `user_additional_emails` directly without
   touching the (non-test) owning user. Accepts two prefix branches:
   `bruno-test-` (canonical) and `bruno-additional-` (legacy, for the
   historical leak shape that PR 5 is migrating away from).

2. **00a / 99a hooks** — call the new endpoint with `prefix=bruno-test-`,
   unconditionally pre- and post- the rest of the collection.

3. **Canonical prefix normalization** — `15-add-additional-email` now POSTs
   `bruno-test-<ts>@e2e.batbern.invalid` (canonical per B1) instead of
   `bruno-additional-NNN@example.com` (the prefix that filled the cap).

## Test execution order

| seq | File | Purpose |
|-----|------|---------|
| 0 | `00-pretest-cleanup-users` | Sweep stale `bruno.test.*` users + cascades. |
| 0 | `00a-pretest-cleanup-additional-emails` | Sweep stale `bruno-test-*` additional emails. |
| 1 | `01-get-current-user` | Captures `currentUserEmail` + `authUserEmail`. |
| … | (existing 02-26 tests, unchanged in seq) | … |
| 98 | `99-posttest-cleanup-users` | Final sweep of `bruno.test.*` users. |
| 99 | `99a-posttest-cleanup-additional-emails` | Final sweep of `bruno-test-*` additional emails. |

Files 00 / 00a share seq=0 (their relative order doesn't matter — both run
before the body of the collection); 99 / 99a use 98 / 99 so the user-cascade
finishes before the targeted additional-emails sweep.
