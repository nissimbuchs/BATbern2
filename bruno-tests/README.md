# Bruno API Contract Tests

API contract / CRUD smoke tests for the BATbern microservices. Run via the Bruno
CLI (`@usebruno/cli`) headlessly in CI, or interactively in the Bruno desktop
app for development.

```bash
# All collections against development
./scripts/ci/run-bruno-tests.sh development

# One collection at a time (use during hardening / debugging)
./scripts/ci/run-bruno-tests.sh staging --collection companies-api

# Cleanup-only sweep (after a Bruno crash, before re-running)
./scripts/ci/run-bruno-tests.sh staging --cleanup-only
```

For the hardening plan and progress log, see
[`docs/plans/bruno-staging-hardening.md`](../docs/plans/bruno-staging-hardening.md).

## Environments

| Environment file | baseUrl | Purpose |
|------------------|---------|---------|
| `environments/development.bru` | `http://localhost:8000/api/v1` | Local dev — services via `make dev-native-up` |
| `environments/staging.bru` | `https://api.batbern.ch/api/v1` | **Production-account staging**. Tests run against real traffic — cleanup discipline is mandatory. |
| `environments/production.bru` | `https://api.batbern.ch/api/v1` | Same host as staging; reserved for read-only diagnostics. |

> Note: "staging" is a CDK envName, not a separate AWS environment. Account
> 188701360969 serves production traffic at `api.batbern.ch`. The previously
> nonexistent `api.staging.batbern.ch` host has been removed (was a months-old
> typo in `environments/staging.bru`).

## Canonical test-data naming

All test-created entities **must** use a canonical prefix per their identifier
column's constraints. The cleanup endpoints (per-service `POST /api/v1/admin/
test-fixtures/cleanup`) only delete rows that match these patterns — non-canonical
test data leaks and accumulates.

| Entity | Identifier | DB constraint | Canonical pattern | Example | Filled length |
|--------|-----------|---------------|-------------------|---------|---------------|
| company | `companies.name` | VARCHAR(255), no enforced regex (OpenAPI says alphanumeric but production code accepts spaces — pattern below is safe regardless) | `BRUNOTESTCO<13-digit-ts>` | `BRUNOTESTCO1779647142000` | 24 |
| user | `user_profiles.username` | VARCHAR(100), **DB regex `^[a-z]+\.[a-z]+(\.[0-9]+)?$`** (dots required, NO underscores, NO hyphens) | `bruno.test.<13-digit-ts>` | `bruno.test.1779647142000` | 24 |
| user email | `user_profiles.email` | VARCHAR(255), RFC 5321 | `bruno-test-<ts>@e2e.batbern.invalid` (reserved domain) | `bruno-test-1779647142000@e2e.batbern.invalid` | 44 |
| event | `events.event_code` | VARCHAR(50) | `BRUNO-TEST-<13-digit-ts>` | `BRUNO-TEST-1779647142000` | 24 |
| session | `sessions.session_slug` | VARCHAR(200), lowercase-hyphens convention | `bruno-test-session-<ts>` | `bruno-test-session-1779647142000` | 33 |
| topic | `topics.topic_code` | VARCHAR(255), lowercase-hyphens convention | `bruno-test-topic-<ts>` | `bruno-test-topic-1779647142000` | 31 |
| partner | `partners.company_name` | **VARCHAR(12)** — known column-width bug (separate ticket) | `brtest<6-digit-id>` (matches existing convention) | `brtest142000` | ≤12 |
| file upload | filename | OpenAPI `maxLength: 255` | `bruno-test-<ts>.png` | `bruno-test-1779647142000.png` | 30 |
| registration | `registrations.registration_code` | VARCHAR(100) | `BRUNO-<ts>-<6char-rand>` | `BRUNO-1779647142000-ABC123` | ≤28 |

### Cleanup-endpoint regexes (binding — enforced server-side)

These regexes are constants in each service's `TestFixtureCleanupService`
(see `services/*/src/main/java/.../service/TestFixtureCleanupService.java`).
The cleanup endpoint validates the incoming **`prefix` request field** against
the entity-specific regex below and rejects anything that doesn't match. The
service then appends `%` to the validated prefix and uses `LIKE prefix%` to
match every row whose identifier starts with that exact prefix.

**The regex matches the `prefix` VALUE you send, not the stored data.** A
request with `prefix=BRUNOTESTCO1779647142000` is **rejected** with 400 because
the regex `^BRUNOTESTCO$` doesn't match the timestamped string. Send
`prefix=BRUNOTESTCO` (the literal prefix) — the server appends `%` and sweeps
every row whose `name` starts with `BRUNOTESTCO`, including the timestamped
ones above.

| Entity type | Send `prefix=` | Regex (request validation) | Server LIKE | Sweeps data shaped like |
|-------------|----------------|----------------------------|-------------|--------------------------|
| `companies` | `BRUNOTESTCO` | `^BRUNOTESTCO$` | `BRUNOTESTCO%` | `BRUNOTESTCO1779647142000` |
| `users` | `bruno.test.` | `^bruno\.test\.$` | `bruno.test.%` | `bruno.test.1779647142000` |
| `events` | `BRUNO-TEST-` | `^BRUNO-TEST-$` | `BRUNO-TEST-%` | `BRUNO-TEST-1779647142000` |
| `sessions` | `bruno-test-session-` | `^bruno-test-session-$` | `bruno-test-session-%` | `bruno-test-session-1779647142000` |
| `topics` | `bruno-test-topic-` | `^bruno-test-topic-$` | `bruno-test-topic-%` | `bruno-test-topic-1779647142000` |
| `partners` | `brtest` | `^brtest$` | `brtest%` | `brtest142000` |

> `registrations` and `uploads` (filename) appear in the data-naming table
> above for test-data discipline but are not yet wired into the cleanup
> endpoint. Adding them is a follow-up; the structural F4 PR-5 fix for
> `ADDITIONAL_EMAILS` is the canonical template (see plan §F4).

### Why per-entity patterns (not a single BRUNO_TEST_ prefix)?

The pre-PR-1 audit (2026-05-24) found **12 real users named "Bruno"** matching
`LIKE 'bruno%'` (Bruno Blumenthal @ RUAG, Bruno Frey @ ASTRA, Bruno Linder @
SBB, etc.). A single broad prefix risks deleting real production data. The
dot-anchored regex `^bruno\.test\.[0-9]+$` makes test data unambiguous and
unmatched by real users (who follow `firstname.lastname` convention without
the second dot+digits).

Similar reasoning for companies: `^[A-Za-z0-9]+$` for production isn't enforced,
so real names like `Swisscom AG` (with space) live alongside test residue
like `bruno test company 1761143046`. The strict canonical `BRUNOTESTCO[0-9]+`
pattern is unambiguous and matches NEITHER.

## Collection layout

Each collection corresponds to one entity domain. The expected structure:

```
<collection-name>/
├── 00-pretest-cleanup.bru     ← Defensive sweep before test starts (status oneOf [200, 204, 404])
├── 01-<first-create>.bru
├── 02-<next-op>.bru
├── ...
├── NN-delete-<entity>.bru
├── NN+1-verify-deletion.bru   ← GET after DELETE asserts 404
└── 99-posttest-cleanup.bru    ← Defensive sweep after test ends (status oneOf [200, 204, 404])
```

Bruno's runner sorts by filename. `00-` runs first (wiping any leftover from a
prior failed run); `99-` runs last (cleaning up this run's data). Each cleanup
file calls the per-service cleanup endpoint with the entity's canonical prefix
and a `oneOf([200, 204, 404])` status assertion — they never fail the run.

Two-layer defense: `scripts/ci/run-bruno-tests.sh` ALSO invokes
`--cleanup-only` against the collection after the main run, so the 99- file
executes even if Bruno's runner crashed mid-collection.

## Collections — current state

| Collection | Entity | Owning service | Auth | Status |
|-----------|--------|----------------|------|--------|
| `file-upload-api` | logos | CUMS | organizer | per-entity, light audit pending |
| `companies-api` | companies | CUMS | organizer | per-entity, light audit pending |
| `users-api` | users | CUMS | organizer | per-entity, light audit pending |
| `tasks-api` | event_tasks | EMS | organizer | per-entity, light audit pending |
| `events-api` | events + sessions + topics + speaker-pool + reminders (monolithic) | EMS | organizer | **decomposition pending** — PR 2 splits into 6 collections |
| `partners-api` | partners + meetings + votes | PCS | organizer | per-entity, light audit pending |
| `speaker-portal-api` | speaker invitation/portal flow | EMS | organizer + speaker | dev-only — depends on `@Profile("dev/local/test")`-gated token endpoint |
| `admin-cleanup-api` | cleanup endpoint authorization | (all 3 services) | organizer | added in PR 1 |

`speaker-portal-api` is automatically excluded from staging runs by
`scripts/ci/run-bruno-tests.sh` because it requires an E2E token helper that
only exists in dev/local/test profiles.

## Auth tokens

For multi-role testing the runner loads three tokens from
`~/.batbern/<environment>-<role>.json` (organizer, speaker, partner) — see
`make setup-test-users` and `.env.test.local`.

Within a .bru file, use the appropriate variable:

```
auth {
  bearer
}
auth:bearer {
  token: {{organizerAuthToken}}
}
```

Variables: `{{organizerAuthToken}}`, `{{speakerAuthToken}}`, `{{partnerAuthToken}}`,
`{{authToken}}` (legacy alias for organizer).

## When tests fail in CI

Post-deploy Bruno tests are wired into `.github/workflows/deploy-staging.yml`:

1. **Today (until PR 14):** failures are warnings (`continue-on-error: true`). Investigate via the workflow's annotation summary.
2. **After PR 14:** failures block the deploy AND trigger
   `rollback-on-bruno-failure` which pins ECS services back to the
   `staging-stable` ECR tag (the last Bruno-green deploy).

If tests fail locally, run the failed collection's cleanup sweep first to make
the next run idempotent:

```bash
./scripts/ci/run-bruno-tests.sh staging --collection companies-api --cleanup-only
```
