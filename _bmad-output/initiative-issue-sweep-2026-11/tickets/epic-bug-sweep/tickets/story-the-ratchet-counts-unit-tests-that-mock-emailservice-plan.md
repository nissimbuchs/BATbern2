---
title: 'The ratchet counts unit tests that mock EmailService'
type: 'chore'
ticket: '11'
created: '2026-11-18'
status: 'in-progress'
baseline_revision: 'f84dea068985c3d6833ccf0039152388baea80f2'
route: 'full'
route_source: 'auto'
risk: 'low'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** (#1318) The test ratchet (`web-frontend/scripts/test-ratchet.ts`) counts `@MockitoBean`/`@MockitoSpyBean` of own code, but not plain Mockito unit tests that `@Mock EmailService`, `mock(EmailService.class)` or assert with `verify(emailService…)`. Those are exactly the tests "assert what SES received, never verify(emailService)" forbids, and nothing stops new ones.

**Approach:** A new ratchet category `be-mock-email-service` counts, per Java test file, `@Mock`/`@Spy` fields of type `EmailService`, `mock(EmailService.class)`/`Mockito.mock(EmailService.class)`/`spy(...)`, and `verify(<anything named emailService>` calls; the committed baseline freezes today's counts (after stories 1-10); a new one fails CI as RAISED.

## Boundaries & Constraints

**Always:** Same comment/string-literal exclusions as the other Java categories. Fixture tests in `test-ratchet.test.ts`: a new `@Mock EmailService` file → RAISED; `verify(emailService)` counted; a mention in a comment or string not counted; an existing file at its count → OK; a lowered count → STALE. Document the category in `docs/architecture/coding-standards.md` → "The ratchet" table.

**Never:** Move existing tests off the mock in this story (the touch rule does that later). Count `@MockitoBean EmailService` twice (it is already `be-mock-in-process`; decide one category and say which).

</frozen-after-approval>

## Code Map

- `web-frontend/scripts/test-ratchet.ts` (categories, Java scanning, comment/string stripping), `scripts/test-ratchet.test.ts`, `scripts/test-ratchet-baseline.txt`, `make check-test-ratchet`.
- `docs/architecture/coding-standards.md` "The ratchet" table (~line 700-730).

## Tasks & Acceptance

**Execution:**
- [ ] RED fixture tests
- [ ] category
- [ ] `npm run check:test-ratchet -- --update` adds today's lines only for the new category (diff shows nothing else changing)
- [ ] doc row

**Acceptance Criteria:**
- Given the committed baseline, when `make check-test-ratchet` runs, then it is clean, and the new category's lines list today's files (11-26 expected per #1318).

## Design Notes

Architecture note: test tooling only. No production code. Deviations: none.

## Verification

**Commands:**
- `cd web-frontend && npx vitest run scripts/test-ratchet.test.ts && npm run lint` -- green.
- `make check-test-ratchet` -- clean.

**Rack rules:** Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).
