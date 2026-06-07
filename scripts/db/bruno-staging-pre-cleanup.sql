-- One-time cleanup of pre-existing Bruno / E2E test residue on staging RDS.
--
-- Source of truth: pre-PR-1 audit recorded in
-- docs/plans/bruno-staging-hardening.md (2026-05-24).
--
-- Two cleanup buckets:
--
-- 1. 17 disposable rows from the original audit: 10 companies + 7 partners.
--    All identified by exact-match name. The cleanup endpoint's regex
--    (^BRUNOTESTCO[0-9]+$ etc.) intentionally does NOT match these legacy
--    patterns — we don't want to keep supporting "company name with spaces"
--    or "testag" as legitimate test-data patterns going forward.
--
-- 2. F4 unblock (added 2026-05-25, see plan §"F4 — 15-add-additional-email
--    accumulates rows"): bruno-additional-NNN@example.com rows leaked into
--    user_additional_emails by the auth user. The 5-per-user cap blocks all
--    future test 15 runs once 5 leaks accumulate. PR 5 ships the structural
--    fix (ADDITIONAL_EMAILS cleanup-endpoint entityType + 00/99 hooks +
--    canonical prefix); PR 1 sweeps the existing leakage so Bruno-on-staging
--    is clean from day one. Matched by LIKE pattern, not exact list, because
--    the {{$randomInt}} suffix means we don't know which numeric IDs will be
--    present at deploy time.
--
-- Runs exactly once when PR 1 deploys. Idempotent: re-running deletes 0 rows.
-- Wrapped in a single transaction with explicit COMMIT at the end so any
-- error aborts cleanly.
--
-- IMPORTANT: this script does NOT touch user_profiles. user.eetest@batbern-test.ch
-- (the one "test"-named user) was confirmed by the user to still be in use
-- (see staging.bru:11 — `testDisposableSpeakerUsername: user.eetest`).
--
-- Run via:
--   ./scripts/staging/start-db-tunnel.sh   # tunnel to RDS via SSM
--   PGPASSWORD=<from-secrets-manager> psql \
--     -h localhost -p 5433 -U postgres -d batbern \
--     -f scripts/db/bruno-staging-pre-cleanup.sql
--
-- ─── Schema reality check (verified 2026-05-25) ──────────────────────────────
-- partners FK ON DELETE CASCADE chain (current schema after V4/V6/V7/V9):
--   partners → partner_meeting_attendance  ✓ (V2 partner_id FK; cascades)
--   partners → partner_notes               ✓ (V7 partner_id FK; cascades)
--   partners → topic_votes                 ✗ (V4 rebuilt — links by company_name string, no FK)
--   partners → topic_suggestions           ✗ (V4 rebuilt — links by company_name string, no FK)
--   partners → partner_meetings            ✗ (V5 schema — meetings are standalone, no partner FK)
--   partners → partner_meeting_rsvps       ✗ (V9 schema — links to partner_meetings via meeting_id, no partner FK)
--
-- That means:
--   - DELETE FROM partners cascades to attendance + notes automatically.
--   - topic_votes and topic_suggestions need EXPLICIT cleanup by company_name.
--   - partner_meetings and partner_meeting_rsvps are not linked to specific
--     partners — the audit found 0 stray rows there for our targets; we leave
--     them alone. (See "Deferred — partner_meetings cleanup coverage" in the
--     plan; that's a separate follow-up.)

BEGIN;

\echo '=== Bruno staging pre-cleanup — exact-match deletion of 17 legacy rows ==='
\echo ''
\echo '--- BEFORE: count rows about to be deleted ---'
SELECT 'companies-to-delete' AS bucket, COUNT(*) AS count
FROM companies
WHERE name IN (
    'bruno test company 1761143046',
    'E2E Test Company 1768749259021',
    'E2E Test Company 1768749674661',
    'E2E Test Company 1768749879358',
    'E2E Test Company 1768750018824',
    'E2E Test Company 1769018072980',
    'E2E Test Company 1771356468161',
    'testag',
    'testcompanya',
    'testdeclinea'
)
UNION ALL
SELECT 'partners-to-delete' AS bucket, COUNT(*) AS count
FROM partners
WHERE company_name IN (
    'brtest117', 'brtest150', 'brtest288', 'brtest424',
    'brtest675', 'brtest861', 'brtest899'
);

\echo ''
\echo '--- Partner cleanup preview (cascade + explicit) ---'
-- Cascade dependents (cleared automatically when the partner row is deleted).
SELECT 'partner_meeting_attendance (cascade)' AS table_name, COUNT(*) AS count
FROM partner_meeting_attendance
WHERE partner_id IN (
    SELECT id FROM partners WHERE company_name IN (
        'brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899'
    )
)
UNION ALL
SELECT 'partner_notes (cascade)', COUNT(*)
FROM partner_notes
WHERE partner_id IN (
    SELECT id FROM partners WHERE company_name IN (
        'brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899'
    )
)
UNION ALL
-- Explicit dependents (no FK; ADR-003 string link by company_name).
SELECT 'topic_votes (explicit)', COUNT(*)
FROM topic_votes
WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899')
UNION ALL
SELECT 'topic_suggestions (explicit)', COUNT(*)
FROM topic_suggestions
WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');

\echo ''
\echo '--- Delete topic_votes + topic_suggestions (no FK; explicit by company_name) ---'
-- V4 rebuilt these tables to link by string company_name (ADR-003), so the
-- partner DELETE below does NOT cascade to them.
DELETE FROM topic_votes
 WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');
DELETE FROM topic_suggestions
 WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');

\echo ''
\echo '--- Delete partners (cascades to partner_meeting_attendance + partner_notes) ---'
DELETE FROM partners
 WHERE company_name IN (
     'brtest117', 'brtest150', 'brtest288', 'brtest424',
     'brtest675', 'brtest861', 'brtest899'
 );

\echo ''
\echo '--- Delete logos referencing about-to-be-deleted test companies (soft FK) ---'
-- logos.associated_entity_id is a VARCHAR string reference, not a real FK.
-- Wipe any logo associated with the 10 legacy test company names.
DELETE FROM logos
 WHERE associated_entity_type = 'COMPANY'
   AND associated_entity_id IN (
       'bruno test company 1761143046',
       'E2E Test Company 1768749259021',
       'E2E Test Company 1768749674661',
       'E2E Test Company 1768749879358',
       'E2E Test Company 1768750018824',
       'E2E Test Company 1769018072980',
       'E2E Test Company 1771356468161',
       'testag',
       'testcompanya',
       'testdeclinea'
   );

\echo ''
\echo '--- Delete companies (the 10 legacy rows) ---'
DELETE FROM companies
 WHERE name IN (
     'bruno test company 1761143046',
     'E2E Test Company 1768749259021',
     'E2E Test Company 1768749674661',
     'E2E Test Company 1768749879358',
     'E2E Test Company 1768750018824',
     'E2E Test Company 1769018072980',
     'E2E Test Company 1771356468161',
     'testag',
     'testcompanya',
     'testdeclinea'
 );

\echo ''
\echo '--- AFTER: verify the rows are gone (both buckets must report 0) ---'
SELECT 'companies-remaining-matching' AS bucket, COUNT(*) AS count
FROM companies
WHERE name IN (
    'bruno test company 1761143046',
    'E2E Test Company 1768749259021',
    'E2E Test Company 1768749674661',
    'E2E Test Company 1768749879358',
    'E2E Test Company 1768750018824',
    'E2E Test Company 1769018072980',
    'E2E Test Company 1771356468161',
    'testag',
    'testcompanya',
    'testdeclinea'
)
UNION ALL
SELECT 'partners-remaining-matching', COUNT(*) FROM partners
WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');

\echo ''
\echo '--- Defensive check: real users named Bruno are UNTOUCHED ---'
SELECT COUNT(*) AS real_bruno_users_remaining
FROM user_profiles
WHERE username LIKE 'bruno.%'
  AND username !~ '^bruno\.test\.[0-9]+$';

\echo ''
\echo '=== F4 unblock: sweep leaked bruno-additional-%@example.com rows ==='
\echo '(plan §"F4 — 15-add-additional-email accumulates rows"; structural fix in PR 5)'

\echo ''
\echo '--- BEFORE: leaked user_additional_emails rows ---'
SELECT COUNT(*) AS leaked_additional_emails
FROM user_additional_emails
WHERE email LIKE 'bruno-additional-%@example.com';

\echo ''
\echo '--- DELETE leaked user_additional_emails ---'
DELETE FROM user_additional_emails
 WHERE email LIKE 'bruno-additional-%@example.com';

\echo ''
\echo '--- AFTER: must report 0 leaked additional emails ---'
SELECT COUNT(*) AS leaked_additional_emails_remaining
FROM user_additional_emails
WHERE email LIKE 'bruno-additional-%@example.com';

\echo ''
\echo '=== Pre-cleanup complete. Committing... ==='

COMMIT;
