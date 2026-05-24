-- One-time cleanup of pre-existing Bruno / E2E test residue on staging RDS.
--
-- Source of truth: pre-PR-1 audit recorded in
-- docs/plans/bruno-staging-hardening.md (2026-05-24).
--
-- 17 disposable rows total: 10 companies + 7 partners. All identified by
-- exact-match name. The cleanup endpoint's regex (^BRUNOTESTCO[0-9]+$ etc.)
-- intentionally does NOT match these legacy patterns — we don't want to
-- keep supporting "company name with spaces" or "testag" as legitimate
-- test-data patterns going forward.
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
\echo '--- Partner cascade preview (rows that will be deleted via FK CASCADE) ---'
SELECT 'partner_meetings'           AS table_name, COUNT(*) AS cascade_count
FROM partner_meetings WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899')
UNION ALL
SELECT 'partner_meeting_attendance', COUNT(*) FROM partner_meeting_attendance WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899')
UNION ALL
SELECT 'partner_meeting_rsvps',      COUNT(*) FROM partner_meeting_rsvps      WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899')
UNION ALL
SELECT 'partner_notes',              COUNT(*) FROM partner_notes              WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899')
UNION ALL
SELECT 'topic_votes',                COUNT(*) FROM topic_votes                WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899')
UNION ALL
SELECT 'topic_suggestions',          COUNT(*) FROM topic_suggestions          WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');

\echo ''
\echo '--- Delete partner-side rows (cascade or explicit) ---'
-- These tables FK-reference partners by company_name (a meaningful ID, not UUID).
-- Some have ON DELETE CASCADE; explicit deletes here are belt-and-suspenders.
DELETE FROM topic_votes
 WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');
DELETE FROM topic_suggestions
 WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');
DELETE FROM partner_notes
 WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');
DELETE FROM partner_meeting_attendance
 WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');
DELETE FROM partner_meeting_rsvps
 WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');
DELETE FROM partner_meetings
 WHERE company_name IN ('brtest117','brtest150','brtest288','brtest424','brtest675','brtest861','brtest899');

\echo ''
\echo '--- Delete partners (the 7 brtest* rows) ---'
DELETE FROM partners
 WHERE company_name IN ('brtest117', 'brtest150', 'brtest288', 'brtest424', 'brtest675', 'brtest861', 'brtest899');

\echo ''
\echo '--- Delete logos referencing about-to-be-deleted test companies (soft FK) ---'
-- logos.associated_entity_id is a string reference (not a real FK constraint).
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
\echo '--- AFTER: verify the rows are gone ---'
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
\echo '=== Pre-cleanup complete. Committing... ==='

COMMIT;
