-- V93__migrate_legacy_speaker_states.sql
-- Story 11.B.3 (Phase B finale): Migrate legacy speaker_pool.status values to the
-- 8-state ADR-009 model; drop side-channel tentative columns + index; tighten the
-- CHECK constraints on speaker_pool + speaker_status_history; expose derived
-- predicates at the read layer (handled in Java, not SQL — see SpeakerPoolResponse).
--
-- SOURCE OF TRUTH:
--   - ADR-009 §0.7 (removed states + side-channel columns)
--   - ADR-009 §"Migration to the new state set" (legacy mapping table)
--   - docs/plans/speaker-workflow-refactor.md §2.2 (data-model migrations)
--   - docs/prd/epic-11-speaker-workflow-refactor.md Story 11.B.3
--
-- Mapping (ADR-009 §"Migration to the new state set"):
--   slot_assigned → accepted          (slot is now derived from session.start_time)
--   confirmed     → quality_reviewed  (is_publishable is now derived from QUALITY_REVIEWED ∧ is_slot_assigned)
--   withdrew      → declined          (decline_reason backfilled when null: "Withdrew after acceptance (legacy)")
--   overflow      → ready             (organizer may re-invite; slot-capacity gate at READY → INVITED enforces oversubscription)
--
-- Step order (intentional — see the V93 commit body / Story 11.B.3 AC2 "Important — order"):
--   1. INSERT audit rows into speaker_status_history using legacy `previous_status`
--      values (still permitted by the V44 constraint that is in force at this point).
--   2. UPDATE speaker_pool.status per the mapping (new values are still permitted by V44).
--   3. Tighten the speaker_pool_status_check constraint to the 8-state allow-list.
--   4. Tighten both speaker_status_history check constraints (previous_status and new_status)
--      to the same 8-state allow-list. Rows inserted in step 1 with legacy `previous_status`
--      values are grandfathered — PostgreSQL CHECK constraints validate on INSERT/UPDATE, not
--      retroactively. Future inserts that try to write legacy values will be rejected.
--   5. Drop idx_speaker_pool_tentative (the partial index from V45 that references
--      speaker_pool.is_tentative — must drop the index BEFORE dropping the column).
--   6. Drop speaker_pool.is_tentative + speaker_pool.tentative_reason columns (and any
--      Lombok-generated metadata; entity-level field deletion happens in SpeakerPool.java).
--   7. Paranoia check — assert every speaker_pool row is in one of the 8 valid states.
--
-- Idempotency: every UPDATE/INSERT is predicated on WHERE status IN (legacy values),
-- so a second run of V93 against an already-migrated database is a no-op (zero rows
-- touched). The ALTER TABLE statements use IF EXISTS so the column/index drops are
-- also idempotent. Flyway already runs each file in its own transaction (do NOT add
-- BEGIN/COMMIT blocks — Flyway forbids them); a failure in any step rolls back the
-- whole migration.
--
-- Casing note: the DB stores statuses as lowercase_snake_case via SpeakerWorkflowStateConverter.
-- All SQL literals below use lowercase. The change_reason text uses Java-side UPPER_CASE
-- names (e.g., "SLOT_ASSIGNED → ACCEPTED") for forensic readability — that's a
-- documentation-only convention; it does not affect the constraint values.
--
-- Audit bypass note: this migration writes directly to speaker_status_history (bypassing
-- SpeakerWorkflowService.transition() — the single-writer service from Story 11.B.2). That
-- is intentional: SpeakerWorkflowService cannot fire at the DB layer; the migration is a
-- one-time data fix that the workflow service deliberately delegates to Flyway. Each
-- audit row is tagged changed_by_username = 'system-migration-v93' for forensic provenance.
--
-- Out-of-scope artefacts (NOT touched here):
--   - `speakers` table → Story 11.C.1 drops it.
--   - `magic_link_tokens` → Phase F (Story 11.F.1) drops it.
--   - `speaker_selection_votes` → does NOT exist in BATbern (PRD AR19 / Plan §2.2 list
--      assumed it existed; grep confirms zero matches in any service's migration files).
--
-- ============================================================================
-- Step 1: INSERT audit rows for legacy rows that are about to be mapped
-- ============================================================================
-- One INSERT per legacy → new mapping. Each statement is scoped via WHERE so re-runs
-- against an already-migrated database insert zero rows.

INSERT INTO speaker_status_history (
    id,
    speaker_pool_id,
    event_id,
    session_id,
    previous_status,
    new_status,
    changed_by_username,
    change_reason,
    changed_at
)
SELECT
    uuid_generate_v4(),
    sp.id,
    sp.event_id,
    sp.session_id,
    'slot_assigned',
    'accepted',
    'system-migration-v93',
    'Legacy SLOT_ASSIGNED → ACCEPTED (slot now derived from session.start_time, ADR-009 §0.7)',
    NOW()
FROM speaker_pool sp
WHERE sp.status = 'slot_assigned';

INSERT INTO speaker_status_history (
    id,
    speaker_pool_id,
    event_id,
    session_id,
    previous_status,
    new_status,
    changed_by_username,
    change_reason,
    changed_at
)
SELECT
    uuid_generate_v4(),
    sp.id,
    sp.event_id,
    sp.session_id,
    'confirmed',
    'quality_reviewed',
    'system-migration-v93',
    'Legacy CONFIRMED → QUALITY_REVIEWED (is_publishable now derived, ADR-009 §0.7)',
    NOW()
FROM speaker_pool sp
WHERE sp.status = 'confirmed';

INSERT INTO speaker_status_history (
    id,
    speaker_pool_id,
    event_id,
    session_id,
    previous_status,
    new_status,
    changed_by_username,
    change_reason,
    changed_at
)
SELECT
    uuid_generate_v4(),
    sp.id,
    sp.event_id,
    sp.session_id,
    'withdrew',
    'declined',
    'system-migration-v93',
    'Withdrew after acceptance (legacy)',
    NOW()
FROM speaker_pool sp
WHERE sp.status = 'withdrew';

INSERT INTO speaker_status_history (
    id,
    speaker_pool_id,
    event_id,
    session_id,
    previous_status,
    new_status,
    changed_by_username,
    change_reason,
    changed_at
)
SELECT
    uuid_generate_v4(),
    sp.id,
    sp.event_id,
    sp.session_id,
    'overflow',
    'ready',
    'system-migration-v93',
    'Legacy OVERFLOW → READY (slot-capacity gate replaces overflow, ADR-009 §0.7)',
    NOW()
FROM speaker_pool sp
WHERE sp.status = 'overflow';

-- ============================================================================
-- Step 2: UPDATE speaker_pool.status per the mapping table
-- ============================================================================
-- Order is irrelevant between the four UPDATEs since each scopes a disjoint set of rows.

UPDATE speaker_pool
SET status = 'accepted'
WHERE status = 'slot_assigned';

UPDATE speaker_pool
SET status = 'quality_reviewed'
WHERE status = 'confirmed';

UPDATE speaker_pool
SET status = 'declined',
    decline_reason = COALESCE(decline_reason, 'Withdrew after acceptance (legacy)')
WHERE status = 'withdrew';

UPDATE speaker_pool
SET status = 'ready'
WHERE status = 'overflow';

-- ============================================================================
-- Step 3: Tighten speaker_pool_status_check to the 8-state allow-list
-- ============================================================================

ALTER TABLE speaker_pool DROP CONSTRAINT IF EXISTS speaker_pool_status_check;
ALTER TABLE speaker_pool ADD CONSTRAINT speaker_pool_status_check CHECK (status IN (
    'identified', 'contacted', 'ready', 'invited', 'accepted', 'declined',
    'content_submitted', 'quality_reviewed'
));

-- ============================================================================
-- Step 4: Tighten speaker_status_history constraints to the 8-state allow-list
-- ============================================================================
-- Step 1 inserted audit rows whose `previous_status` carries legacy values (e.g., 'withdrew')
-- so the forensic record of "how each legacy row migrated" survives. Likewise, pre-V93
-- audit rows may exist with legacy values in either column. We need the *new* constraint
-- to reject future writes of legacy values, but we must NOT fail the migration on the
-- already-persisted rows.
--
-- PostgreSQL's `ADD CONSTRAINT` validates every existing row by default. We use
-- `NOT VALID` to add the constraint without retroactively validating: future inserts/updates
-- are checked; existing rows are grandfathered as-is. This is the documented PostgreSQL
-- pattern for tightening a CHECK constraint when historical rows would otherwise violate it.
-- See https://www.postgresql.org/docs/15/sql-altertable.html — "ADD table_constraint [ NOT VALID ]".

ALTER TABLE speaker_status_history DROP CONSTRAINT IF EXISTS speaker_status_history_previous_status_check;
ALTER TABLE speaker_status_history ADD CONSTRAINT speaker_status_history_previous_status_check CHECK (previous_status IN (
    'identified', 'contacted', 'ready', 'invited', 'accepted', 'declined',
    'content_submitted', 'quality_reviewed'
)) NOT VALID;

ALTER TABLE speaker_status_history DROP CONSTRAINT IF EXISTS speaker_status_history_new_status_check;
ALTER TABLE speaker_status_history ADD CONSTRAINT speaker_status_history_new_status_check CHECK (new_status IN (
    'identified', 'contacted', 'ready', 'invited', 'accepted', 'declined',
    'content_submitted', 'quality_reviewed'
)) NOT VALID;

-- ============================================================================
-- Step 5: Drop the partial index that depends on is_tentative
-- ============================================================================
-- idx_speaker_pool_tentative was added in V45 line 23 with a WHERE predicate that
-- references is_tentative. PostgreSQL will refuse to drop the column while the index
-- exists, so the index must go first. IF EXISTS guards re-runs.

DROP INDEX IF EXISTS idx_speaker_pool_tentative;

-- ============================================================================
-- Step 6: Drop side-channel columns is_tentative + tentative_reason
-- ============================================================================
-- IF EXISTS guards re-runs. No CASCADE — there are no dependent views; if anything
-- unexpected depends on these columns the migration will fail loudly here, which is the
-- correct behaviour (we want to know about hidden coupling, not silently mass-drop it).

ALTER TABLE speaker_pool DROP COLUMN IF EXISTS is_tentative;
ALTER TABLE speaker_pool DROP COLUMN IF EXISTS tentative_reason;

-- ============================================================================
-- Step 7: Paranoia check — every speaker_pool row must be in one of the 8 valid states
-- ============================================================================
-- The CHECK constraint added in Step 3 already enforces this on future writes; this
-- block adds a one-shot SELECT assertion that the V93 mapping reached every legacy row
-- (and that no racing writer slipped in a legacy value between Steps 2 and 3). Because
-- Flyway wraps the whole file in a transaction, a failure here rolls back everything —
-- the migration either lands clean or not at all.

DO $$
DECLARE
    leftover_count BIGINT;
BEGIN
    SELECT COUNT(*)
      INTO leftover_count
      FROM speaker_pool sp
     WHERE sp.status NOT IN (
         'identified', 'contacted', 'ready', 'invited', 'accepted', 'declined',
         'content_submitted', 'quality_reviewed'
     );

    IF leftover_count > 0 THEN
        RAISE EXCEPTION
            'V93 paranoia check failed: % speaker_pool rows still have non-canonical status. ADR-009 §0.7 requires the 8-state allow-list.',
            leftover_count;
    END IF;
END
$$;

-- ============================================================================
-- Documentation comments (PostgreSQL COMMENT ON COLUMN — preserved across schema dumps)
-- ============================================================================

COMMENT ON CONSTRAINT speaker_pool_status_check ON speaker_pool IS
    '8-state ADR-009 allow-list (Story 11.B.3). SLOT_ASSIGNED, CONFIRMED, OVERFLOW, WITHDREW removed.';

COMMENT ON CONSTRAINT speaker_status_history_previous_status_check ON speaker_status_history IS
    '8-state ADR-009 allow-list (Story 11.B.3). Pre-V93 audit rows with legacy previous_status values are grandfathered.';

COMMENT ON CONSTRAINT speaker_status_history_new_status_check ON speaker_status_history IS
    '8-state ADR-009 allow-list (Story 11.B.3). All post-V93 transitions write canonical values only.';
