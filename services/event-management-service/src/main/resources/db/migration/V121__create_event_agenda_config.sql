-- V121: Per-event editable agenda config (Story 15.2)
-- Copy-on-edit override of the shared event_types template. One row per event, created
-- only when an organizer first edits the event type in slot assignment. Events with no
-- row resolve to the shared event_types template (resolver: override if present, else template).
-- ADR-003: events(id) is a within-service UUID FK (same service) — UUID FK + ON DELETE CASCADE is correct.

-- 1. Per-event override table — mirrors the CURRENT event_types knob columns (incl. the
--    duration columns added in V58) plus the new apéro knobs.
CREATE TABLE IF NOT EXISTS event_agenda_config (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id                  UUID NOT NULL UNIQUE REFERENCES events(id) ON DELETE CASCADE,
    min_slots                 INTEGER NOT NULL CHECK (min_slots > 0),
    max_slots                 INTEGER NOT NULL CHECK (max_slots >= min_slots),
    slot_duration             INTEGER NOT NULL CHECK (slot_duration >= 15),
    theoretical_slots_am      BOOLEAN NOT NULL DEFAULT true,
    break_slots               INTEGER NOT NULL DEFAULT 0 CHECK (break_slots >= 0),
    lunch_slots               INTEGER NOT NULL DEFAULT 0 CHECK (lunch_slots >= 0),
    default_capacity          INTEGER NOT NULL CHECK (default_capacity > 0),
    moderation_start_duration INTEGER NOT NULL DEFAULT 5  CHECK (moderation_start_duration >= 1),
    moderation_end_duration   INTEGER NOT NULL DEFAULT 5  CHECK (moderation_end_duration >= 1),
    break_duration            INTEGER NOT NULL DEFAULT 20 CHECK (break_duration >= 1),
    lunch_duration            INTEGER NOT NULL DEFAULT 60 CHECK (lunch_duration >= 1),
    aperitif_slots            INTEGER NOT NULL DEFAULT 0  CHECK (aperitif_slots >= 0),
    aperitif_duration         INTEGER NOT NULL DEFAULT 90 CHECK (aperitif_duration >= 1),
    aperitif_position         VARCHAR(10) NOT NULL DEFAULT 'end' CHECK (aperitif_position IN ('start', 'end')),
    typical_start_time        TIME,
    typical_end_time          TIME,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE event_agenda_config IS
    'Per-event copy-on-edit override of the event_types template (Story 15.2). One row per event, created on first "Edit event type". Absent row → resolve to shared event_types template.';

-- 2. Extend the shared template with the same apéro knobs (added to BOTH per spec §3).
ALTER TABLE event_types
    ADD COLUMN IF NOT EXISTS aperitif_slots    INTEGER NOT NULL DEFAULT 0  CHECK (aperitif_slots >= 0),
    ADD COLUMN IF NOT EXISTS aperitif_duration INTEGER NOT NULL DEFAULT 90 CHECK (aperitif_duration >= 1),
    ADD COLUMN IF NOT EXISTS aperitif_position VARCHAR(10) NOT NULL DEFAULT 'end'
        CHECK (aperitif_position IN ('start', 'end'));

-- 3. Template apéro defaults (Story 15.2, Nissim 2026-06-21): afternoon + evening default
--    apéro ON @ 90 min at end; full_day stays OFF. Intended behaviour change for afternoon/evening.
UPDATE event_types SET aperitif_slots = 1, aperitif_duration = 90, aperitif_position = 'end'
    WHERE type IN ('afternoon', 'evening');
UPDATE event_types SET aperitif_slots = 0, aperitif_duration = 90, aperitif_position = 'end'
    WHERE type = 'full_day';

COMMENT ON COLUMN event_types.aperitif_slots IS 'Apéro on/off count (0=off, ≥1=on) — afternoon/evening default 1, full_day 0';
COMMENT ON COLUMN event_types.aperitif_duration IS 'Apéro duration in minutes (default 90)';
COMMENT ON COLUMN event_types.aperitif_position IS 'Apéro placement: start (after moderation-start) or end (after moderation-end, default)';
