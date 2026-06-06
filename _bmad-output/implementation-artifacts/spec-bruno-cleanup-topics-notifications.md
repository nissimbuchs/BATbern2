---
title: 'Bruno cleanup: partner topics + test-generated notifications'
type: 'bugfix'
created: '2026-06-06'
status: 'done'
context: []
baseline_commit: '4af701ad374f18ac3aefcc67a0ca2923e1eea873'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Bruno run against production (PR deploy) leaks two entity types: (1) partner topic suggestions — only a fragile per-ID DELETE (`98-delete-test-topics.bru`); orphans survive aborted runs; (2) EMS notifications created as side effects of entity/state changes (workflow transitions, publishes, registrations) — no cleanup at all, and they land in **real organizers'** in-app notification lists.

**Approach:** Extend both services' existing admin test-fixture cleanup (enum-whitelisted entityType + server-validated literal prefix, ORGANIZER-only) with `topics` (PCS) and `notifications` (EMS), wire pre/post cleanup `.bru` files into the affected collections, TDD via Testcontainers integration tests. Branch `fix/bruno-cleanup-topics-notifications` off develop.

## Boundaries & Constraints

**Always:** Follow the existing TestFixtureCleanup pattern byte-for-byte (enum + regex-locked prefix + native delete + CleanupResponse counts). Server-side literal validation — no client-supplied patterns. Cleanup endpoints stay non-fatal in Bruno (accept 200/204/404). Bruno `.bru` prose only in `docs {}` blocks, never `#` comments. New Flyway migrations forbidden (no schema change needed).

**Ask First:** Widening deletion criteria beyond the listed predicates; touching real-data tables.

**Never:** Trigger real outbound comms from tests. Delete by patterns that could match production data (e.g. bare `BATbern` prefix). Modify applied migrations.

</frozen-after-approval>

## Code Map

- `services/partner-coordination-service/.../service/TestFixtureCleanupService.java:44-198` -- PCS dispatch (PARTNERS prefix / MEETINGS allowlist)
- `services/partner-coordination-service/.../repository/TestFixtureCleanupRepository.java` -- native deletes
- `services/event-management-service/.../service/TestFixtureCleanupService.java:46-205` -- EMS dispatch (EVENTS/SESSIONS/TOPICS/EVENTS_BY_NUMBER incl. >=10000 threshold precedent)
- `services/event-management-service/.../db/migration/V33__Create_notifications_table.sql` -- notifications: recipient_username, event_code (nullable), metadata JSONB
- `bruno-tests/partners-api/{10-suggest-topic-as-partner,98-delete-test-topics,99b-posttest-cleanup}.bru` -- topic title literal `Bruno Test Topic - ...`
- `bruno-tests/admin-cleanup-api/` -- collection that smoke-verifies cleanup auth/routing first

## Tasks & Acceptance

**Execution:**

- [x] PCS `TestFixtureCleanupService` + `Repository` + DTO -- add entityType `topics`: locked prefix literal `Bruno Test Topic` → `DELETE FROM topic_suggestions WHERE title LIKE 'Bruno Test Topic%'` (topic_votes cascade per V4); count in response -- TDD: integration tests first (403 non-organizer, 400 wrong prefix, deletes matching + survivors keep, votes cascade).
- [x] EMS `TestFixtureCleanupService` + `Repository` + DTO -- add entityType `notifications`: locked sentinel prefix `BRUNO-TEST-` → composite delete of notifications WHERE `event_code LIKE 'BRUNO-TEST-%'` OR (`event_code ~ '^BATbern[0-9]+$'` AND numeric part >= 10000) OR `recipient_username LIKE 'bruno.test.%'`; counts in response -- TDD integration tests incl. survivor assertions (real event_code `BATbern56`, real organizer recipient with NULL event_code stays).
- [x] `bruno-tests/partners-api/` -- add `00-pretest-cleanup-topics.bru` (seq 0) + `99c-posttest-cleanup-topics.bru` posting `{entityType: "topics", prefix: "Bruno Test Topic"}` to pcs cleanup; keep 98 per-ID delete as belt-and-braces.
- [x] `bruno-tests/` EMS-writing collections (`event-full-workflow-api`, `events-crud-api`, `sessions-api`, `tasks-api`, `event-topics-api`, `partner-meetings-api`) -- add posttest `…-cleanup-notifications.bru` (and pretest where the collection already has an EMS pretest file) posting `{entityType: "notifications", prefix: "BRUNO-TEST-"}`; non-fatal assertions per existing cleanup files; prose in `docs {}`.
- [x] `bruno-tests/admin-cleanup-api/` -- extend with auth/routing verification for the two new entityTypes (mirror existing cases).
- [x] Docs -- check `.github/doc-drift-mappings.yml` (service-path mappings match but `skip_commit_scopes` exempts `bruno`/`test` scopes; hardening doc updated regardless); update `docs/plans/bruno-staging-hardening.md` entityType enumeration (rule: "cleanup must enumerate every table tests write to").

**Acceptance Criteria:**

- Given an orphaned `Bruno Test Topic …` suggestion (with votes), when the partners-api pretest cleanup runs, then the topic and its votes are gone and non-test topics survive.
- Given notifications with event_code `BRUNO-TEST-X`, `BATbern10001`, recipient `bruno.test.foo`, plus real ones (`BATbern56`, organizer/NULL event_code), when EMS notifications cleanup runs, then only the first three are deleted.
- Given a non-organizer token, when either new entityType is posted, then 403.
- Given a prefix other than the locked literal, then 400.
- Given the suites, `./gradlew :services:partner-coordination-service:test :services:event-management-service:test` pass; touched `.bru` files parse with zero `Skipping invalid file` warnings.

## Spec Change Log

- 2026-06-06 review round (patches, no loopback): user approved widening the notifications predicate (Ask-First honored) with `subject/body LIKE '%BRUNO-TEST-%'` to catch NULL-event_code notifications; regex bounded to `BATbern[0-9]{1,9}` (INTEGER-overflow guard); `run_cleanup_for_collection` now glob-discovers `*pretest/posttest-cleanup*.bru` (was 2 hardcoded names — missed all suffixed files); partners-api topics pretest seq 0→0.01 (collision); EMS audit log records full composite criteria; task-6 doc-drift claim corrected (mappings match, `skip_commit_scopes` exempts `bruno`/`test`). KEEP: locked-literal prefix validation; admin-cleanup-api 200-assertions as the routing guard; 404-tolerant pre/post sweeps per hardening-doc B3.

## Suggested Review Order

**Production-deletion predicates (the risk core)**

- Entry point: EMS composite notifications delete (event_code prefix, BATbern{1,9}≥10000, recipient, subject/body marker)
  [`TestFixtureCleanupRepository.java:1`](../../services/event-management-service/src/main/java/ch/batbern/events/repository/TestFixtureCleanupRepository.java#L1)

- EMS dispatch + audit of full criteria
  [`TestFixtureCleanupService.java:1`](../../services/event-management-service/src/main/java/ch/batbern/events/service/TestFixtureCleanupService.java#L1)

- PCS topics: locked literal `Bruno Test Topic`, votes cascade
  [`TestFixtureCleanupService.java:1`](../../services/partner-coordination-service/src/main/java/ch/batbern/partners/service/TestFixtureCleanupService.java#L1)
  [`TestFixtureCleanupRepository.java:1`](../../services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/TestFixtureCleanupRepository.java#L1)

**Survivor-proofs (integration tests)**

- 3-deleted/2-survive → now 5/3 incl. NULL-event_code markers + 11-digit pathological survivor + BATbern9999 boundary
  [`TestFixtureCleanupControllerIntegrationTest.java:1`](../../services/event-management-service/src/test/java/ch/batbern/events/integration/TestFixtureCleanupControllerIntegrationTest.java#L1)

- Topic delete + votes-cascade + survivor
  [`TestFixtureCleanupControllerIntegrationTest.java:1`](../../services/partner-coordination-service/src/test/java/ch/batbern/partners/integration/TestFixtureCleanupControllerIntegrationTest.java#L1)

**Bruno wiring**

- Defensive sweep now glob-discovers all cleanup files (was 2 hardcoded names)
  [`run-bruno-tests.sh:240`](../../scripts/ci/run-bruno-tests.sh#L240)

- partners-api topics pre/post; per-collection notifications pre/post (6 collections); admin-cleanup verification 16/17/26/27
  [`00-pretest-cleanup-topics.bru:1`](../../bruno-tests/partners-api/00-pretest-cleanup-topics.bru#L1)
  [`99c-posttest-cleanup-topics.bru:1`](../../bruno-tests/partners-api/99c-posttest-cleanup-topics.bru#L1)

**Docs**

- entityType enumeration + criteria + glob note
  [`bruno-staging-hardening.md:1`](../../docs/plans/bruno-staging-hardening.md#L1)

## Verification

**Commands:**
- `set -o pipefail && ./gradlew :services:partner-coordination-service:test :services:event-management-service:test 2>&1 | tee /tmp/bruno-cleanup-test.log` -- BUILD SUCCESSFUL
- `grep -ri "Skipping invalid file" /tmp/bruno-dryrun.log` after a local `bru run` dry pass of touched collections (or targeted file lint) -- zero hits
- `make lint` (Java) via pre-commit on commit -- clean

**Manual checks (if no CLI):**
- After next PR deploy's Bruno run: organizer notification list on www contains no BRUNO-TEST entries; `topic_suggestions` has no `Bruno Test Topic%` rows.
