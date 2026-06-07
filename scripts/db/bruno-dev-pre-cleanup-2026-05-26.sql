-- One-shot cleanup of pre-existing Bruno / E2E test residue on local-dev RDS.
--
-- Source of truth: local-dev DB audit recorded in chat 2026-05-26 during PR 670
-- (test-enhancement/bruno-events-api-split). The audit found:
--
--   Table             | Count | Notes
--   ------------------|-------|--------------------------------------------------
--   companies         |   14  | "Test Company N", "E2E Test Company N", testco,
--                              | testcompanya, testdeclinea, testtentativ, etc.
--   user_profiles     |   88  | All have @example.com / @e2e.batbern.invalid /
--                              | @batbern-test.ch — exclusively synthetic emails.
--                              | Includes 6× promote.e2e-N (speaker-pool tests),
--                              | 53× delete*.test* (legacy Cognito-sync experiments),
--                              | 10× testfirst.testlast.N, 3× bruno.test*, others.
--   events            |    3  | TestFixedSpeakerWorkflow, TestNewSpeakerWorkflow,
--                              | TesteventNewsletterBounce — all in slot_assignment.
--   sessions          |   14  | Cascade from the 3 test events.
--   speaker_pool      |   12  | Cascade from the 3 test events.
--   partners (brtest) |    1  | brtest763 (old Bruno residue).
--   partner_meetings  |    2  | Look REAL (ZPK 2026-04-04 AUTUMN, Schöngrün
--                              | 2026-02-21 SPRING — real Bern venues). NOT TOUCHED.
--   registrations     |    1  | Test residue (matches event_code prefix).
--   user_additional_emails | 0 | Clean ✓ (per #666 + ADDITIONAL_EMAILS cleanup).
--   topics            |    0  | Clean ✓.
--
-- Filter strategy (safe by construction):
--
--   user_profiles  — match by email domain (@example.com, @e2e.batbern.invalid,
--                    @batbern-test.ch). The 11 real "Bruno"-named Swiss people
--                    (bruno.frey@astra.admin.ch, bruno.schenker@acm.org, etc.)
--                    use real corporate domains and are untouched.
--   companies      — match exact-list because the OpenAPI alphanumeric pattern
--                    is aspirational, not enforced (see plan §"Bugs confirmed
--                    by audit"). Names with spaces / mixed case / "test" prefix
--                    are all residue.
--   events         — match by title prefix or event_code prefix.
--   partners       — match company_name LIKE 'brtest%'.
--   sessions/speaker_pool/registrations — cascade from events deletion.
--   partner_meetings — SKIPPED. The 2 rows look like real BATbern coordination
--                    meetings (real Bern venues + 2026 spring/autumn pattern);
--                    no Bruno-identifying column exists per plan §"Deferred —
--                    partner_meetings cleanup coverage". Bring back later via
--                    the meeting_id allowlist proposal in PR 13.
--
-- Idempotent: re-running deletes 0 rows. Wrapped in a single transaction so
-- any error aborts cleanly without partial deletion.
--
-- Run via:
--   docker exec -i batbern-dev-postgres psql -U postgres -d batbern_development \
--     -f /dev/stdin < scripts/db/bruno-dev-pre-cleanup-2026-05-26.sql
--
-- ─── Schema reality check (verified 2026-05-26) ──────────────────────────────
-- events FK chain — all CASCADE EXCEPT one:
--   events → event_tasks                ✓ cascades
--   events → event_photos               ✓ cascades
--   events → event_teaser_images        ✓ cascades
--   events → topic_usage_history        ✓ cascades
--   events → sessions                   ✓ cascades
--   sessions → session_users            ✓ cascades from sessions
--   sessions → session_materials        ✓ cascades from sessions
--   events → speaker_pool               ✓ cascades
--   speaker_pool → speaker_outreach_history, speaker_arrivals,
--                  speaker_slot_preferences, speaker_status_history,
--                  speaker_reminder_log  ✓ cascades from speaker_pool
--   events → registrations              ✓ cascades
--   events → publishing_config          ✓ cascades
--   events → workflow_state_migration_log ✓ cascades
--   events → newsletter_sends           ✗ NO ACTION — must DELETE explicitly first
--
-- sessions FK chain:
--   sessions → session_users            ✓ cascades from sessions
--   sessions → session_materials        ✓ cascades from sessions
--   sessions → session_timing_history   ✓ cascades from sessions
--   sessions → speaker_pool             SET NULL (column nullable — fine)
--   sessions → session_content_history  ✗ SET NULL BUT session_id has NOT NULL
--                                       constraint → conflicting schema rules.
--                                       Must DELETE session_content_history first.
--
-- So we DELETE FROM newsletter_sends AND session_content_history BEFORE the
-- events delete. The cascade handles everything else.
--
-- user_profiles FK ON DELETE CASCADE chain:
--   user_profiles → user_additional_emails  ✓ cascades
--   user_profiles → logos                   ✓ cascades (via user_id)
--   user_profiles → role_assignments        ✓ cascades
--
-- So DELETE FROM user_profiles cascades to additional emails, logos, role
-- assignments — exactly what we need.

BEGIN;

-- ─── Pre-deletion counts (SELECT for log/audit trail) ────────────────────────

SELECT 'events-to-delete' AS bucket, COUNT(*) AS count
FROM events
WHERE event_code ~* '^(BRUNO|TEST)' OR title ~* '^(Test|Bruno)';

SELECT 'sessions-cascading-from-events' AS bucket, COUNT(*) AS count
FROM sessions
WHERE event_id IN (
    SELECT id FROM events
    WHERE event_code ~* '^(BRUNO|TEST)' OR title ~* '^(Test|Bruno)'
);

SELECT 'speaker_pool-cascading-from-events' AS bucket, COUNT(*) AS count
FROM speaker_pool
WHERE event_id IN (
    SELECT id FROM events
    WHERE event_code ~* '^(BRUNO|TEST)' OR title ~* '^(Test|Bruno)'
);

SELECT 'registrations-cascading-from-events' AS bucket, COUNT(*) AS count
FROM registrations
WHERE event_id IN (
    SELECT id FROM events
    WHERE event_code ~* '^(BRUNO|TEST)' OR title ~* '^(Test|Bruno)'
);

SELECT 'users-to-delete (by test email domain)' AS bucket, COUNT(*) AS count
FROM user_profiles
WHERE email LIKE '%@example.com'
   OR email LIKE '%@e2e.batbern.invalid'
   OR email LIKE '%@batbern-test.ch';

SELECT 'partners-to-delete (brtest*)' AS bucket, COUNT(*) AS count
FROM partners
WHERE company_name ~* '^brtest';

SELECT 'companies-to-delete (exact list)' AS bucket, COUNT(*) AS count
FROM companies
WHERE name IN (
    'E2E Test Company 1768058945273',
    'E2E Test Company 1769019501711',
    'testco',
    'Test Company 1768755689497',
    'Test Company 1768755689501',
    'Test Company 1768755868777',
    'Test Company 1768755868778',
    'Test Company 1768755969699',
    'Test Company 1768755969705',
    'test-company-1768771956705',
    'test-company-1768772144543',
    'testcompanya',
    'testdeclinea',
    'testtentativ'
);

SELECT 'newsletter_sends-to-delete (test event refs)' AS bucket, COUNT(*) AS count
FROM newsletter_sends ns
JOIN events e ON e.id = ns.event_id
WHERE e.event_code ~* '^(BRUNO|TEST)' OR e.title ~* '^(Test|Bruno)';

SELECT 'session_content_history-to-delete (test session refs)' AS bucket, COUNT(*) AS count
FROM session_content_history sch
JOIN sessions s ON s.id = sch.session_id
JOIN events e ON e.id = s.event_id
WHERE e.event_code ~* '^(BRUNO|TEST)' OR e.title ~* '^(Test|Bruno)';

-- ─── Bucket 0a: newsletter_sends — non-cascading FK to events ────────────────

DELETE FROM newsletter_sends
WHERE event_id IN (
    SELECT id FROM events
    WHERE event_code ~* '^(BRUNO|TEST)' OR title ~* '^(Test|Bruno)'
);

-- ─── Bucket 0b: session_content_history — SET NULL on session delete is
-- ─── blocked by a NOT NULL constraint on session_id (schema bug). Pre-delete. ─

DELETE FROM session_content_history
WHERE session_id IN (
    SELECT s.id FROM sessions s
    JOIN events e ON e.id = s.event_id
    WHERE e.event_code ~* '^(BRUNO|TEST)' OR e.title ~* '^(Test|Bruno)'
);

-- ─── Bucket 1: Test events (cascades sessions, speaker_pool, registrations) ──

DELETE FROM events
WHERE event_code ~* '^(BRUNO|TEST)' OR title ~* '^(Test|Bruno)';

-- ─── Bucket 2: Test users (cascades user_additional_emails, logos, role_assignments) ──

DELETE FROM user_profiles
WHERE email LIKE '%@example.com'
   OR email LIKE '%@e2e.batbern.invalid'
   OR email LIKE '%@batbern-test.ch';

-- ─── Bucket 3: Test partners (cascades attendance, notes) ────────────────────

DELETE FROM partners
WHERE company_name ~* '^brtest';

-- ─── Bucket 4: Test companies (last — others may FK-reference) ───────────────

DELETE FROM companies
WHERE name IN (
    'E2E Test Company 1768058945273',
    'E2E Test Company 1769019501711',
    'testco',
    'Test Company 1768755689497',
    'Test Company 1768755689501',
    'Test Company 1768755868777',
    'Test Company 1768755868778',
    'Test Company 1768755969699',
    'Test Company 1768755969705',
    'test-company-1768771956705',
    'test-company-1768772144543',
    'testcompanya',
    'testdeclinea',
    'testtentativ'
);

-- ─── Post-deletion verification (should all be 0) ────────────────────────────

SELECT 'events-remaining-matching' AS bucket, COUNT(*) AS count
FROM events
WHERE event_code ~* '^(BRUNO|TEST)' OR title ~* '^(Test|Bruno)';

SELECT 'users-remaining-matching' AS bucket, COUNT(*) AS count
FROM user_profiles
WHERE email LIKE '%@example.com'
   OR email LIKE '%@e2e.batbern.invalid'
   OR email LIKE '%@batbern-test.ch';

SELECT 'partners-remaining-matching' AS bucket, COUNT(*) AS count
FROM partners
WHERE company_name ~* '^brtest';

SELECT 'companies-remaining-matching' AS bucket, COUNT(*) AS count
FROM companies
WHERE name IN (
    'E2E Test Company 1768058945273',
    'E2E Test Company 1769019501711',
    'testco',
    'Test Company 1768755689497',
    'Test Company 1768755689501',
    'Test Company 1768755868777',
    'Test Company 1768755868778',
    'Test Company 1768755969699',
    'Test Company 1768755969705',
    'test-company-1768771956705',
    'test-company-1768772144543',
    'testcompanya',
    'testdeclinea',
    'testtentativ'
);

-- ─── Real-data invariant: 11 real Bruno-named Swiss people remain ────────────
-- (bruno.frey@astra.admin.ch, bruno.schenker@acm.org, etc. — these are real
-- BATbern community members; if this count drops below 11 something went wrong.)

SELECT 'real-bruno-people-remaining' AS bucket, COUNT(*) AS count
FROM user_profiles
WHERE username ~* '^bruno\.'
  AND email NOT LIKE '%@example.com'
  AND email NOT LIKE '%@e2e.batbern.invalid'
  AND email NOT LIKE '%@batbern-test.ch';

COMMIT;
