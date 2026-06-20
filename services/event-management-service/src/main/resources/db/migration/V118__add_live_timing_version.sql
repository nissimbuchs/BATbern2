-- Story 15.1: Replace WebSockets with REST polling for live agenda control.
--
-- A monotonic, per-event version for the live-timing snapshot. Bumped on every
-- timing action (END_SESSION / EXTEND_SESSION / DELAY_TO_PREVIOUS) so that:
--   * polling consumers can detect change (drives the ETag / If-None-Match → 304 path), and
--   * the value is read from the DB rather than per-task in-memory state, so successive
--     polls landing on different Fargate tasks see identical, monotonically-increasing
--     versions (AC3).
--
-- Distinct from the existing `events.version` JPA optimistic-lock column: that one only
-- moves when the Event row itself is updated, whereas extend/delay actions mutate
-- `sessions` rows (not the event), so it cannot serve as the live-timing version.
--
-- Additive + backfilled with a default; no behaviour change until the first action.

ALTER TABLE events
    ADD COLUMN live_timing_version BIGINT NOT NULL DEFAULT 0;
