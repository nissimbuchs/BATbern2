---
title: 'Spectral verb rule follows ADR-013, every baseline line has an owner (17.4 follow-up)'
type: 'feature'
ticket: ''
created: '2026-11-08'
status: 'built'
baseline_revision: 'b3f55fba04c98cc3d12b6f8f0ede607eb7449f59'
route: 'full'
route_source: 'auto'
risk: 'low'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-013-rest-api-crud-conventions.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The 17.4 rule `no-verb-segment` bans every listed verb, while ADR-013 §1 row 4 prescribes `POST /{id}/{verb}` for a state transition with side effects, so legitimate actions sit in the baseline as "violations". And the 150 baseline lines name no owner, so nothing says which story removes a line or why it may stay.

**Approach:** Make the rule check ADR-013's shape instead of the word: a verb is allowed only as the last segment of a `POST` directly after an instance identifier (`{param}` or `me`); everywhere else it is flagged. The verb list stays only as the detector that tells a verb from a sub-resource noun. Then every remaining baseline line must match exactly one group in a committed owner file (owning story, issue, or owner-accepted exception with its reason), checked by `npm run lint:api`. *(Owner decisions 2026-11-08: all proposed exception classes accepted; Watch pair/authenticate are an exception; no per-action allow-list, the right verb for the right action. Added in review, owner 2026-11-08: a rule `no-personal-data-path-param` flags a path parameter that carries personal data (`email`, `*Email`), ADR-003 amendment; its lines are owned by 17.10b.)*

## Boundaries & Constraints

**Always:** the rule and the owner check run in the existing `lint:api` / CI `frontend-static` step, no new job; baseline semantics unchanged (NEW and STALE fail, `--update` rewrites); the owner check also runs after `--update`, so a newly accepted line needs an owner group in the same PR; owner file is hand-maintained and reviewed like code.

**Never:** no spec or API change (removing violations is the owning stories' work); no allow-list of action names; no exception without a written reason.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| ADR-013 action | `POST /companies/{name}/verify`, `POST /users/me/merge` | no finding | none |
| verb after a literal | `POST /users/me/profile-claims/confirm` (`profile-claims` is no instance) | finding | lint fails if not baselined |
| verb, wrong method | `PUT /tasks/{taskId}/complete` | finding | lint fails if not baselined |
| verb on collection / nested | `POST /users/provision`, `POST /events/{c}/publish/{phase}` | finding | as above |
| read verb | `GET …/download` | finding (last-segment rule for reads unchanged) | as above |
| baseline line without owner | line matches no group | `UNOWNED` problem, exit 1 | names the line |
| personal data in path | `POST /users/me/additional-emails/{email}/make-primary`, `DELETE /users/me/additional-emails/{email}` | finding `no-personal-data-path-param` | owned by 17.10b |
| line in two groups | matches 2+ groups | `AMBIGUOUS` problem, exit 1 | names line and groups |
| owner group matches nothing | group fixed away | `STALE OWNER` problem, exit 1 | remove the group |

</frozen-after-approval>

## Code Map

- `docs/api/spectral-functions/apiConventions.js` `pathViolation(..., 'verb', ...)` (lines ~36–44): today reads flag a verb last segment, writes flag any verb segment. Change writes: skip a verb that is the last segment of a `post` whose previous segment is a parameter or `me`. Reads unchanged.
- `docs/api/.spectral.yaml` `no-verb-segment` (line 44): description/comment states the ADR-013 shape and that the list is a detector, not a permission list.
- `web-frontend/scripts/api-lint.ts`: `compare()` (~218), `parseBaseline()` (~169), `BASELINE_HEADER` (~152, stale "shrinks with Stories 17.6, 17.9, 17.10" and "(Story 17.4)"), `main()` (~272, `--update` path ~280), `defaultPaths()` (~261). Add owner parsing + check here; tests in `scripts/api-lint.test.ts` (vitest, run by CI line `build.yml:568`).
- New `web-frontend/scripts/api-lint-owners.txt`: TSV `<group id>\t<owner>\t<rule regex>\t<operation regex on "METHOD /path">`, `#` comments; owner is a story id (`17.6`, `17.9`, `17.10`, `17.11`, `16.8c`, `17.9b` one transition idiom for the event lifecycle (publish, publish/schedule, publish/{phase}, unpublish/{phase}, workflow/transition), `17.10b` API conventions III (groups N5, N6 in groups.tsv), `17.11a` profile-picture (N1), `17.11b` event-photo (N2), `17.11c` teaser-image (N3), `17.11d` session-material (N4); entries in `_bmad-output/initiative-architecture-hardening/tickets/epic-api-conventions-and-uploads/tickets/tickets.toml`), an issue `#1070`, or `exception: <reason>`.
- Starting mapping (150 lines, verified one-group-each): `/tmp/claude-1000/-home-nissim-dev-bat-BATbern-bmad-v7/c64fedd7-3d26-4079-aa07-6e211aa554e3/scratchpad/groups.tsv`. Exception reasons: X1 ADR-003 owned child under its parent; X2 ADR-003 transient technical handle; X3 ADR-003 natural key is personal data; X4 ADR-003 unnamed top-level aggregate; X5 ADR-013 §1 state transition (most of its lines disappear with the new rule); X6 anonymous action authorised by a mailed token; X7 file representation of a resource; X8 dry run without side effect; X9 job over a whole collection; N7 (Watch pair/authenticate) credential exchange (token endpoint), no resource. Event publish/transition lines go to the new transition-idiom story, not to X5.
- `docs/architecture/coding-standards.md` "API conventions lint" table row `no-verb-segment` and "Baseline policy" paragraph.

## Tasks & Acceptance

**Execution:**
- [x] `apiConventions.js` + `.spectral.yaml` -- ADR-013 action shape exempt -- the rule matches the ADR.
- [x] `api-lint.ts` -- owner file parse, `UNOWNED` / `AMBIGUOUS` / `STALE OWNER` checks in normal and `--update` mode; header without story numbers, pointing to the owner file -- every line accountable.
- [x] `api-lint.test.ts` -- one test per I/O row (rule rows via the existing fixture-spec pattern, owner rows on in-memory text).
- [x] `npm run lint:api -- --update`, then write `api-lint-owners.txt` from `groups.tsv` adjusted to the new baseline (drop groups that no longer match, re-own the publish lines) -- record baseline size before/after per rule in Implementation Notes.
- [x] `coding-standards.md` -- rule row and owner-file policy.
- [x] `.spectral.yaml` + `apiConventions.js` (check `personal-data`) -- rule `no-personal-data-path-param`: a path parameter named `email` or ending in `Email` (case-insensitive) is a finding; fixture spec + test; `--update`; owner group for the new lines -> 17.10b; coding-standards rule table row (ADR-003 amendment).

**Acceptance Criteria:**
- Given develop's specs, when `npm run lint:api` runs, then it passes, the baseline is smaller than 150, and every line belongs to exactly one owner group.
- Given `make check-test-ratchet` and the vitest run of `scripts/api-lint.test.ts`, when they run, then both pass.

## Design Notes

Architecture note: modules: `docs/api` Spectral function + ruleset, `web-frontend/scripts` lint · new dependencies: none · endpoints: none · reused: existing baseline/compare/`--update` mechanics, `findingKey` · deviations: none.

Owner groups use regexes so one line covers a family (e.g. all `/logos/` lines → 17.11); exact-match per line would make the file a second copy of the baseline.

## Verification

Rack rules: one heavy job at a time (no Gradle running), vitest `--maxWorkers=4`; logs to `~/.cache/batbern-test-logs/`; memory check before each run.

**Commands:**
- `cd web-frontend && npx vitest run --maxWorkers=4 scripts/api-lint.test.ts 2>&1 | tee ~/.cache/batbern-test-logs/174o-vitest.log` -- expected: pass.
- `cd web-frontend && npm run lint:api 2>&1 | tee ~/.cache/batbern-test-logs/174o-lint.log` -- expected: exit 0, no UNOWNED/AMBIGUOUS/STALE OWNER.
- `cd web-frontend && npm run type-check && npx eslint scripts/api-lint.ts scripts/api-lint.test.ts` -- expected: clean.
- `make check-test-ratchet` -- expected: OK.

## Implementation Notes

Baseline per rule, before -> after the rule change (`--update`, no spec touched):

| Rule | before | after |
|---|---|---|
| no-verb-segment | 82 | 52 |
| uuid-path-param | 58 | 58 |
| no-literal-under-events | 8 | 8 |
| path-kebab-case | 2 | 2 |
| no-internal-path | 0 | 0 |
| total | 150 | 120 |

The 30 removed lines are all `POST /{param-or-me}/{verb}`, among them `POST /events/{eventCode}/publish`
(an ADR-013 action now, so 17.9b owns only `publish/schedule`, `publish/{phase}`,
`unpublish/{phase}`, `workflow/transition`, group T1).

Added in review (owner 2026-11-08), rule `no-personal-data-path-param`: baseline 120 -> 124
(`no-personal-data-path-param` 0 -> 4, other rules unchanged). The actual output has 4 lines, not
the 3 expected: `DELETE`, `PATCH /users/me/additional-emails/{email}`, `POST .../make-primary`,
`POST .../resend-verification`; all owned by new group N8 (17.10b, family-wide). Tests 39/39,
`npm run lint:api` exit 0.

Owner file from `groups.tsv`, adjusted: G07 (enroll-stakeholders) dropped, matches nothing; G02
narrowed to `provision`; G09 to `invite|self-nominate`; G10 to `subscribe`; G13 to
`uuid-path-param` only; G14 lost `sends/{sendId}/retry` and `send-reminder` (verb part gone, their
UUID lines stay in G15); N5 lost `reset`, `make-primary`, `resend-verification`; X5 lost every line
except the three singleton transitions (`content/submit`, `profile-claims/confirm`,
`theme-image/apply`), reason "ADR-013 section 1 state transition addressed through a literal
sub-resource, not an instance identifier"; after review every exception group (X1-X9, N7) is
anchored to its exact operations `^(METHOD /path|...)$`, story groups stay family-wide;
`main()` delegates to an exported, tested `run(argv, paths)`; publish lines moved from X5 to new group T1
(17.9b). Result: 32 groups, 120 lines, each in exactly one group (63 owned by stories/issue, 57 by
exceptions).

`checkOwners` matches the rule regex against the whole rule name and searches the operation regex
in `METHOD /path` (the spec file is not part of the match). Owner format checked:
`<n>.<n>[a-z]`, `#<n>`, or `exception: <reason>`; anything else is `BAD OWNER`.

Also changed beyond the code map: the comment of the CI step in `.github/workflows/build.yml` (names
the owner check). `docs/prd/epic-17-architecture-hardening.md:373` still says "Stories 17.6, 17.9,
17.10 shrink the baseline"; left as is (17.4 story text, not policy).

Verification run 2026-11-08: vitest `scripts/api-lint.test.ts` 33/33 pass; mutation check (exemption
disabled) makes the new ADR-013 test and the fixture totals fail; `npm run lint:api` exit 0;
`--update` and normal mode both exit 1 with an injected STALE OWNER / UNOWNED and 0 when restored;
type-check, eslint, prettier clean; `make check-test-ratchet` OK.

## Plan Change Log

## Review Triage Log

**Pass 1 (quick lens, 2026-11-08), loop 0.** Verdicts: high 0, medium 2, low 5, false 0, maybe-false 0; 1 rejected.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Exception groups matched by name or open prefix (X7, X3, X4) silently own future lines | medium | patch | every exception group anchored to its exact operations; story groups stay family-wide |
| 2 | X5 reason false for `/users/me/profile-claims/confirm` (a collection) | low | patch | reworded: transition addressed through a literal sub-resource, not an instance identifier |
| 3 | 17.6/17.10 PRD and ticket checks cite lines the new rule removed | low | patch (coordinator) | PRD ACs and 17.10 verify reworded; epic decision line on lines that left the baseline |
| 4 | E-mail in path (`additional-emails/{email}`) invisible after the rule change | medium | intent gap -> owner decided | new rule `no-personal-data-path-param`; found 4 lines (incl. an unseen PATCH), owned by 17.10b |
| 5 | Watch decision not carried into epic and PRD | low | patch (coordinator) | text updated: owner-accepted exception |
| 6 | PRD says 17.10b owns "the remaining" lines | low | patch (coordinator) | points to the owner file |
| 7 | `main()` exit wiring untested | low | patch | exported `run()`, 4 tests on temp files |
| 8 | `isAdr013Action` treats any parameter as instance identifier | low | reject | no current spec line affected; a fix needs parameter typing, more complexity than the risk |

Final: baseline 150 -> 124 (no-verb-segment 82 -> 52, new no-personal-data-path-param 4); 39 tests; `lint:api` exit 0; type-check, eslint, ratchet clean. Not fixed, pre-existing: `docs/api/spectral-functions/apiConventions.js` was not prettier-clean at HEAD either; no gate covers `docs/api`.
